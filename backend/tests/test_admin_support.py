"""Support desk: requests from the app land in the desk; agents triage, note, reply; viewers only read."""
import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.main import create_app
from app.services import support as sup
from app.services.accounts import AccountStore
from app.services.admin_store import AdminStore
from app.services.state import AppState
from app.services.support_desk import SAFETY, SupportDesk
from tests.admin_helpers import make_admins, signed_in
from tests.test_support import FakeMailer


@pytest.fixture
def env(monkeypatch):
    monkeypatch.setattr("app.api.routes.support._limit", sup.RateLimit(100, 600))
    monkeypatch.setattr("app.api.routes.admin._login_limit", sup.RateLimit(100, 600))
    adm = AdminStore(":memory:")
    ids = make_admins(adm)
    m = FakeMailer()
    st = AppState(settings=get_settings().model_copy(update={"support_email": "help@example.org"}),
                  accounts=AccountStore(":memory:", "k"), admin=adm, desk=SupportDesk(adm), mailer=m)
    app = create_app(st, live=False)
    with TestClient(app) as public:
        yield app, public, m, ids, st


def send(public, **kw):
    body = {"kind": "complaint", "message": "The driver of bus 534 was drinking and driving rashly", "email": "rider@example.com",
            "route": "534", **kw}
    r = public.post("/api/v1/support", json=body)
    assert r.status_code == 200, r.text
    return r.json()["ref"]


def test_safety_words():
    assert SAFETY.search("he was drunk") and SAFETY.search("मेरे साथ छेड़छाड़ हुई") and SAFETY.search("conductor ne maar pit ki")
    assert not SAFETY.search("we reached late") and not SAFETY.search("हमारा बस स्टॉप") and not SAFETY.search("chota bus stop")


def test_request_lands_in_desk_with_priority(env):
    app, public, m, ids, st = env
    ref = send(public)
    send(public, kind="idea", message="Please add metro lines too", email=None, route=None)
    owner = signed_in(app, "owner")
    lst = owner.get("/api/v1/admin/support").json()
    assert lst["total"] == 2 and lst["counts"]["new"] == 2 and lst["counts"]["urgent_open"] == 1
    top = lst["items"][0]
    assert top["ref"] == ref and top["priority"] == "urgent" and top["safety"] == 1 and top["emailed"] == 1
    assert lst["items"][1]["priority"] == "normal"
    assert owner.get("/api/v1/admin/support", params={"q": "metro"}).json()["total"] == 1
    assert owner.get("/api/v1/admin/support", params={"route": "534"}).json()["total"] == 1


def test_agent_triage_note_reply(env):
    app, public, m, ids, st = env
    send(public)
    agent = signed_in(app, "agent")
    rid = agent.get("/api/v1/admin/support").json()["items"][0]["id"]
    r = agent.patch(f"/api/v1/admin/support/{rid}", json={"assignee_id": ids["agent"]}).json()
    assert r["assignee_name"] == "Agent" and r["status"] == "in_progress"           # picking it up starts it
    agent.post(f"/api/v1/admin/support/{rid}/notes", json={"body": "Called depot, CCTV requested"})
    m.sent.clear()
    r = agent.post(f"/api/v1/admin/support/{rid}/reply", json={"body": "Sorry. The depot is checking the CCTV.", "then": "resolved"}).json()
    assert r["status"] == "resolved" and r["resolved_at"]
    kinds = [e["kind"] for e in r["events"]]
    assert kinds == ["assign", "status", "note", "reply", "status"]
    mail = m.sent[0]
    assert mail["to"] == "rider@example.com" and mail["reply_to"] == "help@example.org" and r["ref"] in mail["subject"]
    assert "drinking" in mail["text"] and "CCTV" in mail["html"]
    audit = [e["action"] for e in signed_in(app, "owner").get("/api/v1/admin/audit").json()["entries"]]
    assert "support_reply" in audit and "support_note" in audit


