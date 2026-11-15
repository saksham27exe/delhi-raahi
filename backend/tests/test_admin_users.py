"""Users & bookings on the admin website, for both account stores (SQLite and Firebase/Firestore)."""
import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import create_app
from app.services import support as sup
from app.services.accounts import AccountError, AccountStore
from app.services.admin_store import AdminStore
from app.services.firebase_accounts import FirebaseAccountStore, MemoryRepo
from app.services.state import AppState
from app.services.support_desk import SupportDesk
from tests.admin_helpers import make_admins, signed_in

ITEM = {"ticket_id": "T1", "valid_until": "2026-11-20T10:00:00+05:30"}


def local_store():
    s = AccountStore(":memory:", "k")
    _tok, u = s.register("9876543210", "pass1234word", "Riya")
    s.topup(u.id, 100)
    s.purchase(u.id, "ticket", 15, lambda: dict(ITEM))
    return s, str(u.id), "9876543210"


def firebase_store():
    disabled = {}
    s = FirebaseAccountStore(MemoryRepo(), lambda t: {"uid": "UID-riya-000000000000", "email": "riya@example.com", "email_verified": True},
                             set_auth_disabled=lambda uid, d: disabled.__setitem__(uid, d),
                             auth_info=lambda uid: {"email_verified": True, "last_sign_in": None})
    s.user_for_token("tok")
    s.topup("UID-riya-000000000000", 100)
    s.purchase("UID-riya-000000000000", "ticket", 15, lambda: dict(ITEM))
    s.disabled_calls = disabled
    return s, "UID-riya-000000000000", "riya@example.com"


@pytest.fixture(params=["local", "firebase"])
def env(request, monkeypatch):
    monkeypatch.setattr("app.api.routes.admin._login_limit", sup.RateLimit(100, 600))
    acc, uid, contact = local_store() if request.param == "local" else firebase_store()
    adm = AdminStore(":memory:")
    make_admins(adm)
    desk = SupportDesk(adm)
    desk.add("DR-1-AAAA", "ticket", "My ticket shows the wrong stop", None, uid, None, None, None, None, "en")
    st = AppState(settings=get_settings(), accounts=acc, admin=adm, desk=desk)
    app = create_app(st, live=False)
    with TestClient(app):
        yield app, acc, uid, contact, desk


def test_list_search_and_detail(env):
    app, acc, uid, contact, desk = env
    agent = signed_in(app, "agent")
    lst = agent.get("/api/v1/admin/users").json()
    assert [u["id"] for u in lst["users"]] == [uid] and lst["users"][0]["balance"] == 85
    assert agent.get("/api/v1/admin/users", params={"q": contact[:4]}).json()["users"][0]["id"] == uid
    d = agent.get(f"/api/v1/admin/users/{uid}").json()
    assert d["items"][0]["id"] == "T1" and "ticket" in {t["kind"] for t in d["wallet"]["transactions"]} and d["support"][0]["ref"] == "DR-1-AAAA"
    assert contact in (d["email"], d["phone"])


def test_viewer_sees_masked_contact_and_cannot_act(env):
    app, acc, uid, contact, desk = env
    viewer = signed_in(app, "viewer")
    d = viewer.get(f"/api/v1/admin/users/{uid}").json()
    assert contact not in (d["email"], d["phone"]) and ("••" in d["email"] or d["phone"].startswith("XXXXXX"))
    assert viewer.post(f"/api/v1/admin/users/{uid}/disable", json={"disabled": True, "reason": "test"}).status_code == 403
    assert viewer.post("/api/v1/admin/items/T1/void", json={"reason": "test"}).status_code == 403


def test_disable_blocks_sign_in(env):
    app, acc, uid, contact, desk = env
    agent = signed_in(app, "agent")
    assert agent.post(f"/api/v1/admin/users/{uid}/disable", json={"disabled": True, "reason": "fraud report"}).status_code == 200
    if isinstance(acc, AccountStore):
        with pytest.raises(AccountError):
            acc.login(contact, "pass1234word")
    else:
        assert acc.user_for_token("tok") is None and acc.disabled_calls[uid] is True
    assert agent.get(f"/api/v1/admin/users/{uid}").json()["disabled"] is True
    agent.post(f"/api/v1/admin/users/{uid}/disable", json={"disabled": False, "reason": "cleared"})
    assert agent.get(f"/api/v1/admin/users/{uid}").json()["disabled"] is False


def test_wallet_adjust_bounds(env):
    app, acc, uid, contact, desk = env
    agent = signed_in(app, "agent")
    w = agent.post(f"/api/v1/admin/users/{uid}/wallet", json={"amount": 15, "reason": "refund for T1"}).json()
    assert w["balance"] == 100 and w["transactions"][0]["kind"] == "adjust"
    assert agent.post(f"/api/v1/admin/users/{uid}/wallet", json={"amount": -500, "reason": "x x"}).status_code == 422
    assert agent.post(f"/api/v1/admin/users/{uid}/wallet", json={"amount": 0, "reason": "x x"}).status_code == 422


def test_void_ticket_shows_in_verify_and_app(env):
    app, acc, uid, contact, desk = env
    agent = signed_in(app, "agent")
    assert agent.get("/api/v1/admin/items/T1").json() == {"user_id": uid, "status": "active"}
    assert agent.post("/api/v1/admin/items/T1/void", json={"reason": "double booking"}).status_code == 200
    assert agent.post("/api/v1/admin/items/T1/void", json={"reason": "again"}).status_code == 409
    assert acc.item_status("T1") == "voided" and acc.items(uid if isinstance(acc, FirebaseAccountStore) else int(uid), "ticket")[0]["voided"]
    entries = [e["action"] for e in signed_in(app, "owner").get("/api/v1/admin/audit").json()["entries"]]
    assert "item_voided" in entries


def test_delete_owner_only_with_confirmation(env):
    app, acc, uid, contact, desk = env
    assert signed_in(app, "agent").post(f"/api/v1/admin/users/{uid}/delete", json={"confirm": contact, "reason": "asked"}).status_code == 403
    owner = signed_in(app, "owner")
    assert owner.post(f"/api/v1/admin/users/{uid}/delete", json={"confirm": "someone else", "reason": "asked"}).status_code == 422
    assert owner.post(f"/api/v1/admin/users/{uid}/delete", json={"confirm": contact, "reason": "user asked by email"}).status_code == 200
    assert owner.get(f"/api/v1/admin/users/{uid}").status_code == 404 and desk.list()["total"] == 0


def test_stats(env):
    app, acc, uid, contact, desk = env
    s = signed_in(app, "viewer").get("/api/v1/admin/users/stats").json()
    assert s["users"] == 1 and s["tickets"] == 1 and s["demo_topups"] == 100 and s["demo_spent"] == 15
    assert s["per_day"] and s["per_day"][-1]["tickets"] == 1
