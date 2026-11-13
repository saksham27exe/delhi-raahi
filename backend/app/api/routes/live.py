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


@router.get("/arrivals/{stop_id}")
def arrivals(stop_id: str, horizon_min: int = Query(60, ge=5, le=180), st: AppState = Depends(get_state)) -> dict:
    """Next buses at a stop: live estimates for tracked buses, timetable otherwise."""
    require_gtfs(st)
    if stop_id not in st.network.stops.index:
        raise HTTPException(404, detail=f"Unknown stop id '{stop_id}'.")
    board = st.live.arrivals(stop_id, horizon_min=horizon_min)
    return {**board, "summary": summaries.arrivals(board)}
