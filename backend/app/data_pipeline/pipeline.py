"""End-to-end data pipeline: raw files -> clean tables -> mappings -> segment demand -> quality report.

Run:  python scripts/run_pipeline.py            (from the repo root)
Each step writes Parquet into data/processed/ so later steps and the API never re-parse raw files.
"""
from __future__ import annotations

import json
import logging
import time
from pathlib import Path

import duckdb
import pandas as pd

from app.core.config import Settings, get_settings
from app.data_pipeline.ingestion.gtfs_loader import load_gtfs
from app.data_pipeline.ingestion.ticketing_loader import load_tickets
from app.data_pipeline.mapping.direction_mapper import resolve_od
from app.data_pipeline.mapping.route_mapper import build_route_mapping, load_aliases, usable
from app.data_pipeline.mapping.stop_mapper import build_stop_mapping, load_stop_aliases
from app.demand_engine.route_demand import HOLIDAYS, build_route_panel
from app.demand_engine.segment_demand import build_segment_tables

log = logging.getLogger(__name__)


def _step(name: str):
    log.info("== %s", name)
    return time.time()


def run(settings: Settings | None = None, skip_tickets: bool = False) -> dict:
    s = settings or get_settings()
    P = s.processed_dir
    P.mkdir(parents=True, exist_ok=True)
    s.mappings_dir.mkdir(parents=True, exist_ok=True)
    timings: dict[str, float] = {}

    t = _step("1. ticket ingestion")
    tickets_pq = P / "tickets_clean.parquet"
    ticket_summary = None
    if not (skip_tickets and tickets_pq.exists()):
        ticket_summary = load_tickets(s.tickets_csv, tickets_pq, s.crowdwise_bucket_minutes)
    timings["tickets"] = time.time() - t

    t = _step("2. GTFS ingestion")
    gtfs_report = load_gtfs(s.gtfs_dir, P)
    timings["gtfs"] = time.time() - t

    routes = pd.read_parquet(P / "gtfs_routes.parquet")
    stops = pd.read_parquet(P / "gtfs_stops.parquet")
    patterns = pd.read_parquet(P / "gtfs_patterns.parquet")
    con = duckdb.connect()

    t = _step("3. route mapping")
    ticket_routes = con.execute(f"SELECT DISTINCT route_raw FROM read_parquet('{tickets_pq.as_posix()}')").df()["route_raw"]
    rmap = build_route_mapping(ticket_routes, routes, load_aliases(s.mappings_dir / "route_aliases.csv"))
    rmap.to_csv(s.mappings_dir / "route_mapping.csv", index=False)
    rmap.to_parquet(P / "route_mapping.parquet", index=False)
    timings["route_mapping"] = time.time() - t

    t = _step("4. stop mapping (route-constrained)")
    con.register("rmap", usable(rmap)[["ticket_route", "gtfs_route_id"]])
    pairs = con.execute(f"""
        SELECT DISTINCT m.gtfs_route_id, x.stop_norm, x.stop_raw FROM (
            SELECT route_raw, start_stop_norm AS stop_norm, start_stop_raw AS stop_raw FROM read_parquet('{tickets_pq.as_posix()}')
            UNION SELECT route_raw, end_stop_norm, end_stop_raw FROM read_parquet('{tickets_pq.as_posix()}')
        ) x JOIN rmap m ON m.ticket_route = x.route_raw
    """).df()
    smap = build_stop_mapping(pairs, patterns, stops, load_stop_aliases(s.mappings_dir / "stop_aliases.csv"))
    smap.to_csv(s.mappings_dir / "stop_mapping.csv", index=False)
    smap.to_parquet(P / "stop_mapping.parquet", index=False)
    timings["stop_mapping"] = time.time() - t

    t = _step("5. OD direction resolution")
    con.register("smap", smap[smap["gtfs_stop_id"].notna()][["gtfs_route_id", "stop_raw", "gtfs_stop_id", "mapping_confidence"]])
    con.execute(f"""
        CREATE TABLE tk AS
        SELECT t.ticket_id, t.quality_status, m.gtfs_route_id,
               so.gtfs_stop_id AS o_stop_id, sd.gtfs_stop_id AS d_stop_id,
               least(so.mapping_confidence, sd.mapping_confidence) AS stop_conf
        FROM read_parquet('{tickets_pq.as_posix()}') t
        LEFT JOIN rmap m ON m.ticket_route = t.route_raw
        LEFT JOIN smap so ON so.gtfs_route_id = m.gtfs_route_id AND so.stop_raw = t.start_stop_raw
        LEFT JOIN smap sd ON sd.gtfs_route_id = m.gtfs_route_id AND sd.stop_raw = t.end_stop_raw
    """)
    od = con.execute("""SELECT DISTINCT gtfs_route_id, o_stop_id, d_stop_id FROM tk
                        WHERE o_stop_id IS NOT NULL AND d_stop_id IS NOT NULL AND o_stop_id <> d_stop_id""").df()
    od_res = resolve_od(od, patterns)
    con.register("od_res", od_res)
    con.execute(f"""
        COPY (
            SELECT tk.ticket_id, tk.gtfs_route_id, tk.o_stop_id, tk.d_stop_id, tk.stop_conf,
                   o.pattern_id, o.o_seq, o.d_seq,
                   CASE
                     WHEN tk.quality_status <> 'valid' THEN 'invalid_ticket'
                     WHEN tk.gtfs_route_id IS NULL THEN 'route_unmapped'
                     WHEN tk.o_stop_id IS NULL OR tk.d_stop_id IS NULL THEN 'stop_unmapped'
                     WHEN tk.o_stop_id = tk.d_stop_id THEN 'same_stop'
                     ELSE o.od_status
                   END AS od_status
            FROM tk LEFT JOIN od_res o USING (gtfs_route_id, o_stop_id, d_stop_id)
        ) TO '{(P / "ticket_resolution.parquet").as_posix()}' (FORMAT PARQUET)
    """)
    timings["od_resolution"] = time.time() - t

    t = _step("6. segment demand + supply")
    seg_stats = build_segment_tables(
        tickets_pq, P / "ticket_resolution.parquet", P / "gtfs_patterns.parquet",
        P / "gtfs_stop_times.parquet", P / "gtfs_trips.parquet", P,
        [d.isoformat() for d in HOLIDAYS], s.crowdwise_bucket_minutes,
    )
    timings["segments"] = time.time() - t

    t = _step("7. route demand panel")
    panel = build_route_panel(tickets_pq, s.crowdwise_bucket_minutes)
    panel.save(P / "route_panel.npz")
    timings["panel"] = time.time() - t

    t = _step("8. data quality report")
    from app.data_pipeline.validation.data_quality import build_report
    report = build_report(s, gtfs_report, seg_stats)
    timings["report"] = time.time() - t
    report["pipeline_seconds"] = {k: round(v, 1) for k, v in timings.items()}
    (P / "data_quality.json").write_text(json.dumps(report, indent=2, default=str))
    return report
