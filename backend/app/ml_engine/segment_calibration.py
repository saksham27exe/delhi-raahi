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


RATIO_SHRINK_K = 2.0  # passengers; fixed a priori (not tuned on the test week)


def weighted_quantile(values: np.ndarray, weights: np.ndarray, q: float) -> float:
    """Quantile of `values` where each value counts `weights` times (inverse weighted CDF)."""
    m = weights > 0
    v, w = values[m], weights[m]
    order = np.argsort(v)
    v, cw = v[order], np.cumsum(w[order])
    return float(v[min(np.searchsorted(cw, q * cw[-1]), len(v) - 1)])


def build_crowd_arrays(processed_dir: Path, hist: pd.DataFrame, fc: RouteForecasts) -> Path:
    """Dense per-segment arrays for the crowd service (loaded in seconds by the API)."""
    P = processed_dir
    B = fc.pred.shape[2]
    seg = pd.read_parquet(P / "segments.parquet", columns=["segment_id", "route_id"]).drop_duplicates("segment_id")
    idx = pd.Index(seg["segment_id"])
    n = len(idx)
    rm = pd.read_parquet(P / "route_mapping.parquet")
    rm = rm[rm["mapping_method"].isin(["exact_short_name", "exact_long_name", "alias"])]
    key_by_route = dict(zip(rm["gtfs_route_id"], rm["route_key"]))
    route_idx = np.array([fc.route_index.get(key_by_route.get(r), -1) for r in seg["route_id"]], np.int32)

    deps = np.zeros((n, B), np.int16)
    sup = pd.read_parquet(P / "segment_supply.parquet")
    i = idx.get_indexer(sup["segment_id"])
    ok = i >= 0
    deps[i[ok], sup["bucket"].to_numpy()[ok]] = np.minimum(sup["departures"].to_numpy()[ok], 32767)

    h = np.full((n, 3, B), np.nan, np.float32)
    i = idx.get_indexer(hist["segment_id"])
    ok = i >= 0
    h[i[ok], hist["daytype"].to_numpy()[ok], hist["bucket"].to_numpy()[ok]] = hist["seg_demand"].to_numpy()[ok]

    bands = [n_ for _, _, n_ in TIME_BANDS]
    sb = pd.read_parquet(P / "segment_band_daily.parquet", columns=["segment_id", "daytype", "band", "support", "service_date"])
    sb = sb[(pd.to_datetime(sb["service_date"]) >= pd.Timestamp(CAL_FROM)) & (pd.to_datetime(sb["service_date"]) <= pd.Timestamp(CAL_TO))]
    sb = sb.groupby(["segment_id", "daytype", "band"], as_index=False)["support"].sum()
    support = np.zeros((n, 3, len(bands)), np.int32)
    i = idx.get_indexer(sb["segment_id"])
    ok = i >= 0
    support[i[ok], sb["daytype"].to_numpy()[ok], sb["band"].map(bands.index).to_numpy()[ok]] = sb["support"].to_numpy()[ok]

    out = P / "crowd_arrays.npz"
    np.savez_compressed(out, segment_ids=idx.to_numpy().astype(str), seg_route=seg["route_id"].to_numpy().astype(str),
                        route_idx=route_idx, deps=deps, hist=h, support=support)
    return out


def route_ratio(route_fc: np.ndarray, route_hist: np.ndarray, k: float = RATIO_SHRINK_K) -> np.ndarray:
    """Model's expected route-level deviation from history, shrunk towards 1 when volumes are small."""
    return (np.nan_to_num(route_fc) + k) / (np.nan_to_num(route_hist) + k)


