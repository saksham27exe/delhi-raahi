"""In-memory timetable network built once from the processed GTFS Parquet files.

Structures
  Pattern: one exact stop sequence of a route, with a [n_trips x n_stops] matrix of
           scheduled arrival/departure seconds (rows sorted by first departure).
  stop_patterns: stop_id -> [(pattern index, position)]
  footpaths: stop_id -> [(nearby stop_id, walk seconds)] within MAX_TRANSFER_WALK_M
Calendar: the Delhi feed has one all-days service; services are still checked per date
so a feed with real calendars/calendar_dates works too.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd

log = logging.getLogger(__name__)

WALK_SPEED_MPS = 1.2
MAX_TRANSFER_WALK_M = 300
EARTH_R = 6_371_000.0


def haversine_m(lat1, lon1, lat2, lon2):
    lat1, lon1, lat2, lon2 = map(np.radians, (lat1, lon1, lat2, lon2))
    a = np.sin((lat2 - lat1) / 2) ** 2 + np.cos(lat1) * np.cos(lat2) * np.sin((lon2 - lon1) / 2) ** 2
    return 2 * EARTH_R * np.arcsin(np.sqrt(a))


@dataclass
class Pattern:
    pattern_id: str
    route_id: str
    stops: list[str]
    trip_ids: np.ndarray        # [n_trips]
    service_ids: np.ndarray     # [n_trips]
    arr: np.ndarray             # [n_trips, n_stops] seconds after midnight
    dep: np.ndarray
    shape_id: str | None = None


@dataclass
class Network:
    stops: pd.DataFrame                       # indexed by stop_id
    routes: pd.DataFrame                      # indexed by route_id
    patterns: list[Pattern]
    stop_patterns: dict[str, list[tuple[int, int]]]
    footpaths: dict[str, list[tuple[str, float]]]
    calendar: pd.DataFrame | None
    calendar_dates: pd.DataFrame | None
    shapes: pd.DataFrame | None = None
    _active_cache: dict = field(default_factory=dict)

    # ---- calendar ---------------------------------------------------------------
    def active_services(self, d: date) -> set[str] | None:
        """Service ids running on date d. None means 'feed has no calendar: assume all run'."""
        if d in self._active_cache:
            return self._active_cache[d]
        if self.calendar is None and self.calendar_dates is None:
            return None
        if len(self._active_cache) > 400:
            self._active_cache.clear()
        active: set[str] = set()
        ymd = int(d.strftime("%Y%m%d"))
        if self.calendar is not None:
            dayname = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"][d.weekday()]
            c = self.calendar
            m = (c[dayname] == "1") & (c["start_date"].astype(int) <= ymd) & (c["end_date"].astype(int) >= ymd)
            active |= set(c.loc[m, "service_id"])
        if self.calendar_dates is not None:
            cd = self.calendar_dates[self.calendar_dates["date"].astype(int) == ymd]
            active |= set(cd.loc[cd["exception_type"] == "1", "service_id"])
            active -= set(cd.loc[cd["exception_type"] == "2", "service_id"])
        self._active_cache[d] = active
        return active

    def feed_validity(self) -> tuple[str | None, str | None]:
        if self.calendar is None or self.calendar.empty:
            return None, None
        return str(self.calendar["start_date"].min()), str(self.calendar["end_date"].max())

    # ---- spatial ----------------------------------------------------------------
    def nearby_stops(self, lat: float, lon: float, radius_m: float = 600, limit: int = 8) -> pd.DataFrame:
        s = self.stops
        d = haversine_m(lat, lon, s["stop_lat"].values, s["stop_lon"].values)
        idx = np.where(d <= radius_m)[0]
        out = s.iloc[idx].copy()
        out["distance_m"] = d[idx]
        return out.sort_values("distance_m").head(limit)

    def stop_name(self, stop_id: str) -> str:
        return str(self.stops.at[stop_id, "stop_name"]) if stop_id in self.stops.index else stop_id


def load_network(processed_dir: Path, use_cache: bool = True) -> Network:
    """Build the network, or load it from a pickle cache invalidated by GTFS parquet changes."""
    import pickle
    P = processed_dir
    cache = P / "network_cache.pkl"
    sources = [P / f"gtfs_{n}.parquet" for n in ("stops", "routes", "trips", "stop_times", "patterns")]
    stamp = [round(p.stat().st_mtime) for p in sources if p.exists()]
    if use_cache and cache.exists():
        try:
            with cache.open("rb") as f:
                cached_stamp, net = pickle.load(f)
            if cached_stamp == stamp:
                return net
        except Exception as e:  # noqa: BLE001 - a stale/corrupt cache is simply rebuilt
            log.warning("network cache unreadable (%s); rebuilding", e)
    net = _build_network(P)
    if use_cache:
        with cache.open("wb") as f:
            pickle.dump((stamp, net), f, protocol=pickle.HIGHEST_PROTOCOL)
    return net


def _build_network(processed_dir: Path) -> Network:
    P = processed_dir
    stops = pd.read_parquet(P / "gtfs_stops.parquet").dropna(subset=["stop_lat", "stop_lon"])
    stops = stops.drop_duplicates("stop_id").set_index("stop_id", drop=False)
    routes = pd.read_parquet(P / "gtfs_routes.parquet").set_index("route_id", drop=False)
    trips = pd.read_parquet(P / "gtfs_trips.parquet")
    st = pd.read_parquet(P / "gtfs_stop_times.parquet", columns=["trip_id", "stop_id", "arrival_s", "departure_s"])
    pat_stops = pd.read_parquet(P / "gtfs_patterns.parquet")

    st = st.merge(trips[["trip_id", "pattern_id", "service_id"]], on="trip_id", how="inner")
    st["pos"] = st.groupby("trip_id").cumcount()
    patterns: list[Pattern] = []
    stop_patterns: dict[str, list[tuple[int, int]]] = {}
    shape_by_pattern = trips.dropna(subset=["pattern_id"]).groupby("pattern_id")["shape_id"].first() if "shape_id" in trips else {}
    seq_by_pattern = pat_stops.sort_values(["pattern_id", "seq"]).groupby("pattern_id")["stop_id"].apply(list)
    route_by_pattern = pat_stops.groupby("pattern_id")["route_id"].first()

    svc_by_trip = dict(zip(trips["trip_id"], trips["service_id"]))
    st = st.sort_values(["pattern_id", "trip_id", "pos"]).reset_index(drop=True)
    pid_arr = st["pattern_id"].to_numpy()
    bounds = np.flatnonzero(np.r_[True, pid_arr[1:] != pid_arr[:-1], True])
    trip_col, pos_col = st["trip_id"].to_numpy(), st["pos"].to_numpy()
    arr_col, dep_col = st["arrival_s"].to_numpy(), st["departure_s"].to_numpy()

    for a, b in zip(bounds[:-1], bounds[1:]):
        pid = pid_arr[a]
        stops_seq = seq_by_pattern.get(pid)
        if stops_seq is None or len(stops_seq) < 2:
            continue
        n = len(stops_seq)
        sl = slice(a, b)
        tids, pos = trip_col[sl], pos_col[sl]
        first = pos == 0
        order = np.argsort(dep_col[sl][first], kind="stable")
        trip_order = tids[first][order]
        rows = pd.Index(trip_order).get_indexer(tids)
        ok = (rows >= 0) & (pos < n)
        arr = np.full((len(trip_order), n), np.nan)
        dep = np.full((len(trip_order), n), np.nan)
        arr[rows[ok], pos[ok]] = arr_col[sl][ok]
        dep[rows[ok], pos[ok]] = dep_col[sl][ok]
        svc = np.array([svc_by_trip[t] for t in trip_order])
        k = len(patterns)
        patterns.append(Pattern(pid, str(route_by_pattern[pid]), list(stops_seq), trip_order, svc, arr, dep,
                                shape_by_pattern.get(pid) if len(shape_by_pattern) else None))
        for pos, sid in enumerate(stops_seq):
            stop_patterns.setdefault(sid, []).append((k, pos))

    footpaths = _build_footpaths(stops)
    cal = _opt(P / "gtfs_calendar.parquet")
    cal_dates = _opt(P / "gtfs_calendar_dates.parquet")
    shapes = _opt(P / "gtfs_shapes.parquet")
    log.info("network: %d stops, %d patterns, %d footpath stops", len(stops), len(patterns), len(footpaths))
    return Network(stops, routes, patterns, stop_patterns, footpaths, cal, cal_dates, shapes)


def _opt(p: Path) -> pd.DataFrame | None:
    return pd.read_parquet(p) if p.exists() else None


def _build_footpaths(stops: pd.DataFrame) -> dict[str, list[tuple[str, float]]]:
    """Grid-bucketed neighbour search: stops within MAX_TRANSFER_WALK_M (straight line x 1.3 detour)."""
    lat = stops["stop_lat"].to_numpy()
    lon = stops["stop_lon"].to_numpy()
    ids = stops["stop_id"].to_numpy()
    cell = 0.004  # ~400 m
    keys = np.floor(lat / cell).astype(int) * 100000 + np.floor(lon / cell).astype(int)
    grid: dict[int, list[int]] = {}
    for i, k in enumerate(keys):
        grid.setdefault(int(k), []).append(i)
    out: dict[str, list[tuple[str, float]]] = {}
    for i in range(len(ids)):
        ci, cj = int(np.floor(lat[i] / cell)), int(np.floor(lon[i] / cell))
        cand = [j for di in (-1, 0, 1) for dj in (-1, 0, 1) for j in grid.get((ci + di) * 100000 + cj + dj, [])]
        cand = np.array([j for j in cand if j != i])
        if not len(cand):
            continue
        d = haversine_m(lat[i], lon[i], lat[cand], lon[cand]) * 1.3
        near = cand[d <= MAX_TRANSFER_WALK_M]
        if len(near):
            out[ids[i]] = [(ids[j], float(dd / WALK_SPEED_MPS)) for j, dd in zip(near, d[d <= MAX_TRANSFER_WALK_M])]
    return out
