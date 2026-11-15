"""Service alerts (admin + public feed) and data fixes (stop aliases, bus type overrides) on a tiny network."""
from datetime import datetime, timedelta, timezone

import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.fare_engine.fares import FareService
from app.main import create_app
from app.services import support as sup
from app.services.accounts import AccountStore
from app.services.admin_data import AdminData, route_base
from app.services.admin_store import AdminStore
from app.services.journey_service import JourneyService
from app.services.state import AppState
from tests.admin_helpers import make_admins, signed_in
from tests.test_journey import net  # noqa: F401
from tests.test_ml import crowd  # noqa: F401


@pytest.fixture
def env(net, crowd, tmp_path, monkeypatch):  # noqa: F811
    monkeypatch.setattr("app.api.routes.admin._login_limit", sup.RateLimit(100, 600))
    adm = AdminStore(":memory:")
    make_admins(adm)
    fares = FareService(net, tmp_path)
    st = AppState(settings=get_settings(), network=net, crowd=crowd, fares=fares, journeys=JourneyService(net, crowd, fares),
                  accounts=AccountStore(":memory:", "k"), admin=adm, admin_data=AdminData(adm))
    app = create_app(st, live=False)
    with TestClient(app) as public:
        yield app, public, st


def iso(minutes: int) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).isoformat()


def test_route_base():
    assert route_base("534UP") == "534" and route_base("534 down") == "534" and route_base("534") == "534"


def test_alerts_lifecycle_and_targeting(env):
    app, public, st = env
    agent = signed_in(app, "agent")
    everyone = agent.post("/api/v1/admin/alerts", json={"severity": "info", "title_en": "Diwali timetable", "title_hi": "दिवाली समय-सारिणी"}).json()
    route = agent.post("/api/v1/admin/alerts", json={"severity": "critical", "title_en": "534 diverted", "routes": ["534UP"]}).json()
    later = agent.post("/api/v1/admin/alerts", json={"severity": "warning", "title_en": "Strike tomorrow", "starts_at": iso(60)}).json()
    assert route["routes"] == ["534"] and later["state"] == "scheduled" and everyone["scope"] == "everyone"
    feed = public.get("/api/v1/alerts", params={"routes": "534DOWN"}).json()["alerts"]
    assert [a["title_en"] for a in feed] == ["534 diverted", "Diwali timetable"]          # critical first; scheduled one hidden
    assert [a["title_en"] for a in public.get("/api/v1/alerts").json()["alerts"]] == ["Diwali timetable"]
    assert public.get("/api/v1/alerts", params={"routes": "534", "everyone": "false"}).json()["alerts"][0]["id"] == route["id"]
    agent.post(f"/api/v1/admin/alerts/{route['id']}/end")
    assert all(a["id"] != route["id"] for a in public.get("/api/v1/alerts", params={"routes": "534"}).json()["alerts"])
    assert agent.post("/api/v1/admin/alerts", json={"severity": "info", "title_en": "x", "starts_at": iso(60), "ends_at": iso(0)}).status_code == 422
    assert signed_in(app, "viewer").post("/api/v1/admin/alerts", json={"severity": "info", "title_en": "x"}).status_code == 403


def test_stop_alias_changes_search(env):
    app, public, st = env
    agent = signed_in(app, "agent")
    before = public.get("/api/v1/stops/search", params={"q": "zulu"}).json()["results"]
    assert not before or before[0]["name"] != "Foxtrot"
    assert agent.post("/api/v1/admin/data/aliases", json={"alias": "Zulu", "stop_name": "Not a stop"}).status_code == 422
    assert agent.post("/api/v1/admin/data/aliases", json={"alias": "Zulu", "stop_name": "Foxtrot"}).status_code == 200
    after = public.get("/api/v1/stops/search", params={"q": "zulu"}).json()["results"]
    assert after[0]["name"] == "Foxtrot"
    agent.delete("/api/v1/admin/data/aliases/Zulu")
    again = public.get("/api/v1/stops/search", params={"q": "zulu"}).json()["results"]
    assert not again or again[0]["name"] != "Foxtrot"


def test_bus_type_override(env):
    app, public, st = env
    rid = str(st.network.routes["route_id"].iloc[0])
    short = str(st.network.routes["route_short_name"].iloc[0])
    assert st.fares.bus_type(rid)[0] == "unknown"
    agent = signed_in(app, "agent")
    r = agent.post("/api/v1/admin/data/bus-types", json={"route": short, "bus_type": "ac", "note": "depot confirmed"})
    assert r.status_code == 200 and r.json()["overrides"][0]["route"] == route_base(short)
    assert st.fares.bus_type(rid)[0] == "ac"
    look = agent.get("/api/v1/admin/lookup/routes", params={"q": route_base(short)}).json()["results"][0]
    assert look["override"] == "ac" and short in look["directions"]
    agent.delete(f"/api/v1/admin/data/bus-types/{route_base(short)}")
    assert st.fares.bus_type(rid)[0] == "unknown"
