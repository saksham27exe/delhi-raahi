"""Live bus positions (GTFS-RT VehiclePositions) matched to the static timetable.

What the Delhi feed carries (checked on a live snapshot, Nov 2026):
  * route_id -> matches static GTFS route_id (~94 %)
  * trip_id  -> runtime ids such as '1597_23_48_8177'; they do NOT exist in the static feed
  * trip.start_time / start_date, position, vehicle timestamp; no stop_id / stop sequence / occupancy
So each vehicle is matched by
  1. route_id -> that route's stop patterns,
  2. snapping the position onto each pattern's stop-to-stop polyline (nearest pattern wins),
  3. the scheduled trip of that pattern whose first departure is closest to trip.start_time (<= 30 min).
Delay = vehicle timestamp - scheduled time at the snapped position (interpolated between stops).
Arrival estimates at a stop = scheduled arrival of the matched trip + current delay
(labelled 'live_estimate'); buses not matched to a trip use the pattern's median running time.
If the feed is down or stale, arrivals fall back to the timetable (labelled 'scheduled').
"""
from __future__ import annotations

import logging
import threading
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timedelta
from typing import Callable
from zoneinfo import ZoneInfo

import numpy as np

from app.journey_engine.network import Network

log = logging.getLogger(__name__)
IST = ZoneInfo("Asia/Kolkata")

OFF_ROUTE_M = 300          # farther than this from every pattern of its route -> not used for arrivals
TRIP_MATCH_MAX_S = 30 * 60
MAX_PLAUSIBLE_DELAY_S = 60 * 60   # larger gaps mean a wrong trip match, not a delay
MIN_MOVING_KMH = 3.0       # unmatched buses slower than this (parked / layover) are not used for arrivals
STALE_S = 10 * 60          # a position older than this is not used for arrivals
FEED_STALE_S = 5 * 60      # feed considered down if no successful fetch for this long
M_PER_DEG_LAT = 111_320.0


@dataclass
class VehicleObs:
    vehicle_id: str
    route_id: str
    rt_trip_id: str
    start_time: str            # HH:MM:SS
    start_date: str            # YYYYMMDD
    lat: float
    lon: float
    speed_mps: float | None
    bearing: float | None
    timestamp: int             # unix seconds


@dataclass
class VehicleState:
    vehicle_id: str
    route_id: str
    route: str | None
    lat: float
    lon: float
    speed_kmh: float | None
    bearing: float | None
    timestamp: int
    age_s: int
    stale: bool
    pattern_idx: int | None
    progress: float | None     # stop position along the pattern (3.4 = 40 % from stop 3 to stop 4)
    off_route_m: float | None
    trip_row: int | None       # matched scheduled trip (row in the pattern matrix)
    scheduled_trip_id: str | None
    delay_s: float | None
    next_stop_id: str | None
    next_stop: str | None

    def public(self) -> dict:
        d = asdict(self)
        for k in ("pattern_idx", "trip_row"):
            d.pop(k)
        d["delay_min"] = None if self.delay_s is None else round(self.delay_s / 60, 1)
        d["progress"] = None if self.progress is None else round(self.progress, 2)
        d["off_route_m"] = None if self.off_route_m is None else round(self.off_route_m)
        d.pop("delay_s")
        return d


def parse_vehicle_positions(blob: bytes) -> tuple[int, list[VehicleObs]]:
    from google.transit import gtfs_realtime_pb2 as rt
    f = rt.FeedMessage()
    f.ParseFromString(blob)
    out = []
    for e in f.entity:
        if not e.HasField("vehicle") or not e.vehicle.HasField("position"):
            continue
        v = e.vehicle
        out.append(VehicleObs(
            vehicle_id=v.vehicle.id or v.vehicle.label or e.id, route_id=v.trip.route_id, rt_trip_id=v.trip.trip_id,
            start_time=v.trip.start_time, start_date=v.trip.start_date,
            lat=v.position.latitude, lon=v.position.longitude,
            speed_mps=v.position.speed if v.position.HasField("speed") else None,
            bearing=v.position.bearing if v.position.HasField("bearing") else None,
            timestamp=int(v.timestamp or f.header.timestamp)))
    return int(f.header.timestamp), out


def _hms(s: str) -> int | None:
    try:
        h, m, sec = (int(x) for x in s.split(":"))
        return h * 3600 + m * 60 + sec
    except (ValueError, AttributeError):
        return None


