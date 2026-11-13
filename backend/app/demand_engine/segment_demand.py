"""Segment-level demand (metric B) and supply normalisation.

B. Estimated segment demand
    L[s, d, b] = sum_j n_j * I(ticket j crosses segment s, crossing time in bucket b)
A ticket from stop A to stop D on a resolved pattern crosses A-B, B-C, C-D. The crossing
time of each segment is the ticket time plus the scheduled running time from the
boarding stop (pattern median offsets). This is passenger flow across a segment; it is
NOT simultaneous onboard occupancy.

Supply normalisation
    DPD[s, b] = L[s, b] / scheduled departures over segment s in bucket b
'demand per scheduled departure' - an index, not passengers per bus, because the
e-ticket sample covers only part of all riders.

Segment shares let the route-level forecast be distributed to segments:
    share[s, daytype, band] = L[s, daytype, band] / route passengers[daytype, band]
(computed on resolved tickets only: unresolved tickets are assumed to travel like
resolved ones on the same route - documented in docs/assumptions.md).
"""
from __future__ import annotations

from pathlib import Path

import duckdb
import pandas as pd

TIME_BANDS = [(0, 6, "early"), (6, 10, "am_peak"), (10, 16, "midday"), (16, 21, "pm_peak"), (21, 24, "night")]


def band_case(hour_expr: str) -> str:
    parts = " ".join(f"WHEN {hour_expr} >= {a} AND {hour_expr} < {b} THEN '{n}'" for a, b, n in TIME_BANDS)
    return f"CASE {parts} END"


def daytype_case(date_expr: str, holidays: list[str]) -> str:
    hol = ", ".join(f"DATE '{h}'" for h in holidays) or "DATE '1900-01-01'"
    return (f"CASE WHEN {date_expr} IN ({hol}) OR isodow({date_expr}) = 7 THEN 2 "
            f"WHEN isodow({date_expr}) = 6 THEN 1 ELSE 0 END")


