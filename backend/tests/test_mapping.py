import pandas as pd
import pytest

from app.data_pipeline.mapping.direction_mapper import resolve_od
from app.data_pipeline.mapping.normalize import name_variants, normalize_stop_name, split_route_direction
from app.data_pipeline.mapping.route_mapper import build_route_mapping, usable
from app.data_pipeline.mapping.stop_mapper import build_stop_mapping


# ---- normalisation ------------------------------------------------------------------
@pytest.mark.parametrize("raw,expected", [
    ("Badarpur Bdr", "badarpur border"),
    ("Badarpur Border (T)", "badarpur border terminal"),
    ("Laxmi Nagar X-ing", "laxmi nagar crossing"),
    ("Laxmi Nagar Xing", "laxmi nagar crossing"),
    ("P.S. Pahar Gunj", "police station pahar gunj"),
    ("Saket Metro Sation", "saket metro station"),
    ("  KISHAN GARH & NPD ", "kishan garh and npd"),
    (None, ""),
])
def test_normalize_stop_name(raw, expected):
    assert normalize_stop_name(raw) == expected


def test_name_variants_split_on_slash():
    assert name_variants("AIIMS / Safdurjung Hospital") == ["aiims safdurjung hospital", "aiims", "safdurjung hospital"]


@pytest.mark.parametrize("raw,expected", [
    ("8ADOWN", ("8A", "DOWN")), ("717AUP", ("717A", "UP")), ("102 dn", ("102", "DOWN")),
    ("AIRPORT-STL", ("AIRPORTSTL", None)),
])
def test_split_route_direction(raw, expected):
    assert split_route_direction(raw) == expected


# ---- route mapping ------------------------------------------------------------------
def test_route_mapping_exact_ambiguous_unmapped():
    gtfs = pd.DataFrame({"route_id": ["1", "2", "3", "4"],
                         "route_short_name": ["8UP", "8DOWN", "717AUP", "717AUP"], "route_long_name": ""})
    m = build_route_mapping(pd.Series(["8UP", "8 down", "717AUP", "999UP"]), gtfs).set_index("ticket_route")
    assert m.loc["8UP", "gtfs_route_id"] == "1" and m.loc["8UP", "mapping_method"] == "exact_short_name"
    assert m.loc["8 down", "gtfs_route_id"] == "2"
    assert m.loc["717AUP", "mapping_method"] == "exact_short_name_ambiguous"
    assert pd.isna(m.loc["999UP", "gtfs_route_id"])
    assert set(usable(m.reset_index())["ticket_route"]) == {"8UP", "8 down"}  # ambiguous never used


# ---- stop mapping -------------------------------------------------------------------
def _stops_and_patterns():
    stops = pd.DataFrame([
        ("A", "Shadipur Metro Station", 28.650, 77.150),
        ("B", "Badarpur Border (T)", 28.500, 77.300),
        ("C", "Laxmi Nagar X-ing / Laxmi Nagar", 28.630, 77.270),
        ("D", "Sector 9 Market", 28.700, 77.100),
        ("E", "Sector 12 Market", 28.720, 77.110),       # far from D: generic 'market' subset must not match
        ("F", "Bamnoli Crossing", 28.560, 77.020),
        ("X", "Bamnoli", 28.5605, 77.0205),             # same name elsewhere, ~70 m from F, NOT on route
    ], columns=["stop_id", "stop_name", "stop_lat", "stop_lon"])
    pat = pd.DataFrame({"route_id": "1", "stop_id": list("ABCDEF")})
    return stops, pat


@pytest.mark.parametrize("ticket_name,expected_stop,method", [
    ("Shadipur Metro Station", "A", "exact_on_route"),
    ("Shadi Pur Metro Station", "A", "compact_on_route"),
    ("Badarpur Border", "B", "subset_on_route"),
    ("Laxmi Nagar", "C", "exact_on_route"),                  # '/'-part of the GTFS name
    ("Laxmi Nagar Xing", "C", "exact_on_route"),
    ("Market", None, "unmatched"),                           # generic-only overlap is rejected
    ("Bamnoli", "F", "subset_on_route"),
    ("Nowhere Vihar", None, "unmatched"),
])
def test_stop_mapping_ladder(ticket_name, expected_stop, method):
    stops, pat = _stops_and_patterns()
    pairs = pd.DataFrame({"gtfs_route_id": ["1"], "stop_norm": [normalize_stop_name(ticket_name)], "stop_raw": [ticket_name]})
    r = build_stop_mapping(pairs, pat, stops).iloc[0]
    assert r["gtfs_stop_id"] == expected_stop
    assert r["mapping_method"] == method


def test_stop_mapping_located_by_name_and_alias():
    stops, pat = _stops_and_patterns()
    stops.loc[stops.stop_id == "F", "stop_name"] = "Najafgarh Road Crossing"   # route stop renamed
    pairs = pd.DataFrame({"gtfs_route_id": ["1", "1"], "stop_norm": ["bamnoli", "sj hospital"],
                          "stop_raw": ["Bamnoli", "SJ Hospital"]})
    out = build_stop_mapping(pairs, pat, stops, aliases={"sj hospital": "shadipur metro station"}).set_index("stop_raw")
    assert out.loc["Bamnoli", "gtfs_stop_id"] == "F"
    assert out.loc["Bamnoli", "mapping_method"] == "located_by_name_nearest_on_route"
    assert out.loc["SJ Hospital", "gtfs_stop_id"] == "A"
    assert out.loc["SJ Hospital", "mapping_method"].startswith("alias_")


# ---- OD direction resolution ------------------------------------------------------------
def _patterns():
    rows = []
    for pid, route, seq, primary, n in (("p1", "1", "ABCD", True, 10), ("p2", "1", "BC", False, 2),
                                        ("p3", "2", "ABCB", True, 5)):
        rows += [(pid, route, i, s, n, primary) for i, s in enumerate(seq)]
    return pd.DataFrame(rows, columns=["pattern_id", "route_id", "seq", "stop_id", "n_trips", "is_primary"])


def test_resolve_od_statuses():
    od = pd.DataFrame([("1", "A", "D"), ("1", "D", "A"), ("1", "A", "Z"), ("2", "C", "B")],
                      columns=["gtfs_route_id", "o_stop_id", "d_stop_id"])
    r = resolve_od(od, _patterns()).set_index(["gtfs_route_id", "o_stop_id", "d_stop_id"])
    assert r.loc[("1", "A", "D"), "od_status"] == "resolved_primary"
    assert (r.loc[("1", "A", "D"), "o_seq"], r.loc[("1", "A", "D"), "d_seq"]) == (0, 3)
    assert r.loc[("1", "D", "A"), "od_status"] == "reverse_order"
    assert r.loc[("1", "A", "Z"), "od_status"] == "stops_not_on_same_pattern"
    # loop A-B-C-B: C -> B resolves to the second visit of B (seq 3)
    assert r.loc[("2", "C", "B"), "d_seq"] == 3
