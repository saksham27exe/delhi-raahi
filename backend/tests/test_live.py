"""Live tracking on the synthetic network of test_journey (route 1: A-B-C-D, 10 min per hop,
trips 08:00 and 08:30; stops 0.01 deg latitude apart along lon 77.20)."""
from datetime import datetime

import pytest
from google.transit import gtfs_realtime_pb2 as rt

from app.services.live_service import IST, LivePoller, LiveTracker, parse_vehicle_positions
from tests.test_journey import net  # noqa: F401  (fixture)


def ts(h, m, s=0):
    return int(datetime(2026, 11, 20, h, m, s, tzinfo=IST).timestamp())


def feed(*vehicles) -> bytes:
    """vehicles: dicts with id, route, start, lat, lon, t, speed (m/s)."""
    f = rt.FeedMessage()
    f.header.gtfs_realtime_version = "2.0"
    f.header.timestamp = max(v["t"] for v in vehicles) if vehicles else 0
    for v in vehicles:
        e = f.entity.add()
        e.id = v["id"]
        e.vehicle.vehicle.id = v["id"]
        e.vehicle.trip.trip_id = f"{v['route']}_runtime_{v['id']}"
        e.vehicle.trip.route_id = v["route"]
        e.vehicle.trip.start_time = v.get("start", "08:01:00")
        e.vehicle.trip.start_date = "20261120"
        e.vehicle.position.latitude = v["lat"]
        e.vehicle.position.longitude = v.get("lon", 77.20)
        e.vehicle.position.speed = v.get("speed", 6.0)
        e.vehicle.timestamp = v["t"]
    return f.SerializeToString()


BUS = dict(id="DL1PC0001", route="1", lat=28.615, t=ts(8, 20))   # halfway B->C; t1 is scheduled there at 08:15


def test_parse_feed():
    _, obs = parse_vehicle_positions(feed(BUS))
    o = obs[0]
    assert (o.vehicle_id, o.route_id, o.start_time) == ("DL1PC0001", "1", "08:01:00")
    assert o.lat == pytest.approx(28.615) and o.speed_mps == pytest.approx(6.0)


def test_snap_and_delay(net):  # noqa: F811
    tr = LiveTracker(net)
    tr.ingest(*parse_vehicle_positions(feed(BUS)), now=ts(8, 20, 30))
    v = tr.vehicles["DL1PC0001"]
    assert v.progress == pytest.approx(1.5, abs=0.02) and v.off_route_m < 5
    assert v.scheduled_trip_id == "r1a" and v.delay_s == pytest.approx(300, abs=15)   # 5 min late
    assert v.next_stop_id == "C" and v.speed_kmh == pytest.approx(21.6)


def test_arrivals_use_live_delay_then_timetable(net):  # noqa: F811
    tr = LiveTracker(net)
    now = ts(8, 20, 30)
    tr.ingest(*parse_vehicle_positions(feed(BUS)), now=now)
    a = tr.arrivals("C", now=now)
    assert a["live_feed"] and a["note"] is None
    first, second = a["arrivals"][:2]
    assert first["basis"] == "live_estimate" and first["eta"] == "08:25" and first["delay_min"] == pytest.approx(5, abs=0.3)
    assert second["basis"] == "scheduled" and second["eta"] == "08:50"
    assert len(a["arrivals"]) == 2                           # tracked trip is not listed twice
    assert all(r["vehicle_id"] != "DL1PC0001" for r in tr.arrivals("B", now=now)["arrivals"])   # already passed B


def test_unusable_vehicles_are_ignored(net):  # noqa: F811
    tr = LiveTracker(net)
    now = ts(8, 20, 30)
    vs = [dict(BUS, id="STALE", t=ts(8, 5)),                                   # 15 min old position
          dict(BUS, id="PARKED", start="05:00:00", speed=0.0),                 # no trip match, not moving
          dict(BUS, id="FAR", lat=28.615, lon=77.25),                          # ~5 km off the route
          dict(BUS, id="GHOST", route="999")]                                  # route not in the timetable
    tr.ingest(*parse_vehicle_positions(feed(*vs)), now=now)
    assert tr.vehicles["STALE"].stale and tr.vehicles["GHOST"].pattern_idx is None
    assert tr.vehicles["FAR"].off_route_m > 1000 and tr.vehicles["FAR"].scheduled_trip_id is None
    assert all(r["basis"] == "scheduled" for r in tr.arrivals("C", now=now)["arrivals"])
    assert tr.status(now)["matched_to_route"] == 3


