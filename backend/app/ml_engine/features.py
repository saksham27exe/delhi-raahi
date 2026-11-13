"""Leakage-safe features for route-direction demand forecasting.

Every feature for target day t uses only observations from days <= t - HORIZON_DAYS.
With HORIZON_DAYS = 7 the model is a genuine one-week-ahead forecaster, which is also
what the journey planner needs: commuters plan for days that have not happened yet.

For dates after the data window we build features for the next same-weekday day index
after the window ("virtual" t), so the same code path serves training and inference.
"""
from __future__ import annotations

import numpy as np
import pandas as pd

from app.demand_engine.route_demand import HOLIDAYS, DemandPanel

HORIZON_DAYS = 7
FEATURES = [
    "lag7", "lag14", "lag21", "lag28", "lag7_is_holiday", "sdow_mean", "sdow_std",
    "lag7_prev_b", "lag7_next_b", "daytype_mean_b", "daytype_mean_b_prev", "daytype_mean_b_next",
    "daytype_mean_day", "all_mean_b", "route_mean_day", "route_bucket_share", "n_hist_days",
    "dow", "is_weekend", "is_holiday", "bucket", "hour_sin", "hour_cos",
]


def day_types(dates: np.ndarray) -> np.ndarray:
    """0 = working weekday, 1 = Saturday, 2 = Sunday or holiday."""
    d = pd.to_datetime(dates)
    hol = np.array([x.date() in HOLIDAYS for x in d])
    return np.where((d.dayofweek == 6) | hol, 2, np.where(d.dayofweek == 5, 1, 0))


def _nanmean(a: np.ndarray, axis: int) -> np.ndarray:
    with np.errstate(invalid="ignore", divide="ignore"):
        cnt = np.sum(~np.isnan(a), axis=axis)
        s = np.nansum(a, axis=axis)
        return np.where(cnt > 0, s / np.maximum(cnt, 1), np.nan)


def features_for_day(panel: DemandPanel, t: int, target_date: np.datetime64 | None = None) -> pd.DataFrame:
    """Feature frame for every (route, bucket) on day index t (t may exceed the window)."""
    R, T, B = panel.y.shape
    y = panel.y
    if target_date is None:
        target_date = panel.dates[0] + np.timedelta64(t, "D")
    tdate = pd.Timestamp(target_date)
    hist_end = min(t - HORIZON_DAYS, T - 1)  # inclusive index of last usable day
    dtypes = day_types(panel.dates)
    t_type = int(day_types(np.array([np.datetime64(tdate.date())]))[0])
    is_hol_hist = np.array([pd.Timestamp(d).date() in HOLIDAYS for d in panel.dates])

    def lag(k: int) -> np.ndarray:
        s = t - k
        return y[:, s, :] if 0 <= s <= hist_end else np.full((R, B), np.nan, np.float32)

    lags = np.stack([lag(7), lag(14), lag(21), lag(28)])
    lag_valid_nonhol = np.array([0 <= t - k <= hist_end and not is_hol_hist[t - k] for k in (7, 14, 21, 28)])
    sd = lags[lag_valid_nonhol] if lag_valid_nonhol.any() else lags[:1] * np.nan
    sdow_mean = _nanmean(sd, 0)
    with np.errstate(invalid="ignore"):
        sdow_std = np.nanstd(sd, axis=0) if lag_valid_nonhol.sum() > 1 else np.full((R, B), np.nan)

    hist = y[:, : hist_end + 1, :] if hist_end >= 0 else np.zeros((R, 0, B), np.float32)
    same_type = dtypes[: hist_end + 1] == t_type
    if same_type.any():
        dt_mean_b = hist[:, same_type, :].mean(axis=1)
        dt_mean_day = hist[:, same_type, :].sum(axis=2).mean(axis=1)
    else:
        dt_mean_b = np.full((R, B), np.nan)
        dt_mean_day = np.full(R, np.nan)
    all_mean_b = hist.mean(axis=1) if hist.shape[1] else np.full((R, B), np.nan)
    route_mean_day = hist.sum(axis=2).mean(axis=1) if hist.shape[1] else np.full(R, np.nan)
    with np.errstate(invalid="ignore", divide="ignore"):
        share = all_mean_b / np.maximum(route_mean_day[:, None], 1e-6)

    l7 = lags[0]
    prev_b = np.concatenate([np.full((R, 1), np.nan), l7[:, :-1]], axis=1)
    next_b = np.concatenate([l7[:, 1:], np.full((R, 1), np.nan)], axis=1)
    dt_prev = np.concatenate([np.full((R, 1), np.nan), dt_mean_b[:, :-1]], axis=1)
    dt_next = np.concatenate([dt_mean_b[:, 1:], np.full((R, 1), np.nan)], axis=1)

    b = np.arange(B)
    hour = (b + 0.5) * panel.bucket_minutes / 60
    lag7_hol = float(0 <= t - 7 <= hist_end and is_hol_hist[t - 7])
    cols = {
        "lag7": l7, "lag14": lags[1], "lag21": lags[2], "lag28": lags[3],
        "lag7_is_holiday": np.full((R, B), lag7_hol),
        "sdow_mean": sdow_mean, "sdow_std": sdow_std, "lag7_prev_b": prev_b, "lag7_next_b": next_b,
        "daytype_mean_b": dt_mean_b, "daytype_mean_b_prev": dt_prev, "daytype_mean_b_next": dt_next,
        "daytype_mean_day": np.repeat(dt_mean_day[:, None], B, 1),
        "all_mean_b": all_mean_b, "route_mean_day": np.repeat(route_mean_day[:, None], B, 1),
        "route_bucket_share": share, "n_hist_days": np.full((R, B), hist_end + 1),
        "dow": np.full((R, B), tdate.dayofweek), "is_weekend": np.full((R, B), int(tdate.dayofweek >= 5)),
        "is_holiday": np.full((R, B), int(tdate.date() in HOLIDAYS)),
        "bucket": np.repeat(b[None, :], R, 0),
        "hour_sin": np.repeat(np.sin(2 * np.pi * hour / 24)[None, :], R, 0),
        "hour_cos": np.repeat(np.cos(2 * np.pi * hour / 24)[None, :], R, 0),
    }
    df = pd.DataFrame({k: np.asarray(v, dtype=np.float32).ravel() for k, v in cols.items()})
    df.insert(0, "route_idx", np.repeat(np.arange(R), B))
    df.insert(1, "t", t)
    if t < T:
        df["y"] = y[:, t, :].ravel()
    return df


def build_training_frame(panel: DemandPanel, t_from: int, t_to: int) -> pd.DataFrame:
    """Stack feature frames for day indices t_from..t_to inclusive (all within the window)."""
    return pd.concat([features_for_day(panel, t) for t in range(t_from, t_to + 1)], ignore_index=True)


def future_day_index(panel: DemandPanel, target_date: np.datetime64) -> int:
    """Map any date to a day index usable by features_for_day.

    Inside the window: its own index. After the window: the first index after the window
    with the same weekday (history = the most recent weeks, a stationarity assumption).
    """
    T = len(panel.dates)
    delta = int((np.datetime64(target_date, "D") - panel.dates[0]).astype(int))
    if delta < T:
        return max(delta, 0)
    last = panel.dates[-1]
    for k in range(1, 8):
        if pd.Timestamp(last + np.timedelta64(k, "D")).dayofweek == pd.Timestamp(target_date).dayofweek:
            return T - 1 + k
    raise AssertionError("unreachable")
