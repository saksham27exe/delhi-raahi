"""Free OpenStreetMap services, called from the server (the app only talks to our API).

- Place search: Photon (photon.komoot.io), restricted to a Delhi NCR bounding box.
- Walking: FOSSGIS OSRM foot profile (routing.openstreetmap.de/routed-foot):
  `table` for walking distances from one point to many stops in one request, `route` for the path.

Privacy: user coordinates are rounded to 4 decimals (~11 m) before leaving the server; no user ids
or headers identifying the user are sent. Every call has a short timeout; on failure callers fall
back to the straight-line estimate and label it as such. Results are cached in memory.
"""
from __future__ import annotations

import logging
import threading
import time
from collections import OrderedDict

import requests

log = logging.getLogger(__name__)

PHOTON_URL = "https://photon.komoot.io/api/"
FOOT_URL = "https://routing.openstreetmap.de/routed-foot"


def parse_steps(data: dict | None) -> dict | None:
    """OSRM route response (steps=true) -> {distance_m, duration_s, path, steps}. None if unusable."""
    if not data or data.get("code") != "Ok" or not data.get("routes"):
        return None
    rt = data["routes"][0]
    coords = rt.get("geometry", {}).get("coordinates", [])
    step = max(1, len(coords) // 400)           # detailed enough to notice a wrong turn
    path = [[round(y, 6), round(x, 6)] for x, y in coords[::step]]
    if coords and path[-1] != [round(coords[-1][1], 6), round(coords[-1][0], 6)]:
        path.append([round(coords[-1][1], 6), round(coords[-1][0], 6)])
    steps = []
    for leg in rt.get("legs", []):
        for s in leg.get("steps", []):
            m = s.get("maneuver", {})
            loc = m.get("location") or [None, None]
            if loc[0] is None:
                continue
            steps.append({
                "type": m.get("type", "continue"), "modifier": m.get("modifier", ""),
                "name": s.get("name", "") or "", "distance_m": round(float(s.get("distance", 0.0)), 1),
                "lat": round(loc[1], 6), "lon": round(loc[0], 6), "bearing_after": int(m.get("bearing_after", 0) or 0),
            })
    return {"distance_m": float(rt.get("distance", 0.0)), "duration_s": float(rt.get("duration", 0.0)), "path": path, "steps": steps}
USER_AGENT = "DelhiRaahi/0.9 (hackathon transit-planner demo)"
NCR_BBOX = (76.84, 28.40, 77.55, 28.90)          # minLon, minLat, maxLon, maxLat
TIMEOUT_S = 4.0


def _r(x: float) -> float:
    return round(float(x), 4)


class _LRU:
    def __init__(self, size: int = 2000):
        self.size, self.d, self.lock = size, OrderedDict(), threading.Lock()

    def get(self, k):
        with self.lock:
            if k in self.d:
                self.d.move_to_end(k)
                return self.d[k]
        return None

    def put(self, k, v):
        with self.lock:
            self.d[k] = v
            self.d.move_to_end(k)
            while len(self.d) > self.size:
                self.d.popitem(last=False)


class OsmGeo:
    def __init__(self, session: requests.Session | None = None):
        self.http = session or requests.Session()
        self.http.headers["User-Agent"] = USER_AGENT
        self._places, self._tables, self._routes = _LRU(500), _LRU(2000), _LRU(2000)
        self._down_until = 0.0          # back off for a minute after a failure

    def _get(self, url: str, params: dict | None = None) -> dict | None:
        if time.time() < self._down_until:
            return None
        try:
            r = self.http.get(url, params=params, timeout=TIMEOUT_S)
            r.raise_for_status()
            return r.json()
        except (requests.RequestException, ValueError) as e:
            log.warning("OSM service failed (%s): %s", url.split("/")[2], type(e).__name__)
            self._down_until = time.time() + 60
            return None

    # ---- place search -----------------------------------------------------------------
    def search(self, q: str, limit: int = 6) -> list[dict] | None:
        q = " ".join(q.split())[:100]
        key = (q.lower(), limit)
        hit = self._places.get(key)
        if hit is not None:
            return hit
        data = self._get(PHOTON_URL, {"q": q, "limit": limit * 2, "lang": "en",
                                      "bbox": ",".join(str(v) for v in NCR_BBOX)})
        if data is None:
            return None
        out, seen = [], set()
        for f in data.get("features", []):
            p = f.get("properties", {})
            lon, lat = f.get("geometry", {}).get("coordinates", [None, None])
            name = p.get("name") or " ".join(x for x in (p.get("housenumber"), p.get("street")) if x)
            if not name or lat is None:
                continue
            area = ", ".join(dict.fromkeys(x for x in (p.get("street") if p.get("name") else None, p.get("locality"),
                                                       p.get("district"), p.get("city") or p.get("county")) if x))
            k = (name.lower(), area.lower())
            if k in seen:
                continue
            seen.add(k)
            out.append({"name": name, "area": area, "lat": float(lat), "lon": float(lon),
                        "kind": p.get("osm_value") or p.get("osm_key") or "place"})
            if len(out) >= limit:
                break
        self._places.put(key, out)
        return out

    # ---- walking ----------------------------------------------------------------------
    def walk_distances(self, lat: float, lon: float, targets: list[tuple[float, float]]) -> list[float | None] | None:
        """Road walking distance (m) from (lat, lon) to each target, in one `table` request."""
        if not targets:
            return []
        src = (_r(lat), _r(lon))
        key = (src, tuple((round(a, 5), round(b, 5)) for a, b in targets))
        hit = self._tables.get(key)
        if hit is not None:
            return hit
        coords = ";".join(f"{b},{a}" for a, b in [src, *targets])
        data = self._get(f"{FOOT_URL}/table/v1/driving/{coords}", {"sources": "0", "annotations": "distance"})
        if not data or data.get("code") != "Ok":
            return None
        row = data["distances"][0][1:]
        out = [None if d is None else float(d) for d in row]
        self._tables.put(key, out)
        return out

    def walk_steps(self, a: tuple[float, float], b: tuple[float, float]) -> dict | None:
        """Turn-by-turn walking directions: distance, a detailed path and one entry per manoeuvre.

        Each step: type (depart/turn/continue/new name/roundabout/arrive/...), modifier (left, slight right, ...),
        name (street, may be ""), distance_m (length of the stretch after the manoeuvre), lat/lon of the
        manoeuvre and bearing_after (degrees, 0 = north). Text is built on the phone so it can be spoken in Hindi.
        """
        key = ("steps", (_r(a[0]), _r(a[1])), (round(b[0], 5), round(b[1], 5)))
        hit = self._routes.get(key)
        if hit is not None:
            return hit
        (la, lo), (lb, lob) = key[1], key[2]
        data = self._get(f"{FOOT_URL}/route/v1/driving/{lo},{la};{lob},{lb}",
                         {"overview": "full", "geometries": "geojson", "steps": "true"})
        out = parse_steps(data)
        if out is not None:
            self._routes.put(key, out)
        return out

    def walk_path(self, a: tuple[float, float], b: tuple[float, float]) -> dict | None:
        """Walking path between two points: distance (m) and [[lat, lon], ...]."""
        key = ((_r(a[0]), _r(a[1])), (round(b[0], 5), round(b[1], 5)))
        hit = self._routes.get(key)
        if hit is not None:
            return hit
        (la, lo), (lb, lob) = key
        data = self._get(f"{FOOT_URL}/route/v1/driving/{lo},{la};{lob},{lb}", {"overview": "full", "geometries": "geojson"})
        if not data or data.get("code") != "Ok" or not data.get("routes"):
            return None
        rt = data["routes"][0]
        coords = rt["geometry"]["coordinates"]
        step = max(1, len(coords) // 150)            # keep responses small
        path = [[round(y, 6), round(x, 6)] for x, y in coords[::step]]
        if coords and path[-1] != [round(coords[-1][1], 6), round(coords[-1][0], 6)]:
            path.append([round(coords[-1][1], 6), round(coords[-1][0], 6)])
        out = {"distance_m": float(rt["distance"]), "path": path}
        self._routes.put(key, out)
        return out
