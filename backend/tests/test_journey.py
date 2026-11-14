"""Journey planner on a tiny synthetic network.

Network (10 min between consecutive stops, all stops ~1.1 km apart unless noted):
  R1  A-B-C-D        trips 08:00, 08:30
  R2  C2-E-F         trips 08:30, 09:00     C2 is 50 m from C (walk transfer)
  R3  A-X-F          trip 08:05 (slower, quiet: 15 min per hop)
  R4  A-Y-F          trip 08:05 (fast but crowded: 10 min per hop)
"""
from datetime import date, datetime

import pytest

from app.data_pipeline.ingestion.gtfs_loader import load_gtfs
from app.journey_engine.network import load_network
from app.services.crowd_service import SegmentPrediction
from app.services.journey_service import JourneyService, PlaceError
from tests.conftest import write_gtfs

D = date(2026, 11, 20)


def _hm(m: int) -> str:
    return f"{m // 60:02d}:{m % 60:02d}:00"


@pytest.fixture
def net(tmp_path):
    stops = [("A", "Alpha", 28.60, 77.20), ("B", "Bravo", 28.61, 77.20), ("C", "Charlie", 28.62, 77.20),
             ("D", "Delta", 28.63, 77.20), ("C2", "Charlie Opp", 28.6204, 77.2002), ("E", "Echo", 28.62, 77.21),
             ("F", "Foxtrot", 28.62, 77.22), ("X", "Xray", 28.59, 77.21), ("Y", "Yankee", 28.605, 77.215)]
    trips, st = [], []

    def add(tid, route, seq, start, hop):
        trips.append((tid, route))
        for i, s in enumerate(seq):
            st.append((tid, _hm(start + hop * i), s, i))

    add("r1a", "1", "ABCD", 8 * 60, 10)
    add("r1b", "1", "ABCD", 8 * 60 + 30, 10)
    add("r2a", "2", ["C2", "E", "F"], 8 * 60 + 30, 10)
    add("r2b", "2", ["C2", "E", "F"], 9 * 60, 10)
    add("r3a", "3", "AXF", 8 * 60 + 5, 15)
    add("r4a", "4", "AYF", 8 * 60 + 5, 10)
    feed = write_gtfs(tmp_path / "gtfs", stops, trips, st)
    P = tmp_path / "processed"
    load_gtfs(feed, P)
    return load_network(P, use_cache=False)


class FakeCrowd:
    """Route 4 is very crowded, everything else quiet; records every (segment, bucket) lookup."""
    bucket_minutes = 30

    def __init__(self):
        self.calls = []

        class F:
            window_end = "2026-10-31"

            @staticmethod
            def slot_for(d):
                return 0, "recurring_weekday_forecast"
        self.forecasts = F()

    def predict(self, segment_id, d, bucket):
        self.calls.append((segment_id, bucket))
        crowded = segment_id.startswith("4:")
        ci, cat = (0.97, "VERY_HIGH") if crowded else (0.1, "LOW")
        return SegmentPrediction(segment_id, segment_id.split(":")[0], bucket, 1.0, 0.5, 1.5, 4.0, 4, cat, ci,
                                 1.0, 0.0, "HIGH", 500, 1, "test")


def stop(s):
    return {"type": "stop", "id": s}


def test_direct_trip_takes_next_departure(net):
    js = JourneyService(net, None)
    r = js.plan(stop("A"), stop("D"), datetime(2026, 11, 20, 8, 10))
    j = r["journeys"][0]
    assert j["transfers"] == 0 and j["departure"] == "08:30" and j["arrival"] == "09:00"
    assert [s["from_stop_id"] for s in j["legs"][0]["segments"]] == ["A", "B", "C"]
    assert j["crowd_level"] == "UNKNOWN"                      # no crowd model -> never shown as low
    assert any("without crowd estimates" in n for n in r["notes"])


def test_one_transfer_with_footpath(net):
    js = JourneyService(net, None)
    r = js.plan(stop("B"), stop("E"), datetime(2026, 11, 20, 8, 0))
    j = r["journeys"][0]
    assert j["transfers"] == 1 and [l["route"] for l in j["legs"]] == ["R1UP", "R2UP"]
    assert j["legs"][0]["alight_stop_id"] == "C" and j["legs"][1]["board_stop_id"] == "C2"
    assert j["walk"]["transfer_min"] > 0                       # walked C -> C2
    assert j["departure"] == "08:10" and j["arrival"] == "08:40"


def test_no_service_and_invalid_stop(net):
    js = JourneyService(net, None)
    assert js.plan(stop("A"), stop("D"), datetime(2026, 11, 20, 22, 0))["journeys"] == []   # after last bus
    assert js.plan(stop("D"), stop("A"), datetime(2026, 11, 20, 8, 0))["journeys"] == []    # wrong direction
    with pytest.raises(PlaceError):
        js.plan(stop("NOPE"), stop("A"), datetime(2026, 11, 20, 8, 0))
    with pytest.raises(PlaceError):
        js.plan(stop("A"), stop("D"), datetime(2026, 11, 20, 8, 0), preference="cheapest")


