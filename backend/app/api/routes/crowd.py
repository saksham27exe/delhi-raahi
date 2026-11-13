from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query

from app.api.deps import get_state, parse_date, require_crowd, require_gtfs
from app.services import crowd_views, summaries
from app.services.crowd_service import CATEGORIES, METRIC, METRIC_DEFINITION
from app.services.state import AppState, bucket_of

router = APIRouter(prefix="/api/v1/crowd", tags=["crowding"])


def _bucket(time: str, st: AppState) -> int:
    try:
        return bucket_of(time, st.crowd.bucket_minutes)
    except ValueError as e:
        raise HTTPException(422, detail=str(e)) from None


def _meta(st: AppState, d) -> dict:
    _, basis = st.crowd.forecasts.slot_for(d)
    return {"metric": METRIC, "definition": METRIC_DEFINITION, "forecast_basis": basis,
            "data_window_end": str(st.crowd.forecasts.window_end)}


@router.get("/route/{route_id}")
def route_crowd(route_id: str, date: str | None = None, time: str | None = Query(None, description="HH:MM"),
                st: AppState = Depends(get_state)) -> dict:
    """Without `time`: the route through the day (when is it quiet?). With `time`: crowding stop by stop."""
    require_gtfs(st)
    require_crowd(st)
    d = parse_date(date)
    if route_id not in st.network.routes.index:
        raise HTTPException(404, detail=f"Unknown route id '{route_id}'.")
    route = str(st.network.routes.at[route_id, "route_short_name"])
    if time is None:
        res = crowd_views.route_day_profile(st.network, st.crowd, route_id, d)
        if res and res["quietest"]:
            res["summary"] = {
                "en": f"Bus {route} is usually least crowded around {res['quietest']} and busiest around {res['busiest']}.",
                "hi": f"बस {route} में आम तौर पर {res['quietest']} के आसपास सबसे कम और {res['busiest']} के आसपास सबसे ज़्यादा भीड़ होती है।"}
    else:
        res = crowd_views.route_along(st.network, st.crowd, route_id, d, _bucket(time, st))
        if res:
            cats = [s["category"] for s in res["segments"] if s["category"] in CATEGORIES]
            worst = max(cats, key=CATEGORIES.index) if cats else "UNKNOWN"
            res["summary"] = {"en": summaries.crowd(worst, f"bus {route}", time)["en"],
                              "hi": summaries.crowd(worst, f"बस {route}", time)["hi"]}
    if res is None:
        raise HTTPException(404, detail="Route has no timetable pattern.")
    return {**res, "route": route, **_meta(st, d)}


@router.get("/segment/{segment_id}")
def segment(segment_id: str, date: str | None = None, time: str = Query(..., description="HH:MM"),
            st: AppState = Depends(get_state)) -> dict:
    require_crowd(st)
    d = parse_date(date)
    return {**st.crowd.predict(segment_id, d, _bucket(time, st)).to_dict(), **_meta(st, d)}


@router.get("/hotspots")
def hotspots(date: str | None = None, time: str = Query(..., description="HH:MM"),
             limit: int = Query(50, ge=1, le=300), st: AppState = Depends(get_state)) -> dict:
    """Busiest road segments in the network at a time (only well-supported estimates)."""
    require_gtfs(st)
    require_crowd(st)
    d = parse_date(date)
    return {"date": d.isoformat(), "time": time,
            "segments": crowd_views.hotspots(st.network, st.crowd, d, _bucket(time, st), limit), **_meta(st, d)}
