"""Accounts, DEMO wallet, passes and ticket ownership (store + API)."""
import pytest

from app.services.accounts import AccountError, AccountStore, normalise_phone
from tests.test_api import client  # noqa: F401  (fixture)
from tests.test_journey import net  # noqa: F401
from tests.test_ml import crowd  # noqa: F401

LEG = {"legs": [{"route_id": "1", "board_stop_id": "A", "alight_stop_id": "C"}], "passengers": 1}


def test_phone_and_password_rules():
    assert normalise_phone("+91 98765-43210") == "9876543210"
    assert normalise_phone("09876543210") == "9876543210"
    for bad in ("12345", "5876543210", "98765432101"):
        with pytest.raises(AccountError):
            normalise_phone(bad)
    s = AccountStore(":memory:", "k")
    for pw in ("short1", "lettersonly", "12345678"):
        with pytest.raises(AccountError):
            s.register("9876543210", pw, "A")


def test_register_login_logout_and_lockout():
    s = AccountStore(":memory:", "k")
    tok, u = s.register("9876543210", "bus12345", "Asha")
    assert s.user_for_token(tok).id == u.id and "pw" not in str(u.public())
    with pytest.raises(AccountError) as e:
        s.register("98765 43210", "bus12345", "Other")
    assert e.value.status == 409
    tok2, _ = s.login("9876543210", "bus12345")
    s.logout(u.id)                                   # signs out everywhere
    assert s.user_for_token(tok) is None and s.user_for_token(tok2) is None
    assert s.user_for_token("garbage") is None
    assert AccountStore(":memory:", "other").user_for_token(s.login("9876543210", "bus12345")[0]) is None
    for _ in range(5):
        with pytest.raises(AccountError):
            s.login("9876543210", "wrong123")
    with pytest.raises(AccountError) as e:
        s.login("9876543210", "bus12345")            # locked even with the right password
    assert e.value.status == 429


def test_wallet_limits_and_atomic_purchase():
    s = AccountStore(":memory:", "k")
    _, u = s.register("9876543210", "bus12345", "Asha")
    for bad in (5, 2500):
        with pytest.raises(AccountError):
            s.topup(u.id, bad)
    s.topup(u.id, 20)
    with pytest.raises(AccountError) as e:
        s.purchase(u.id, "pass", 40.0, lambda: {"pass_id": "P1", "valid_until": "x"})
    assert e.value.status == 402 and s.wallet(u.id)["balance"] == 20.0 and not s.item_on_record("P1")
    s.purchase(u.id, "ticket", 13.5, lambda: {"ticket_id": "T1", "valid_until": "x"})
    w = s.wallet(u.id)
    assert w["balance"] == 6.5 and [t["kind"] for t in w["transactions"]] == ["ticket", "topup"]


def test_api_account_flow(client):  # noqa: F811
    assert client.get("/api/v1/me").status_code == 401
    assert client.post("/api/v1/auth/register", json={"phone": "123", "password": "bus12345", "name": "A"}).status_code == 422
    r = client.post("/api/v1/auth/register", json={"phone": "9876543210", "password": "bus12345", "name": "Asha"})
    h = {"Authorization": f"Bearer {r.json()['token']}"}
    assert client.post("/api/v1/auth/login", json={"phone": "9876543210", "password": "nope1234"}).status_code == 401
    assert client.patch("/api/v1/me", json={"woman_traveller": True}, headers=h).json()["user"]["woman_traveller"]

    cat = client.get("/api/v1/passes/catalog").json()["passes"]
    monthly = next(p for p in cat if p["kind"] == "monthly")
    assert monthly["price"] == {"non_ac": 800, "ac": 1000} and monthly["verified"]
    assert not next(p for p in cat if p["kind"] == "daily")["verified"]

    client.post("/api/v1/wallet/topup", json={"amount": 100}, headers=h)
    p = client.post("/api/v1/passes/buy", json={"kind": "daily", "bus_type": "ac"}, headers=h).json()
    assert p["pass_id"].startswith("PASS-") and p["price"] == 50 and p["holder"] == "XXXXXX3210"
    assert client.post("/api/v1/passes/buy", json={"kind": "monthly", "bus_type": "ac"}, headers=h).status_code == 402
    t = client.post("/api/v1/tickets/book", json=LEG, headers=h).json()
    assert [x["pass_id"] for x in client.get("/api/v1/passes/mine", headers=h).json()["passes"]] == [p["pass_id"]]
    assert [x["ticket_id"] for x in client.get("/api/v1/tickets/mine", headers=h).json()["tickets"]] == [t["ticket_id"]]
    assert client.post("/api/v1/tickets/verify", json={"qr_payload": p["qr_payload"]}).json()["status"] == "active_demo"
    assert client.post("/api/v1/feedback", json={"text": "Nice app"}).json()["ok"]        # guests may send feedback

    assert client.post("/api/v1/me/delete", json={"password": "wrong123"}, headers=h).status_code == 401
    assert client.post("/api/v1/me/delete", json={"password": "bus12345"}, headers=h).json()["deleted"]
    assert client.get("/api/v1/me", headers=h).status_code == 401
    v = client.post("/api/v1/tickets/verify", json={"qr_payload": t["qr_payload"]}).json()
    assert v["valid_signature"] and v["status"] == "not_on_record"
