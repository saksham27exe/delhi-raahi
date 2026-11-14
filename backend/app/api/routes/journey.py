from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_state, require_gtfs
from app.schemas.api import AdvisorRequest, JourneySearchRequest
from app.services import summaries
from app.services.journey_service import PlaceError
from app.services.state import AppState

router = APIRouter(prefix="/api/v1/journeys", tags=["journeys"])


@router.post("/search")
def search(req: JourneySearchRequest, st: AppState = Depends(get_state)) -> dict:
    """Timetable-based bus journeys between two places, ranked by time and expected crowding."""
    require_gtfs(st)
    try:
        res = st.journeys.plan(req.origin.model_dump(), req.destination.model_dump(), req.departure_datetime,
                               req.preference, req.max_transfers, req.access_radius_m)
    except PlaceError as e:
        raise HTTPException(422, detail=e.detail()) from None
    for j in res["journeys"]:
        j["summary"] = summaries.journey(j)
    if not res["journeys"]:
        res["notes"].insert(0, "No scheduled bus journey with at most one transfer was found within 3 hours. "
                               "Try a different time or a nearby stop.")
    return res


@router.post("/advisor")
def advisor(req: AdvisorRequest, st: AppState = Depends(get_state)) -> dict:
    """Smart departure advisor: compare nearby departure times on duration and expected crowding."""
    require_gtfs(st)
    try:
        return st.journeys.advise(req.origin.model_dump(), req.destination.model_dump(), req.departure_datetime,
                                  req.preference, tuple(req.offsets_min), req.access_radius_m)
    except PlaceError as e:
        raise HTTPException(422, detail=e.detail()) from None
