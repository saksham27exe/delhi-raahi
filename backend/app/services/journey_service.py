"""Journey search orchestration: resolve places -> timetable search -> crowd scoring -> ranking."""
from __future__ import annotations

import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

import numpy as np
import pandas as pd
from rapidfuzz import fuzz, process

from app.data_pipeline.mapping.normalize import normalize_stop_name
from app.journey_engine.network import WALK_SPEED_MPS, Network, haversine_m
from app.journey_engine.ranker import CONFIG, ScoredJourney, rank
from app.journey_engine.search import Candidate, JourneySearch
from app.services.crowd_service import CATEGORIES, CrowdService

IST = ZoneInfo("Asia/Kolkata")
ACCESS_RADIUS_M = 600
MAX_ACCESS_RADIUS_M = 1500
WALK_ONLY_MAX_M = 2000      # offer "walk the whole way" up to this road distance
DETOUR = 1.3


MAX_SCORED_CANDIDATES = 60


class PlaceError(ValueError):
    def __init__(self, message: str, code: str = "invalid_place", radius_m: int | None = None, side: str | None = None):
        super().__init__(message)
        self.code, self.radius_m, self.side = code, radius_m, side

    def detail(self) -> dict:
        return {"message": str(self), "code": self.code, "radius_m": self.radius_m, "side": self.side}


def ci_category(ci: float | None) -> str:
    if ci is None:
        return "UNKNOWN"
    return CATEGORIES[int(np.searchsorted([0.5, 0.8, 0.95], ci, side="right"))]


def hhmm(sec: float) -> str:
    sec = int(round(sec))
    return f"{(sec // 3600) % 24:02d}:{(sec % 3600) // 60:02d}"


