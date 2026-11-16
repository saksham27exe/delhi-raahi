"""Regressions for bugs found in the QA pass (16 Nov 2026)."""
import time

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import create_app
from app.services import support as sup
from app.services.accounts import AccountStore
from app.services.admin_data import AdminData
from app.services.admin_store import AdminStore
from app.services.state import AppState
from app.services.support_desk import SupportDesk
from tests.admin_helpers import make_admins, signed_in


@pytest.fixture
def env(monkeypatch):
    monkeypatch.setattr("app.api.routes.admin._login_limit", sup.RateLimit(100, 600))
    adm = AdminStore(":memory:")
    make_admins(adm)
    st = AppState(settings=get_settings(), accounts=AccountStore(":memory:", "k"), admin=adm, desk=SupportDesk(adm),
                  admin_data=AdminData(adm))
    app = create_app(st, live=False)
    with TestClient(app) as public:
        yield app, public, st


# ---------- audit ledger
def _ledger(n=4):
    adm = AdminStore(":memory:")
    for i in range(n):
        adm.audit(None, f"a{i}@example.org", "login_failed", None, None, "1.1.1.1")
    assert adm.verify_ledger()["ok"]
    return adm


def test_ledger_wiped_hash_is_not_resealed():
    adm = _ledger()
    adm._db.execute("UPDATE audit SET hash = NULL, prev_hash = NULL, target = 'forged' WHERE id = 4")
    adm.audit(None, "x@example.org", "login_failed", None, None, None)      # next real entry must not launder it
    r = adm.verify_ledger()
    assert not r["ok"] and r["broken_at"] == 4


def test_ledger_injected_row_is_caught():
    adm = _ledger()
    adm._db.execute("INSERT INTO audit (at, admin_email, action) VALUES ('2026-11-16T00:00:00+00:00', 'evil@x', 'admin_created')")
    adm.audit(None, "x@example.org", "login_failed", None, None, None)
    r = adm.verify_ledger()
    assert not r["ok"] and r["broken_at"] == 5


def test_ledger_legacy_rows_sealed_once(tmp_path):
    db = tmp_path / "admin.db"
    adm = AdminStore(db)
    adm.audit(None, "a@example.org", "login_failed", None, None, None)
    assert adm.verify_ledger()["ok"]
    (tmp_path / "ledger_head.json").unlink()              # head removed by someone
    r = AdminStore(db).verify_ledger()
    assert not r["ok"] and "head" in r["reason"]


# ---------- admin: bad params are 4xx, not 500
def test_bad_query_params_are_not_500(env):
    app, public, st = env
    agent = signed_in(app, "agent")
    assert agent.get("/api/v1/admin/support", params={"assignee": "me"}).status_code == 422
    assert agent.get("/api/v1/admin/users", params={"cursor": "abc"}).status_code == 422
    assert agent.get("/api/v1/admin/support/99999999999999999999").status_code == 422


def test_wrong_current_password_is_not_401(env):
    app, public, st = env
    owner = signed_in(app, "owner")
    r = owner.post("/api/v1/admin/me/password", json={"old_password": "nope-nope-nope", "new_password": "Brand-new-pass-77"})
    assert r.status_code == 400          # 401 would sign the admin out of the site


def test_viewer_sees_masked_support_email_and_cannot_search_users(env):
    app, public, st = env
    st.desk.add("DR-1-AAAA", "complaint", "Bus never came", "rider.person@example.com", None, "534", None, None, None, "en")
    viewer, agent = signed_in(app, "viewer"), signed_in(app, "agent")
    v = viewer.get("/api/v1/admin/support").json()["items"][0]
    assert v["email"] != "rider.person@example.com" and "•" in v["email"]
    assert viewer.get(f"/api/v1/admin/support/{v['id']}").json()["email"] == v["email"]
    assert agent.get("/api/v1/admin/support").json()["items"][0]["email"] == "rider.person@example.com"
    assert viewer.get("/api/v1/admin/users", params={"q": "98"}).status_code == 403


def test_csv_export_follows_filters_and_keeps_empty_cells_empty(env):
    app, public, st = env
    st.desk.add("DR-1-AAAA", "complaint", "Open one", None, None, "534", None, None, None, "en")
    rid = st.desk.add("DR-1-BBBB", "complaint", "Closed one", None, None, "534", None, None, None, "en")
    st.desk.s.x("UPDATE support_requests SET status = 'resolved' WHERE id = ?", (rid,))
    agent = signed_in(app, "agent")
    text = agent.get("/api/v1/admin/support/export.csv", params={"status": "open"}).text
    assert "Open one" in text and "Closed one" not in text
    assert ",'," not in text and not text.rstrip().endswith("'")


def test_alert_end_time_can_be_cleared(env):
    app, public, st = env
    agent = signed_in(app, "agent")
    from datetime import datetime, timedelta, timezone
    end = (datetime.now(timezone.utc) + timedelta(hours=2)).isoformat()
    a = agent.post("/api/v1/admin/alerts", json={"severity": "info", "title_en": "Works", "ends_at": end}).json()
    assert a["ends_at"]
    b = agent.patch(f"/api/v1/admin/alerts/{a['id']}", json={"ends_at": None}).json()
    assert b["ends_at"] is None


# ---------- app side
def test_feedback_is_on_the_desk_once_even_after_restart_import(env):
    app, public, st = env
    assert public.post("/api/v1/feedback", json={"text": "Please add more night buses."}).status_code == 200
    assert st.desk.import_feedback(st.accounts.all_feedback()) == 0
    assert st.desk.list()["total"] == 1


def test_feedback_is_rate_limited(env):
    app, public, st = env
    codes = [public.post("/api/v1/feedback", json={"text": f"spam number {i}"}).status_code for i in range(12)]
    assert codes[:10] == [200] * 10 and codes[-1] == 429


def test_journey_dates_out_of_range_are_422(env):
    app, public, st = env
    body = {"origin": {"type": "stop_name", "name": "A"}, "destination": {"type": "stop_name", "name": "B"},
            "departure_datetime": "9999-12-31T23:30:00+05:30"}
    assert public.post("/api/v1/journeys/search", json=body).status_code == 422
    body["departure_datetime"] = "2026-11-20T09:00:00+05:30"
    assert public.post("/api/v1/journeys/advisor", json={**body, "offsets_min": [100000]}).status_code == 422
