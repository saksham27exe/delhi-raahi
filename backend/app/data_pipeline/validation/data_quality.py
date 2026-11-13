"""Automated data-quality / coverage report (JSON + CSV), also served at /api/v1/analytics/data-quality."""
from __future__ import annotations

import duckdb
import pandas as pd

from app.core.config import Settings


def fare_consistency(processed_dir) -> list[dict]:
    """Independent check of stop matching: Delhi bus fares are stage-based, so for correctly matched
    tickets the fare must rise with the matched ride length. Compare that relationship per match
    method (weakest method of the two endpoints); a wrong method shows a weaker correlation."""
    P = processed_dir
    order = ["exact_on_route", "alias_exact_on_route", "compact_on_route", "exact_on_route_multi", "fuzzy_on_route",
             "compact_fuzzy_on_route", "subset_on_route", "subset_on_route_colocated", "located_by_name_nearest_on_route"]
    rank_sql = "CASE " + " ".join(f"WHEN m = '{m}' THEN {i}" for i, m in enumerate(order)) + " ELSE 99 END"
    con = duckdb.connect()
    df = con.execute(f"""
        WITH sm AS (SELECT gtfs_route_id, stop_raw, mapping_method AS m, {rank_sql} AS rk
                    FROM read_parquet('{(P / "stop_mapping.parquet").as_posix()}')),
        pat AS (SELECT pattern_id, seq, offset_s FROM read_parquet('{(P / "gtfs_patterns.parquet").as_posix()}')),
        x AS (
            SELECT t.amount, r.d_seq - r.o_seq AS hops, (pd.offset_s - po.offset_s) / 60 AS ride_min,
                   CASE WHEN so.rk >= sd.rk THEN so.m ELSE sd.m END AS weakest_method
            FROM read_parquet('{(P / "tickets_clean.parquet").as_posix()}') t
            JOIN read_parquet('{(P / "ticket_resolution.parquet").as_posix()}') r USING (ticket_id)
            JOIN sm so ON so.gtfs_route_id = r.gtfs_route_id AND so.stop_raw = t.start_stop_raw
            JOIN sm sd ON sd.gtfs_route_id = r.gtfs_route_id AND sd.stop_raw = t.end_stop_raw
            JOIN pat po ON po.pattern_id = r.pattern_id AND po.seq = r.o_seq
            JOIN pat pd ON pd.pattern_id = r.pattern_id AND pd.seq = r.d_seq
            WHERE r.od_status LIKE 'resolved%' AND t.is_ac
        )
        SELECT weakest_method, count(*) AS tickets,
               round(corr(amount, hops), 3) AS corr_fare_vs_stops,
               round(corr(amount, ride_min), 3) AS corr_fare_vs_ride_minutes,
               median(hops) FILTER (WHERE amount = 9) AS median_stops_at_rs9,
               median(hops) FILTER (WHERE amount = 13.5) AS median_stops_at_rs13_5,
               median(hops) FILTER (WHERE amount = 18) AS median_stops_at_rs18
        FROM x GROUP BY 1 ORDER BY tickets DESC
    """).df()
    return df.to_dict("records")


