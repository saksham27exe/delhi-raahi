"""Admin website: sign-in, CSRF, roles, forced password change, lockout, admin management, audit."""
import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import create_app
from app.services.accounts import AccountStore
from app.services.admin_store import AdminStore
from app.services.state import AppState

PW = "Owner-pass-1234"


@pytest.fixture
def env(monkeypatch):
    monkeypatch.setattr("app.api.routes.admin._login_limit", __import__("app.services.support", fromlist=["RateLimit"]).RateLimit(100, 600))
    adm = AdminStore(":memory:")
    adm.create_admin("owner@example.org", "Owner", "owner", PW, must_change=False)
    st = AppState(settings=get_settings(), accounts=AccountStore(":memory:", "k"), admin=adm)
    with TestClient(create_app(st, live=False)) as c:
        yield c, adm


def signin(c, email="owner@example.org", pw=PW):
    r = c.post("/api/v1/admin/login", json={"email": email, "password": pw})
    assert r.status_code == 200, r.text
    return r.json()["csrf"]


def test_login_sets_httponly_cookie_and_me(env):
    c, _ = env
    r = c.post("/api/v1/admin/login", json={"email": "OWNER@example.org", "password": PW})
    cookie = r.headers["set-cookie"].lower()
    assert "httponly" in cookie and "samesite=strict" in cookie
    me = c.get("/api/v1/admin/me").json()
    assert me["admin"]["role"] == "owner" and me["csrf"] == r.json()["csrf"]
    assert r.headers["x-frame-options"] == "DENY" and "no-store" in r.headers["cache-control"]


def test_wrong_password_and_lockout(env):
    c, _ = env
    for _ in range(5):
        assert c.post("/api/v1/admin/login", json={"email": "owner@example.org", "password": "nope"}).status_code == 401
    assert c.post("/api/v1/admin/login", json={"email": "owner@example.org", "password": PW}).status_code == 429
    assert c.post("/api/v1/admin/login", json={"email": "ghost@example.org", "password": PW}).status_code == 401


def test_needs_session_and_csrf(env):
    c, _ = env
    assert c.get("/api/v1/admin/admins").status_code == 401
    csrf = signin(c)
    assert c.post("/api/v1/admin/admins", json={"email": "a@example.org"}).status_code == 403          # no CSRF header
    r = c.post("/api/v1/admin/admins", json={"email": "a@example.org", "role": "agent"}, headers={"X-CSRF-Token": csrf})
    assert r.status_code == 200 and len(r.json()["one_time_password"]) == 16


def test_new_admin_must_change_password_and_roles(env):
    c, adm = env
    csrf = signin(c)
    otp = c.post("/api/v1/admin/admins", json={"email": "agent@example.org", "role": "viewer"},
                 headers={"X-CSRF-Token": csrf}).json()["one_time_password"]
    c.cookies.clear()
    csrf2 = signin(c, "agent@example.org", otp)
    assert c.get("/api/v1/admin/me").json()["admin"]["must_change"] is True
    assert c.get("/api/v1/admin/audit").status_code == 403                       # blocked until the password changes
    h = {"X-CSRF-Token": csrf2}
    assert c.post("/api/v1/admin/me/password", json={"old_password": otp, "new_password": "short"}, headers=h).status_code == 422
    assert c.post("/api/v1/admin/me/password", json={"old_password": otp, "new_password": "Viewer-pass-9876"}, headers=h).status_code == 200
    assert c.get("/api/v1/admin/me").json()["admin"]["must_change"] is False
    assert c.get("/api/v1/admin/audit").status_code == 403                       # viewer < agent
    assert c.get("/api/v1/admin/admins").status_code == 403                      # viewer < owner


def test_last_owner_is_protected_and_disable_signs_out(env):
    c, adm = env
    csrf = signin(c)
    h = {"X-CSRF-Token": csrf}
    assert c.patch("/api/v1/admin/admins/1", json={"role": "agent"}, headers=h).status_code == 409
    new = c.post("/api/v1/admin/admins", json={"email": "b@example.org", "role": "agent"}, headers=h).json()
    c2 = TestClient(c.app)
    c2.post("/api/v1/admin/login", json={"email": "b@example.org", "password": new["one_time_password"]})
    assert c2.get("/api/v1/admin/me").status_code == 200
    assert c.patch(f"/api/v1/admin/admins/{new['id']}", json={"disabled": True}, headers=h).status_code == 200
    assert c2.get("/api/v1/admin/me").status_code == 401


def test_audit_records_actions(env):
    c, _ = env
    csrf = signin(c)
    c.post("/api/v1/admin/admins", json={"email": "c@example.org"}, headers={"X-CSRF-Token": csrf})
    actions = [e["action"] for e in c.get("/api/v1/admin/audit").json()["entries"]]
    assert actions[:2] == ["admin_created", "login"]


def test_logout(env):
    c, _ = env
    csrf = signin(c)
    assert c.post("/api/v1/admin/logout", headers={"X-CSRF-Token": csrf}).status_code == 200
    assert c.get("/api/v1/admin/me").status_code == 401


def test_admin_site_path_traversal(env):
    c, _ = env
    r = c.get("/admin/..%2f..%2f.env")
    assert r.status_code in (200, 404) and "TS_API_KEY" not in r.text
