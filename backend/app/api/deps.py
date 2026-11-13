from __future__ import annotations

from datetime import date

from fastapi import HTTPException, Request

from app.services.state import AppState


def get_state(request: Request) -> AppState:
    return request.app.state.cw


def require_gtfs(st: AppState) -> None:
    if not st.gtfs_ready:
        raise HTTPException(503, detail={"message": "Bus network (static GTFS) is not loaded yet.",
                                         "fix": st.load_errors.get("gtfs_network")})


def require_crowd(st: AppState) -> None:
    if st.crowd is None:
        raise HTTPException(503, detail={"message": "Crowd model is not available.",
                                         "fix": st.load_errors.get("crowd_model")})


def require_panel(st: AppState) -> None:
    if st.panel is None:
        raise HTTPException(503, detail={"message": "Demand data is not processed yet.",
                                         "fix": st.load_errors.get("demand_panel")})


def parse_date(s: str | None) -> date:
    if not s:
        from datetime import datetime
        from zoneinfo import ZoneInfo
        return datetime.now(ZoneInfo("Asia/Kolkata")).date()
    try:
        return date.fromisoformat(s)
    except ValueError:
        raise HTTPException(422, detail="date must be YYYY-MM-DD") from None