def build_report(s: Settings, gtfs_report: dict, seg_stats: dict) -> dict:
    P = s.processed_dir
    con = duckdb.connect()
    q = lambda sql: con.execute(sql).df()  # noqa: E731
    tk = (P / "tickets_clean.parquet").as_posix()
    res = (P / "ticket_resolution.parquet").as_posix()

    t = q(f"""
        SELECT count(*) AS total_records,
               count(*) FILTER (WHERE quality_status = 'valid') AS valid_records,
               count(*) FILTER (WHERE quality_status <> 'valid') AS invalid_records,
               count(*) FILTER (WHERE vehicle_number IS NULL) AS missing_vehicle_number,
               count(*) FILTER (WHERE passenger_count IS NULL) AS missing_or_invalid_passenger_count,
               count(*) FILTER (WHERE created_at IS NULL) AS invalid_timestamps,
               count(*) - count(DISTINCT (passenger_count_raw, amount, vehicle_number, ticket_type_id,
                     start_stop_raw, end_stop_raw, route_raw, created_at, is_ac)) AS exact_duplicate_records,
               sum(passenger_count) AS passengers,
               min(created_at)::VARCHAR AS first_ticket, max(created_at)::VARCHAR AS last_ticket,
               count(DISTINCT route_raw) AS distinct_routes,
               count(DISTINCT start_stop_raw) AS distinct_boarding_stop_names
        FROM read_parquet('{tk}')
    """).iloc[0].to_dict()
    by_status = q(f"SELECT quality_status, count(*) n FROM read_parquet('{tk}') GROUP BY 1 ORDER BY 2 DESC")
    od = q(f"SELECT od_status, count(*) AS tickets FROM read_parquet('{res}') GROUP BY 1 ORDER BY 2 DESC")
    rm = pd.read_parquet(P / "route_mapping.parquet")
    sm = pd.read_parquet(P / "stop_mapping.parquet")
    route_weight = q(f"SELECT route_raw AS ticket_route, count(*) AS tickets FROM read_parquet('{tk}') GROUP BY 1")
    rmw = rm.merge(route_weight, on="ticket_route", how="left")
    route_cov = rmw.groupby("mapping_method").agg(routes=("ticket_route", "size"), tickets=("tickets", "sum")).reset_index()
    stop_cov = sm.groupby("mapping_method").size().rename("route_stop_names").reset_index()
    unmapped_routes = rmw[rmw["gtfs_route_id"].isna()].sort_values("tickets", ascending=False).head(25)
    unmapped_stops = sm[sm["gtfs_stop_id"].isna()].head(50)

    veh = q(f"""
        SELECT is_ac, round(100.0 * count(*) FILTER (WHERE vehicle_number IS NULL) / count(*), 1) AS pct_missing_vehicle,
               count(*) AS tickets
        FROM read_parquet('{tk}') GROUP BY 1
    """)
    total = int(t["total_records"])
    seg_usable = int(od.loc[od["od_status"].isin(["resolved_primary", "resolved_other"]), "tickets"].sum())

    # CSV companions for spreadsheet users
    s.mappings_dir.mkdir(parents=True, exist_ok=True)
    unmapped_routes.to_csv(s.mappings_dir / "unmapped_routes_top.csv", index=False)
    sm[sm["gtfs_stop_id"].isna()].to_csv(s.mappings_dir / "unmapped_stops.csv", index=False)
    od.to_csv(P / "data_quality_od_status.csv", index=False)

    return {
        "sources": [{"source": "ondc_eticket_2026_10", "file": s.tickets_csv.name,
                     "note": "ONDC e-ticketing only; not all ETM/paper tickets. No separate ETM file was supplied."}],
        "tickets": {k: (int(v) if hasattr(v, "__int__") and not isinstance(v, str) else v) for k, v in t.items()},
        "tickets_by_quality_status": dict(zip(by_status["quality_status"], by_status["n"].astype(int))),
        "vehicle_number_coverage": veh.to_dict("records"),
        "gtfs": gtfs_report,
        "route_mapping": route_cov.to_dict("records"),
        "stop_mapping": stop_cov.to_dict("records"),
        "od_resolution": od.to_dict("records"),
        "records_usable_for_segment_analysis": seg_usable,
        "pct_usable_for_segment_analysis": round(100 * seg_usable / total, 2) if total else 0,
        "records_usable_for_vehicle_analysis": int(total - t["missing_vehicle_number"]),
        "segments": seg_stats,
        "fare_consistency_ac_tickets": fare_consistency(P),
        "top_unmapped_routes": unmapped_routes[["ticket_route", "tickets", "mapping_method", "candidates"]].to_dict("records"),
        "sample_unmapped_stops": unmapped_stops[["gtfs_route_id", "stop_raw"]].head(20).to_dict("records"),
    }
