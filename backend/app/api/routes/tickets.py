from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException

from app.api.deps import get_state, require_gtfs, require_user, require_verified
from app.fare_engine.fares import RULES
from app.schemas.api import FareQuoteRequest, TicketVerifyRequest
from app.services import summaries
from app.services.state import AppState, _json
from app.services.accounts import AccountError, User
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
def book(req: FareQuoteRequest, st: AppState = Depends(get_state), u: User = Depends(require_verified)) -> dict:
    """DEMO booking for a logged-in user, paid from the DEMO wallet (no real money); not valid for travel.
    The ticket carries the holder's masked phone and is stored on the account."""
    require_gtfs(st)
    try:
        price = st.tickets.quote(**_args(req))["total"]
        return st.accounts.purchase(u.id, "ticket", price,
                                    lambda: st.tickets.book(**_args(req), holder=u.holder))
    except BookingError as e:
        raise HTTPException(422, detail=str(e)) from None
    except AccountError as e:
        raise HTTPException(e.status, detail=str(e)) from None


@router.post("/tickets/verify")
def verify(req: TicketVerifyRequest, st: AppState = Depends(get_state)) -> dict:
    require_gtfs(st)
    v = st.tickets.verify(req.qr_payload)
    if v.get("valid_signature") and st.accounts is not None:
        body = v["ticket"]
        item_id = body.get("ticket_id") or body.get("pass_id")
        # Signed but not held by any account (e.g. the account was deleted): do not accept.
        if not st.accounts.item_on_record(item_id):
            v["status"] = "not_on_record"
        elif getattr(st.accounts, "item_status", lambda _i: None)(item_id) == "voided":
            v["status"] = "voided"          # cancelled by support on the admin website
    return v
