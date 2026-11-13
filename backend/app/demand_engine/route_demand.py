"""Route-direction demand panel: observed passengers per (route, date, time bucket).

Metric definition (A. observed passenger demand):
    D[r, d, b] = sum of passenger_count over valid e-tickets on route-direction r
                 created on service date d in time bucket b (Asia/Kolkata).
Zero is a real observation (route active in the period, no ticket in that bucket);
routes below MIN_TICKETS over the whole window are excluded from the model panel and
handled by the fallback hierarchy instead.
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import date
from pathlib import Path

import duckdb
import numpy as np
import pandas as pd

MIN_TICKETS = 31  # ~1 ticket/day over the October window

# Gazetted holidays inside the data window (confirmed by network-wide demand dips, see docs/assumptions.md)
HOLIDAYS = {date(2026, 10, 2): "Gandhi Jayanti", date(2026, 10, 20): "Dussehra"}


@dataclass
class DemandPanel:
    routes: np.ndarray           # route keys (normalised ticket designation, e.g. '717AUP')
    dates: np.ndarray            # datetime64[D]
    buckets_per_day: int
    y: np.ndarray                # float32 [route, date, bucket] passengers
    tickets: np.ndarray          # int32  [route, date, bucket] ticket rows

    @property
    def bucket_minutes(self) -> int:
        return 1440 // self.buckets_per_day

    def save(self, path: Path) -> None:
        np.savez_compressed(path, routes=self.routes, dates=self.dates.astype("datetime64[D]").astype(str),
                            buckets_per_day=self.buckets_per_day, y=self.y, tickets=self.tickets)

    @classmethod
    def load(cls, path: Path) -> "DemandPanel":
        z = np.load(path, allow_pickle=False)
        return cls(z["routes"], z["dates"].astype("datetime64[D]"), int(z["buckets_per_day"]), z["y"], z["tickets"])


def build_route_panel(tickets_parquet: Path, bucket_minutes: int = 30, min_tickets: int = MIN_TICKETS) -> DemandPanel:
    bpd = 1440 // bucket_minutes
    con = duckdb.connect()
    df = con.execute(
        f"""
        WITH v AS (
            SELECT route_base || coalesce(direction, '') AS route_key, service_date,
                   (hour * 60 + minute) // {bucket_minutes} AS b, passenger_count
            FROM read_parquet(?) WHERE quality_status = 'valid'
        ), keep AS (
            SELECT route_key FROM v GROUP BY 1 HAVING count(*) >= {min_tickets}
        )
        SELECT route_key, service_date, b, sum(passenger_count) AS pax, count(*) AS n
        FROM v JOIN keep USING (route_key) GROUP BY ALL
        """,
        [str(tickets_parquet)],
    ).df()
    routes = np.array(sorted(df["route_key"].unique()))
    d0, d1 = df["service_date"].min(), df["service_date"].max()
    dates = np.arange(np.datetime64(d0, "D"), np.datetime64(d1, "D") + np.timedelta64(1, "D"))
    ri = pd.Index(routes).get_indexer(df["route_key"])
    di = (df["service_date"].values.astype("datetime64[D]") - dates[0]).astype(int)
    y = np.zeros((len(routes), len(dates), bpd), dtype=np.float32)
    n = np.zeros_like(y, dtype=np.int32)
    y[ri, di, df["b"].values] = df["pax"].values
    n[ri, di, df["b"].values] = df["n"].values
    return DemandPanel(routes, dates, bpd, y, n)


def route_profile(panel: DemandPanel, route_key: str) -> pd.DataFrame:
    """Mean observed passengers per bucket for weekday / Saturday / Sunday (holidays excluded)."""
    i = int(np.searchsorted(panel.routes, route_key))
    if i >= len(panel.routes) or panel.routes[i] != route_key:
        raise KeyError(route_key)
    dts = pd.to_datetime(panel.dates)
    hol = np.array([d.date() in HOLIDAYS for d in dts])
    out = {}
    for name, mask in {"weekday": (dts.dayofweek < 5) & ~hol, "saturday": dts.dayofweek == 5,
                       "sunday_holiday": (dts.dayofweek == 6) | hol}.items():
        out[name] = panel.y[i, mask].mean(axis=0)
    df = pd.DataFrame(out)
    df.insert(0, "bucket", np.arange(panel.buckets_per_day))
    return df