class LiveTracker:
    def __init__(self, net: Network):
        self.net = net
        self._geom: dict[int, tuple[np.ndarray, np.ndarray]] = {}
        self._median_off: dict[int, np.ndarray] = {}
        self.route_patterns: dict[str, list[int]] = {}
        for k, p in enumerate(net.patterns):
            self.route_patterns.setdefault(str(p.route_id), []).append(k)
        self.vehicles: dict[str, VehicleState] = {}
        self.feed_timestamp: int | None = None
        self.last_success: float | None = None
        self.last_error: str | None = None
        self.unmatched_routes = 0
        self._lock = threading.Lock()

    # ---- geometry ---------------------------------------------------------------
    def _pattern_xy(self, k: int) -> tuple[np.ndarray, np.ndarray]:
        if k not in self._geom:
            s = self.net.stops.loc[self.net.patterns[k].stops]
            self._geom[k] = (s["stop_lat"].to_numpy(float), s["stop_lon"].to_numpy(float))
        return self._geom[k]

    def snap(self, k: int, lat: float, lon: float) -> tuple[float, float]:
        """(progress in stop positions, distance in m) of the closest point on pattern k's stop polyline."""
        la, lo = self._pattern_xy(k)
        cos = np.cos(np.radians(lat))
        y, x = (la - lat) * M_PER_DEG_LAT, (lo - lon) * M_PER_DEG_LAT * cos
        ax, ay, bx, by = x[:-1], y[:-1], x[1:], y[1:]
        dx, dy = bx - ax, by - ay
        L2 = dx * dx + dy * dy
        t = np.clip(np.where(L2 > 0, -(ax * dx + ay * dy) / np.where(L2 > 0, L2, 1), 0), 0, 1)
        px, py = ax + t * dx, ay + t * dy
        d = np.hypot(px, py)
        i = int(np.argmin(d))
        return i + float(t[i]), float(d[i])

    def median_offsets(self, k: int) -> np.ndarray:
        if k not in self._median_off:
            p = self.net.patterns[k]
            self._median_off[k] = np.nan_to_num(np.nanmedian(p.arr - p.dep[:, :1], axis=0))
        return self._median_off[k]

    @staticmethod
    def _interp(row_dep: np.ndarray, row_arr: np.ndarray, progress: float) -> float:
        i = min(int(progress), len(row_dep) - 2)
        f = progress - i
        a, b = row_dep[i], row_arr[i + 1]
        return float(a + f * (b - a))

    # ---- update -----------------------------------------------------------------
    def ingest(self, feed_ts: int, obs: list[VehicleObs], now: float | None = None) -> dict:
        now = now or time.time()
        states, unmatched = {}, 0
        for o in obs:
            st = self._match(o, now)
            if st.pattern_idx is None:
                unmatched += 1
            states[o.vehicle_id] = st
        with self._lock:
            self.vehicles = states
            self.feed_timestamp = feed_ts
            self.last_success = now
            self.last_error = None
            self.unmatched_routes = unmatched
        return {"vehicles": len(states), "unmatched": unmatched}

    def _match(self, o: VehicleObs, now: float) -> VehicleState:
        age = int(now - o.timestamp)
        rid = str(o.route_id)
        route = self.net.routes.at[rid, "route_short_name"] if rid in self.net.routes.index else None
        base = dict(vehicle_id=o.vehicle_id, route_id=rid, route=None if route is None else str(route),
                    lat=o.lat, lon=o.lon, speed_kmh=None if o.speed_mps is None else round(o.speed_mps * 3.6, 1),
                    bearing=o.bearing, timestamp=o.timestamp, age_s=age, stale=age > STALE_S)
        none = dict(pattern_idx=None, progress=None, off_route_m=None, trip_row=None, scheduled_trip_id=None,
                    delay_s=None, next_stop_id=None, next_stop=None)
        pats = self.route_patterns.get(rid)
        if not pats:
            return VehicleState(**base, **none)
        snaps = [(self.snap(k, o.lat, o.lon), k) for k in pats]
        (prog, dist), k = min(snaps, key=lambda x: x[0][1])
        p = self.net.patterns[k]
        nxt = min(int(np.floor(prog)) + 1, len(p.stops) - 1)
        row, delay, sched_id = None, None, None
        t_start = _hms(o.start_time)
        t_obs = self._service_seconds(o.timestamp, o.start_date)
        if t_start is not None and t_obs is not None and dist <= OFF_ROUTE_M:
            # candidate trips: scheduled start within TRIP_MATCH_MAX_S of the feed's start_time;
            # pick the one whose schedule puts a bus at this position closest to now
            first = p.dep[:, 0]
            cand = np.where(np.abs(first - t_start) <= TRIP_MATCH_MAX_S)[0]
            if len(cand):
                at = np.array([self._interp(p.dep[r], p.arr[r], prog) for r in cand])
                j = int(np.nanargmin(np.abs(at - t_obs))) if np.isfinite(at).any() else None
                if j is not None and abs(t_obs - at[j]) <= MAX_PLAUSIBLE_DELAY_S:
                    row, delay = int(cand[j]), float(t_obs - at[j])
                    sched_id = str(p.trip_ids[row])
        return VehicleState(**base, pattern_idx=k, progress=prog, off_route_m=dist, trip_row=row,
                            scheduled_trip_id=sched_id, delay_s=delay, next_stop_id=p.stops[nxt],
                            next_stop=self.net.stop_name(p.stops[nxt]))

    @staticmethod
    def _service_seconds(ts: int, start_date: str) -> float | None:
        """Seconds since midnight of the trip's service day (can exceed 86400 after midnight)."""
        try:
            d = datetime.strptime(start_date, "%Y%m%d").replace(tzinfo=IST)
        except ValueError:
            d = datetime.fromtimestamp(ts, IST).replace(hour=0, minute=0, second=0, microsecond=0)
        return ts - d.timestamp()

    def record_error(self, msg: str) -> None:
        with self._lock:
            self.last_error = msg

    # ---- queries ----------------------------------------------------------------
    def status(self, now: float | None = None) -> dict:
        now = now or time.time()
        live = self.last_success is not None and now - self.last_success <= FEED_STALE_S
        vs = list(self.vehicles.values())
        return {"live": live, "vehicles": len(vs), "fresh_vehicles": sum(not v.stale for v in vs),
                "matched_to_route": sum(v.pattern_idx is not None for v in vs),
                "matched_to_trip": sum(v.trip_row is not None for v in vs),
                "feed_timestamp": self.feed_timestamp,
                "last_success": None if self.last_success is None else datetime.fromtimestamp(self.last_success, IST).isoformat(),
                "last_error": self.last_error,
                "source": "GTFS-RT VehiclePositions (Transport Stack, delhi-buses)"}

    def list_vehicles(self, route_id: str | None = None, bbox: tuple[float, float, float, float] | None = None) -> list[dict]:
        out = []
        for v in self.vehicles.values():
            if route_id is not None and v.route_id != str(route_id):
                continue
            if bbox is not None and not (bbox[0] <= v.lat <= bbox[2] and bbox[1] <= v.lon <= bbox[3]):
                continue
            out.append(v.public())
        return out

    def arrivals(self, stop_id: str, now: float | None = None, horizon_min: int = 60, limit: int = 15) -> dict:
        """Next buses at a stop: live estimates where a bus is tracked, timetable otherwise."""
        now = now or time.time()
        now_dt = datetime.fromtimestamp(now, IST)
        today = now_dt.date()
        live_ok = self.status(now)["live"]
        rows, covered = [], set()
        positions = self.net.stop_patterns.get(stop_id, [])
        if live_ok:
            by_pattern: dict[int, list[VehicleState]] = {}
            for v in self.vehicles.values():
                usable = v.trip_row is not None or (v.speed_kmh or 0) >= MIN_MOVING_KMH
                if v.pattern_idx is not None and not v.stale and (v.off_route_m or 0) <= OFF_ROUTE_M and usable:
                    by_pattern.setdefault(v.pattern_idx, []).append(v)
            for k, m in positions:
                p = self.net.patterns[k]
                if m >= len(p.stops) - 1:
                    continue                              # terminal stop: nobody boards here
                for v in by_pattern.get(k, []):
                    if v.progress is None or v.progress >= m:
                        continue                          # already passed this stop
                    if v.trip_row is not None and v.delay_s is not None and np.isfinite(p.arr[v.trip_row, m]):
                        sched = float(p.arr[v.trip_row, m])
                        eta_s = self._service_day_ts(v, sched) + v.delay_s
                        basis = "live_estimate"
                        covered.add((k, v.trip_row))
                    else:
                        off = self.median_offsets(k)
                        i = min(int(v.progress), len(off) - 2)
                        at = off[i] + (v.progress - i) * (off[i + 1] - off[i])
                        eta_s = v.timestamp + (off[m] - at)
                        basis = "live_estimate_typical_running_time"
                    eta_s = max(eta_s, now)
                    if eta_s - now > horizon_min * 60:
                        continue
                    rows.append(self._row(k, eta_s, now, basis, v))
        # timetable for everything not already tracked live
        midnight = datetime.combine(today, datetime.min.time(), IST).timestamp()
        active = self.net.active_services(today)
        for k, m in positions:
            p = self.net.patterns[k]
            if m >= len(p.stops) - 1:
                continue                                   # terminal stop: buses end here
            col = p.arr[:, m]
            t_now = now - midnight
            ok = np.isfinite(col) & (col >= t_now) & (col <= t_now + horizon_min * 60)
            if active is not None:
                ok &= np.isin(p.service_ids, list(active))
            for r in np.where(ok)[0]:
                if (k, int(r)) in covered:
                    continue
                rows.append(self._row(k, midnight + float(col[r]), now, "scheduled", None))
        rows.sort(key=lambda r: r["eta_min"])
        return {"stop_id": stop_id, "stop": self.net.stop_name(stop_id), "live_feed": live_ok,
                "now": now_dt.isoformat(timespec="seconds"), "arrivals": rows[:limit],
                "note": None if live_ok else "Live feed unavailable: showing timetable times only."}

    @staticmethod
    def _service_day_ts(v: VehicleState, sched_s: float) -> float:
        # the trip's service day is the day its timestamp falls on, minus a day if the schedule wrapped past midnight
        d = datetime.fromtimestamp(v.timestamp, IST).replace(hour=0, minute=0, second=0, microsecond=0)
        if sched_s >= 86400 and datetime.fromtimestamp(v.timestamp, IST).hour < 12:
            d -= timedelta(days=1)
        return d.timestamp() + sched_s

    def _row(self, k: int, eta_s: float, now: float, basis: str, v: VehicleState | None) -> dict:
        p = self.net.patterns[k]
        rid = str(p.route_id)
        r = self.net.routes.loc[rid] if rid in self.net.routes.index else None
        return {"route_id": rid, "route": str(r["route_short_name"]) if r is not None else rid,
                "towards": self.net.stop_name(p.stops[-1]),
                "eta": datetime.fromtimestamp(eta_s, IST).strftime("%H:%M"),
                "eta_min": round((eta_s - now) / 60, 1), "basis": basis,
                "vehicle_id": None if v is None else v.vehicle_id,
                "delay_min": None if v is None or v.delay_s is None else round(v.delay_s / 60, 1),
                "position_age_s": None if v is None else v.age_s}


