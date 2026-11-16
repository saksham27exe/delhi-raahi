"""Admin website: app users, their demo wallet, tickets and passes.

Viewers see contact details masked. Agents can disable/enable, adjust the demo wallet, void a ticket
or pass and resend the email confirmation. Only owners can delete an account. Everything is audited.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.api.deps import get_state
from app.api.routes.admin import audit, require
from app.services.accounts import AccountError, mask_phone
from app.services.admin_store import Admin
from app.services.firebase_accounts import mask_email
from app.services.state import AppState

router = APIRouter(prefix="/api/v1/admin", tags=["admin: users & bookings"])


def accounts(st: AppState = Depends(get_state)):
    if st.accounts is None or not hasattr(st.accounts, "admin_users"):
        raise HTTPException(503, detail="Accounts are not available on this server.")
    return st.accounts


def _e(e: AccountError) -> HTTPException:
    return HTTPException(e.status, detail=str(e))


def _mask(u: dict, a: Admin) -> dict:
    if a.can("agent"):
        return u
    return {**u, "email": mask_email(u.get("email", "")) if u.get("email") else "",
            "phone": mask_phone(u["phone"]) if u.get("phone") else ""}


@router.get("/users")
def users(q: str = "", cursor: str | None = None, limit: int = 25, a: Admin = Depends(require("viewer")), acc=Depends(accounts)) -> dict:
    if q.strip() and not a.can("agent"):
        raise HTTPException(403, detail="Searching riders needs the agent role (viewers see masked contact details).")
    try:
        res = acc.admin_users(q.strip(), min(max(limit, 1), 100), cursor)
    except AccountError as e:
        raise _e(e) from None
    return {**res, "users": [_mask(u, a) for u in res["users"]], "store": getattr(acc, "kind", "local")}


@router.get("/users/stats")
def user_stats(days: int = 30, a: Admin = Depends(require("viewer")), acc=Depends(accounts)) -> dict:
    return acc.admin_stats(min(max(days, 1), 365))


@router.get("/users/{uid}")
def user(uid: str, a: Admin = Depends(require("viewer")), acc=Depends(accounts), st: AppState = Depends(get_state)) -> dict:
    try:
        u = acc.admin_user(uid)
    except AccountError as e:
        raise _e(e) from None
    reqs = st.desk.list(size=50, sort="newest", user_id=uid)["items"] if st.desk is not None else []
    return {**_mask(u, a), "support": [{k: r[k] for k in ("id", "ref", "kind", "status", "priority", "created_at")} for r in reqs]}


class DisableReq(BaseModel):
    disabled: bool
    reason: str = Field(..., min_length=3, max_length=200)


@router.post("/users/{uid}/disable")
def disable(uid: str, req: DisableReq, request: Request, a: Admin = Depends(require("agent")), acc=Depends(accounts)) -> dict:
    try:
        acc.admin_set_disabled(uid, req.disabled)
    except AccountError as e:
        raise _e(e) from None
    audit(request, a, "user_disabled" if req.disabled else "user_enabled", f"user:{uid}", {"reason": req.reason})
    return {"ok": True}


class WalletReq(BaseModel):
    amount: float = Field(..., ge=-10000, le=10000)
    reason: str = Field(..., min_length=3, max_length=200)


@router.post("/users/{uid}/wallet")
def adjust_wallet(uid: str, req: WalletReq, request: Request, a: Admin = Depends(require("agent")), acc=Depends(accounts)) -> dict:
    if round(req.amount * 100) == 0:
        raise HTTPException(422, detail="Enter an amount other than zero.")
    try:
        w = acc.admin_adjust(uid, req.amount, req.reason)
    except AccountError as e:
        raise _e(e) from None
    audit(request, a, "wallet_adjusted", f"user:{uid}", {"amount": req.amount, "reason": req.reason, "demo": True})
    return w


@router.post("/users/{uid}/resend-verification")
def resend(uid: str, request: Request, lang: str = "en", a: Admin = Depends(require("agent")), acc=Depends(accounts)) -> dict:
    try:
        sent = acc.admin_resend_verification(uid, "hi" if lang == "hi" else "en")
    except AccountError as e:
        raise _e(e) from None
    audit(request, a, "verification_resent", f"user:{uid}")
    return {"ok": True, "sent": sent}


class DeleteReq(BaseModel):
    confirm: str = Field(..., max_length=254)      # the user's email, phone or id, typed again
    reason: str = Field(..., min_length=3, max_length=200)


@router.post("/users/{uid}/delete")
def delete(uid: str, req: DeleteReq, request: Request, a: Admin = Depends(require("owner")), acc=Depends(accounts),
           st: AppState = Depends(get_state)) -> dict:
    try:
        u = acc.admin_user(uid)
    except AccountError as e:
        raise _e(e) from None
    if req.confirm.strip().lower() not in {str(u["id"]).lower(), (u.get("email") or "").lower(), u.get("phone") or ""} - {""}:
        raise HTTPException(422, detail="Type the user's email, phone or id to confirm.")
    try:
        acc.admin_delete(uid)
    except AccountError as e:
        raise _e(e) from None
    removed = st.desk.delete_user(uid) if st.desk is not None else 0
    audit(request, a, "user_deleted", f"user:{uid}", {"reason": req.reason, "support_requests_removed": removed})
    return {"deleted": True}


# ---------- tickets & passes ----------
class VoidReq(BaseModel):
    reason: str = Field(..., min_length=3, max_length=200)


@router.get("/items/{item_id}")
def find_item(item_id: str, a: Admin = Depends(require("viewer")), acc=Depends(accounts)) -> dict:
    owner = acc.admin_find_item(item_id.strip())
    if owner is None:
        raise HTTPException(404, detail="No ticket or pass with that id.")
    return {"user_id": owner, "status": acc.item_status(item_id.strip())}


@router.post("/items/{item_id}/void")
def void(item_id: str, req: VoidReq, request: Request, a: Admin = Depends(require("agent")), acc=Depends(accounts)) -> dict:
    try:
        acc.admin_void(item_id, req.reason)
    except AccountError as e:
        raise _e(e) from None
    audit(request, a, "item_voided", f"item:{item_id}", {"reason": req.reason})
    return {"ok": True}


class VerifyReq(BaseModel):
    qr_payload: str = Field(..., max_length=4000)


@router.post("/verify")
def verify_qr(req: VerifyReq, a: Admin = Depends(require("viewer")), st: AppState = Depends(get_state)) -> dict:
    """Check a ticket or pass QR as an inspector would (signature, validity, on record, voided)."""
    if st.tickets is None:
        raise HTTPException(503, detail="Ticketing is not available on this server.")
    from app.api.routes.tickets import verify as public_verify
    from app.schemas.api import TicketVerifyRequest
    return public_verify(TicketVerifyRequest(qr_payload=req.qr_payload), st)
