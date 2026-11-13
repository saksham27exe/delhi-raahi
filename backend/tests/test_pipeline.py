"""End-to-end pipeline on a hand-checkable feed (see conftest.line_feed):
route R1UP: A -08:00- B -08:10- C -08:20- D (trip t1), same again from 08:30 (trip t2)."""
import pandas as pd
import pytest

from app.core.config import Settings
from app.data_pipeline.ingestion.gtfs_loader import gtfs_time_to_seconds
from app.data_pipeline.ingestion.ticketing_loader import SchemaError, load_tickets
from app.data_pipeline.pipeline import run

HEADER = "passenger_count,amount,vehicle_number,ticket_type_id,end_stop_name,route,start_stop_name,created_at,is_ac\n"


def _ticket(pc, end, start, ts, route="R1UP", veh="NULL"):
    return f'{pc},"9",{veh},1,"{end}","{route}","{start}","{ts}",True\n'


def _write_tickets(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(HEADER + "".join(rows), encoding="utf-8")


@pytest.fixture
def settings(tmp_path, line_feed):
    s = Settings(_env_file=None, crowdwise_data_dir=tmp_path / "data", crowdwise_models_dir=tmp_path / "models")
    (s.crowdwise_data_dir / "gtfs").parent.mkdir(parents=True, exist_ok=True)
    line_feed.rename(s.gtfs_dir)
    rows = [_ticket(2, "Delta Border", "Alpha", "2026-10-07 08:25:00+05:30")]          # A->D, crosses 08:25/08:35/08:45
    rows += [_ticket(1, "Charlie Metro Sation", "Bravo Nagar", "2026-10-07 08:12:00+05:30")] * 40  # B->C
    rows += [_ticket(1, "Alpha", "Delta Border", "2026-10-07 09:00:00+05:30")]          # wrong direction
    rows += [_ticket(1, "Nowhere", "Alpha", "2026-10-07 09:00:00+05:30")]               # unmapped stop
    rows += [_ticket(0, "Bravo Nagar", "Alpha", "2026-10-07 09:00:00+05:30")]           # invalid passenger count
    _write_tickets(s.tickets_csv, rows)
    return s


def test_time_parsing_beyond_midnight():
    assert gtfs_time_to_seconds(pd.Series(["25:10:00", "08:00:30"])).tolist() == [90600, 28830]


def test_ticket_loader_flags_and_local_time(tmp_path):
    csv = tmp_path / "t.csv"
    _write_tickets(csv, [_ticket(1, "B", "A", "2026-10-31 23:58:00+05:30"), _ticket(0, "B", "A", "2026-10-01 00:01:00+05:30"),
                         _ticket(1, "A", "A", "2026-10-01 00:01:00+05:30")])
    summary = load_tickets(csv, tmp_path / "t.parquet")
    df = pd.read_parquet(tmp_path / "t.parquet").sort_values("ticket_id")
    assert summary["total"] == 3
    assert df["quality_status"].tolist() == ["valid", "implausible_passenger_count", "same_origin_destination"]
    assert pd.isna(df["passenger_count"].iloc[1]) and df["passenger_count_raw"].iloc[1] == "0"  # never imputed
    assert str(df["service_date"].iloc[0]) == "2026-10-31" and df["hour"].iloc[0] == 23     # IST, not shifted to UTC
    assert df["bucket"].iloc[0] == 47


def test_ticket_loader_rejects_missing_columns(tmp_path):
    csv = tmp_path / "bad.csv"
    csv.write_text("passenger_count,route\n1,8UP\n")
    with pytest.raises(SchemaError):
        load_tickets(csv, tmp_path / "x.parquet")


def test_pipeline_end_to_end(settings):
    report = run(settings)
    P = settings.processed_dir
    od = {r["od_status"]: r["tickets"] for r in report["od_resolution"]}
    assert od == {"resolved_primary": 41, "reverse_order": 1, "stop_unmapped": 1, "invalid_ticket": 1}

    dem = pd.read_parquet(P / "segment_demand.parquet")
    seg = pd.read_parquet(P / "segments.parquet").set_index("segment_id")
    dem["from"] = dem["segment_id"].map(seg["from_stop_id"])
    got = {(r["from"], int(r.bucket)): int(r.pax) for _, r in dem.iterrows()}
    # A->D ticket (2 pax) at 08:25 crosses A-B at 08:25 (bucket 16), B-C at 08:35 and C-D at 08:45 (bucket 17)
    # 40 B->C tickets at 08:12 cross B-C in bucket 16
    assert got == {("A", 16): 2, ("B", 16): 40, ("B", 17): 2, ("C", 17): 2}

    sup = pd.read_parquet(P / "segment_supply.parquet")
    sup["from"] = sup["segment_id"].map(seg["from_stop_id"])
    s = {(r["from"], int(r.bucket)): int(r.departures) for _, r in sup.iterrows()}
    assert s == {("A", 16): 1, ("A", 17): 1, ("B", 16): 1, ("B", 17): 1, ("C", 16): 1, ("C", 17): 1}

    sh = pd.read_parquet(P / "segment_shares.parquet")
    sh["from"] = sh["segment_id"].map(seg["from_stop_id"])
    am = sh[sh["band"] == "am_peak"].set_index("from")["share"]
    # resolved route passengers in the am band = 2 + 40 = 42
    assert am["B"] == pytest.approx(42 / 42) and am["A"] == pytest.approx(2 / 42)
    assert report["pct_usable_for_segment_analysis"] == pytest.approx(100 * 41 / 44, abs=0.01)
