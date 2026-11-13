import sys
from pathlib import Path

import pandas as pd
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))


def write_gtfs(d: Path, stops, trips, stop_times, routes=None) -> Path:
    """Write a tiny GTFS feed. stops: [(id, name, lat, lon)], trips: [(trip_id, route_id)],
    stop_times: [(trip_id, 'HH:MM:SS', stop_id, seq)]."""
    d.mkdir(parents=True, exist_ok=True)
    routes = routes or sorted({(r, f"R{r}UP") for _, r in trips})
    pd.DataFrame(routes, columns=["route_id", "route_short_name"]).assign(route_long_name="", route_type=3) \
        .to_csv(d / "routes.txt", index=False)
    pd.DataFrame(stops, columns=["stop_id", "stop_name", "stop_lat", "stop_lon"]).to_csv(d / "stops.txt", index=False)
    pd.DataFrame(trips, columns=["trip_id", "route_id"]).assign(service_id="1").to_csv(d / "trips.txt", index=False)
    pd.DataFrame([(t, tm, tm, s, q) for t, tm, s, q in stop_times],
                 columns=["trip_id", "arrival_time", "departure_time", "stop_id", "stop_sequence"]) \
        .to_csv(d / "stop_times.txt", index=False)
    pd.DataFrame([["1", 1, 1, 1, 1, 1, 1, 1, "20250101", "20400101"]],
                 columns=["service_id", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday",
                          "sunday", "start_date", "end_date"]).to_csv(d / "calendar.txt", index=False)
    return d


@pytest.fixture
def line_feed(tmp_path):
    """One route R1 (A-B-C-D, 10 min between stops), two trips at 08:00 and 08:30."""
    stops = [("A", "Alpha", 28.60, 77.20), ("B", "Bravo Nagar", 28.61, 77.20),
             ("C", "Charlie Metro Station", 28.62, 77.20), ("D", "Delta Border (T)", 28.63, 77.20)]
    trips = [("t1", "1"), ("t2", "1")]
    st = []
    for t, h, m in (("t1", 8, 0), ("t2", 8, 30)):
        for i, s in enumerate("ABCD"):
            mm = m + 10 * i
            st.append((t, f"{h + mm // 60:02d}:{mm % 60:02d}:00", s, i))
    return write_gtfs(tmp_path / "gtfs", stops, trips, st)
