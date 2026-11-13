from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_state, require_gtfs
from app.fare_engine.fares import RULES
from app.schemas.api import FareQuoteRequest, TicketVerifyRequest
from app.services import summaries
from app.services.state import AppState, _json
from app.services.ticket_service import BookingError

router = APIRouter(prefix="/api/v1", tags=["fares & demo tickets"])


def _args(req: FareQuoteRequest) -> dict:
    return dict(legs=[l.model_dump() for l in req.legs], passengers=req.passengers, women_free=req.women_free,
                bus_types=req.bus_types)


@router.get("/fares/rules")
def fare_rules(st: AppState = Depends(get_state)) -> dict:
    """The slab table in use and how well it matches what passengers actually paid."""
    return {"rules": RULES, "validation": _json(st.settings.metrics_dir / "fare_validation.json")}


@router.post("/fares/quote")
def quote(req: FareQuoteRequest, st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    try:
        q = st.tickets.quote(**_args(req))
    except BookingError as e:
        raise HTTPException(422, detail=str(e)) from None
    return {**q, "summary": summaries.fare_quote(q)}


@router.post("/tickets/book")
def book(req: FareQuoteRequest, st: AppState = Depends(get_state)) -> dict:
    """DEMO booking: no payment, not valid for travel. The ticket is returned to be stored on the device."""
    require_gtfs(st)
    try:
        return st.tickets.book(**_args(req))
    except BookingError as e:
        raise HTTPException(422, detail=str(e)) from None


@router.post("/tickets/verify")
def verify(req: TicketVerifyRequest, st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    return st.tickets.verify(req.qr_payload)
