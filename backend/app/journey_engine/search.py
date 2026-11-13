"""Timetable-based bus journey search (direct + one-transfer), pattern-enumeration style.

Why not plain RAPTOR? RAPTOR returns one best arrival per transfer count. A crowd-aware
planner needs *alternatives* (a slower but emptier route must survive), so we enumerate
route-pattern combinations, pick the first feasible scheduled trip for each, and keep the
best candidate per (first pattern, second pattern). Pareto filtering happens after crowd
scoring in the ranker.

All times are seconds after midnight of the service date (Asia/Kolkata).
"""
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

import numpy as np

from app.journey_engine.network import Network, Pattern

MIN_TRANSFER_S = 120          # minimum time to change buses at the same stop
MAX_WAIT_S = 90 * 60          # do not propose boarding a bus more than 90 min after arriving
MAX_JOURNEY_S = 3 * 3600


@dataclass
class Leg:
    pattern_idx: int
    trip_row: int
    board_pos: int
    alight_pos: int
    dep_s: float
    arr_s: float


@dataclass
class Candidate:
    legs: list[Leg]
    walk_origin_s: float
    walk_transfer_s: float
    walk_dest_s: float
    start_s: float               # requested departure time
    origin_stop: str
    dest_stop: str
    transfer_stops: list[tuple[str, str]] = field(default_factory=list)  # (alight stop, board stop)

    @property
    def arrive_s(self) -> float:
        return self.legs[-1].arr_s + self.walk_dest_s

    @property
    def duration_s(self) -> float:
        return self.arrive_s - self.start_s

    @property
    def transfers(self) -> int:
        return len(self.legs) - 1

    @property
    def walk_s(self) -> float:
        return self.walk_origin_s + self.walk_transfer_s + self.walk_dest_s


class JourneySearch:
    def __init__(self, net: Network):
        self.net = net
        self._svc_mask: dict[tuple[int, date], np.ndarray] = {}
        self._median_offsets = [
            np.nan_to_num(np.nanmedian(p.arr - p.dep[:, :1], axis=0)) if len(p.trip_ids) else np.zeros(len(p.stops))
            for p in net.patterns
        ]

    # ---- timetable primitives ------------------------------------------------------
    def _mask(self, k: int, d: date) -> np.ndarray:
        key = (k, d)
        if key not in self._svc_mask:
            active = self.net.active_services(d)
            p = self.net.patterns[k]
            self._svc_mask[key] = np.ones(len(p.trip_ids), bool) if active is None else np.isin(p.service_ids, list(active))
        return self._svc_mask[key]

    def first_trip(self, k: int, pos: int, t: float, d: date) -> int | None:
        p: Pattern = self.net.patterns[k]
        col = p.dep[:, pos]
        ok = self._mask(k, d) & (col >= t) & (col <= t + MAX_WAIT_S)
        if not ok.any():
            return None
        cand = np.where(ok)[0]
        return int(cand[np.argmin(col[cand])])

    # ---- search ----------------------------------------------------------------------
    def search(self, origins: list[tuple[str, float]], dests: list[tuple[str, float]],
               d: date, t0: float, max_transfers: int = 1) -> list[Candidate]:
        dest_walk = {s: w for s, w in dests}
        origin_walk = {s: w for s, w in origins}
        best: dict[tuple, Candidate] = {}

        def keep(key: tuple, c: Candidate) -> None:
            if c.duration_s > MAX_JOURNEY_S:
                return
            cur = best.get(key)
            if cur is None or (c.arrive_s, c.walk_s) < (cur.arrive_s, cur.walk_s):
                best[key] = c

        # direct
        for o, wo in origins:
            for k, po in self.net.stop_patterns.get(o, []):
                p = self.net.patterns[k]
                for pd_ in range(po + 1, len(p.stops)):
                    sd = p.stops[pd_]
                    if sd not in dest_walk:
                        continue
                    r = self.first_trip(k, po, t0 + wo, d)
                    if r is None or np.isnan(p.arr[r, pd_]):
                        continue
                    leg = Leg(k, r, po, pd_, float(p.dep[r, po]), float(p.arr[r, pd_]))
                    keep(("D", k), Candidate([leg], wo, 0.0, dest_walk[sd], t0, o, sd))

        if max_transfers >= 1:
            self._one_transfer(origins, dest_walk, d, t0, keep)
        return list(best.values())

    def _one_transfer(self, origins, dest_walk, d, t0, keep) -> None:
        net = self.net
        # tail[x] = [(k2, m, pd, walk_d)]: board pattern k2 at stop x (position m) and reach a destination stop
        tail: dict[str, list[tuple[int, int, int, float]]] = {}
        for sd, wd in dest_walk.items():
            for k2, pd_ in net.stop_patterns.get(sd, []):
                p2 = net.patterns[k2]
                for m in range(pd_):
                    tail.setdefault(p2.stops[m], []).append((k2, m, pd_, wd))
        direct_patterns = set()
        for o, _ in origins:
            for k, _ in net.stop_patterns.get(o, []):
                direct_patterns.add(k)

        for o, wo in origins:
            for k1, po in net.stop_patterns.get(o, []):
                p1 = net.patterns[k1]
                r1 = self.first_trip(k1, po, t0 + wo, d)
                if r1 is None:
                    continue
                seen_k2: set[int] = set()
                for x_pos in range(po + 1, len(p1.stops)):
                    a1 = p1.arr[r1, x_pos]
                    if np.isnan(a1) or a1 - t0 > MAX_JOURNEY_S:
                        break
                    x = p1.stops[x_pos]
                    transfer_points = [(x, 0.0)] + net.footpaths.get(x, [])
                    for xb, walk in transfer_points:
                        for k2, m, pd_, wd in tail.get(xb, []):
                            if k2 == k1 or k2 in direct_patterns or net.patterns[k2].route_id == p1.route_id:
                                continue
                            if (k2, x_pos) in seen_k2:
                                continue
                            p2 = net.patterns[k2]
                            r2 = self.first_trip(k2, m, a1 + walk + MIN_TRANSFER_S, d)
                            if r2 is None or np.isnan(p2.arr[r2, pd_]):
                                continue
                            seen_k2.add((k2, x_pos))
                            l1 = Leg(k1, r1, po, x_pos, float(p1.dep[r1, po]), float(a1))
                            l2 = Leg(k2, r2, m, pd_, float(p2.dep[r2, m]), float(p2.arr[r2, pd_]))
                            c = Candidate([l1, l2], wo, walk, wd, t0, o, p2.stops[pd_], [(x, xb)])
                            keep(("T", k1, k2), c)
