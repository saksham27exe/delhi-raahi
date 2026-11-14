import pytest

from app.services.accounts import AccountError
from app.services.firebase_accounts import FirebaseAccountStore, MemoryRepo, mask_email

TOKENS = {"tok-a": {"uid": "A", "email": "asha@example.com", "name": "Asha"},
          "tok-b": {"uid": "B", "email": "bilal@example.com"}}


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
    store.delete("A")
    assert store.deleted == ["A"] and store.repo.get_user("A") is None and store.repo.feedback == []


def test_old_phone_login_is_refused(store):
    with pytest.raises(AccountError) as e:
        store.login("9999999999", "secret12")
    assert e.value.status == 410


def test_mask_email():
    assert mask_email("ab@x.in") == "ab•@x.in" and mask_email("nope") == "nope"
