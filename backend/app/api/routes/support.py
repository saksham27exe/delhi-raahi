"""Help & support: send a complaint or support request from the app; see the support email address."""
from __future__ import annotations

import logging
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.api.deps import current_user, get_state
from app.services import support as sup
from app.services.accounts import AccountError, User
from app.services.state import AppState

router = APIRouter(prefix="/api/v1/support", tags=["help & support"])
log = logging.getLogger(__name__)
_limit = sup.RateLimit()


class SupportReq(BaseModel):
    kind: Literal["complaint", "problem", "ticket", "idea"]
    message: str = Field(..., max_length=2000)
    email: str | None = Field(None, max_length=254)      # where to reply; a copy is sent here
    route: str | None = Field(None, max_length=20)       # complaint about a bus: its route…
    when: str | None = Field(None, max_length=40)        # …and roughly when (free text, e.g. "today 9 am")
    where: str | None = Field(None, max_length=80)       # …and the stop or area
    app_version: str | None = Field(None, max_length=40)
    lang: Literal["en", "hi"] = "en"


def _inbox(st: AppState) -> str | None:
    s = st.settings
    return (s.support_email or s.smtp_user or "").strip() or None


@router.get("")
def info(st: AppState = Depends(get_state)) -> dict:
    """`email` is the address for "Email us"; `form` says whether in-app requests reach a person by email."""
    return {"email": _inbox(st), "form": st.mailer is not None and _inbox(st) is not None}


@router.post("")
def send(req: SupportReq, request: Request, st: AppState = Depends(get_state),
         u: User | None = Depends(current_user)) -> dict:
    message = req.message.strip()
    if not 10 <= len(message) <= 2000:
        raise HTTPException(422, detail="Please write at least 10 characters.")
    try:
        reply_to = sup.clean_email(req.email)
    except sup.SupportError as e:
        raise HTTPException(e.status, detail=str(e)) from None
    if not _limit.allow(request.client.host if request.client else "?"):
        raise HTTPException(429, detail="Too many messages. Please try again in a few minutes.")

    ref = sup.new_ref()
    fields = [("Reference", ref), ("Type", sup.KINDS[req.kind][0]), ("Received", sup.received_at()),
              ("Bus route", (req.route or "").strip()), ("When", (req.when or "").strip()),
              ("Stop or area", (req.where or "").strip()),
              ("Account", "signed in" if u else "guest"), ("Reply to", reply_to or ""),
              ("App", (req.app_version or "").strip())]
    # Kept in the support desk (admin website); "Delete account" removes it too.
    rid = None
    if st.desk is not None:
        rid = st.desk.add(ref, req.kind, message, reply_to, str(u.id) if u else None, (req.route or "").strip(),
                          (req.when or "").strip(), (req.where or "").strip(), (req.app_version or "").strip() or None, req.lang)
    elif st.accounts is not None:
        details = "; ".join(f"{k}: {v}" for k, v in fields[3:6] if v)
        try:
            st.accounts.add_feedback(u.id if u else None, f"[{ref}] {req.kind}" + (f" ({details})" if details else "") + f": {message}")
        except AccountError as e:
            raise HTTPException(e.status, detail=str(e)) from None

    inbox, emailed, copied = _inbox(st), False, False
    if st.mailer is not None and inbox:
        try:
            st.mailer.send(inbox, *sup.inbox_email(ref, req.kind, message, fields, reply_to), reply_to=reply_to)
            emailed = True
            if rid:
                st.desk.mark_emailed(rid)
        except Exception as e:  # noqa: BLE001  (never log the SMTP password; the exception text doesn't hold it)
            log.warning("support email to inbox failed for %s: %s", ref, type(e).__name__)
        if emailed and reply_to:
            try:
                st.mailer.send(reply_to, *sup.copy_email(ref, req.kind, message, fields[:6], req.lang))
                copied = True
            except Exception as e:  # noqa: BLE001
                log.warning("support copy email failed for %s: %s", ref, type(e).__name__)
    if not emailed and rid is None and st.accounts is None:
        raise HTTPException(503, detail="Support isn't available on this server right now.")
    return {"ok": True, "ref": ref, "emailed": emailed, "copy_sent": copied}
