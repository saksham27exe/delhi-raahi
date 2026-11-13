"""End-to-end API tests on the synthetic network (test_journey) + synthetic crowd model (test_ml)."""
import pytest
from fastapi.testclient import TestClient

from app.core.config import get_settings
from app.fare_engine.fares import FareService
from app.main import create_app
from app.services.journey_service import JourneyService
from app.services.live_service import LiveTracker
from app.services.state import AppState
from app.services.ticket_service import TicketService
from tests.test_journey import net  # noqa: F401  (fixture)
from tests.test_ml import crowd  # noqa: F401  (fixture)


@pytest.fixture
def client(net, crowd, tmp_path):  # noqa: F811
    fares = FareService(net, tmp_path)
    st = AppState(settings=get_settings(), network=net, crowd=crowd, fares=fares,
                  tickets=TicketService(fares, "k"), journeys=JourneyService(net, crowd, fares), live=LiveTracker(net))
    with TestClient(create_app(st, live=False)) as c:
        yield c


FRI = "2026-10-30"


def test_health(client):
    h = client.get("/api/v1/health").json()
    assert h["status"] == "ok" and h["components"]["gtfs_network"] and not h["components"]["live_feed"]


def test_stops_and_routes(client):
    r = client.get("/api/v1/stops/search", params={"q": "charli"}).json()
    assert r["results"][0]["name"] == "Charlie"
    near = client.get("/api/v1/stops/nearby", params={"lat": 28.6201, "lon": 77.2001}).json()["results"]
    assert {n["stop_id"] for n in near[:2]} == {"C", "C2"}
    s = client.get("/api/v1/stops/C").json()
    assert {x["route"] for x in s["routes"]} == {"R1UP"}
    assert client.get("/api/v1/stops/NOPE").status_code == 404
    assert client.get("/api/v1/stops/search", params={"q": "a"}).status_code == 422
    rs = client.get("/api/v1/routes/search", params={"q": "r1"}).json()["results"]
    assert rs[0]["route"] == "R1UP"
    d = client.get("/api/v1/routes/1").json()
    assert [x["stop_id"] for x in d["stops"]] == list("ABCD") and d["trips_per_day"] == 2
    assert d["first_departure"] == "08:00" and d["stops"][-1]["km"] > 3
    assert client.get("/api/v1/routes/999").status_code == 404


def test_journey_search_has_bilingual_summary(client):
    body = {"origin": {"type": "stop", "id": "B"}, "destination": {"type": "stop", "id": "E"},
            "departure_datetime": "2026-10-30T08:00:00+05:30"}
    j = client.post("/api/v1/journeys/search", json=body).json()["journeys"][0]
    assert "R1UP" in j["summary"]["en"] and "R2UP" in j["summary"]["en"] and "बस" in j["summary"]["hi"]
    assert j["fare"]["tickets_needed"] == 2
    bad = dict(body, origin={"type": "stop", "id": "NOPE"})
    assert client.post("/api/v1/journeys/search", json=bad).status_code == 422
    assert client.post("/api/v1/journeys/search", json=dict(body, origin={"type": "coord", "lat": 10, "lon": 10})).status_code == 422
    adv = client.post("/api/v1/journeys/advisor", json=dict(body, offsets_min=[0, 30])).json()
    assert len(adv["departures"]) == 2


def test_live_endpoints_fall_back_to_timetable(client):
    s = client.get("/api/v1/live/status").json()
    assert not s["live"] and not s["enabled"]
    assert client.get("/api/v1/live/vehicles").json()["count"] == 0
    assert client.get("/api/v1/live/vehicles", params={"bbox": "1,2"}).status_code == 422
    a = client.get("/api/v1/live/arrivals/A").json()
    assert not a["live_feed"] and "en" in a["summary"] and "hi" in a["summary"]
    assert client.get("/api/v1/live/arrivals/NOPE").status_code == 404


def test_fares_and_demo_tickets(client):
    req = {"legs": [{"route_id": "1", "board_stop_id": "A", "alight_stop_id": "C"}], "passengers": 2}
    q = client.post("/api/v1/fares/quote", json=req).json()
    assert q["demo"] and q["total"] == 2 * 9.0 and "₹18" in q["summary"]["en"]      # unknown type -> AC price
    t = client.post("/api/v1/tickets/book", json=req).json()
    assert t["ticket_id"].startswith("DEMO-") and "not valid for travel" in t["watermark"]["en"]
    v = client.post("/api/v1/tickets/verify", json={"qr_payload": t["qr_payload"]}).json()
    assert v["status"] == "active_demo"
    wrong = {"legs": [{"route_id": "1", "board_stop_id": "C", "alight_stop_id": "A"}]}
    assert client.post("/api/v1/fares/quote", json=wrong).status_code == 422
    assert client.post("/api/v1/tickets/book", json={"legs": []}).status_code == 422
    rules = client.get("/api/v1/fares/rules").json()
    assert rules["rules"]["digital_discount_pct"] == 10


def test_crowd_endpoints(client):
    day = client.get("/api/v1/crowd/route/1", params={"date": FRI}).json()
    assert day["metric"] == "demand_per_scheduled_departure" and day["buckets"]
    assert any(b["time"] == "08:30" for b in day["buckets"])
    along = client.get("/api/v1/crowd/route/1", params={"date": FRI, "time": "08:30"}).json()
    assert [s["from_stop_id"] for s in along["segments"]] == ["A", "B", "C"]
    assert along["segments"][0]["category"] == "HIGH" and "high" in along["summary"]["en"]
    seg = client.get("/api/v1/crowd/segment/1:A>B", params={"date": FRI, "time": "08:30"}).json()
    assert seg["fallback_level"] == 1 and seg["forecast_basis"] == "one_week_ahead_forecast"
    hot = client.get("/api/v1/crowd/hotspots", params={"date": FRI, "time": "08:30"}).json()["segments"]
    assert [h["segment_id"] for h in hot] == ["1:A>B"]          # only the well-supported segment
    assert hot[0]["dpd"] == pytest.approx(seg["value"], abs=1e-3)  # vectorised == single prediction
    assert client.get("/api/v1/crowd/hotspots", params={"time": "25:00"}).status_code == 422
    assert client.get("/api/v1/crowd/route/999").status_code == 404


def test_insights(client):
    m = client.get("/api/v1/insights/model").json()
    assert len(m["limitations"]) >= 4 and "route_model" in m
