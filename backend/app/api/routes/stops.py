from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from app.api.deps import get_state, require_gtfs
from app.services.crowd_views import primary_pattern
from app.services.state import AppState

router = APIRouter(prefix="/api/v1", tags=["stops & routes"])


def _hhmm(s: float) -> str:
    s = int(s)
    return f"{(s // 3600) % 24:02d}:{(s % 3600) // 60:02d}"


@router.get("/stops/search")
def stop_search(q: str = Query(..., min_length=2, max_length=80), limit: int = Query(10, ge=1, le=25),
                st: AppState = Depends(get_state)) -> dict:
    """Typo-tolerant stop search. Same-name stops on both sides of a road are grouped."""
    require_gtfs(st)
    return {"query": q, "results": st.journeys.search_stops(q, limit)}


@router.get("/stops/nearby")
def stops_nearby(lat: float = Query(..., ge=27.5, le=29.5), lon: float = Query(..., ge=76.5, le=78.0),
                 radius_m: int = Query(600, ge=50, le=2000), st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    near = st.network.nearby_stops(lat, lon, radius_m, limit=15)
    return {"results": [{"stop_id": r.stop_id, "name": r.stop_name, "lat": float(r.stop_lat), "lon": float(r.stop_lon),
                         "distance_m": round(float(r.distance_m))} for r in near.itertuples()]}


@router.get("/stops/{stop_id}")
def stop_detail(stop_id: str, st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    net = st.network
    if stop_id not in net.stops.index:
        raise HTTPException(404, detail=f"Unknown stop id '{stop_id}'.")
    r = net.stops.loc[stop_id]
    routes = {}
    for k, pos in net.stop_patterns.get(stop_id, []):
        p = net.patterns[k]
        rt = net.routes.loc[p.route_id] if p.route_id in net.routes.index else None
        routes[p.route_id] = {"route_id": p.route_id, "route": str(rt["route_short_name"]) if rt is not None else p.route_id,
                              "towards": net.stop_name(p.stops[-1]), "terminal_here": pos == len(p.stops) - 1}
    return {"stop_id": stop_id, "name": r["stop_name"], "lat": float(r["stop_lat"]), "lon": float(r["stop_lon"]),
            "routes": sorted(routes.values(), key=lambda x: x["route"])}


@router.get("/routes/search")
def route_search(q: str = Query(..., min_length=1, max_length=20), limit: int = Query(15, ge=1, le=50),
                 st: AppState = Depends(get_state)) -> dict:
    """Find a bus by its number, e.g. '717' -> 717AUP, 717ADOWN, 717UP ..."""
    require_gtfs(st)
    key = "".join(ch for ch in q.upper() if ch.isalnum())
    r = st.network.routes
    norm = r["route_short_name"].astype(str).str.upper().str.replace(r"[^A-Z0-9]", "", regex=True)
    m = norm.str.startswith(key)
    hit = r[m].assign(_exact=norm[m].str.fullmatch(key + "(UP|DOWN)?"))
    hit = hit.sort_values(["_exact", "route_short_name"], ascending=[False, True]).head(limit)
    return {"query": q, "results": [{"route_id": str(x.route_id), "route": str(x.route_short_name),
                                     "description": str(getattr(x, "route_desc", "") or "")} for x in hit.itertuples()]}


@router.get("/routes/{route_id}")
def route_detail(route_id: str, st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    net = st.network
    if route_id not in net.routes.index:
        raise HTTPException(404, detail=f"Unknown route id '{route_id}'.")
    k = primary_pattern(net, route_id)
    rt = net.routes.loc[route_id]
    out = {"route_id": route_id, "route": str(rt["route_short_name"]), "description": str(rt.get("route_desc", "") or ""),
           "stops": [], "trips_per_day": 0, "first_departure": None, "last_departure": None}
    if k is not None:
        p = net.patterns[k]
        cum = st.fares.cum_km(k) if st.fares else None
        out["stops"] = [{"stop_id": s, "name": net.stop_name(s), "lat": float(net.stops.at[s, "stop_lat"]),
                         "lon": float(net.stops.at[s, "stop_lon"]),
                         "km": None if cum is None else round(float(cum[i]), 2)} for i, s in enumerate(p.stops)]
        first = sorted(float(t) for ps in net.patterns if ps.route_id == route_id for t in ps.dep[:, 0])
        out.update(trips_per_day=len(first), first_departure=_hhmm(first[0]) if first else None,
                   last_departure=_hhmm(first[-1]) if first else None)
    if st.fares:
        out["bus_type"], out["ac_ticket_share"] = st.fares.bus_type(route_id)
    return out
