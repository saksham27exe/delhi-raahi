"""Admin website: support desk (complaints, problems, ticket questions, suggestions from the app)."""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import Response
from pydantic import BaseModel, Field

from app.api.deps import get_state
from app.api.routes.admin import audit, err, require
from app.services import support as sup
from app.services.admin_store import Admin, AdminError
from app.services.state import AppState
from app.services.support_desk import SupportDesk

IST = timezone(timedelta(hours=5, minutes=30))
router = APIRouter(prefix="/api/v1/admin/support", tags=["admin: support desk"])
log = logging.getLogger(__name__)


def desk(st: AppState = Depends(get_state)) -> SupportDesk:
    if st.desk is None:
        raise HTTPException(503, detail="The support desk is not available on this server.")
    return st.desk


def _for(a: Admin, r: dict) -> dict:
    """Viewers (read-only role) see the desk without riders' contact details, like on the Users page."""
    if a.can("agent") or not r.get("email"):
        return r
    from app.services.firebase_accounts import mask_email
    return {**r, "email": mask_email(r["email"])}


@router.get("")
def list_requests(status: str | None = None, kind: str | None = None, priority: str | None = None,
                  assignee: str | None = None, q: str | None = None, route: str | None = None, user_id: str | None = None,
                  safety: bool = False, sort: str = "priority", page: int = 1, size: int = 25,
                  a: Admin = Depends(require("viewer")), d: SupportDesk = Depends(desk)) -> dict:
    if q and not a.can("agent"):
        q = None if "@" in q else q          # no probing for a masked email one letter at a time
    try:
        res = d.list(page=page, size=size, sort=sort, status=status, kind=kind, priority=priority, assignee=assignee,
                     q=q, route=route, user_id=user_id, safety=safety)
    except AdminError as e:
        raise err(e) from None
    return {**res, "items": [_for(a, r) for r in res["items"]], "counts": d.counts()}


@router.get("/stats")
def stats(days: int = 30, a: Admin = Depends(require("viewer")), d: SupportDesk = Depends(desk)) -> dict:
    return d.stats(min(max(days, 1), 365))


@router.get("/export.csv")
def export(request: Request, status: str | None = None, kind: str | None = None, route: str | None = None,
           priority: str | None = None, assignee: str | None = None, q: str | None = None, safety: bool = False,
           a: Admin = Depends(require("agent")), d: SupportDesk = Depends(desk)) -> Response:
    filters = {"status": status, "kind": kind, "route": route, "priority": priority, "assignee": assignee, "q": q, "safety": safety}
    try:
        body = d.csv(**filters)
    except AdminError as e:
        raise err(e) from None
    audit(request, a, "support_exported", None, {k: v for k, v in filters.items() if v})
    name = f"delhiraahi-support-{datetime.now(IST):%Y%m%d-%H%M}.csv"
    return Response("\ufeff" + body, media_type="text/csv; charset=utf-8",
                    headers={"Content-Disposition": f'attachment; filename="{name}"'})


@router.get("/{rid}")
def get_request(rid: int, a: Admin = Depends(require("viewer")), d: SupportDesk = Depends(desk)) -> dict:
    try:
        return _for(a, d.get(rid))
    except AdminError as e:
        raise err(e) from None


class Patch(BaseModel):
    status: Literal["new", "in_progress", "waiting", "resolved"] | None = None
    priority: Literal["low", "normal", "high", "urgent"] | None = None
    assignee_id: int | None = None
    unassign: bool = False


@router.patch("/{rid}")
def update(rid: int, req: Patch, request: Request, a: Admin = Depends(require("agent")), d: SupportDesk = Depends(desk)) -> dict:
    try:
        changes = d.update(rid, a, req.status, req.priority, req.assignee_id, req.unassign)
    except AdminError as e:
        raise err(e) from None
    if changes:
        audit(request, a, "support_updated", f"support:{rid}", changes)
    return d.get(rid)


class Note(BaseModel):
    body: str = Field(..., min_length=1, max_length=4000)


@router.post("/{rid}/notes")
def add_note(rid: int, req: Note, request: Request, a: Admin = Depends(require("agent")), d: SupportDesk = Depends(desk)) -> dict:
    try:
        d.get(rid)
    except AdminError as e:
        raise err(e) from None
    d.event(rid, a, "note", req.body.strip())
    audit(request, a, "support_note", f"support:{rid}")
    return d.get(rid)


class Reply(BaseModel):
    body: str = Field(..., min_length=2, max_length=6000)
    then: Literal["waiting", "resolved", "in_progress"] = "waiting"


@router.post("/{rid}/reply")
def reply(rid: int, req: Reply, request: Request, a: Admin = Depends(require("agent")), d: SupportDesk = Depends(desk),
          st: AppState = Depends(get_state)) -> dict:
    try:
        r = d.get(rid)
    except AdminError as e:
        raise err(e) from None
    if not r["email"]:
        raise HTTPException(409, detail="This person gave no email address, so they can't be answered by email. Add an internal note instead.")
    if st.mailer is None:
        raise HTTPException(503, detail="Email is not set up on this server (SMTP_USER / SMTP_PASSWORD in .env).")
    inbox = (st.settings.support_email or st.settings.smtp_user or "").strip() or None
    try:
        st.mailer.send(r["email"], *sup.reply_email(r["ref"], r["kind"], req.body.strip(), r["message"], a.name, r["lang"]), reply_to=inbox)
    except Exception as e:  # noqa: BLE001
        log.warning("support reply email failed for %s: %s", r["ref"], type(e).__name__)
        raise HTTPException(502, detail="The email could not be sent. Try again in a minute.") from None
    d.event(rid, a, "reply", req.body.strip(), {"to": r["email"]})
    d.update(rid, a, status=req.then)
    audit(request, a, "support_reply", f"support:{rid}", {"then": req.then})
    return d.get(rid)
