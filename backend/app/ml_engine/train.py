"""Train and evaluate route-direction demand forecasters with a chronological split.

Split (October 2026 window, day indices from 1 Oct):
    features need 7 days of history, so the first trainable day is 8 Oct
    train       8-21 Oct    model fitting
    validation 22-24 Oct    model selection, early stopping, interval calibration (CQR)
    test       25-31 Oct    final, untouched evaluation of the selected model
The served model is fitted on train+validation with the selected settings and is the
exact model whose test metrics are reported. Rolling-origin backtests are run as well.
"""
from __future__ import annotations

import json
import logging
import time
from datetime import datetime
from pathlib import Path

import lightgbm as lgb
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor

from app.demand_engine.route_demand import DemandPanel
from app.ml_engine import metrics as M
from app.ml_engine.features import FEATURES, build_training_frame, features_for_day

log = logging.getLogger(__name__)

MODEL_VERSION = "route-demand-v1"
SERVICE_BUCKET_HOURS = (5, 23)  # evaluation subset: 05:00-23:00
LGB_PARAMS = dict(
    objective="tweedie", tweedie_variance_power=1.3, learning_rate=0.05, num_leaves=63,
    min_data_in_leaf=200, feature_fraction=0.85, bagging_fraction=0.8, bagging_freq=1,
    lambda_l2=1.0, verbose=-1, seed=42, num_threads=0,
)
INTERVAL = (0.1, 0.9)  # nominal 80% prediction interval


def conformal_interval(p: np.ndarray, q: float) -> tuple[np.ndarray, np.ndarray]:
    """Interval p -/+ q*sqrt(p+1), clipped at zero. q comes from validation conformity scores."""
    half = q * np.sqrt(np.asarray(p) + 1)
    return np.clip(p - half, 0, None), p + half


def _day_index(panel: DemandPanel, iso: str) -> int:
    return int((np.datetime64(iso) - panel.dates[0]).astype(int))


def _service_mask(df: pd.DataFrame, bucket_minutes: int) -> np.ndarray:
    h = df["bucket"].values * bucket_minutes / 60
    return (h >= SERVICE_BUCKET_HOURS[0]) & (h < SERVICE_BUCKET_HOURS[1])


def _baselines(df: pd.DataFrame) -> dict[str, np.ndarray]:
    sdow = df["sdow_mean"].fillna(df["daytype_mean_b"]).fillna(0).values
    return {
        "naive_last_week": df["lag7"].fillna(0).values,
        "same_weekday_avg": sdow,
        "daytype_seasonal_avg": df["daytype_mean_b"].fillna(0).values,
    }


def _fit_lgb(tr: pd.DataFrame, va: pd.DataFrame | None, params: dict, rounds: int) -> lgb.Booster:
    dtr = lgb.Dataset(tr[FEATURES], tr["y"], free_raw_data=False)
    if va is None:
        return lgb.train(params, dtr, num_boost_round=rounds)
    dva = lgb.Dataset(va[FEATURES], va["y"], reference=dtr)
    return lgb.train(params, dtr, num_boost_round=rounds, valid_sets=[dva],
                     callbacks=[lgb.early_stopping(50, verbose=False)])


def _evaluate(df: pd.DataFrame, preds: dict[str, np.ndarray], bucket_minutes: int, thresholds: list[float]) -> dict:
    svc = _service_mask(df, bucket_minutes)
    y = df["y"].values
    out = {}
    for name, p in preds.items():
        out[name] = {
            "all_buckets": M.summary(y, p),
            "service_hours": M.summary(y[svc], p[svc]),
            "macro_f1_demand_class": round(M.macro_f1(M.classify(y[svc], thresholds), M.classify(p[svc], thresholds)), 4),
        }
    return out


