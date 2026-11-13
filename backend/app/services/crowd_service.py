"""Segment-level crowd prediction served to the journey planner and the API.

Method (hierarchical adjustment, evaluated in ml_engine/segment_calibration.py):
    H_s[daytype, b]   segment's own historical demand (resolved e-tickets, 1-24 Oct)
    F_r[d, b]         route-direction forecast (LightGBM, with 80% conformal interval)
    H_r[daytype, b]   route's historical demand, same basis as F
    expected segment demand  L = H_s * (F_r + k) / (H_r + k)        k = RATIO_SHRINK_K
    demand per scheduled departure  DPD = L / scheduled departures  <- the crowd metric
Fallback hierarchy (fallback_level on every prediction):
    1 segment history adjusted by the route forecast
    3 segment history only (route has no forecast)
    5 insufficient evidence -> category UNKNOWN (never treated as low)
(Level 2 route-share spreading was evaluated and dropped: it was less accurate than history.
Level 4, a network-wide baseline, is not used: e-ticket adoption differs by route.)
Evidence level comes from the number of supporting tickets for the segment/daytype/band;
thresholds are derived from test-week accuracy by support (segment_calibration.json).
"""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from datetime import date
from pathlib import Path

import numpy as np

from app.demand_engine.route_demand import HOLIDAYS
from app.demand_engine.segment_demand import TIME_BANDS
from app.ml_engine.forecast import RouteForecasts
from app.ml_engine.segment_calibration import RATIO_SHRINK_K

CATEGORIES = ["LOW", "MODERATE", "HIGH", "VERY_HIGH"]
METRIC = "demand_per_scheduled_departure"
METRIC_DEFINITION = (
    "Expected e-ticketed passengers crossing this segment per scheduled bus departure in the "
    "time bucket. A relative pressure index: e-tickets are a sample of all riders, so this is "
    "not an occupancy count."
)


def band_index_of_hour(h: int) -> int:
    for i, (a, b, _) in enumerate(TIME_BANDS):
        if a <= h < b:
            return i
    return len(TIME_BANDS) - 1


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
    """All lookups are numpy arrays indexed by segment number (~45 MB), loaded once."""

    def __init__(self, processed_dir: Path, artifacts_dir: Path):
        P = processed_dir
        self.forecasts = RouteForecasts(P / "route_forecasts.npz")
        B = self.forecasts.pred.shape[2]
        self.bucket_minutes = 1440 // B
        rh = np.load(P / "route_history.npz")
        assert (rh["routes"] == self.forecasts.routes).all(), "route_history does not match forecasts"
        self.route_hist = rh["hist"]                       # [R, 3, B]

        a = np.load(P / "crowd_arrays.npz")             # built by segment_calibration.build_crowd_arrays
        self.seg_index = {s: i for i, s in enumerate(a["segment_ids"])}
        self.seg_route = a["seg_route"]
        self.seg_route_idx = a["route_idx"]
        self.deps, self.hist, self.support = a["deps"], a["hist"], a["support"]

        cal = json.loads((artifacts_dir / "segment_calibration.json").read_text())
        self.calibration = cal
        self.thresholds = cal["category_thresholds"]
        self.cdf_knots = np.asarray(cal["dpd_cdf_knots"])
        self.evidence_cut = cal["evidence_support_thresholds"]

    # ------------------------------------------------------------------------------
    @staticmethod
    def daytype(d: date) -> int:
        if d in HOLIDAYS or d.weekday() == 6:
            return 2
        return 1 if d.weekday() == 5 else 0

    def category(self, dpd: float) -> str:
        return CATEGORIES[int(np.searchsorted(self.thresholds, dpd, side="right"))]

    def crowd_index(self, dpd: float) -> float:
        return float(np.interp(dpd, self.cdf_knots, np.linspace(0, 1, len(self.cdf_knots))))

    def _evidence(self, support: int) -> str:
        if support >= self.evidence_cut["HIGH"]:
            return "HIGH"
        if support >= self.evidence_cut["MEDIUM"]:
            return "MEDIUM"
        return "LOW"

    def predict_all(self, d: date, bucket: int) -> dict[str, np.ndarray]:
        """Vectorised `predict` for every segment at once (same formulas): DPD (NaN = UNKNOWN),
        fallback level and supporting tickets. Used for network maps / hotspots."""
        dt = self.daytype(d)
        h = self.hist[:, dt, bucket].astype(float)
        deps = self.deps[:, bucket].astype(float)
        r = self.seg_route_idx
        has = r >= 0
        s, _ = self.forecasts.slot_for(d)
        p = np.where(has, self.forecasts.pred[np.clip(r, 0, None), s, bucket], np.nan)
        hr = np.where(has, self.route_hist[np.clip(r, 0, None), dt, bucket], np.nan)
        k = RATIO_SHRINK_K
        L = np.where(has, h * (p + k) / (hr + k), h)
        with np.errstate(divide="ignore", invalid="ignore"):
            v = np.where(deps > 0, L / deps, np.nan)
        support = self.support[:, dt, band_index_of_hour(bucket * self.bucket_minutes // 60)]
        return {"value": v, "fallback_level": np.where(np.isnan(v), 5, np.where(has, 1, 3)),
                "support": support, "departures": deps}

    def predict(self, segment_id: str, d: date, bucket: int) -> SegmentPrediction:
        i = self.seg_index.get(segment_id)
        route_id = str(self.seg_route[i]) if i is not None else segment_id.split(":")[0]
        deps = int(self.deps[i, bucket]) if i is not None else 0
        dt = self.daytype(d)
        h = float(self.hist[i, dt, bucket]) if i is not None else float("nan")
        support = int(self.support[i, dt, band_index_of_hour(bucket * self.bucket_minutes // 60)]) if i is not None else 0
        hist_dpd = None if (np.isnan(h) or deps == 0) else h / deps
        base = dict(segment_id=segment_id, route_id=route_id, bucket=bucket, scheduled_departures=deps,
                    historical_value=hist_dpd, support=support)
        if deps == 0 or np.isnan(h):
            return SegmentPrediction(**base, value=None, lower=None, upper=None, expected_segment_demand=None,
                                     category="UNKNOWN", crowd_index=None, unusualness_pct=None, evidence="INSUFFICIENT",
                                     fallback_level=5,
                                     method="no_scheduled_departures_in_bucket" if deps == 0 else "insufficient_evidence")
        r = int(self.seg_route_idx[i])
        if r >= 0:
            p, lo, hi = self.forecasts.get(self.forecasts.routes[r], d, bucket)
            hr = float(self.route_hist[r, dt, bucket])
            k = RATIO_SHRINK_K
            L, Llo, Lhi = h * (p + k) / (hr + k), h * (lo + k) / (hr + k), h * (hi + k) / (hr + k)
            level, method = 1, "segment_history_x_route_forecast_ratio"
        else:
            L = Llo = Lhi = h
            level, method = 3, "segment_historical_average"
        v = L / deps
        unusual = round(100 * (v - hist_dpd) / hist_dpd, 1) if hist_dpd else None
        return SegmentPrediction(**base, value=v, lower=min(Llo, L) / deps, upper=max(Lhi, L) / deps,
                                 expected_segment_demand=L, category=self.category(v), crowd_index=self.crowd_index(v),
                                 unusualness_pct=unusual, evidence=self._evidence(support) if level == 1 else "LOW",
                                 fallback_level=level, method=method)
