import time

import pytest

from app.services.accounts import AccountError
from app.services.firebase_accounts import FirebaseAccountStore, MemoryRepo, mask_email
from tests.test_journey import net  # noqa: F401
from tests.test_ml import crowd  # noqa: F401

TOKENS = {"tok-a": {"uid": "A", "email": "asha@example.com", "name": "Asha", "email_verified": True},
          "tok-b": {"uid": "B", "email": "bilal@example.com"},
          "tok-u": {"uid": "U", "email": "new@example.com", "email_verified": False}}


def verify(tok):
    if tok not in TOKENS:
        raise ValueError("invalid token")
    return TOKENS[tok]


@pytest.fixture()
def store():
    deleted = []
    s = FirebaseAccountStore(MemoryRepo(), verify, delete_auth=deleted.append)
    s.deleted = deleted
    return s


def test_first_request_creates_profile(store):
    u = store.user_for_token("tok-a")
    assert u.id == "A" and u.name == "Asha" and u.email == "asha@example.com" and not u.woman_traveller
    assert store.user_for_token("tok-b").name == "bilal"            # no display name: email prefix
    assert store.user_for_token("forged") is None
    assert u.public()["email_masked"] == "as••@example.com"


def test_profile_update(store):
    store.user_for_token("tok-a")
    u = store.update_profile("A", name=" Asha K ", woman_traveller=True)
    assert u.name == "Asha K" and u.woman_traveller
    with pytest.raises(AccountError):
        store.update_profile("A", name="  ")


def test_wallet_and_purchase(store):
    store.user_for_token("tok-a")
    w = store.topup("A", 100)
    assert w["balance"] == 100 and w["transactions"][0]["kind"] == "topup"
    item = store.purchase("A", "ticket", 15, lambda: {"ticket_id": "T1", "valid_until": "2026-11-20T10:00:00+05:30"})
    assert item["ticket_id"] == "T1"
    assert store.wallet("A")["balance"] == 85
    assert store.items("A", "ticket")[0]["ticket_id"] == "T1" and store.items("A", "pass") == []
    assert store.item_on_record("T1") and not store.item_on_record("T2")
    with pytest.raises(AccountError) as e:
        store.purchase("A", "ticket", 500, lambda: {"ticket_id": "T3", "valid_until": "x"})
    assert e.value.status == 402 and store.wallet("A")["balance"] == 85 and not store.item_on_record("T3")
    with pytest.raises(AccountError):
        store.topup("A", 5)                                            # below the minimum


def test_delete_removes_data_and_sign_in(store):
    store.user_for_token("tok-a")
    store.topup("A", 50)
    store.add_feedback("A", "nice app")
    with pytest.raises(AccountError):
        store.delete("A")                                   # no fresh sign-in: refused
    with pytest.raises(AccountError):
        store.delete("A", auth_time=time.time() - 3600)     # signed in an hour ago: refused
    assert store.deleted == []
    store.delete("A", auth_time=time.time() - 10)
    assert store.deleted == ["A"] and store.repo.get_user("A") is None and store.repo.feedback == []


def test_old_phone_login_is_refused(store):
    with pytest.raises(AccountError) as e:
        store.login("9999999999", "secret12")
    assert e.value.status == 410


def test_mask_email():
    assert mask_email("ab@x.in") == "ab•@x.in" and mask_email("nope") == "nope"