def test_feed_down_falls_back_to_timetable(net):  # noqa: F811
    tr = LiveTracker(net)
    a = tr.arrivals("C", now=ts(8, 10))
    assert not a["live_feed"] and "timetable" in a["note"]
    assert [r["eta"] for r in a["arrivals"]] == ["08:20", "08:50"] and {r["basis"] for r in a["arrivals"]} == {"scheduled"}

    calls = iter([feed(BUS), RuntimeError("HTTP 503")])

    def fetch():
        x = next(calls)
        if isinstance(x, Exception):
            raise x
        return x
    po = LivePoller(tr, fetch)
    assert po.poll_once()["vehicles"] == 1
    assert po.poll_once() is None                              # outage: no crash, last snapshot kept
    assert "HTTP 503" in tr.status()["last_error"] and len(tr.vehicles) == 1


def test_terminal_stop_has_no_departures(net):  # noqa: F811
    tr = LiveTracker(net)
    tr.ingest(*parse_vehicle_positions(feed(BUS)), now=ts(8, 20, 30))
    assert tr.arrivals("D", now=ts(8, 20, 30))["arrivals"] == []   # route 1 ends at D: nothing to board


def test_vehicle_detail(net):  # noqa: F811
    tr = LiveTracker(net)
    now = ts(8, 20, 30)
    tr.ingest(*parse_vehicle_positions(feed(BUS)), now=now)
    d = tr.vehicle_detail("DL1PC0001", now=now)
    assert d["route"] == "R1UP" and d["towards"] == "Delta" and d["matched"]
    assert [s["passed"] for s in d["stops"]] == [True, True, False, False]
    assert d["stops"][2]["eta"] == "08:25" and d["stops"][2]["basis"] == "live_estimate"
    assert "eta" not in d["stops"][0]
    assert tr.vehicle_detail("nope") is None


def test_track_ride(net):  # noqa: F811
    tr = LiveTracker(net)
    now = ts(8, 20, 30)
    tr.ingest(*parse_vehicle_positions(feed(BUS)), now=now)
    a = tr.track("1", "C", "D", now=now)                         # bus is coming to C
    assert a["phase"] == "approaching" and a["vehicle"]["vehicle_id"] == "DL1PC0001"
    assert a["stops_away"] == 1 and a["eta_board"] == "08:25" and a["eta_alight"] == "08:35"
    assert [s["name"] for s in a["stops"]] == ["Charlie", "Delta"]
    b = tr.track("1", "B", "D", vehicle_id="DL1PC0001", now=now)  # we are on it: past B
    assert b["phase"] == "on_board" and b["stops_left"] == 2
    late = dict(BUS, lat=28.6299, t=ts(8, 36))
    tr.ingest(*parse_vehicle_positions(feed(late)), now=ts(8, 36, 10))
    assert tr.track("1", "B", "D", vehicle_id="DL1PC0001", now=ts(8, 36, 10))["phase"] == "arrived"


def test_track_falls_back_to_timetable(net):  # noqa: F811
    tr = LiveTracker(net)
    now = ts(8, 20, 30)
    tr.ingest(*parse_vehicle_positions(feed(BUS)), now=now)
    r = tr.track("1", "A", "C", now=now)                         # the only bus already passed A
    assert r["phase"] == "no_live" and r["next_scheduled"] == "08:30"
    assert tr.track("1", "D", "A", now=now)["phase"] == "unknown_route"
    assert tr.track("1", "C", "D", skip={"DL1PC0001"}, now=now)["phase"] == "no_live"
