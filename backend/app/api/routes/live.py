from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from app.api.deps import get_state, require_gtfs
from app.services import summaries
from app.services.state import AppState

router = APIRouter(prefix="/api/v1/live", tags=["live buses"])


@router.get("/status")
def status(st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    return {**st.live.status(), "enabled": st.live_poller is not None,
            "reason_disabled": st.load_errors.get("live_feed")}


@router.get("/vehicles")
def vehicles(route_id: str | None = None,
             bbox: str | None = Query(None, description="min_lat,min_lon,max_lat,max_lon"),
             st: AppState = Depends(get_state)) -> dict:
    """Latest bus positions (GTFS-RT), matched to routes and, where possible, scheduled trips."""
    require_gtfs(st)
    box = None
    if bbox:
        try:
            box = tuple(float(x) for x in bbox.split(","))
        except ValueError:
            box = ()
        if len(box) != 4:
            raise HTTPException(422, detail="bbox must be min_lat,min_lon,max_lat,max_lon")
    vs = st.live.list_vehicles(route_id, box)
    return {"live": st.live.status()["live"], "count": len(vs), "vehicles": vs}


@router.get("/vehicle")
def vehicle(id: str = Query(..., min_length=1, max_length=64), st: AppState = Depends(get_state)) -> dict:
    """One bus: position, route, its stops (passed or not) and estimated times at the stops ahead."""
    require_gtfs(st)
    d = st.live.vehicle_detail(id)
    if d is None:
        raise HTTPException(404, detail="That bus is not in the live feed right now.")
    if st.fares and d.get("route_id"):
        d["bus_type"] = st.fares.bus_type(str(d["route_id"]))[0]   # estimated per route from e-ticket sales
    return d


@router.get("/track")
def track(route_id: str, board_stop_id: str, alight_stop_id: str,
          vehicle_id: str | None = None, skip: str | None = Query(None, description="comma-separated vehicle ids to ignore"),
          st: AppState = Depends(get_state)) -> dict:
    """Follow one ride on live data: the bus coming to your stop, then your stops left to get off."""
    require_gtfs(st)
    for sid in (board_stop_id, alight_stop_id):
        if sid not in st.network.stops.index:
            raise HTTPException(404, detail=f"Unknown stop id '{sid}'.")
    return st.live.track(route_id, board_stop_id, alight_stop_id, vehicle_id,
                         set(filter(None, (skip or "").split(","))))


@router.get("/arrivals/{stop_id}")
def arrivals(stop_id: str, horizon_min: int = Query(60, ge=5, le=180), st: AppState = Depends(get_state)) -> dict:
    """Next buses at a stop: live estimates for tracked buses, timetable otherwise."""
    require_gtfs(st)
    if stop_id not in st.network.stops.index:
        raise HTTPException(404, detail=f"Unknown stop id '{stop_id}'.")
    board = st.live.arrivals(stop_id, horizon_min=horizon_min)
    return {**board, "summary": summaries.arrivals(board)}
