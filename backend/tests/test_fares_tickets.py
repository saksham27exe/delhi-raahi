"""Fare engine and demo tickets on the synthetic network of test_journey.
Route 1 A-B-C-D: stops 0.01 deg latitude apart (~1.11 km), A->D ~3.3 km.
Route 3 A-X-F: ~1.5 km + ~3.5 km = ~5 km."""
from datetime import datetime, timedelta

import pandas as pd
import pytest

from app.fare_engine.fares import FareService, digital_fare, slab_fare
from app.services.journey_service import JourneyService
from app.services.ticket_service import IST, BookingError, TicketService
from tests.test_journey import net  # noqa: F401  (fixture)


def test_slabs_and_digital_discount():
    assert [slab_fare(km, False) for km in (0.5, 4.0, 4.01, 10.0, 25)] == [5, 5, 10, 10, 15]
    assert [slab_fare(km, True) for km in (3, 4.5, 8.0, 9, 12.5)] == [10, 15, 15, 20, 25]
    assert [digital_fare(f) for f in (5, 10, 15, 20, 25)] == [4.5, 9, 13.5, 18, 22.5]   # = ONDC amounts


def fares(net, tmp_path, ac_share=None):  # noqa: F811
    if ac_share is not None:
        pd.DataFrame({"gtfs_route_id": ["1", "3"], "ac_share": [ac_share, 0.5], "standard_share": [1.0, 0.5],
                      "tickets": [100, 100]}).to_parquet(tmp_path / "route_fare_profile.parquet")
    return FareService(net, tmp_path)


def test_leg_fare_unknown_bus_type_gives_both(net, tmp_path):  # noqa: F811
    fs = fares(net, tmp_path)
    f = fs.leg_fare(fs.pattern_index[net.patterns[0].pattern_id], 0, 3)
    assert f["bus_type"] == "unknown" and f["fare"] is None
    assert 3.2 < f["distance_km"] < 3.5 and (f["fare_non_ac"], f["fare_ac"]) == (5, 10)
    j = fs.journey_fare([f, f])
    assert (j["total_min"], j["total_max"], j["tickets_needed"]) == (10, 20, 2)


def test_bus_type_from_ticket_profile(net, tmp_path):  # noqa: F811
    fs = fares(net, tmp_path, ac_share=0.95)
    k1 = next(k for k, p in enumerate(net.patterns) if p.route_id == "1")
    k3 = next(k for k, p in enumerate(net.patterns) if p.route_id == "3")
    a = fs.leg_fare(k1, 0, 3)
    assert a["bus_type"] == "ac" and a["fare"] == 10 and a["fare_digital"] == 9 and a["note"] is None
    m = fs.leg_fare(k3, 0, 2)
    assert m["bus_type"] == "mixed" and (m["fare_non_ac"], m["fare_ac"]) == (10, 15)
    assert "Interstate" in m["note"]                              # only half its tickets used city slabs


@pytest.fixture
def tickets(net, tmp_path):  # noqa: F811
    return TicketService(fares(net, tmp_path, ac_share=0.0), secret="test-secret")


LEG = {"route_id": "1", "board_stop_id": "A", "alight_stop_id": "C"}


def test_quote_recomputes_fare_server_side(tickets):
    q = tickets.quote([dict(LEG, fare=0.01)], passengers=3, women_free=1)     # client price ignored
    l = q["legs"][0]
    assert l["bus_type"] == "non_ac" and l["digital_fare_per_passenger"] == 4.5 and l["paying_passengers"] == 2
    assert q["total"] == 9.0 and q["demo"] and "DEMO" in q["watermark"]["en"]


def test_quote_rejects_bad_requests(tickets):
    with pytest.raises(BookingError):
        tickets.quote([{"route_id": "1", "board_stop_id": "C", "alight_stop_id": "A"}])   # wrong direction
    with pytest.raises(BookingError):
        tickets.quote([{"route_id": "2", "board_stop_id": "A", "alight_stop_id": "C"}])   # route not at stop
    with pytest.raises(BookingError):
        tickets.quote([LEG], passengers=0)
    with pytest.raises(BookingError):
        tickets.quote([LEG], passengers=2, women_free=3)
    with pytest.raises(BookingError):
        tickets.quote([])


def test_mixed_fleet_priced_at_ac(net, tmp_path):  # noqa: F811
    t = TicketService(fares(net, tmp_path, ac_share=0.0))
    q = t.quote([{"route_id": "3", "board_stop_id": "A", "alight_stop_id": "F"}])
    assert q["legs"][0]["bus_type"] == "ac" and q["total"] == 13.5
    q = t.quote([{"route_id": "3", "board_stop_id": "A", "alight_stop_id": "F"}], bus_types=["non_ac"])
    assert q["total"] == 9.0


def test_book_and_verify(tickets):
    now = datetime(2026, 11, 20, 8, 0, tzinfo=IST)
    t = tickets.book([LEG], passengers=2, now=now)
    assert t["ticket_id"].startswith("DEMO-") and t["demo"] and t["payment"] == "none (demo)"
    v = tickets.verify(t["qr_payload"], now=now + timedelta(minutes=30))
    assert v["valid_signature"] and v["status"] == "active_demo" and v["ticket"]["total"] == 9.0
    assert tickets.verify(t["qr_payload"], now=now + timedelta(hours=3))["status"] == "expired"
    body, sig = t["qr_payload"].split(".")
    forged = body[:-4] + ("AAAA" if body[-4:] != "AAAA" else "BBBB") + "." + sig
    assert not tickets.verify(forged)["valid_signature"]
    assert tickets.verify("garbage")["status"] == "unreadable"
    other = TicketService(tickets.fares, secret="another-server")
    assert other.verify(t["qr_payload"])["status"] == "tampered_or_foreign"


def test_journeys_carry_fares(net, tmp_path):  # noqa: F811
    js = JourneyService(net, None, fares(net, tmp_path, ac_share=0.0))
    j = js.plan({"type": "stop", "id": "B"}, {"type": "stop", "id": "E"}, datetime(2026, 11, 20, 8, 0))["journeys"][0]
    assert j["fare"]["tickets_needed"] == 2 and all(l["fare"] is not None for l in j["legs"])
    assert j["fare"]["total_min"] == 5 + 5          # route 1 non-AC (known) + route 2 unknown -> non-AC low end
    assert j["fare"]["total_max"] == 5 + 10