def train(panel: DemandPanel, out_dir: Path, metrics_dir: Path) -> dict:
    t0 = time.time()
    out_dir.mkdir(parents=True, exist_ok=True)
    metrics_dir.mkdir(parents=True, exist_ok=True)
    bm = panel.bucket_minutes
    i_tr0, i_tr1 = _day_index(panel, "2026-10-08"), _day_index(panel, "2026-10-21")
    i_va0, i_va1 = _day_index(panel, "2026-10-22"), _day_index(panel, "2026-10-24")
    i_te0, i_te1 = _day_index(panel, "2026-10-25"), _day_index(panel, "2026-10-31")
    tr = build_training_frame(panel, i_tr0, i_tr1)
    va = build_training_frame(panel, i_va0, i_va1)
    te = build_training_frame(panel, i_te0, i_te1)
    log.info("frames: train %d, val %d, test %d rows", len(tr), len(va), len(te))

    svc_tr = _service_mask(tr, bm)
    pos = tr.loc[svc_tr, "y"]
    thresholds = [float(np.quantile(pos, q)) for q in (0.5, 0.8, 0.95)]

    # ---- model selection on validation ----------------------------------------
    booster = _fit_lgb(tr, va, LGB_PARAMS, 2000)
    best_iter = booster.best_iteration or 500
    rf = RandomForestRegressor(n_estimators=120, min_samples_leaf=20, max_features=0.5, n_jobs=-1, random_state=42)
    rf_sample = tr.sample(n=min(400_000, len(tr)), random_state=42)
    rf.fit(rf_sample[FEATURES].fillna(-1), rf_sample["y"])
    val_preds = {**_baselines(va), "random_forest": rf.predict(va[FEATURES].fillna(-1)),
                 "lightgbm": booster.predict(va[FEATURES], num_iteration=best_iter)}
    val_metrics = _evaluate(va, val_preds, bm, thresholds)
    selected = min(val_metrics, key=lambda k: val_metrics[k]["service_hours"]["mae"])
    log.info("validation MAE (service hours): %s -> selected %s",
             {k: v["service_hours"]["mae"] for k, v in val_metrics.items()}, selected)

    # ---- final fit on train+val ------------------------------------------------
    trva = pd.concat([tr, va], ignore_index=True)
    final = _fit_lgb(trva, None, LGB_PARAMS, best_iter)
    rf_final = RandomForestRegressor(**rf.get_params())
    s2 = trva.sample(n=min(400_000, len(trva)), random_state=42)
    rf_final.fit(s2[FEATURES].fillna(-1), s2["y"])

    # Locally adaptive split-conformal interval: score = |y - p| / sqrt(p + 1), calibrated on
    # validation predictions of the train-only model (count noise grows ~ sqrt(mean)).
    p_val = np.clip(booster.predict(va[FEATURES], num_iteration=best_iter), 0, None)
    conformity = np.abs(va["y"].values - p_val) / np.sqrt(p_val + 1)
    nominal = INTERVAL[1] - INTERVAL[0]
    n_cal = len(conformity)
    cqr_q = float(np.quantile(conformity, min(1.0, np.ceil((n_cal + 1) * nominal) / n_cal)))

    # ---- untouched test ----------------------------------------------------------
    p_te = np.clip(final.predict(te[FEATURES]), 0, None)
    lo_te, hi_te = conformal_interval(p_te, cqr_q)
    test_preds = {**_baselines(te), "random_forest": rf_final.predict(te[FEATURES].fillna(-1)), "lightgbm": p_te}
    test_metrics = _evaluate(te, test_preds, bm, thresholds)
    svc_te = _service_mask(te, bm)
    coverage = {
        "nominal": nominal,
        "all_buckets": round(M.interval_coverage(te["y"].values, lo_te, hi_te), 4),
        "service_hours": round(M.interval_coverage(te["y"].values[svc_te], lo_te[svc_te], hi_te[svc_te]), 4),
        "conformal_q": round(cqr_q, 4),
        "mean_width_service_hours": round(float(np.mean(hi_te[svc_te] - lo_te[svc_te])), 3),
    }

    # How much of the error is irreducible count noise? If demand were exactly Poisson with
    # the predicted mean, the expected MAE would be E|Y - mu|. Simulated, fixed seed.
    rng = np.random.default_rng(0)
    mu = p_te[svc_te]
    noise_floor = float(np.mean(np.abs(rng.poisson(mu, size=(20, len(mu))) - mu)))
    # Accuracy at coarser aggregation (same predictions summed up)
    agg = te[["route_idx", "t", "bucket", "y"]].copy()
    agg["p"], agg["b"] = p_te, test_preds["daytype_seasonal_avg"]
    agg = agg[svc_te]
    agg["hour"] = agg["bucket"] * bm // 60
    coarse = {}
    for name, keys in {"route_hour": ["route_idx", "t", "hour"], "route_day": ["route_idx", "t"],
                       "network_bucket": ["t", "bucket"]}.items():
        g = agg.groupby(keys)[["y", "p", "b"]].sum()
        coarse[name] = {"lightgbm_wape": round(M.wape(g["y"].values, g["p"].values) or 0, 4),
                        "seasonal_avg_wape": round(M.wape(g["y"].values, g["b"].values) or 0, 4),
                        "n": int(len(g))}
    noise = {"poisson_noise_floor_mae_service_hours": round(noise_floor, 4),
             "lightgbm_mae_service_hours": test_metrics["lightgbm"]["service_hours"]["mae"],
             "mean_actual_per_bucket": round(float(te["y"].values[svc_te].mean()), 3),
             "coarser_aggregation_test": coarse}

    # rolling-origin backtest (expanding window, 1-day test blocks, LightGBM with fixed rounds)
    backtest = []
    for t_test in range(_day_index(panel, "2026-10-22"), i_te1 + 1, 3):
        fr = build_training_frame(panel, i_tr0, t_test - 1)
        m = _fit_lgb(fr, None, LGB_PARAMS, best_iter)
        ft = features_for_day(panel, t_test)
        svc = _service_mask(ft, bm)
        p = np.clip(m.predict(ft[FEATURES]), 0, None)
        b = ft["sdow_mean"].fillna(ft["daytype_mean_b"]).fillna(0).values
        backtest.append({"test_date": str(panel.dates[t_test]),
                         "lightgbm_mae": round(M.mae(ft["y"].values[svc], p[svc]), 4),
                         "baseline_same_weekday_mae": round(M.mae(ft["y"].values[svc], b[svc]), 4),
                         "lightgbm_wape": round(M.wape(ft["y"].values[svc], p[svc]) or 0, 4),
                         "baseline_same_weekday_wape": round(M.wape(ft["y"].values[svc], b[svc]) or 0, 4)})

    # ---- persist ---------------------------------------------------------------------
    final.save_model(str(out_dir / "route_lgbm.txt"), num_iteration=best_iter)
    for stale in out_dir.glob("route_lgbm_q*.txt"):
        stale.unlink()
    imp = dict(zip(FEATURES, final.feature_importance("gain").round(1).tolist()))

    te_out = te[["route_idx", "t", "bucket", "y"]].copy()
    te_out["route_key"] = panel.routes[te_out["route_idx"].values]
    te_out["date"] = panel.dates[te_out["t"].values].astype(str)
    te_out["pred"], te_out["lo"], te_out["hi"] = p_te, lo_te, hi_te
    te_out["baseline"] = test_preds["same_weekday_avg"]
    te_out.drop(columns=["route_idx", "t"]).to_parquet(metrics_dir / "test_predictions.parquet", index=False)

    meta = {
        "model_version": MODEL_VERSION,
        "model_type": "LightGBM (tweedie) with locally adaptive split-conformal 80% intervals",
        "target": "observed e-ticket passengers per route-direction per time bucket",
        "bucket_minutes": bm,
        "horizon_days": 7,
        "features": FEATURES,
        "training_period": [str(panel.dates[i_tr0]), str(panel.dates[i_va1])],
        "validation_period": [str(panel.dates[i_va0]), str(panel.dates[i_va1])],
        "test_period": [str(panel.dates[i_te0]), str(panel.dates[i_te1])],
        "dataset_version": "ondc_eticket_2026_10",
        "n_routes": int(len(panel.routes)),
        "boosting_rounds": int(best_iter),
        "params": LGB_PARAMS,
        "selected_on_validation": selected,
        "demand_class_thresholds": thresholds,
        "created_at": datetime.now().isoformat(timespec="seconds"),
        "train_seconds": round(time.time() - t0, 1),
    }
    report = {"meta": meta, "validation": val_metrics, "test": test_metrics,
              "interval_coverage_test": coverage, "noise_analysis": noise, "rolling_backtest": backtest, "feature_importance_gain": imp}
    (metrics_dir / "route_model_evaluation.json").write_text(json.dumps(report, indent=2))
    (out_dir / "route_model_meta.json").write_text(json.dumps({**meta, "conformal_q": cqr_q}, indent=2))
    return report
