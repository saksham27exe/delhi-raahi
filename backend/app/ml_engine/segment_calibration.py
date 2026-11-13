"""Calibrate crowd categories / evidence levels and evaluate segment-level predictions.

Everything used for calibration comes from the TRAIN+VALIDATION window (1-24 Oct); the
test week (25-31 Oct) is only used for evaluation.

Outputs
  models/artifacts/segment_calibration.json  thresholds, CDF knots, evidence thresholds
  data/processed/segment_hist_dpd.parquet    historical DPD per (segment, daytype, bucket)
  models/metrics/segment_evaluation.json     test-week segment-level accuracy
"""
from __future__ import annotations

import json
from datetime import date
from pathlib import Path

import duckdb
import numpy as np
import pandas as pd

from app.demand_engine.route_demand import HOLIDAYS
from app.demand_engine.segment_demand import TIME_BANDS, shares_sql
from app.ml_engine import metrics as M
from app.ml_engine.forecast import RouteForecasts

CAL_FROM, CAL_TO = "2026-10-01", "2026-10-24"
TEST_FROM, TEST_TO = "2026-10-25", "2026-10-31"
SERVICE_HOURS = (5, 23)
SUPPORT_BINS = [0, 10, 30, 100, 300, 1000, 10**9]


def _daytype(d: date) -> int:
    return 2 if (d in HOLIDAYS or d.weekday() == 6) else (1 if d.weekday() == 5 else 0)


def _days_per_type(d0: str, d1: str) -> dict[int, int]:
    ds = pd.date_range(d0, d1)
    out = {0: 0, 1: 0, 2: 0}
    for d in ds:
        out[_daytype(d.date())] += 1
    return out


def _band(hour: np.ndarray) -> np.ndarray:
    out = np.empty(len(hour), dtype=object)
    for a, b, n in TIME_BANDS:
        out[(hour >= a) & (hour < b)] = n
    return out


def _period_dpd(con, d0: str, d1: str, bucket_minutes: int) -> pd.DataFrame:
    """Mean DPD per (segment, daytype, bucket) over a period, zeros included."""
    ndays = _days_per_type(d0, d1)
    df = con.execute(f"""
        WITH dem AS (
            SELECT segment_id, CASE WHEN service_date IN ({",".join(f"DATE '{h}'" for h in HOLIDAYS)})
                        OR isodow(service_date)=7 THEN 2 WHEN isodow(service_date)=6 THEN 1 ELSE 0 END AS daytype,
                   bucket, sum(pax) AS pax
            FROM segment_demand WHERE service_date BETWEEN DATE '{d0}' AND DATE '{d1}' GROUP BY ALL
        ), active AS (SELECT DISTINCT segment_id FROM segment_demand)
        SELECT s.segment_id, s.bucket, s.departures, dt.daytype, coalesce(dem.pax, 0) AS pax
        FROM segment_supply s JOIN active USING (segment_id)
        CROSS JOIN (SELECT unnest([0,1,2]) AS daytype) dt
        LEFT JOIN dem ON dem.segment_id = s.segment_id AND dem.bucket = s.bucket AND dem.daytype = dt.daytype
        WHERE s.departures > 0
    """).df()
    df["n_days"] = df["daytype"].map(ndays)
    df = df[df["n_days"] > 0].copy()
    df["seg_demand"] = df["pax"] / df["n_days"]
    df["dpd"] = df["seg_demand"] / df["departures"]
    h = df["bucket"] * bucket_minutes / 60
    df["service_hours"] = (h >= SERVICE_HOURS[0]) & (h < SERVICE_HOURS[1])
    return df


