"""Resolve each ticket OD pair to an ordered path on a GTFS route pattern.

A ticket (route r, origin o, destination d) is *resolved* when some stop
pattern of r visits o before d. Outcomes (od_status):
  resolved_primary     found on the route's most-used pattern
  resolved_other       found only on a secondary pattern (short-turn, variant)
  reverse_order        both stops are on the route but d comes before o on every
                       pattern (wrong UP/DOWN label, or a loop) -> audit only
  stop_unmapped        origin or destination could not be mapped to a stop
  route_unmapped       the ticket route has no usable GTFS route
"""
from __future__ import annotations

import duckdb
import pandas as pd


def resolve_od(od: pd.DataFrame, patterns: pd.DataFrame) -> pd.DataFrame:
    """od: distinct (gtfs_route_id, o_stop_id, d_stop_id). Returns od + pattern_id, o_seq, d_seq, od_status."""
    con = duckdb.connect()
    con.register("od", od)
    con.register("pat", patterns[["pattern_id", "route_id", "seq", "stop_id", "n_trips", "is_primary"]])
    res = con.execute(
        """
        WITH cand AS (
            SELECT od.gtfs_route_id, od.o_stop_id, od.d_stop_id,
                   po.pattern_id, po.is_primary, po.n_trips,
                   min(po.seq) AS o_seq,
                   -- first occurrence of d after o (handles loops visiting a stop twice)
                   min(pd.seq) FILTER (WHERE pd.seq > po.seq) AS d_seq_after,
                   min(pd.seq) AS d_seq_any
            FROM od
            JOIN pat po ON po.route_id = od.gtfs_route_id AND po.stop_id = od.o_stop_id
            JOIN pat pd ON pd.pattern_id = po.pattern_id AND pd.stop_id = od.d_stop_id
            GROUP BY ALL
        ),
        ranked AS (
            SELECT *, row_number() OVER (
                PARTITION BY gtfs_route_id, o_stop_id, d_stop_id
                ORDER BY (d_seq_after IS NOT NULL) DESC, is_primary DESC, n_trips DESC, pattern_id
            ) AS rk
            FROM cand
        )
        SELECT od.*, r.pattern_id, r.o_seq, r.d_seq_after AS d_seq,
               CASE
                 WHEN r.pattern_id IS NULL THEN 'stops_not_on_same_pattern'
                 WHEN r.d_seq_after IS NULL THEN 'reverse_order'
                 WHEN r.is_primary THEN 'resolved_primary'
                 ELSE 'resolved_other'
               END AS od_status
        FROM od
        LEFT JOIN ranked r ON r.rk = 1
            AND r.gtfs_route_id = od.gtfs_route_id
            AND r.o_stop_id = od.o_stop_id AND r.d_stop_id = od.d_stop_id
        """
    ).df()
    res.loc[res["od_status"] == "reverse_order", ["pattern_id", "o_seq", "d_seq"]] = None
    return res