class LivePoller:
    """Background thread: fetch -> parse -> ingest every `interval_s`. Errors keep the last snapshot."""

    def __init__(self, tracker: LiveTracker, fetch: Callable[[], bytes], interval_s: float = 30.0):
        self.tracker, self.fetch, self.interval_s = tracker, fetch, interval_s
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None

    def poll_once(self) -> dict | None:
        try:
            ts, obs = parse_vehicle_positions(self.fetch())
            return self.tracker.ingest(ts, obs)
        except Exception as e:  # noqa: BLE001 - feed outages must never crash the app
            msg = f"{type(e).__name__}: {str(e)[:200]}"
            log.warning("GTFS-RT poll failed: %s", msg)
            self.tracker.record_error(msg)
            return None

    def _run(self) -> None:
        while not self._stop.is_set():
            self.poll_once()
            self._stop.wait(self.interval_s)

    def start(self) -> None:
        if self._thread is None:
            self._thread = threading.Thread(target=self._run, name="gtfs-rt-poller", daemon=True)
            self._thread.start()

    def stop(self) -> None:
        self._stop.set()


def transport_stack_fetcher(api_base: str, api_key: str, timeout: float = 20.0) -> Callable[[], bytes]:
    """The key is sent only as a request header and never logged."""
    import requests

    def fetch() -> bytes:
        r = requests.get(api_base, params={"agency": "delhi-buses", "category": "realtime_gtfs",
                                           "filename": "VehiclePositions.pb"},
                         headers={"x-api-key": api_key}, timeout=timeout)
        if r.status_code != 200:
            raise RuntimeError(f"HTTP {r.status_code}")
        return r.content
    return fetch