def test_viewer_reads_only(env):
    app, public, m, ids, st = env
    send(public)
    viewer = signed_in(app, "viewer")
    rid = viewer.get("/api/v1/admin/support").json()["items"][0]["id"]
    assert viewer.get(f"/api/v1/admin/support/{rid}").status_code == 200
    assert viewer.patch(f"/api/v1/admin/support/{rid}", json={"status": "resolved"}).status_code == 403
    assert viewer.post(f"/api/v1/admin/support/{rid}/notes", json={"body": "x"}).status_code == 403
    assert viewer.get("/api/v1/admin/support/export.csv").status_code == 403


def test_reply_needs_email_and_cant_assign_viewer(env):
    app, public, m, ids, st = env
    send(public, email=None)
    agent = signed_in(app, "agent")
    rid = agent.get("/api/v1/admin/support").json()["items"][0]["id"]
    assert agent.post(f"/api/v1/admin/support/{rid}/reply", json={"body": "Hello there"}).status_code == 409
    assert agent.patch(f"/api/v1/admin/support/{rid}", json={"assignee_id": ids["viewer"]}).status_code == 422


def test_csv_export_neutralises_formulas(env):
    app, public, m, ids, st = env
    send(public, message="=HYPERLINK(\"http://evil\") the bus never came")
    csv = signed_in(app, "agent").get("/api/v1/admin/support/export.csv")
    assert csv.headers["content-disposition"].startswith("attachment") and "'=HYPERLINK" in csv.text


def test_stats(env):
    app, public, m, ids, st = env
    send(public); send(public, route="764")
    s = signed_in(app, "viewer").get("/api/v1/admin/support/stats").json()
    assert s["by_kind"] == {"complaint": 2} and {r["route"] for r in s["complaints_by_route"]} == {"534", "764"}


def test_deleting_account_deletes_requests(env):
    app, public, m, ids, st = env
    acc = st.accounts
    tok = public.post("/api/v1/auth/register", json={"phone": "9876543210", "password": "pass1234word", "name": "R"}).json()["token"]
    public.post("/api/v1/support", json={"kind": "problem", "message": "Map does not load at all"}, headers={"Authorization": f"Bearer {tok}"})
    assert st.desk.list()["total"] == 1
    r = public.post("/api/v1/me/delete", json={"password": "pass1234word"}, headers={"Authorization": f"Bearer {tok}"})
    assert r.status_code == 200 and st.desk.list()["total"] == 0 and acc is st.accounts


def test_old_feedback_is_imported_once():
    from app.services.firebase_accounts import FirebaseAccountStore, MemoryRepo  # noqa: F401
    adm = AdminStore(":memory:")
    desk = SupportDesk(adm)
    acc = AccountStore(":memory:", "k")
    _t, u = acc.register("9876500009", "pass1234word", "Old User")
    acc.add_feedback(u.id, "[DR-1101-AB12] complaint (Bus route: 534; When: 9 am): Driver was driving rashly.")
    acc.add_feedback(None, "Nice app, please add metro.")
    assert desk.import_feedback(acc.all_feedback()) == 2
    assert desk.import_feedback(acc.all_feedback()) == 0                  # running again adds nothing
    rows = {r["ref"]: r for r in desk.list(status=None, size=50)["items"]}
    c = rows["DR-1101-AB12"]
    assert c["kind"] == "complaint" and c["route"] == "534" and c["priority"] == "urgent" and c["message"] == "Driver was driving rashly."
    plain = next(r for ref, r in rows.items() if ref.startswith("FB-"))
    assert plain["kind"] == "feedback" and plain["message"] == "Nice app, please add metro."


def test_app_feedback_lands_in_desk(env):
    app, public, m, ids, st = env
    assert public.post("/api/v1/feedback", json={"text": "Bus 764 crowd numbers were spot on."}).status_code == 200
    items = st.desk.list(status=None, size=50)["items"]
    assert any(r["kind"] == "feedback" and "764" in r["message"] for r in items)
