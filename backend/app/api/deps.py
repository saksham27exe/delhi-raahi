from __future__ import annotations

from datetime import date

from fastapi import HTTPException, Request

from app.services.accounts import User

from app.services.state import AppState


def get_state(request: Request) -> AppState:
    return request.app.state.cw


def current_user(request: Request) -> User | None:
    """User from `Authorization: Bearer <token>`, or None for guests."""
    st: AppState = request.app.state.cw
    h = request.headers.get("authorization", "")
    if st.accounts is None or not h.lower().startswith("bearer "):
        return None
    return st.accounts.user_for_token(h[7:])


def require_user(request: Request) -> User:
    st: AppState = request.app.state.cw
    if st.accounts is None:
        raise HTTPException(503, detail="Accounts are not available on this server.")
    u = current_user(request)
    if u is None:
        raise HTTPException(401, detail="Please log in to continue.", headers={"WWW-Authenticate": "Bearer"})
    return u


def require_verified(request: Request) -> User:
    """Signed in AND email verified (Firebase accounts): needed to add money, book or buy."""
    u = require_user(request)
    if not getattr(u, "verified", True):
        raise HTTPException(403, detail={"message": "Verify your email first: open the link we sent you, then try again.",
                                         "code": "email_not_verified"})
    return u


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
