"""Segment-level crowd prediction served to the journey planner and the API.

For segment s on route r at date d, bucket b:
    route forecast   F[r, d, b]            (LightGBM, passengers/bucket, with 80% interval)
    segment share    share[s, daytype, band]  (fraction of route passengers crossing s)
    supply           dep[s, b]             (scheduled departures crossing s in b)
    expected segment demand  L = F * share
    demand per scheduled departure  DPD = L / dep      <- the crowd metric
Fallback hierarchy (recorded per prediction as fallback_level):
    1 segment      share supported by >= MIN_SEGMENT_SUPPORT tickets
    2 route        route forecast spread with the route's average share in that band
    3 historical   route not in the model panel; segment's own historical average
    5 insufficient no defensible estimate -> category UNKNOWN (never treated as low)
(Level 4, a network-wide baseline, is intentionally not used: e-ticket adoption differs by
route, so a route with no e-tickets says nothing about how crowded it is.)
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd

from app.demand_engine.segment_demand import TIME_BANDS
from app.ml_engine.forecast import RouteForecasts

CATEGORIES = ["LOW", "MODERATE", "HIGH", "VERY_HIGH"]
METRIC = "demand_per_scheduled_departure"
METRIC_DEFINITION = (
    "Expected e-ticketed passengers crossing this segment per scheduled bus departure in the "
    "time bucket. A relative pressure index: e-tickets are a sample of all riders, so this is "
    "not an occupancy count."
)
MIN_SEGMENT_SUPPORT = 20


def band_of_hour(h: int) -> str:
    for a, b, n in TIME_BANDS:
        if a <= h < b:
            return n
    return "night"


@dataclass
class SegmentPrediction:
    segment_id: str
    route_id: str
    bucket: int
    value: float | None            # DPD
    lower: float | None
    upper: float | None
    expected_segment_demand: float | None
    scheduled_departures: int
    category: str                  # LOW..VERY_HIGH | UNKNOWN
    crowd_index: float | None      # 0..1 network percentile of DPD
    historical_value: float | None  # historical avg DPD, same daytype & bucket
    unusualness_pct: float | None   # (value - historical)/historical * 100
    evidence: str                  # HIGH | MEDIUM | LOW | INSUFFICIENT
    support: int
    fallback_level: int
    method: str

    def to_dict(self) -> dict:
        return asdict(self)


class CrowdService:
    def __init__(self, processed_dir: Path, artifacts_dir: Path, shares: pd.DataFrame | None = None,
                 calibration: dict | None = None):
        P = processed_dir
        self.forecasts = RouteForecasts(P / "route_forecasts.npz")
        self.bucket_minutes = 1440 // self.forecasts.pred.shape[2]
        seg = pd.read_parquet(P / "segments.parquet").drop_duplicates("segment_id")
        self.segment_route = dict(zip(seg["segment_id"], seg["route_id"]))
        self.segment_stops = dict(zip(seg["segment_id"], zip(seg["from_stop_id"], seg["to_stop_id"])))
        rm = pd.read_parquet(P / "route_mapping.parquet")
        rm = rm[rm["mapping_method"].isin(["exact_short_name", "exact_long_name", "alias"])]
        self.route_key = dict(zip(rm["gtfs_route_id"], rm["route_key"]))
        self.route_conf = dict(zip(rm["gtfs_route_id"], rm["mapping_confidence"]))

        sh = shares if shares is not None else pd.read_parquet(P / "segment_shares.parquet")
        self.share = {(r.segment_id, int(r.daytype), r.band): (float(r.share), int(r.support))
                      for r in sh.itertuples(index=False)}
        rs = sh.groupby(["route_id", "daytype", "band"]).agg(share=("share", "mean")).reset_index()
        self.route_avg_share = {(r.route_id, int(r.daytype), r.band): float(r.share) for r in rs.itertuples(index=False)}

        sup = pd.read_parquet(P / "segment_supply.parquet")
        self.supply = {(r.segment_id, int(r.bucket)): int(r.departures) for r in sup.itertuples(index=False)}

        cal = calibration or json.loads((artifacts_dir / "segment_calibration.json").read_text())
        self.calibration = cal
        self.thresholds = cal["category_thresholds"]          # 3 ascending DPD cut points
        self.cdf_knots = np.asarray(cal["dpd_cdf_knots"])     # DPD at percentiles 0..100
        self.evidence_cut = cal["evidence_support_thresholds"]  # {"HIGH": n, "MEDIUM": n}
        hist = cal.get("historical_dpd_path")
        self.hist = {}
        if hist and Path(hist).exists():
            h = pd.read_parquet(hist)
            self.hist = {(r.segment_id, int(r.daytype), int(r.bucket)): float(r.dpd) for r in h.itertuples(index=False)}

    # ------------------------------------------------------------------------------
    @staticmethod
    def daytype(d: date) -> int:
        from app.demand_engine.route_demand import HOLIDAYS
        if d in HOLIDAYS or d.weekday() == 6:
            return 2
        return 1 if d.weekday() == 5 else 0

    def category(self, dpd: float) -> str:
        return CATEGORIES[int(np.searchsorted(self.thresholds, dpd, side="right"))]

    def crowd_index(self, dpd: float) -> float:
        return float(np.interp(dpd, self.cdf_knots, np.linspace(0, 1, len(self.cdf_knots))))

    def _evidence(self, level: int, support: int) -> str:
        if level == 1 and support >= self.evidence_cut["HIGH"]:
            return "HIGH"
        if level == 1 and support >= self.evidence_cut["MEDIUM"]:
            return "MEDIUM"
        if level in (1, 2, 3):
            return "LOW"
        return "INSUFFICIENT"

    def predict(self, segment_id: str, d: date, bucket: int) -> SegmentPrediction:
        route_id = self.segment_route.get(segment_id, "")
        deps = self.supply.get((segment_id, bucket), 0)
        dt = self.daytype(d)
        band = band_of_hour(bucket * self.bucket_minutes // 60)
        hist = self.hist.get((segment_id, dt, bucket))
        base = dict(segment_id=segment_id, route_id=route_id, bucket=bucket, scheduled_departures=deps,
                    historical_value=hist)
        unknown = SegmentPrediction(**base, value=None, lower=None, upper=None, expected_segment_demand=None,
                                    category="UNKNOWN", crowd_index=None, unusualness_pct=None,
                                    evidence="INSUFFICIENT", support=0, fallback_level=5, method="insufficient_evidence")
        if deps == 0:
            unknown.method = "no_scheduled_departures_in_bucket"
            return unknown
        rkey = self.route_key.get(route_id)
        f = self.forecasts.get(rkey, d, bucket) if rkey else None
        share_sup = self.share.get((segment_id, dt, band))
        if f is not None and share_sup and share_sup[1] >= MIN_SEGMENT_SUPPORT:
            share, support = share_sup
            level, method = 1, "segment_share_x_route_forecast"
        elif f is not None and (route_id, dt, band) in self.route_avg_share:
            share, support = self.route_avg_share[(route_id, dt, band)], (share_sup[1] if share_sup else 0)
            level, method = 2, "route_forecast_x_route_avg_share"
        elif hist is not None:
            v = hist
            return SegmentPrediction(**base, value=v, lower=None, upper=None, expected_segment_demand=v * deps,
                                     category=self.category(v), crowd_index=self.crowd_index(v), unusualness_pct=0.0,
                                     evidence="LOW", support=0, fallback_level=3, method="segment_historical_average")
        else:
            return unknown
        p, lo, hi = f
        L, Llo, Lhi = p * share, lo * share, hi * share
        v = L / deps
        unusual = None if not hist else round(100 * (v - hist) / hist, 1)
        return SegmentPrediction(**base, value=v, lower=Llo / deps, upper=Lhi / deps, expected_segment_demand=L,
                                 category=self.category(v), crowd_index=self.crowd_index(v), unusualness_pct=unusual,
                                 evidence=self._evidence(level, support), support=support, fallback_level=level,
                                 method=method)
