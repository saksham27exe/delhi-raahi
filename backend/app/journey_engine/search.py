"""Timetable-based bus journey search (direct, one and two transfers), pattern-enumeration style.

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
TWO_CHANGE_GAIN_S = 10 * 60   # a two-change trip must arrive this much earlier than any simpler one


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
    transfer_walks: list[float] = field(default_factory=list)             # walk seconds at each change

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
            if len(self._svc_mask) > 100_000:      # many different dates asked: start again rather than grow forever
                self._svc_mask.clear()
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
                    keep(("D", p.route_id), Candidate([leg], wo, 0.0, dest_walk[sd], t0, o, sd))

        tail = self._tail(dest_walk) if max_transfers >= 1 else {}
        if max_transfers >= 1:
            self._one_transfer(origins, dest_walk, d, t0, keep, tail)
        if max_transfers >= 2:
            # A second change is only worth it when it beats every simpler option by 10+ minutes
            # (or when nothing simpler exists); this bound also keeps the search fast.
            simpler = [c.arrive_s for c in best.values()]
            cutoff = (min(simpler) - TWO_CHANGE_GAIN_S) if simpler else t0 + MAX_JOURNEY_S
            self._two_transfers(origins, d, t0, keep, tail, cutoff)
        return list(best.values())

    def _tail(self, dest_walk: dict[str, float]) -> dict[str, list[tuple[int, int, int, float]]]:
        """tail[x] = [(k, m, pd, walk_d)]: board pattern k at stop x (position m) and ride to a destination stop."""
        tail: dict[str, list[tuple[int, int, int, float]]] = {}
        for sd, wd in dest_walk.items():
            for k2, pd_ in self.net.stop_patterns.get(sd, []):
                p2 = self.net.patterns[k2]
                for m in range(pd_):
                    tail.setdefault(p2.stops[m], []).append((k2, m, pd_, wd))
        return tail

    def _one_transfer(self, origins, dest_walk, d, t0, keep, tail) -> None:
        net = self.net
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
                            c = Candidate([l1, l2], wo, walk, wd, t0, o, p2.stops[pd_], [(x, xb)], [walk])
                            keep(("T", p1.route_id, p2.route_id), c)  # one option per route pair

    def _two_transfers(self, origins, d, t0, keep, tail, cutoff: float) -> None:
        """Bus - change - bus - change - bus, for places no one-change trip connects well.

        Pruned so it stays fast: the first ride keeps only the earliest arrival at each stop, each middle
        pattern is tried once per scheduled trip, and only the first (earliest) way onto each final route
        is kept per middle trip. At most one option per route triple survives (via `keep`)."""
        net = self.net
        # 1) everything reachable with one ride: stop -> earliest moment we can board another bus there
        reach: dict[str, tuple] = {}
        for o, wo in origins:
            for k1, po in net.stop_patterns.get(o, []):
                p1 = net.patterns[k1]
                r1 = self.first_trip(k1, po, t0 + wo, d)
                if r1 is None:
                    continue
                for x_pos in range(po + 1, len(p1.stops)):
                    a1 = p1.arr[r1, x_pos]
                    if np.isnan(a1) or a1 > cutoff:
                        break
                    x = p1.stops[x_pos]
                    for xb, walk in [(x, 0.0)] + net.footpaths.get(x, []):
                        ready = float(a1) + walk + MIN_TRANSFER_S
                        if ready > cutoff:
                            continue
                        cur = reach.get(xb)
                        if cur is None or ready < cur[0]:
                            reach[xb] = (ready, k1, r1, po, x_pos, o, wo, x, walk)
        # stops from which the last ride starts, including a short walk to them
        tail_fp: dict[str, list[tuple[str, float]]] = {}
        for y in tail:
            tail_fp.setdefault(y, []).append((y, 0.0))
            for z, w in net.footpaths.get(y, []):
                tail_fp.setdefault(z, []).append((y, w))
        # Best final rides from stop y when ready at time t, cached in 5-minute steps (rounded up, so a
        # cached bus never leaves before we get there). Up to 3 routes, earliest arrival first.
        memo: dict[tuple[str, int], list[tuple]] = {}
        # Lower bound of the last ride from y (typical ride time + final walk), to skip hopeless stops early.
        min_rest: dict[str, float] = {}
        for y, entries in tail.items():
            min_rest[y] = min(max(0.0, float(self._median_offsets[k3][pd3] - self._median_offsets[k3][m3])) + wd
                              for k3, m3, pd3, wd in entries)

        def last_ride(y: str, t: float) -> list[tuple]:
            tq = int(-(-t // 300) * 300)
            key = (y, tq)
            if key not in memo:
                opts: dict[str, tuple] = {}
                for k3, m3, pd3, wd in tail[y]:
                    p3 = net.patterns[k3]
                    r3 = self.first_trip(k3, m3, tq, d)
                    if r3 is None or np.isnan(p3.arr[r3, pd3]):
                        continue
                    arr = float(p3.arr[r3, pd3]) + wd
                    if p3.route_id not in opts or arr < opts[p3.route_id][0]:
                        opts[p3.route_id] = (arr, k3, r3, m3, pd3, wd)
                memo[key] = sorted(opts.values())[:3]
            return memo[key]

        # 2) middle ride: board at a reached stop, ride to a stop where a final ride starts
        boards: dict[int, list[tuple[int, str]]] = {}
        for xb in reach:
            for k2, m in net.stop_patterns.get(xb, []):
                boards.setdefault(k2, []).append((m, xb))
        for k2, entries in boards.items():
            p2 = net.patterns[k2]
            tail_pos = [(pos, y, w2) for pos, s in enumerate(p2.stops) for y, w2 in tail_fp.get(s, [])]
            if not tail_pos:
                continue
            tried_trips: set[int] = set()
            for m, xb in sorted(entries):
                ready, k1, r1, po, x_pos, o, wo, x, walk1 = reach[xb]
                p1 = net.patterns[k1]
                if p1.route_id == p2.route_id:
                    continue
                r2 = self.first_trip(k2, m, ready, d)
                if r2 is None or r2 in tried_trips:
                    continue
                tried_trips.add(r2)
                for pos, y, w2 in tail_pos:
                    if pos <= m:
                        continue
                    a2 = p2.arr[r2, pos]
                    if np.isnan(a2) or a2 > cutoff:
                        break
                    if float(a2) + w2 + MIN_TRANSFER_S + 0.8 * min_rest[y] > cutoff:
                        continue
                    for arr3, k3, r3, m3, pd3, wd in last_ride(y, float(a2) + w2 + MIN_TRANSFER_S):
                        p3 = net.patterns[k3]
                        if p3.route_id in (p1.route_id, p2.route_id) or arr3 > cutoff:
                            continue
                        l1 = Leg(k1, r1, po, x_pos, float(p1.dep[r1, po]), float(p1.arr[r1, x_pos]))
                        l2 = Leg(k2, r2, m, pos, float(p2.dep[r2, m]), float(a2))
                        l3 = Leg(k3, r3, m3, pd3, float(p3.dep[r3, m3]), float(p3.arr[r3, pd3]))
                        c = Candidate([l1, l2, l3], wo, walk1 + w2, wd, t0, o, p3.stops[pd3],
                                      [(x, xb), (p2.stops[pos], y)], [walk1, w2])
                        keep(("T2", p1.route_id, p2.route_id, p3.route_id), c)
                        break   # earliest usable last ride from this stop