def calibrate_and_evaluate(processed_dir: Path, artifacts_dir: Path, metrics_dir: Path) -> dict:
    P = processed_dir
    con = duckdb.connect()
    for t in ("segment_demand", "segment_supply", "segment_band_daily", "route_resolved_daily", "segments"):
        con.execute(f"CREATE VIEW {t} AS SELECT * FROM read_parquet('{(P / (t + '.parquet')).as_posix()}')")
    fc = RouteForecasts(P / "route_forecasts.npz")
    bm = 1440 // fc.pred.shape[2]

    # ---- calibration on 1-24 Oct --------------------------------------------------------
    hist = _period_dpd(con, CAL_FROM, CAL_TO, bm)
    hist_path = P / "segment_hist_dpd.parquet"
    hist[["segment_id", "daytype", "bucket", "dpd", "departures"]].to_parquet(hist_path, index=False)
    svc = hist[hist["service_hours"]]
    thresholds = [float(np.quantile(svc["dpd"], q)) for q in (0.5, 0.8, 0.95)]
    knots = [float(x) for x in np.quantile(svc["dpd"], np.linspace(0, 1, 101))]
    for i in range(1, len(knots)):  # strictly increasing for interpolation
        knots[i] = max(knots[i], knots[i - 1] + 1e-9)

    # ---- test-week evaluation (25-31 Oct) -------------------------------------------------
    obs = _period_dpd(con, TEST_FROM, TEST_TO, bm)
    obs = obs[obs["service_hours"]].copy()
    shares_tr = con.execute(shares_sql("segment_band_daily", "route_resolved_daily", CAL_FROM, CAL_TO)).df()
    seg_route = con.execute("SELECT DISTINCT segment_id, route_id FROM segments").df()
    rm = pd.read_parquet(P / "route_mapping.parquet")
    rm = rm[rm["mapping_method"].isin(["exact_short_name", "exact_long_name", "alias"])][["gtfs_route_id", "route_key"]]
    obs = obs.merge(seg_route, on="segment_id", how="left").merge(rm, left_on="route_id", right_on="gtfs_route_id", how="left")
    obs["band"] = _band((obs["bucket"] * bm // 60).to_numpy())
    obs = obs.merge(shares_tr[["segment_id", "daytype", "band", "share", "support"]], on=["segment_id", "daytype", "band"], how="left")
    ravg = shares_tr.groupby(["route_id", "daytype", "band"])["share"].mean().rename("route_avg_share").reset_index()
    obs = obs.merge(ravg, on=["route_id", "daytype", "band"], how="left")
    obs["support"] = obs["support"].fillna(0)

    # mean route forecast over the test days of each daytype
    test_days = pd.date_range(TEST_FROM, TEST_TO)
    R, _, B = fc.pred.shape
    fmean = np.zeros((R, 3, B))
    for dt in (0, 1, 2):
        slots = [fc.slot_for(d.date())[0] for d in test_days if _daytype(d.date()) == dt]
        if slots:
            fmean[:, dt, :] = fc.pred[:, slots, :].mean(axis=1)
    ridx = obs["route_key"].map(fc.route_index)
    has_f = ridx.notna()
    obs["route_fc"] = np.nan
    obs.loc[has_f, "route_fc"] = fmean[ridx[has_f].astype(int), obs.loc[has_f, "daytype"].astype(int), obs.loc[has_f, "bucket"].astype(int)]
    lvl1 = has_f & (obs["support"] >= 20) & obs["share"].notna()
    lvl2 = has_f & ~lvl1 & obs["route_avg_share"].notna()
    obs["level"] = np.where(lvl1, 1, np.where(lvl2, 2, 5))
    obs["pred_demand"] = np.where(lvl1, obs["route_fc"] * obs["share"], np.where(lvl2, obs["route_fc"] * obs["route_avg_share"], np.nan))
    obs = obs.merge(hist[["segment_id", "daytype", "bucket", "seg_demand"]].rename(columns={"seg_demand": "hist_demand"}),
                    on=["segment_id", "daytype", "bucket"], how="left")
    ev = obs[obs["level"] < 5].copy()
    ev["hist_demand"] = ev["hist_demand"].fillna(0)
    y, p, b = ev["seg_demand"].to_numpy(), ev["pred_demand"].to_numpy(), ev["hist_demand"].to_numpy()
    y_cls = M.classify(ev["dpd"].to_numpy(), thresholds)
    p_cls = M.classify(p / ev["departures"].to_numpy(), thresholds)
    b_cls = M.classify(b / ev["departures"].to_numpy(), thresholds)

    by_support = []
    ev["support_bin"] = pd.cut(ev["support"], SUPPORT_BINS, right=False)
    for k, g in ev.groupby("support_bin", observed=True):
        by_support.append({"support_bin": str(k), "rows": int(len(g)),
                           "model_wape": round(M.wape(g["seg_demand"].values, g["pred_demand"].values) or 0, 4),
                           "hist_avg_wape": round(M.wape(g["seg_demand"].values, g["hist_demand"].values) or 0, 4)})
    # evidence thresholds: lowest support bin from which WAPE stays below targets
    def cut(target: float) -> int:
        lows = [int(str(r["support_bin"]).strip("[(").split(",")[0]) for r in by_support]
        ok = [lo for lo, r in zip(lows, by_support) if r["model_wape"] <= target]
        return min(ok) if ok else 10**9
    ev_cut = {"HIGH": max(cut(0.45), 100), "MEDIUM": max(cut(0.7), 20)}

    result = {
        "calibration_period": [CAL_FROM, CAL_TO], "test_period": [TEST_FROM, TEST_TO],
        "metric": "expected e-ticket passengers crossing a segment per bucket (segment demand) and per scheduled departure (DPD)",
        "rows_evaluated": int(len(ev)),
        "rows_insufficient_evidence": int((obs["level"] == 5).sum()),
        "fallback_usage": {str(k): int(v) for k, v in obs["level"].value_counts().sort_index().items()},
        "model": M.summary(y, p), "historical_average_baseline": M.summary(y, b),
        "category_macro_f1": {"model": round(M.macro_f1(y_cls, p_cls), 4), "historical_average": round(M.macro_f1(y_cls, b_cls), 4)},
        "accuracy_by_support": by_support,
    }
    cal = {"category_thresholds": thresholds, "category_names": ["LOW", "MODERATE", "HIGH", "VERY_HIGH"],
           "category_basis": "network percentiles p50/p80/p95 of historical DPD (service hours, 1-24 Oct)",
           "dpd_cdf_knots": knots, "evidence_support_thresholds": ev_cut,
           "historical_dpd_path": str(hist_path)}
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    (artifacts_dir / "segment_calibration.json").write_text(json.dumps(cal, indent=2))
    (metrics_dir / "segment_evaluation.json").write_text(json.dumps(result, indent=2))
    return {"calibration": {k: v for k, v in cal.items() if k != "dpd_cdf_knots"}, "evaluation": result}