def build_route_history(processed_dir: Path, fc: RouteForecasts) -> np.ndarray:
    """Mean observed route passengers per (route, daytype, bucket) over CAL_FROM..CAL_TO, in forecast route order."""
    from app.demand_engine.route_demand import DemandPanel
    panel = DemandPanel.load(processed_dir / "route_panel.npz")
    days = pd.to_datetime(panel.dates)
    in_cal = (days >= CAL_FROM) & (days <= CAL_TO)
    dts = np.array([_daytype(d.date()) for d in days])
    H = np.zeros((len(fc.routes), 3, panel.buckets_per_day))
    pos = {r: i for i, r in enumerate(panel.routes)}
    order = np.array([pos.get(r, -1) for r in fc.routes])
    for dt in (0, 1, 2):
        m = in_cal & (dts == dt)
        if m.any():
            h = panel.y[:, m, :].mean(axis=1)
            H[order >= 0, dt, :] = h[order[order >= 0]]
    return H


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
    hist[["segment_id", "daytype", "bucket", "seg_demand", "dpd", "departures"]].to_parquet(hist_path, index=False)
    svc = hist[hist["service_hours"]]
    # passenger-weighted: categories describe the crowding a typical rider experiences,
    # not a typical (often empty) segment-time slot
    w = svc["seg_demand"].to_numpy()
    thresholds = [float(weighted_quantile(svc["dpd"].to_numpy(), w, q)) for q in (0.5, 0.8, 0.95)]
    knots = [float(weighted_quantile(svc["dpd"].to_numpy(), w, q)) for q in np.linspace(0, 1, 101)]
    for i in range(1, len(knots)):  # strictly increasing for interpolation
        knots[i] = max(knots[i], knots[i - 1] + 1e-9)
    build_crowd_arrays(P, hist, fc)

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
    # route history H[r, daytype, bucket] over the calibration window (same basis as the forecast)
    route_hist = build_route_history(P, fc)
    np.savez_compressed(P / "route_history.npz", routes=fc.routes, hist=route_hist)
    ridx = obs["route_key"].map(fc.route_index)
    has_f = ridx.notna()
    obs["route_fc"], obs["route_hist"] = np.nan, np.nan
    ix = (ridx[has_f].astype(int), obs.loc[has_f, "daytype"].astype(int), obs.loc[has_f, "bucket"].astype(int))
    obs.loc[has_f, "route_fc"] = fmean[ix]
    obs.loc[has_f, "route_hist"] = route_hist[ix]
    obs = obs.merge(hist[["segment_id", "daytype", "bucket", "seg_demand"]].rename(columns={"seg_demand": "hist_demand"}),
                    on=["segment_id", "daytype", "bucket"], how="left")
    has_h = obs["hist_demand"].notna()
    # level 1: segment history scaled by the model's route-level deviation; level 3: history only
    obs["level"] = np.where(has_f & has_h, 1, np.where(has_h, 3, 5))
    ratio = route_ratio(obs["route_fc"].to_numpy(), obs["route_hist"].to_numpy())
    obs["pred_demand"] = np.where(obs["level"] == 1, obs["hist_demand"] * ratio,
                                  np.where(obs["level"] == 3, obs["hist_demand"], np.nan))
    ev = obs[obs["level"] < 5].copy()
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
    cal = {"calibration_period": [CAL_FROM, CAL_TO], "ratio_shrink_k": RATIO_SHRINK_K,
           "category_thresholds": thresholds, "category_names": ["LOW", "MODERATE", "HIGH", "VERY_HIGH"],
           "category_basis": "passenger-weighted percentiles p50/p80/p95 of historical DPD (service hours, 1-24 Oct): the crowding a typical rider experiences",
           "dpd_cdf_knots": knots, "evidence_support_thresholds": ev_cut,
           "historical_dpd_path": str(hist_path)}
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    (artifacts_dir / "segment_calibration.json").write_text(json.dumps(cal, indent=2))
    (metrics_dir / "segment_evaluation.json").write_text(json.dumps(result, indent=2))
    return {"calibration": {k: v for k, v in cal.items() if k != "dpd_cdf_knots"}, "evaluation": result}
