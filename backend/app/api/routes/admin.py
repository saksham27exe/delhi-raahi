"""Admin website API: sign-in, admin accounts, audit log. Later phases add their own admin_* routers.

Sessions are an HttpOnly, SameSite=Strict cookie. Every request that changes something must also send
the session's CSRF token in `X-CSRF-Token`. An admin who must change their password can do nothing else.
"""
from __future__ import annotations

from typing import Callable, Literal

from fastapi import APIRouter, Depends, HTTPException, Request, Response
from pydantic import BaseModel, Field

from app.api.deps import get_state
from app.services.admin_store import Admin, AdminError, AdminStore, token_hash
from app.services.state import AppState
from app.services.support import RateLimit

COOKIE = "dr_admin"
router = APIRouter(prefix="/api/v1/admin", tags=["admin"])
_login_limit = RateLimit(10, 600)


def store(st: AppState = Depends(get_state)) -> AdminStore:
    if getattr(st, "admin", None) is None:
        raise HTTPException(503, detail="The admin store is not available on this server.")
    return st.admin


def ip_of(request: Request) -> str:
    return request.client.host if request.client else "?"


def err(e: AdminError) -> HTTPException:
    return HTTPException(e.status, detail=str(e))


def _current(request: Request, s: AdminStore, allow_must_change: bool = False) -> Admin:
    a = s.session(request.cookies.get(COOKIE))
    if a is None:
        raise HTTPException(401, detail="Please sign in.")
    if request.method not in ("GET", "HEAD") and request.headers.get("x-csrf-token") != a.csrf:
        raise HTTPException(403, detail="Missing or wrong CSRF token. Reload the page.")
    if a.must_change and not allow_must_change:
        raise HTTPException(403, detail="Change your password first.")
    return a


def require(role: Literal["viewer", "agent", "owner"] = "viewer") -> Callable[..., Admin]:
    """Dependency: a signed-in admin with at least this role."""
    def dep(request: Request, s: AdminStore = Depends(store)) -> Admin:
        a = _current(request, s)
        if not a.can(role):
            raise HTTPException(403, detail=f"This needs the {role} role.")
        return a
    return dep


def audit(request: Request, a: Admin, action: str, target: str | None = None, detail: dict | None = None) -> None:
    request.app.state.cw.admin.audit(a, None, action, target, detail, ip_of(request))


# ---------- sign-in ----------
class LoginReq(BaseModel):
    email: str = Field(..., max_length=254)
    password: str = Field(..., max_length=200)


class PasswordReq(BaseModel):
    old_password: str = Field(..., max_length=200)
    new_password: str = Field(..., max_length=200)


@router.post("/login")
def login(req: LoginReq, request: Request, response: Response, s: AdminStore = Depends(store)) -> dict:
    if not _login_limit.allow(ip_of(request)):
        raise HTTPException(429, detail="Too many sign-in attempts. Try again in a few minutes.")
    try:
        token, a = s.login(req.email, req.password, ip_of(request))
    except AdminError as e:
        raise err(e) from None
    response.set_cookie(COOKIE, token, httponly=True, samesite="strict", secure=request.url.scheme == "https",
                        path="/", max_age=12 * 3600)
    return {"admin": a.public(), "csrf": a.csrf}


@router.post("/logout")
def logout(request: Request, response: Response, s: AdminStore = Depends(store)) -> dict:
    a = s.session(request.cookies.get(COOKIE))
    if a is not None:
        if request.headers.get("x-csrf-token") != a.csrf:
            raise HTTPException(403, detail="Missing or wrong CSRF token.")
        s.audit(a, None, "logout", None, None, ip_of(request))
        s.logout(request.cookies.get(COOKIE))
    response.delete_cookie(COOKIE, path="/")
    return {"ok": True}


@router.get("/me")
def me(request: Request, s: AdminStore = Depends(store)) -> dict:
    a = _current(request, s, allow_must_change=True)
    return {"admin": a.public(), "csrf": a.csrf}


@router.post("/me/password")
def change_password(req: PasswordReq, request: Request, s: AdminStore = Depends(store)) -> dict:
    a = _current(request, s, allow_must_change=True)
    try:
        s.change_password(a.id, req.old_password, req.new_password, keep_token_hash=token_hash(request.cookies[COOKIE]))
    except AdminError as e:
        raise err(e) from None
    s.audit(a, None, "password_changed", f"admin:{a.id}", None, ip_of(request))
    return {"ok": True}


# ---------- admin accounts (owner) ----------
class NewAdminReq(BaseModel):
    email: str = Field(..., max_length=254)
    name: str = Field("", max_length=60)
    role: Literal["viewer", "agent", "owner"] = "agent"


class AdminPatch(BaseModel):
    role: Literal["viewer", "agent", "owner"] | None = None
    disabled: bool | None = None
    name: str | None = Field(None, max_length=60)


@router.get("/admins")
def admins(a: Admin = Depends(require("owner")), s: AdminStore = Depends(store)) -> dict:
    return {"admins": s.list_admins()}


@router.post("/admins")
def add_admin(req: NewAdminReq, request: Request, a: Admin = Depends(require("owner")), s: AdminStore = Depends(store)) -> dict:
    from app.services.admin_store import generate_password
    pw = generate_password()
    try:
        new_id = s.create_admin(req.email, req.name, req.role, pw, must_change=True)
    except AdminError as e:
        raise err(e) from None
    audit(request, a, "admin_created", f"admin:{new_id}", {"email": req.email.lower(), "role": req.role})
    return {"id": new_id, "one_time_password": pw}


@router.patch("/admins/{admin_id}")
def edit_admin(admin_id: int, req: AdminPatch, request: Request, a: Admin = Depends(require("owner")),
               s: AdminStore = Depends(store)) -> dict:
    if admin_id == a.id and (req.disabled or (req.role and req.role != "owner")):
        raise HTTPException(409, detail="You can't disable yourself or remove your own owner role.")
    try:
        s.update_admin(admin_id, role=req.role, disabled=req.disabled, name=req.name)
    except AdminError as e:
        raise err(e) from None
    audit(request, a, "admin_updated", f"admin:{admin_id}", req.model_dump(exclude_none=True))
    return {"ok": True}


@router.post("/admins/{admin_id}/reset-password")
def reset_admin_password(admin_id: int, request: Request, a: Admin = Depends(require("owner")),
                         s: AdminStore = Depends(store)) -> dict:
    try:
        pw = s.reset_password(admin_id)
    except AdminError as e:
        raise err(e) from None
    audit(request, a, "admin_password_reset", f"admin:{admin_id}")
    return {"one_time_password": pw}


# ---------- audit log ----------
@router.get("/audit")
def audit_log(limit: int = 100, before: int | None = None, action: str | None = None, admin: str | None = None,
              a: Admin = Depends(require("agent")), s: AdminStore = Depends(store)) -> dict:
    return {"entries": s.audit_log(limit, before, action, admin)}


@router.get("/agents")
def agents(a: Admin = Depends(require("viewer")), s: AdminStore = Depends(store)) -> dict:
    """Admins who can take support requests (active agents and owners), for assignment menus."""
    return {"agents": [{"id": r["id"], "name": r["name"]} for r in s.list_admins() if not r["disabled"] and r["role"] != "viewer"]}
