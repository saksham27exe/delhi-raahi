"""Support requests: validation, reference numbers, inbox + copy emails, rate limit."""
import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import create_app
from app.services import support as sup
from app.services.accounts import AccountStore
from app.services.state import AppState


class FakeMailer:
    def __init__(self):
        self.sent = []

    def send(self, to, subject, html, text, reply_to=None):
        self.sent.append({"to": to, "subject": subject, "html": html, "text": text, "reply_to": reply_to})


@pytest.fixture
def setup(monkeypatch):
    monkeypatch.setattr("app.api.routes.support._limit", sup.RateLimit(3, 600))
    s = get_settings().model_copy(update={"support_email": "help@example.org"})
    m = FakeMailer()
    st = AppState(settings=s, accounts=AccountStore(":memory:", "k"), mailer=m)
    with TestClient(create_app(st, live=False)) as c:
        yield c, m, st


def test_info(setup):
    c, _, _ = setup
    assert c.get("/api/v1/support").json() == {"email": "help@example.org", "form": True}


def test_complaint_emails_inbox_and_copy(setup):
    c, m, st = setup
    r = c.post("/api/v1/support", json={"kind": "complaint", "message": "Bus 534 skipped the stop at 9 am <b>today</b>",
                                         "email": "rider@example.com", "route": "534", "when": "today 9 am", "lang": "hi"}).json()
    assert r["ok"] and r["emailed"] and r["copy_sent"] and r["ref"].startswith("DR-")
    inbox, copy = m.sent
    assert inbox["to"] == "help@example.org" and inbox["reply_to"] == "rider@example.com" and r["ref"] in inbox["subject"]
    assert "&lt;b&gt;today" in inbox["html"] and "534" in inbox["text"]     # user text is escaped
    assert copy["to"] == "rider@example.com" and r["ref"] in copy["subject"] and "शिकायत" in copy["html"]
    assert "Reply to" not in copy["text"]
    stored = st.accounts._db.execute("SELECT text FROM feedback").fetchone()["text"]
    assert stored.startswith(f"[{r['ref']}] complaint") and "534" in stored


def test_without_email_no_copy(setup):
    c, m, _ = setup
    r = c.post("/api/v1/support", json={"kind": "problem", "message": "The map is blank on my phone"}).json()
    assert r["emailed"] and not r["copy_sent"] and len(m.sent) == 1 and m.sent[0]["reply_to"] is None


@pytest.mark.parametrize("body", [
    {"kind": "complaint", "message": "too short"},
    {"kind": "complaint", "message": "A long enough message here", "email": "not-an-email"},
    {"kind": "other", "message": "A long enough message here"},
])
def test_rejects_bad_input(setup, body):
    c, m, _ = setup
    assert c.post("/api/v1/support", json=body).status_code == 422 and not m.sent


def test_rate_limit(setup):
    c, _, _ = setup
    codes = [c.post("/api/v1/support", json={"kind": "idea", "message": "Please add metro lines too"}).status_code for _ in range(4)]
    assert codes == [200, 200, 200, 429]


def test_ref_format():
    assert all(len(sup.new_ref()) == 12 for _ in range(20))
    assert not set(sup.new_ref()[8:]) & set("01IO")