def test_crowd_evaluated_at_each_segment_crossing_time(net):
    crowd = FakeCrowd()
    js = JourneyService(net, crowd)
    j = js.plan(stop("B"), stop("E"), datetime(2026, 11, 20, 8, 0))["journeys"][0]
    seg = {s["segment_id"]: s["depart"] for l in j["legs"] for s in l["segments"]}
    assert seg == {"1:B>C": "08:10", "2:C2>E": "08:30"}
    called = dict(crowd.calls)
    assert called["1:B>C"] == 16 and called["2:C2>E"] == 17   # 08:10 -> bucket 16, 08:30 -> bucket 17


def test_preference_changes_ranking(net):
    js = JourneyService(net, FakeCrowd())
    when = datetime(2026, 11, 20, 8, 0)
    fast = js.plan(stop("A"), stop("F"), when, "fastest")["journeys"]
    calm = js.plan(stop("A"), stop("F"), when, "least_crowded")["journeys"]
    assert [l["route"] for l in fast[0]["legs"]] == ["R4UP"]           # 20 min but very crowded
    assert [l["route"] for l in calm[0]["legs"]] == ["R3UP"]           # 35 min, quiet, within 1.6x the fastest
    assert "fastest" in fast[0]["labels"] and "least_crowded" in calm[0]["labels"]
    assert calm[0]["crowd_exposure"] < fast[0]["crowd_exposure"]


def test_stop_search_ranks_full_matches_first(net):
    js = JourneyService(net, None)
    assert js.search_stops("charlie")[0]["name"] == "Charlie"
    assert js.search_stops("charlie opp")[0]["name"] == "Charlie Opp"
    assert js.search_stops("") == []


def test_advisor_returns_row_per_offset(net):
    js = JourneyService(net, FakeCrowd())
    a = js.advise(stop("A"), stop("D"), datetime(2026, 11, 20, 8, 0), offsets_min=(0, 30))
    assert [r["offset_min"] for r in a["departures"]] == [0, 30]
    assert a["departures"][0]["board_time"] == "08:00" and a["departures"][1]["board_time"] == "08:30"
    assert a["recommendation"]["offset_min"] == 0


class FakeGeo:
    """Road distances = 2x straight line, except stop B which is unreachable by road."""
    def __init__(self, net, down=False):
        self.net, self.down, self.paths = net, down, []

    def walk_distances(self, lat, lon, pts):
        if self.down:
            return None
        from app.journey_engine.network import haversine_m
        b = (float(self.net.stops.at["B", "stop_lat"]), float(self.net.stops.at["B", "stop_lon"]))
        return [None if p == b else float(haversine_m(lat, lon, *p)) * 2 for p in pts]

    def walk_path(self, a, b):
        self.paths.append((a, b))
        return {"distance_m": 321.0, "path": [list(a), list(b)]}


def coord(lat, lon, label="Home"):
    return {"type": "coord", "lat": lat, "lon": lon, "label": label}


def test_coord_place_uses_road_walks_and_paths(net):
    geo = FakeGeo(net)
    js = JourneyService(net, None, geo=geo)
    r = js.plan(coord(28.6003, 77.2003), stop("D"), datetime(2026, 11, 20, 7, 50))
    assert r["query"]["origin"]["walk_basis"] == "osm_road"
    j = r["journeys"][0]
    assert j["legs"][0]["board_stop_id"] == "A"
    assert j["walk"]["basis"] == {"origin": "osm_road", "destination": "stop"}
    assert j["walk"]["origin_path"] and j["walk"]["destination_path"] is None
    assert j["walk"]["origin_m"] == 321
    # road distance (2x ~42 m) / 1.2 m/s, not straight line x 1.3
    assert 1.0 < j["walk"]["origin_min"] < 1.4


def test_road_unreachable_stop_is_dropped_and_fallback_is_labelled(net):
    js = JourneyService(net, None, geo=FakeGeo(net))
    items, info = js.resolve(coord(28.61, 77.2001), 1500)
    assert "B" not in dict(items)
    js2 = JourneyService(net, None, geo=FakeGeo(net, down=True))
    r = js2.plan(coord(28.6003, 77.2003), stop("D"), datetime(2026, 11, 20, 7, 50))
    assert r["query"]["origin"]["walk_basis"] == "straight_line_estimate"
    assert any("straight-line" in n for n in r["notes"])
    assert r["journeys"][0]["walk"]["origin_path"] is None


def test_no_stop_nearby_has_code_and_wider_radius_works(net):
    js = JourneyService(net, None)
    far = coord(28.6, 77.2105)            # ~1 km east of A, ~0.6+ km from others
    with pytest.raises(PlaceError) as e:
        js.plan(far, stop("D"), datetime(2026, 11, 20, 7, 50), radius_m=300)
    assert e.value.code == "no_stop_nearby" and e.value.detail()["radius_m"] == 300
    r = js.plan(far, stop("D"), datetime(2026, 11, 20, 7, 50), radius_m=1500)
    assert r["query"]["origin"]["radius_m"] == 1500
