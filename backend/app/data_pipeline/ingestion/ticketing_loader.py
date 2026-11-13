"""Load the ONDC e-ticketing CSV into a cleaned, typed Parquet file.

Design rules (see docs/assumptions.md):
* raw values are preserved next to normalised values;
* nothing is silently imputed (passenger_count is never defaulted to 1);
* rows are flagged, not dropped, so the data-quality report can count them;
* timestamps are parsed as timezone-aware and reported in Asia/Kolkata.
"""
from __future__ import annotations

import logging
from pathlib import Path

import duckdb
import pandas as pd

from app.core.config import TIMEZONE
from app.data_pipeline.mapping.normalize import normalize_stop_name, split_route_direction

log = logging.getLogger(__name__)

SOURCE_ID = "ondc_eticket_2026_10"
EXPECTED_COLUMNS = [
    "passenger_count",
    "amount",
    "vehicle_number",
    "ticket_type_id",
    "end_stop_name",
    "route",
    "start_stop_name",
    "created_at",
    "is_ac",
]
MAX_PLAUSIBLE_PASSENGERS = 10  # a single QR ticket covering >10 people is treated as implausible


class SchemaError(ValueError):
    pass


def validate_columns(csv_path: Path, con: duckdb.DuckDBPyConnection) -> list[str]:
    cols = [r[0] for r in con.execute(
        "SELECT column_name FROM (DESCRIBE SELECT * FROM read_csv(?, header=true, all_varchar=true))",
        [str(csv_path)],
    ).fetchall()]
    missing = [c for c in EXPECTED_COLUMNS if c not in cols]
    if missing:
        raise SchemaError(f"{csv_path.name}: missing expected columns {missing}; found {cols}")
    return cols


def load_tickets(csv_path: Path, out_path: Path, bucket_minutes: int = 30) -> dict:
    """Clean the ticket CSV and write Parquet. Returns summary counts."""
    if not csv_path.exists():
        raise FileNotFoundError(
            f"Ticketing CSV not found at {csv_path}. Download October-Delhi.csv.zip "
            "(see README 'Data') and unzip it into data/raw/."
        )
    out_path.parent.mkdir(parents=True, exist_ok=True)
    con = duckdb.connect()
    con.execute(f"SET TimeZone='{TIMEZONE}'")
    validate_columns(csv_path, con)

    con.execute(
        """
        CREATE TABLE raw AS
        SELECT row_number() OVER () AS ticket_id, *
        FROM read_csv(?, header=true, all_varchar=true, nullstr=['NULL', ''])
        """,
        [str(csv_path)],
    )

    # Normalise distinct names in Python (only ~7k distinct values)
    stops = con.execute(
        "SELECT DISTINCT start_stop_name FROM raw UNION SELECT DISTINCT end_stop_name FROM raw"
    ).df()
    stops.columns = ["stop_raw"]
    stops["stop_norm"] = stops["stop_raw"].map(normalize_stop_name)
    routes = con.execute("SELECT DISTINCT route AS route_raw FROM raw").df()
    parts = routes["route_raw"].map(split_route_direction)
    routes["route_base"] = parts.map(lambda p: p[0])
    routes["direction"] = parts.map(lambda p: p[1])
    con.register("stop_norm_df", stops)
    con.register("route_norm_df", routes)

    con.execute(
        f"""
        CREATE TABLE clean AS
        WITH typed AS (
            SELECT
                r.ticket_id,
                '{SOURCE_ID}' AS source,
                r.passenger_count AS passenger_count_raw,
                TRY_CAST(r.passenger_count AS INTEGER) AS pc_int,
                TRY_CAST(r.amount AS DOUBLE) AS amount,
                NULLIF(upper(trim(r.vehicle_number)), '') AS vehicle_number,
                TRY_CAST(r.ticket_type_id AS INTEGER) AS ticket_type_id,
                CASE lower(r.is_ac) WHEN 'true' THEN TRUE WHEN 'false' THEN FALSE END AS is_ac,
                r.route AS route_raw,
                rn.route_base,
                rn.direction,
                r.start_stop_name AS start_stop_raw,
                r.end_stop_name AS end_stop_raw,
                s1.stop_norm AS start_stop_norm,
                s2.stop_norm AS end_stop_norm,
                TRY_CAST(r.created_at AS TIMESTAMPTZ) AS created_at
            FROM raw r
            LEFT JOIN route_norm_df rn ON rn.route_raw = r.route
            LEFT JOIN stop_norm_df s1 ON s1.stop_raw = r.start_stop_name
            LEFT JOIN stop_norm_df s2 ON s2.stop_raw = r.end_stop_name
        )
        SELECT
            * EXCLUDE (pc_int),
            CASE WHEN pc_int BETWEEN 1 AND {MAX_PLAUSIBLE_PASSENGERS} THEN pc_int END AS passenger_count,
            -- local (IST) wall-clock fields
            CAST(created_at AS DATE) AS service_date,
            hour(created_at) AS hour,
            minute(created_at) AS minute,
            isodow(created_at) - 1 AS dow,               -- 0=Mon .. 6=Sun
            isodow(created_at) >= 6 AS is_weekend,
            (hour(created_at) * 60 + minute(created_at)) // {bucket_minutes} AS bucket,
            CASE
                WHEN pc_int IS NULL THEN 'invalid_passenger_count'
                WHEN pc_int < 1 OR pc_int > {MAX_PLAUSIBLE_PASSENGERS} THEN 'implausible_passenger_count'
                WHEN created_at IS NULL THEN 'invalid_timestamp'
                WHEN route_raw IS NULL THEN 'missing_route'
                WHEN start_stop_raw IS NULL OR end_stop_raw IS NULL THEN 'missing_stop'
                WHEN start_stop_norm = end_stop_norm THEN 'same_origin_destination'
                ELSE 'valid'
            END AS quality_status
        FROM typed
        """
    )
    con.execute(
        "COPY (SELECT * FROM clean ORDER BY created_at) TO ? (FORMAT PARQUET, COMPRESSION ZSTD)",
        [str(out_path)],
    )
    summary = con.execute(
        "SELECT quality_status, count(*) AS n FROM clean GROUP BY 1 ORDER BY 2 DESC"
    ).df()
    total = int(summary["n"].sum())
    log.info("tickets: %s rows written to %s", total, out_path)
    return {"total": total, "by_status": dict(zip(summary["quality_status"], summary["n"].astype(int)))}


def read_tickets(path: Path, columns: list[str] | None = None) -> pd.DataFrame:
    return pd.read_parquet(path, columns=columns)