class JourneyService:
    def __init__(self, net: Network, crowd: CrowdService | None, fares=None, geo=None):
        self.net = net
        self.geo = geo    # OsmGeo | None: real walking distances/paths for coordinate places
        self.fares = fares  # FareService | None
        self.search_engine = JourneySearch(net)
        self.crowd = crowd
        st = net.stops.copy()
        st["norm"] = st["stop_name"].map(normalize_stop_name)
        self._stops = st
        self._names = st.groupby("norm").agg(stop_ids=("stop_id", list), name=("stop_name", "first"),
                                              lat=("stop_lat", "mean"), lon=("stop_lon", "mean")).reset_index()

    # ---- places ---------------------------------------------------------------------
    def set_aliases(self, aliases: dict[str, str]) -> None:
        """Other names for stops, added by admins: {"AIIMS": "AIIMS (Ring Road)"} (normalised here)."""
        idx = {n: i for i, n in enumerate(self._names["norm"])}
        self._aliases = {normalize_stop_name(a): idx[normalize_stop_name(s)] for a, s in aliases.items()
                         if normalize_stop_name(a) and normalize_stop_name(s) in idx}

    def _alias_hits(self, qn: str) -> list[dict]:
        out = []
        for alias, i in getattr(self, "_aliases", {}).items():
            if alias == qn or (len(qn) >= 3 and alias.startswith(qn)) or fuzz.ratio(alias, qn) >= 90:
                r = self._names.iloc[i]
                out.append({"name": r["name"], "stop_ids": r["stop_ids"], "lat": round(r["lat"], 6), "lon": round(r["lon"], 6),
                            "score": 200.0 if alias == qn else 150.0, "routes_served": self._routes_at(r["stop_ids"]), "alias": alias})
        return out

    def search_stops(self, q: str, limit: int = 10) -> list[dict]:
        qn = normalize_stop_name(q)
        if not qn:
            return []
        alias_hits = self._alias_hits(qn)
        names = self._names["norm"].tolist()
        hits = process.extract(qn, names, scorer=fuzz.WRatio, limit=max(60, limit * 6))
        qtok = qn.split()
        out = []
        for name, sc, i in hits:
            r = self._names.iloc[i]
            ntok = name.replace("(", " ").replace(")", " ").split()
            # share of query words found in the stop name (typo-tolerant per word)
            cover = sum(max((fuzz.ratio(t, w) for w in ntok), default=0) >= 85 for t in qtok) / len(qtok)
            routes = self._routes_at(r["stop_ids"])
            score = 0.5 * sc + 50 * cover + (8 if name.startswith(qn) else 0) + min(6.0, np.log1p(routes))
            out.append({"name": r["name"], "stop_ids": r["stop_ids"], "lat": round(r["lat"], 6),
                        "lon": round(r["lon"], 6), "score": round(float(score), 1), "routes_served": routes})
        seen = {h["name"] for h in alias_hits}
        out = alias_hits + [o for o in out if o["name"] not in seen]
        out.sort(key=lambda x: -x["score"])
        return out[:limit]

    def _routes_at(self, stop_ids: list[str]) -> int:
        return len({self.net.patterns[k].route_id for s in stop_ids for k, _ in self.net.stop_patterns.get(s, [])})

    def resolve(self, place: dict, radius_m: int = ACCESS_RADIUS_M) -> tuple[list[tuple[str, float]], dict]:
        """place: {'type':'stop','id':..} | {'type':'stop_name','name':..} | {'type':'coord','lat':..,'lon':..}"""
        t = place.get("type")
        if t == "stop":
            sid = str(place.get("id"))
            if sid not in self.net.stops.index:
                raise PlaceError(f"Unknown stop id '{sid}'.")
            r = self.net.stops.loc[sid]
            lat, lon, name = r["stop_lat"], r["stop_lon"], r["stop_name"]
            ids = [sid]
        elif t == "stop_name":
            norm = normalize_stop_name(place.get("name", ""))
            row = self._names[self._names["norm"] == norm]
            if row.empty:
                raise PlaceError(f"No stop named '{place.get('name')}'. Use /api/v1/stops/search to find one.")
            row = row.iloc[0]
            ids, lat, lon, name = list(row["stop_ids"]), row["lat"], row["lon"], row["name"]
        elif t == "coord":
            lat, lon = float(place["lat"]), float(place["lon"])
            name = place.get("label") or f"{lat:.5f}, {lon:.5f}"
            ids = []
        else:
            raise PlaceError("place.type must be 'stop', 'stop_name' or 'coord'.")
        near = self.net.nearby_stops(lat, lon, radius_m, limit=12)
        access: dict[str, float] = {}
        basis = "straight_line_estimate"
        for sid in ids:  # same-name stops (both kerbs) get their real walking distance from the centroid
            r = self.net.stops.loc[sid]
            access[sid] = float(haversine_m(lat, lon, r["stop_lat"], r["stop_lon"]) * DETOUR / WALK_SPEED_MPS) if t != "stop" else 0.0
        for r in near.itertuples():
            w = r.distance_m * DETOUR / WALK_SPEED_MPS
            if r.stop_id not in access:
                access[r.stop_id] = w
        if t == "coord" and self.geo is not None and access:
            sids = list(access)
            pts = [(float(self.net.stops.at[s, "stop_lat"]), float(self.net.stops.at[s, "stop_lon"])) for s in sids]
            dist = self.geo.walk_distances(lat, lon, pts)
            if dist is not None and any(x is not None for x in dist):
                basis = "osm_road"
                access = {s: x / WALK_SPEED_MPS for s, x in zip(sids, dist)
                          if x is not None and x <= radius_m * 1.6}   # drop stops that are far by road (river, rail line)
        if not access:
            raise PlaceError(f"No bus stop within {radius_m} m of {name}.", "no_stop_nearby", radius_m)
        items = sorted(access.items(), key=lambda x: x[1])[:10]
        return items, {"name": name, "lat": float(lat), "lon": float(lon), "candidate_stops": len(items),
                       "type": t, "radius_m": radius_m, "walk_basis": basis}

    # ---- journeys ------------------------------------------------------------------
    def plan(self, origin: dict, destination: dict, when: datetime, preference: str = "balanced",
             max_transfers: int = 1, radius_m: int = ACCESS_RADIUS_M, with_paths: bool = True) -> dict:
        if preference not in CONFIG["modes"]:
            raise PlaceError(f"preference must be one of {list(CONFIG['modes'])}")
        when = when.astimezone(IST) if when.tzinfo else when.replace(tzinfo=IST)
        radius_m = int(min(max(radius_m, 200), MAX_ACCESS_RADIUS_M))
        o, o_info = self._resolve_side(origin, radius_m, "origin")
        dd, d_info = self._resolve_side(destination, radius_m, "destination")
        d = when.date()
        t0 = when.hour * 3600 + when.minute * 60 + when.second
        cands = prune_candidates(self.search_engine.search(o, dd, d, t0, max_transfers))
        scored = [self._score(c, d) for c in cands]
        ranked = rank(scored, preference)
        notes = []
        if self.crowd is None:
            notes.append("Crowd model artifacts not loaded: journeys are shown without crowd estimates.")
        else:
            _, basis = self.crowd.forecasts.slot_for(d)
            if basis == "recurring_weekday_forecast":
                notes.append(f"Crowd estimates for {d.isoformat()} use the recurring {d.strftime('%A')} pattern "
                             f"learned from data up to {self.crowd.forecasts.window_end}.")
        notes.append("Times are GTFS scheduled times, not live arrivals.")
        journeys = [self._public(j) for j in ranked]
        walk_option = self._walk_option(o_info, d_info)
        for info in (o_info, d_info):
            if info["type"] == "coord" and info["walk_basis"] != "osm_road":
                notes.append(f"Walking times to and from {info['name']} are straight-line estimates "
                             "(walking-route service unavailable).")
        if with_paths:
            self._attach_walk_paths(journeys, o_info, d_info)
        return {
            "search_id": uuid.uuid4().hex[:12],
            "query": {"origin": o_info, "destination": d_info, "departure": when.isoformat(),
                      "preference": preference, "max_transfers": max_transfers},
            "journeys": journeys,
            "walk_option": walk_option,
            "counts": {"direct": sum(j["transfers"] == 0 for j in journeys),
                       "one_change": sum(j["transfers"] == 1 for j in journeys),
                       "two_changes": sum(j["transfers"] == 2 for j in journeys)},
            "candidates_considered": len(cands),
            "notes": notes,
            "metric": {"name": "demand_per_scheduled_departure",
                       "crowd_index": "network percentile (0-1) of expected demand per scheduled departure",
                       "exposure": "sum over segments of crowd_index x minutes on that segment"},
        }

    def advise(self, origin: dict, destination: dict, when: datetime, preference: str = "balanced",
               offsets_min: tuple[int, ...] = (-30, -15, 0, 15, 30, 45, 60), radius_m: int = ACCESS_RADIUS_M) -> dict:
        when = when.astimezone(IST) if when.tzinfo else when.replace(tzinfo=IST)
        rows = []
        for off in offsets_min:
            t = when + timedelta(minutes=off)
            res = self.plan(origin, destination, t, preference, radius_m=radius_m, with_paths=False)
            js = res["journeys"]
            if not js:
                rows.append({"offset_min": off, "departure": t.isoformat(), "available": False})
                continue
            best = js[0]  # already ranked for the requested preference
            rows.append({"offset_min": off, "departure": t.isoformat(), "available": True,
                         "board_time": best["departure"], "arrival": best["arrival"],
                         "duration_min": best["duration_minutes"], "crowd_level": best["crowd_level"],
                         "mean_crowd_index": best["mean_crowd_index"], "exposure": best["crowd_exposure"],
                         "routes": [l["route"] for l in best["legs"]], "evidence": best["evidence_quality"]})
        avail = [r for r in rows if r["available"]]
        rec = None
        if avail:
            base = next((r for r in avail if r["offset_min"] == 0), avail[0])
            better = [r for r in avail if r["mean_crowd_index"] is not None and base["mean_crowd_index"] is not None
                      and r["mean_crowd_index"] < base["mean_crowd_index"] - 0.1 and r["duration_min"] <= base["duration_min"] * 1.25]
            if better:
                r = min(better, key=lambda x: (x["mean_crowd_index"], abs(x["offset_min"])))
                rec = {"offset_min": r["offset_min"], "reason":
                       f"Leaving {abs(r['offset_min'])} min {'later' if r['offset_min'] > 0 else 'earlier'} lowers expected "
                       f"crowding from {base['crowd_level']} to {r['crowd_level']} (arrive {r['arrival']} instead of {base['arrival']})."}
            else:
                rec = {"offset_min": 0, "reason": "No nearby departure time is meaningfully less crowded without a much longer trip."}
        return {"departures": rows, "recommendation": rec}

    def _resolve_side(self, place: dict, radius_m: int, side: str):
        try:
            return self.resolve(place, radius_m)
        except PlaceError as e:
            e.side = side
            raise

    # ---- walking paths --------------------------------------------------------------
    def _walk_option(self, o_info: dict, d_info: dict) -> dict | None:
        """Walking the whole way, offered when it is at most WALK_ONLY_MAX_M along roads."""
        straight = float(haversine_m(o_info["lat"], o_info["lon"], d_info["lat"], d_info["lon"]))
        if straight > WALK_ONLY_MAX_M:
            return None
        route = self.geo.walk_path((o_info["lat"], o_info["lon"]), (d_info["lat"], d_info["lon"])) if self.geo else None
        if route is not None:
            dist, path, basis = route["distance_m"], route["path"], "osm_road"
        else:
            dist, path, basis = straight * DETOUR, None, "straight_line_estimate"
        if dist > WALK_ONLY_MAX_M:
            return None
        return {"distance_m": round(dist), "minutes": round(dist / WALK_SPEED_MPS / 60, 1), "path": path, "basis": basis}

    def _stop_pt(self, sid: str) -> tuple[float, float]:
        return float(self.net.stops.at[sid, "stop_lat"]), float(self.net.stops.at[sid, "stop_lon"])

    def _attach_walk_paths(self, journeys: list[dict], o_info: dict, d_info: dict) -> None:
        """Road walking paths origin -> first board stop and last alight stop -> destination (coordinate places only)."""
        for j in journeys:
            j["walk"]["basis"] = {"origin": o_info["walk_basis"] if o_info["type"] == "coord" else "stop",
                                  "destination": d_info["walk_basis"] if d_info["type"] == "coord" else "stop"}
            j["walk"]["origin_path"] = j["walk"]["destination_path"] = None
        if self.geo is None or not journeys:
            return
        jobs: dict[tuple, tuple] = {}
        for j in journeys:
            if not j["legs"]:
                continue
            if o_info["type"] == "coord" and o_info["walk_basis"] == "osm_road":
                b = self._stop_pt(j["legs"][0]["board_stop_id"])
                jobs.setdefault(("o", b), ((o_info["lat"], o_info["lon"]), b))
            if d_info["type"] == "coord" and d_info["walk_basis"] == "osm_road":
                a = self._stop_pt(j["legs"][-1]["alight_stop_id"])
                jobs.setdefault(("d", a), (a, (d_info["lat"], d_info["lon"])))
        if not jobs:
            return
        with ThreadPoolExecutor(max_workers=min(6, len(jobs))) as ex:
            res = dict(zip(jobs, ex.map(lambda ab: self.geo.walk_path(*ab), jobs.values())))
        for j in journeys:
            if not j["legs"]:
                continue
            o = res.get(("o", self._stop_pt(j["legs"][0]["board_stop_id"])))
            d = res.get(("d", self._stop_pt(j["legs"][-1]["alight_stop_id"])))
            j["walk"]["origin_path"] = o["path"] if o else None
            j["walk"]["destination_path"] = d["path"] if d else None
            if o:
                j["walk"]["origin_m"] = round(o["distance_m"])
            if d:
                j["walk"]["destination_m"] = round(d["distance_m"])

    # ---- internals ----------------------------------------------------------------
    def _score(self, c: Candidate, d: date) -> ScoredJourney:
        legs, exposure, known_t, known_ci_t, unknown_t, ivt, high = [], 0.0, 0.0, 0.0, 0.0, 0.0, 0
        evid_time = {"HIGH": 0.0, "MEDIUM": 0.0, "LOW": 0.0, "INSUFFICIENT": 0.0}
        imput = CONFIG["unknown_crowd_index_imputation"]
        for leg in c.legs:
            p = self.net.patterns[leg.pattern_idx]
            r = leg.trip_row
            route = self.net.routes.loc[p.route_id] if p.route_id in self.net.routes.index else None
            segs = []
            for i in range(leg.board_pos, leg.alight_pos):
                a, b = p.stops[i], p.stops[i + 1]
                t_from, t_to = p.dep[r, i], p.arr[r, i + 1]
                minutes = max(float(t_to - t_from) / 60, 0.0) if not (np.isnan(t_from) or np.isnan(t_to)) else 0.0
                sid = f"{p.route_id}:{a}>{b}"
                bucket = int((t_from % 86400) // 60 // (self.crowd.bucket_minutes if self.crowd else 30)) if not np.isnan(t_from) else 0
                pred = self.crowd.predict(sid, d, bucket) if self.crowd else None
                ci = pred.crowd_index if pred else None
                exposure += (ci if ci is not None else imput) * minutes
                ivt += minutes
                if ci is None:
                    unknown_t += minutes
                else:
                    known_t += minutes
                    known_ci_t += ci * minutes
                ev = pred.evidence if pred else "INSUFFICIENT"
                evid_time[ev] += minutes
                cat = pred.category if pred else "UNKNOWN"
                high += cat in ("HIGH", "VERY_HIGH")
                segs.append({"segment_id": sid, "from_stop_id": a, "from_stop": self.net.stop_name(a),
                             "to_stop_id": b, "to_stop": self.net.stop_name(b),
                             "depart": hhmm(t_from) if not np.isnan(t_from) else None, "minutes": round(minutes, 1),
                             "lat": float(self.net.stops.at[a, "stop_lat"]), "lon": float(self.net.stops.at[a, "stop_lon"]),
                             "to_lat": float(self.net.stops.at[b, "stop_lat"]), "to_lon": float(self.net.stops.at[b, "stop_lon"]),
                             "crowd": _pred_public(pred)})
            legs.append({"route_id": p.route_id,
                         "route": str(route["route_short_name"]) if route is not None else p.route_id,
                         "route_desc": str(route.get("route_desc", "")) if route is not None else "",
                         "trip_id": str(p.trip_ids[r]), "board_stop_id": p.stops[leg.board_pos],
                         "board_stop": self.net.stop_name(p.stops[leg.board_pos]),
                         "alight_stop_id": p.stops[leg.alight_pos], "alight_stop": self.net.stop_name(p.stops[leg.alight_pos]),
                         "departure": hhmm(leg.dep_s), "arrival": hhmm(leg.arr_s),
                         "in_vehicle_minutes": round((leg.arr_s - leg.dep_s) / 60, 1),
                         "stops": leg.alight_pos - leg.board_pos, "segments": segs,
                         "fare": self.fares.leg_fare(leg.pattern_idx, leg.board_pos, leg.alight_pos) if self.fares else None})
        mean_ci = known_ci_t / known_t if known_t > 0 else None
        total = max(ivt, 1e-6)
        if evid_time["INSUFFICIENT"] / total > 0.5:
            evidence = "INSUFFICIENT"
        elif evid_time["HIGH"] / total >= 0.8:
            evidence = "HIGH"
        elif (evid_time["HIGH"] + evid_time["MEDIUM"]) / total >= 0.6:
            evidence = "MEDIUM"
        else:
            evidence = "LOW"
        wait_first = c.legs[0].dep_s - (c.start_s + c.walk_origin_s)
        walks = c.transfer_walks or [c.walk_transfer_s] * (len(c.legs) - 1)
        connections = []
        for i, (a, b) in enumerate(c.transfer_stops):
            w = walks[i] if i < len(walks) else 0.0
            wait = c.legs[i + 1].dep_s - c.legs[i].arr_s - w
            connections.append({"alight": self.net.stop_name(a), "board": self.net.stop_name(b),
                                "walk_min": round(w / 60, 1), "wait_min": round(max(wait, 0.0) / 60, 1),
                                "from_route": legs[i]["route"], "to_route": legs[i + 1]["route"]})
        transfer_wait = sum(x["wait_min"] for x in connections) * 60
        payload = {
            "legs": legs, "departure": hhmm(c.legs[0].dep_s), "arrival": hhmm(c.arrive_s),
            "start": hhmm(c.start_s), "walk_origin_min": round(c.walk_origin_s / 60, 1),
            "walk_transfer_min": round(c.walk_transfer_s / 60, 1), "walk_dest_min": round(c.walk_dest_s / 60, 1),
            "wait_min": round(wait_first / 60, 1), "transfer_wait_min": round(transfer_wait / 60, 1),
            "transfer_stops": [{"alight": self.net.stop_name(a), "board": self.net.stop_name(b)} for a, b in c.transfer_stops],
            "connections": connections,
            "evidence_quality": evidence, "high_demand_segments": int(high),
            "evidence_time_share": {k: round(v / total, 3) for k, v in evid_time.items()},
            "fare": self.fares.journey_fare([l["fare"] for l in legs]) if self.fares else None,
        }
        key = "|".join(l["route"] for l in legs)
        return ScoredJourney(key=key, duration_min=c.duration_s / 60, exposure=exposure, transfers=c.transfers,
                             walk_min=c.walk_s / 60, in_vehicle_min=ivt, mean_crowd_index=mean_ci,
                             unknown_time_share=unknown_t / total, payload=payload)

    @staticmethod
    def _public(j: ScoredJourney) -> dict:
        p = j.payload
        return {
            "journey_id": uuid.uuid4().hex[:10], "labels": j.labels, "ranking_score": j.score,
            "departure": p["departure"], "arrival": p["arrival"], "duration_minutes": round(j.duration_min, 1),
            "in_vehicle_minutes": round(j.in_vehicle_min, 1), "walk_minutes": round(j.walk_min, 1),
            "wait_minutes": p["wait_min"], "transfer_wait_minutes": p["transfer_wait_min"], "transfers": j.transfers,
            "crowd_exposure": round(j.exposure, 2),
            "mean_crowd_index": None if j.mean_crowd_index is None else round(j.mean_crowd_index, 3),
            "crowd_level": ci_category(j.mean_crowd_index), "unknown_crowd_share": round(j.unknown_time_share, 3),
            "high_demand_segments": p["high_demand_segments"], "evidence_quality": p["evidence_quality"],
            "evidence_time_share": p["evidence_time_share"], "walk": {"origin_min": p["walk_origin_min"],
            "transfer_min": p["walk_transfer_min"], "destination_min": p["walk_dest_min"]},
            "transfer_stops": p["transfer_stops"], "explanation": j.explanation, "legs": p["legs"], "fare": p["fare"],
            "kind": "direct" if j.transfers == 0 else "transfer", "connections": p["connections"],
        }


def prune_candidates(cands: list[Candidate], max_keep: int = MAX_SCORED_CANDIDATES) -> list[Candidate]:
    """Drop options too slow to ever be recommended before the (costlier) crowd scoring.
    The least-crowded mode accepts at most `least_crowded_max_duration_ratio` x the fastest duration."""
    if not cands:
        return cands
    fastest = min(c.duration_s for c in cands)
    limit = fastest * CONFIG["least_crowded_max_duration_ratio"] + 5 * 60
    ok = [c for c in cands if c.duration_s <= limit]
    direct = sorted((c for c in ok if c.transfers == 0), key=lambda c: c.duration_s)
    rest = sorted((c for c in ok if c.transfers > 0), key=lambda c: c.duration_s)
    return direct + rest[:max(0, max_keep - len(direct))]


def _pred_public(pred) -> dict | None:
    if pred is None:
        return None
    r = lambda x, n=3: None if x is None else round(float(x), n)  # noqa: E731
    return {"category": pred.category, "crowd_index": r(pred.crowd_index), "dpd": r(pred.value),
            "lower": r(pred.lower), "upper": r(pred.upper), "historical_dpd": r(pred.historical_value),
            "unusualness_pct": pred.unusualness_pct, "evidence": pred.evidence, "fallback_level": pred.fallback_level,
            "method": pred.method, "scheduled_departures": pred.scheduled_departures, "support": pred.support}