def test_routes_with_firebase_store(net, crowd, tmp_path):  # noqa: F811
    """Booking a pass through the API with Firebase accounts (holder comes from the email, not a phone)."""
    from fastapi.testclient import TestClient
    from app.core.config import get_settings
    from app.fare_engine.fares import FareService
    from app.main import create_app
    from app.services.journey_service import JourneyService
    from app.services.live_service import LiveTracker
    from app.services.state import AppState
    from app.services.ticket_service import TicketService
    fares = FareService(net, tmp_path)
    st = AppState(settings=get_settings(), network=net, crowd=crowd, fares=fares, tickets=TicketService(fares, "k"),
                  journeys=JourneyService(net, crowd, fares), live=LiveTracker(net),
                  accounts=FirebaseAccountStore(MemoryRepo(), verify))
    h = {"Authorization": "Bearer tok-a"}
    with TestClient(create_app(st, live=False)) as c:
        assert c.get("/api/v1/health").json()["accounts_store"] == "firebase"
        assert c.get("/api/v1/me", headers=h).json()["user"]["email"] == "asha@example.com"
        assert c.post("/api/v1/wallet/topup", json={"amount": 200}, headers=h).status_code == 200
        p = c.post("/api/v1/passes/buy", json={"kind": "daily", "bus_type": "ac"}, headers=h).json()
        assert p["pass_id"] and c.get("/api/v1/passes/mine", headers=h).json()["passes"][0]["pass_id"] == p["pass_id"]
        assert c.get("/api/v1/me", headers={"Authorization": "Bearer nope"}).status_code == 401
        # Not verified yet: may look, may not add money, book or buy.
        u = {"Authorization": "Bearer tok-u"}
        me = c.get("/api/v1/me", headers=u).json()["user"]
        assert me["email_verified"] is False
        r = c.post("/api/v1/wallet/topup", json={"amount": 200}, headers=u)
        assert r.status_code == 403 and r.json()["detail"]["code"] == "email_not_verified"
        assert c.post("/api/v1/passes/buy", json={"kind": "daily", "bus_type": "ac"}, headers=u).status_code == 403
        assert c.get("/api/v1/wallet", headers=u).status_code == 200


class FakeMailer:
    def __init__(self):
        self.sent = []

    def send(self, to, subject, html, text):
        self.sent.append((to, subject, html, text))


def _reset_link(email):
    if email != "asha@example.com":
        raise ValueError("no user")
    return "https://example.test/reset?oob=R1"


def mail_store():
    m = FakeMailer()
    s = FirebaseAccountStore(MemoryRepo(), verify, mailer=m, verify_link=lambda e: f"https://example.test/verify?oob={e}",
                             reset_link=_reset_link, user_name=lambda e: "Asha")
    return s, m


def test_branded_verification_email():
    s, m = mail_store()
    u = s.user_for_token("tok-u")                         # not verified
    assert s.send_verification(u, "en") is True
    to, subject, html, text = m.sent[-1]
    assert to == "new@example.com" and subject == "Confirm your email for DelhiRaahi"
    assert 'href="https://example.test/verify?oob=new@example.com"' in html and "DelhiRaahi" in html
    assert "project-" not in html and "https://example.test/verify" in text
    with pytest.raises(AccountError) as e:                # second email within a minute
        s.send_verification(u, "en")
    assert e.value.status == 429
    assert s.send_verification(s.user_for_token("tok-a"), "en") is False   # already verified: nothing sent
    assert len(m.sent) == 1


def test_reset_email_never_reveals_accounts():
    s, m = mail_store()
    s.send_reset("asha@example.com", "hi")
    to, subject, html, _ = m.sent[-1]
    assert to == "asha@example.com" and "पासवर्ड" in subject and "नमस्ते Asha" in html
    s.send_reset("nobody@example.com", "en")              # no such account: same answer, no email
    assert len(m.sent) == 1


def test_no_mailer_is_503():
    s = FirebaseAccountStore(MemoryRepo(), verify)
    with pytest.raises(AccountError) as e:
        s.send_verification(s.user_for_token("tok-u"))
    assert e.value.status == 503


def test_template_escapes_user_text():
    from app.services.email_templates import render
    _, html, _ = render("verify", "en", "<script>x</script>", "a@b.in", "https://l.test/?a=1&b=2")
    assert "<script>" not in html and "&lt;script&gt;" in html and "a=1&amp;b=2" in html
