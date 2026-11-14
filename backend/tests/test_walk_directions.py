from app.services.geo import parse_steps
from tests.test_api import client  # noqa: F401  (fixture)
from tests.test_journey import net  # noqa: F401
from tests.test_ml import crowd  # noqa: F401

OSRM = {
    "code": "Ok",
    "routes": [{
        "distance": 412.3, "duration": 300.0,
        "geometry": {"coordinates": [[77.2500, 28.5500], [77.2510, 28.5500], [77.2510, 28.5530]]},
        "legs": [{"steps": [
            {"distance": 98.0, "name": "Main Road", "maneuver": {"type": "depart", "location": [77.25, 28.55], "bearing_after": 90}},
            {"distance": 314.3, "name": "", "maneuver": {"type": "turn", "modifier": "left", "location": [77.251, 28.55], "bearing_after": 0}},
            {"distance": 0, "name": "", "maneuver": {"type": "arrive", "location": [77.251, 28.553], "bearing_after": 0}},
        ]}],
    }],
}


def test_parse_steps():
    r = parse_steps(OSRM)
    assert r["distance_m"] == 412.3
    assert r["path"][0] == [28.55, 77.25] and r["path"][-1] == [28.553, 77.251]
    assert [s["type"] for s in r["steps"]] == ["depart", "turn", "arrive"]
    assert r["steps"][1]["modifier"] == "left" and r["steps"][1]["lat"] == 28.55 and r["steps"][0]["bearing_after"] == 90
    assert r["steps"][0]["name"] == "Main Road" and r["steps"][1]["name"] == ""


def test_parse_steps_bad():
    assert parse_steps(None) is None
    assert parse_steps({"code": "NoRoute"}) is None


def test_endpoint_too_far(client):
    r = client.get("/api/v1/walk/directions", params={"from_lat": 28.5, "from_lon": 77.1, "to_lat": 28.7, "to_lon": 77.3})
    assert r.status_code == 422
