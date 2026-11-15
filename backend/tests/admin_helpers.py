"""Shared set-up for admin website tests: an app with in-memory stores and signed-in clients per role."""
from __future__ import annotations

from fastapi.testclient import TestClient

from app.services.admin_store import AdminStore

PW = "Test-pass-12345"


def make_admins(adm: AdminStore) -> dict[str, int]:
    return {role: adm.create_admin(f"{role}@example.org", role.title(), role, PW, must_change=False)
            for role in ("owner", "agent", "viewer")}


def signed_in(app, role: str) -> TestClient:
    """A client signed in as owner / agent / viewer, sending its CSRF token on every request."""
    c = TestClient(app)
    r = c.post("/api/v1/admin/login", json={"email": f"{role}@example.org", "password": PW})
    assert r.status_code == 200, r.text
    c.headers["X-CSRF-Token"] = r.json()["csrf"]
    return c
