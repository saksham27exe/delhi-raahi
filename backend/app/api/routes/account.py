"""Accounts (phone + password), DEMO wallet, my tickets, DEMO passes, feedback.

Guests can plan trips, see crowding and live buses; buying a ticket or pass needs an account.
No real money moves anywhere: wallet top-ups are simulated and labelled DEMO.
"""
from __future__ import annotations

from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field

from app.api.deps import current_user, get_state, require_user, require_verified
from app.services import passes
from app.services.accounts import AccountError, User
from app.services.state import AppState
from app.services.ticket_service import BookingError

router = APIRouter(prefix="/api/v1", tags=["accounts, demo wallet & passes"])


class RegisterReq(BaseModel):
    phone: str = Field(..., max_length=20)
    password: str = Field(..., max_length=128)
    name: str = Field(..., max_length=60)


class LoginReq(BaseModel):
    phone: str = Field(..., max_length=20)
    password: str = Field(..., max_length=128)


class ProfileReq(BaseModel):
    name: str | None = Field(None, max_length=60)
    woman_traveller: bool | None = None


class PasswordReq(BaseModel):
    old_password: str = Field(..., max_length=128)
    new_password: str = Field(..., max_length=128)


class DeleteReq(BaseModel):
    password: str = Field(..., max_length=128)


class LangReq(BaseModel):
    lang: Literal["en", "hi"] = "en"


class ResetReq(BaseModel):
    email: str = Field(..., max_length=254)
    lang: Literal["en", "hi"] = "en"


class TopupReq(BaseModel):
    amount: float = Field(..., gt=0, le=100000)


class PassReq(BaseModel):
    kind: Literal["monthly", "daily"]
    bus_type: Literal["ac", "non_ac"]


class FeedbackReq(BaseModel):
    text: str = Field(..., max_length=2000)


def _acc(st: AppState):
    if st.accounts is None:
        raise HTTPException(503, detail="Accounts are not available on this server.")
    return st.accounts


def _err(e: AccountError):
    return HTTPException(e.status, detail=str(e))


@router.post("/auth/register")
def register(req: RegisterReq, st: AppState = Depends(get_state)) -> dict:
    try:
        token, u = _acc(st).register(req.phone, req.password, req.name)
    except AccountError as e:
        raise _err(e) from None
    return {"token": token, "user": u.public()}


@router.post("/auth/login")
def login(req: LoginReq, st: AppState = Depends(get_state)) -> dict:
    try:
        token, u = _acc(st).login(req.phone, req.password)
    except AccountError as e:
        raise _err(e) from None
    return {"token": token, "user": u.public()}


@router.post("/auth/logout")
def logout(u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    """Signs out everywhere: every token issued before now stops working."""
    _acc(st).logout(u.id)
    return {"ok": True}


@router.post("/auth/send-verification")
def send_verification(req: LangReq, u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    """Email a 'confirm your email' link (DelhiRaahi's own email). 503 if this server can't send email."""
    acc = _acc(st)
    if not hasattr(acc, "send_verification"):
        raise HTTPException(404, detail="Not used for these accounts.")
    try:
        return {"sent": acc.send_verification(u, req.lang)}
    except AccountError as e:
        raise _err(e) from None


@router.post("/auth/send-reset")
def send_reset(req: ResetReq, st: AppState = Depends(get_state)) -> dict:
    """Email a password-reset link if the account exists; the answer is the same either way."""
    acc = _acc(st)
    if not hasattr(acc, "send_reset"):
        raise HTTPException(404, detail="Not used for these accounts.")
    try:
        acc.send_reset(req.email, req.lang)
    except AccountError as e:
        raise _err(e) from None
    return {"ok": True}


@router.get("/me")
def me(u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    return {"user": u.public(), "wallet_balance": _acc(st).wallet(u.id, limit=0)["balance"]}


@router.patch("/me")
def update_me(req: ProfileReq, u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    try:
        return {"user": _acc(st).update_profile(u.id, req.name, req.woman_traveller).public()}
    except AccountError as e:
        raise _err(e) from None


@router.post("/me/password")
def change_password(req: PasswordReq, u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    try:
        return {"token": _acc(st).change_password(u.id, req.old_password, req.new_password)}
    except AccountError as e:
        raise _err(e) from None


@router.post("/me/delete")
def delete_me(req: DeleteReq, u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    """Permanently deletes the account, wallet history, tickets, passes and feedback."""
    try:
        _acc(st).delete(u.id, req.password)
    except AccountError as e:
        raise _err(e) from None
    if st.desk is not None:
        st.desk.delete_user(str(u.id))      # their support requests and complaints go too
    return {"deleted": True}


@router.get("/wallet")
def wallet(u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    return _acc(st).wallet(u.id)


@router.post("/wallet/topup")
def topup(req: TopupReq, u: User = Depends(require_verified), st: AppState = Depends(get_state)) -> dict:
    """DEMO top-up: no payment is taken, the balance only works inside this demo."""
    try:
        return _acc(st).topup(u.id, req.amount)
    except AccountError as e:
        raise _err(e) from None


@router.get("/tickets/mine")
def my_tickets(u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    return {"tickets": _acc(st).items(u.id, "ticket")}


@router.get("/passes/catalog")
def pass_catalog() -> dict:
    return passes.catalog()


@router.get("/passes/mine")
def my_passes(u: User = Depends(require_user), st: AppState = Depends(get_state)) -> dict:
    return {"passes": _acc(st).items(u.id, "pass")}


@router.post("/passes/buy")
def buy_pass(req: PassReq, u: User = Depends(require_verified), st: AppState = Depends(get_state)) -> dict:
    if st.tickets is None:
        raise HTTPException(503, detail="Ticketing is not available on this server.")
    price = passes.CATALOG[req.kind]["price"][req.bus_type]
    try:
        return _acc(st).purchase(u.id, "pass", price,
                                 lambda: st.tickets.issue_pass(req.kind, req.bus_type, u.holder))
    except AccountError as e:
        raise _err(e) from None
    except BookingError as e:
        raise HTTPException(422, detail=str(e)) from None


@router.post("/feedback")
def feedback(req: FeedbackReq, st: AppState = Depends(get_state), u: User | None = Depends(current_user)) -> dict:
    try:
        _acc(st).add_feedback(u.id if u else None, req.text)
    except AccountError as e:
        raise _err(e) from None
    return {"ok": True}