def build_segment_tables(
    tickets: Path, resolved: Path, patterns: Path, stop_times: Path, trips: Path,
    out_dir: Path, holidays: list[str], bucket_minutes: int = 30,
) -> dict:
    """Writes segment_demand, segment_shares, segment_supply, segments parquet files."""
    con = duckdb.connect()
    con.execute("SET TimeZone='Asia/Kolkata'")
    for name, p in {"t": tickets, "r": resolved, "p": patterns, "st": stop_times, "tr": trips}.items():
        con.execute(f"CREATE VIEW {name} AS SELECT * FROM read_parquet('{p.as_posix()}')")

    # canonical segment list: consecutive stops of every pattern
    con.execute("""
        CREATE TABLE segments AS
        SELECT a.pattern_id, a.route_id, a.seq AS seg_seq, a.stop_id AS from_stop_id, b.stop_id AS to_stop_id,
               a.route_id || ':' || a.stop_id || '>' || b.stop_id AS segment_id,
               b.offset_s - a.offset_s AS run_s, a.offset_s AS from_offset_s, a.is_primary
        FROM p a JOIN p b ON b.pattern_id = a.pattern_id AND b.seq = a.seq + 1
    """)

    # tickets x crossed segments, with crossing time
    con.execute(f"""
        CREATE TABLE seg_flow AS
        WITH tk AS (
            SELECT t.ticket_id, t.passenger_count AS n, t.created_at, r.pattern_id, r.o_seq, r.d_seq
            FROM t JOIN r ON r.ticket_id = t.ticket_id
            WHERE r.od_status LIKE 'resolved%'
        ), o AS (
            SELECT pattern_id, seq, offset_s FROM p
        )
        SELECT s.segment_id, s.route_id, tk.n,
               tk.created_at + to_seconds(CAST(coalesce(s.from_offset_s - oo.offset_s, 0) AS BIGINT)) AS cross_ts
        FROM tk
        JOIN o oo ON oo.pattern_id = tk.pattern_id AND oo.seq = tk.o_seq
        JOIN segments s ON s.pattern_id = tk.pattern_id AND s.seg_seq >= tk.o_seq AND s.seg_seq < tk.d_seq
    """)
    con.execute(f"""
        CREATE TABLE segment_demand AS
        SELECT segment_id, any_value(route_id) AS route_id, CAST(cross_ts AS DATE) AS service_date,
               (hour(cross_ts) * 60 + minute(cross_ts)) // {bucket_minutes} AS bucket,
               sum(n) AS pax, count(*) AS tickets
        FROM seg_flow GROUP BY segment_id, service_date, bucket
    """)

    # Per-day band totals so shares can be computed for any date range (no test leakage)
    con.execute(f"""
        CREATE TABLE route_resolved_daily AS
        SELECT p0.route_id, t.service_date, {daytype_case('t.service_date', holidays)} AS daytype,
               {band_case('t.hour')} AS band, sum(t.passenger_count) AS route_pax
        FROM t JOIN r ON r.ticket_id = t.ticket_id
        JOIN (SELECT DISTINCT pattern_id, route_id FROM p) p0 ON p0.pattern_id = r.pattern_id
        WHERE r.od_status LIKE 'resolved%' GROUP BY ALL
    """)
    con.execute(f"""
        CREATE TABLE segment_band_daily AS
        SELECT segment_id, route_id, CAST(cross_ts AS DATE) AS service_date,
               {daytype_case('CAST(cross_ts AS DATE)', holidays)} AS daytype,
               {band_case('hour(cross_ts)')} AS band, sum(n) AS seg_pax, count(*) AS support
        FROM seg_flow GROUP BY ALL
    """)
    con.execute("CREATE TABLE segment_shares AS " + shares_sql("segment_band_daily", "route_resolved_daily"))

    # scheduled supply: departures from the segment's first stop per bucket (all patterns of the route)
    con.execute(f"""
        CREATE TABLE segment_supply AS
        WITH dep AS (
            SELECT tr.pattern_id, st.stop_id, CAST(st.departure_s AS BIGINT) % 86400 AS dep_s
            FROM st JOIN tr USING (trip_id)
        )
        SELECT s.segment_id, (dep.dep_s // 60) // {bucket_minutes} AS bucket, count(*) AS departures
        FROM (SELECT DISTINCT segment_id, pattern_id, from_stop_id FROM segments) s
        JOIN dep ON dep.pattern_id = s.pattern_id AND dep.stop_id = s.from_stop_id
        GROUP BY ALL
    """)

    out_dir.mkdir(parents=True, exist_ok=True)
    for tbl in ("segments", "segment_demand", "segment_shares", "segment_supply",
                "segment_band_daily", "route_resolved_daily"):
        con.execute(f"COPY {tbl} TO '{(out_dir / (tbl + '.parquet')).as_posix()}' (FORMAT PARQUET)")
    stats = con.execute("""
        SELECT (SELECT count(*) FROM segments) segments_all,
               (SELECT count(DISTINCT segment_id) FROM segment_demand) segments_with_demand,
               (SELECT sum(pax) FROM segment_demand) segment_pax_crossings,
               (SELECT count(*) FROM seg_flow) ticket_segment_rows
    """).df().iloc[0].to_dict()
    return {k: int(v) for k, v in stats.items()}


def shares_sql(seg_tbl: str, route_tbl: str, date_from: str | None = None, date_to: str | None = None) -> str:
    """SQL computing share[segment, daytype, band] over an optional inclusive date range."""
    where = ""
    if date_from and date_to:
        where = f"WHERE service_date BETWEEN DATE '{date_from}' AND DATE '{date_to}'"
    return f"""
        WITH f AS (
            SELECT segment_id, route_id, daytype, band, sum(seg_pax) AS seg_pax, sum(support) AS support
            FROM {seg_tbl} {where} GROUP BY ALL
        ), rr AS (
            SELECT route_id, daytype, band, sum(route_pax) AS route_pax FROM {route_tbl} {where} GROUP BY ALL
        )
        SELECT f.*, rr.route_pax, f.seg_pax / rr.route_pax AS share
        FROM f JOIN rr USING (route_id, daytype, band)
    """


def load_table(processed_dir: Path, name: str) -> pd.DataFrame:
    return pd.read_parquet(processed_dir / f"{name}.parquet")
