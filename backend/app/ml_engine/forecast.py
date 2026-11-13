"""Batch inference: persist route-direction forecasts so the API never runs feature
engineering or retrains per request.

Day slots
  * every in-window day from the first forecastable day (one-week-ahead forecasts,
    used for the 'forecast vs observed' views);
  * 7 'virtual' days right after the window, one per weekday. Any date after the data
    window is served from the slot with the same weekday (stationarity assumption,
    stated in the API response as forecast_basis).
"""
from __future__ import annotations

import json
from pathlib import Path

import lightgbm as lgb
import numpy as np
import pandas as pd

from app.demand_engine.route_demand import DemandPanel
from app.ml_engine.features import FEATURES, HORIZON_DAYS, features_for_day
from app.ml_engine.train import conformal_interval


def build_forecasts(panel: DemandPanel, artifacts: Path, out_path: Path) -> dict:
    meta = json.loads((artifacts / "route_model_meta.json").read_text())
    point = lgb.Booster(model_file=str(artifacts / "route_lgbm.txt"))
    q = float(meta["conformal_q"])
    T = len(panel.dates)
    slots = list(range(HORIZON_DAYS, T)) + list(range(T, T + 7))
    slot_dates = [panel.dates[0] + np.timedelta64(t, "D") for t in slots]
    R, B = len(panel.routes), panel.buckets_per_day
    P = np.zeros((R, len(slots), B), np.float32)
    LO, HI = np.zeros_like(P), np.zeros_like(P)
    for i, t in enumerate(slots):
        f = features_for_day(panel, t, slot_dates[i])
        X = f[FEATURES]
        p = np.clip(point.predict(X), 0, None)
        lo, hi = conformal_interval(p, q)
        P[:, i, :], LO[:, i, :], HI[:, i, :] = p.reshape(R, B), lo.reshape(R, B), hi.reshape(R, B)
    np.savez_compressed(out_path, routes=panel.routes, slot_dates=np.array(slot_dates).astype("datetime64[D]").astype(str),
                        in_window=np.array([t < T for t in slots]), pred=P, lo=LO, hi=HI)
    return {"slots": len(slots), "routes": R, "buckets": B}


class RouteForecasts:
    def __init__(self, path: Path):
        z = np.load(path)
        self.routes = z["routes"]
        self.route_index = {r: i for i, r in enumerate(self.routes)}
        self.slot_dates = z["slot_dates"].astype("datetime64[D]")
        self.in_window = z["in_window"]
        self.pred, self.lo, self.hi = z["pred"], z["lo"], z["hi"]
        self.window_end = self.slot_dates[self.in_window][-1] if self.in_window.any() else None
        virt = np.where(~self.in_window)[0]
        self._virtual_by_dow = {pd.Timestamp(self.slot_dates[i]).dayofweek: i for i in virt}
        self._slot_by_date = {str(self.slot_dates[i]): i for i in np.where(self.in_window)[0]}

    def slot_for(self, d) -> tuple[int, str]:
        """Return (slot index, basis). basis: 'one_week_ahead_forecast' or 'recurring_weekday_forecast'."""
        key = str(np.datetime64(d, "D"))
        if key in self._slot_by_date:
            return self._slot_by_date[key], "one_week_ahead_forecast"
        return self._virtual_by_dow[pd.Timestamp(d).dayofweek], "recurring_weekday_forecast"

    def get(self, route_key: str, d, bucket: int) -> tuple[float, float, float] | None:
        i = self.route_index.get(route_key)
        if i is None:
            return None
        s, _ = self.slot_for(d)
        return float(self.pred[i, s, bucket]), float(self.lo[i, s, bucket]), float(self.hi[i, s, bucket])
