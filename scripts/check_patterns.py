"""Sanity checks of the crowding estimates against patterns everyone knows (Problem 2, deliverable 5).

    python scripts/check_patterns.py          # ~1 min; writes models/metrics/pattern_checks.json

1. Daily shape    - morning and evening peaks on weekdays, flatter weekends (tickets per hour).
2. Day types      - weekday vs Saturday vs Sunday vs public holiday volume.
3. Tidal flow     - on two-way routes, does the busier direction swap between the AM and PM peaks?
4. Busy corridors - the busiest stretches (stop pair, all routes together) in each peak.
5. Stability      - do estimates hold up from one week to the next? Correlation and crowd-level
                    agreement between consecutive weekday weeks, by amount of evidence.

Everything is computed from the processed tables of `run_pipeline.py`; nothing here feeds the model.
"""
import json
import math

import _bootstrap  # noqa: F401
import duckdb
import numpy as np

from app.core.config import get_settings
from app.demand_engine.route_demand import HOLIDAYS

S = get_settings()
P = S.processed_dir
HOL = ", ".join(f"DATE '{d}'" for d in HOLIDAYS)
# Full Monday-Friday weeks in the data (1 Oct 2026 is a Thursday); holidays are left out of every week.
WEEKS = [("2026-10-05", "2026-10-09"), ("2026-10-12", "2026-10-16"), ("2026-10-19", "2026-10-23"), ("2026-10-26", "2026-10-30")]
THRESH = json.loads((S.crowdwise_models_dir / "artifacts" / "segment_calibration.json").read_text())["category_thresholds"]
BUCKET_MIN = S.crowdwise_bucket_minutes
con = duckdb.connect()
con.execute("SET threads TO 4")


def q(sql: str):
    return con.execute(sql).fetchall()


def daytype_sql(col: str = "service_date") -> str:
    return (f"CASE WHEN {col} IN ({HOL}) THEN 'holiday' WHEN isodow({col}) = 7 THEN 'sunday' "
            f"WHEN isodow({col}) = 6 THEN 'saturday' ELSE 'weekday' END")


def daily_shape() -> dict:
    rows = q(f"""
        WITH t AS (SELECT service_date, hour, sum(passenger_count) AS pax FROM '{P}/tickets_clean.parquet'
                   WHERE quality_status = 'valid' GROUP BY 1, 2),
             d AS (SELECT {daytype_sql()} AS dt, count(DISTINCT service_date) AS days FROM '{P}/tickets_clean.parquet' GROUP BY 1)
        SELECT {daytype_sql()} AS dt, hour, sum(pax) / any_value(d.days) AS per_day
        FROM t JOIN d ON d.dt = {daytype_sql()} GROUP BY 1, 2 ORDER BY 1, 2""")
    out: dict = {}
    for dt, hour, v in rows:
        out.setdefault(dt, [0.0] * 24)[int(hour)] = round(float(v), 1)
    summary = {}
    for dt, prof in out.items():
        am = int(np.argmax(prof[5:12])) + 5
        pm = int(np.argmax(prof[14:22])) + 14
        midday = float(np.mean(prof[11:15]))
        summary[dt] = {"am_peak_hour": am, "pm_peak_hour": pm, "am_peak_vs_midday": round(prof[am] / midday, 2),
                       "pm_peak_vs_midday": round(prof[pm] / midday, 2), "per_hour": prof}
    return summary


def day_types() -> dict:
    rows = q(f"""SELECT {daytype_sql()} AS dt, count(DISTINCT service_date) AS days, sum(passenger_count) AS pax
                 FROM '{P}/tickets_clean.parquet' WHERE quality_status = 'valid' GROUP BY 1""")
    per_day = {dt: pax / days for dt, days, pax in rows}
    wk = per_day["weekday"]
    return {dt: {"days": days, "passengers_per_day": round(pax / days), "vs_weekday": round(pax / days / wk, 2)}
            for dt, days, pax in rows}


def tidal_flow(min_tickets: int = 200) -> dict:
    rows = q(f"""
        SELECT route_base, direction,
               sum(passenger_count) FILTER (WHERE hour BETWEEN 7 AND 9) AS am,
               sum(passenger_count) FILTER (WHERE hour BETWEEN 17 AND 19) AS pm
        FROM '{P}/tickets_clean.parquet'
        WHERE quality_status = 'valid' AND direction IN ('UP', 'DOWN') AND {daytype_sql()} = 'weekday'
        GROUP BY 1, 2""")
    by: dict = {}
    for r, d, am, pm in rows:
        by.setdefault(r, {})[d] = (am or 0, pm or 0)
    flips, strong_flips, n, strong_n = 0, 0, 0, 0
    for r, dirs in by.items():
        if len(dirs) != 2:
            continue
        (up_am, up_pm), (dn_am, dn_pm) = dirs["UP"], dirs["DOWN"]
        if min(up_am + dn_am, up_pm + dn_pm) < min_tickets:
            continue
        n += 1
        flip = (up_am > dn_am) != (up_pm > dn_pm)
        flips += flip
        am_share, pm_share = up_am / (up_am + dn_am), up_pm / (up_pm + dn_pm)
        if abs(am_share - 0.5) > 0.1 and abs(pm_share - 0.5) > 0.1:     # clearly one-sided in both peaks
            strong_n += 1
            strong_flips += flip
    return {"routes_checked": n, "min_tickets_per_peak": min_tickets, "busier_direction_swaps": flips,
            "share_swapping": round(flips / n, 3) if n else None,
            "routes_clearly_one_sided_in_both_peaks": strong_n,
            "share_swapping_among_one_sided": round(strong_flips / strong_n, 3) if strong_n else None,
            "peaks": "07:00-09:59 vs 17:00-19:59, weekdays without holidays"}


def corridors(top: int = 10) -> dict:
    out = {}
    for name, (h0, h1) in {"am_peak": (7, 10), "pm_peak": (17, 20)}.items():
        b0, b1 = h0 * 60 // BUCKET_MIN, h1 * 60 // BUCKET_MIN
        rows = q(f"""
            WITH d AS (SELECT segment_id, sum(pax) AS pax FROM '{P}/segment_demand.parquet'
                       WHERE bucket >= {b0} AND bucket < {b1} AND {daytype_sql()} = 'weekday' GROUP BY 1),
                 s AS (SELECT seg.from_stop_id, seg.to_stop_id, sum(d.pax) AS pax, count(DISTINCT seg.route_id) AS routes
                       FROM d JOIN '{P}/segments.parquet' seg USING (segment_id) GROUP BY 1, 2)
            SELECT a.stop_name, b.stop_name, s.pax, s.routes
            FROM s JOIN '{P}/gtfs_stops.parquet' a ON a.stop_id = s.from_stop_id
                   JOIN '{P}/gtfs_stops.parquet' b ON b.stop_id = s.to_stop_id
            ORDER BY s.pax DESC LIMIT {top}""")
        days = q(f"SELECT count(DISTINCT service_date) FROM '{P}/tickets_clean.parquet' WHERE {daytype_sql()} = 'weekday'")[0][0]
        out[name] = [{"from": a, "to": b, "eticket_passengers_per_weekday": round(pax / days), "routes": r} for a, b, pax, r in rows]
    return out


def level(dpd: np.ndarray) -> np.ndarray:
    return np.searchsorted(np.asarray(THRESH), dpd, side="right")


def stability() -> dict:
    con.execute(f"""CREATE OR REPLACE TEMP TABLE sup AS SELECT segment_id, bucket, departures FROM '{P}/segment_supply.parquet'
                    WHERE departures > 0""")
    pairs = []
    for i in range(len(WEEKS) - 1):
        (a0, a1), (b0, b1) = WEEKS[i], WEEKS[i + 1]
        df = con.execute(f"""
            WITH w AS (SELECT segment_id, bucket,
                              sum(pax) FILTER (WHERE service_date BETWEEN DATE '{a0}' AND DATE '{a1}') AS pa,
                              sum(pax) FILTER (WHERE service_date BETWEEN DATE '{b0}' AND DATE '{b1}') AS pb,
                              sum(tickets) AS support
                       FROM '{P}/segment_demand.parquet'
                       WHERE service_date BETWEEN DATE '{a0}' AND DATE '{b1}' AND service_date NOT IN ({HOL})
                         AND isodow(service_date) <= 5
                       GROUP BY 1, 2),
                 n AS (SELECT count(DISTINCT service_date) FILTER (WHERE service_date BETWEEN DATE '{a0}' AND DATE '{a1}') AS da,
                              count(DISTINCT service_date) FILTER (WHERE service_date BETWEEN DATE '{b0}' AND DATE '{b1}') AS db
                       FROM '{P}/tickets_clean.parquet' WHERE service_date NOT IN ({HOL}))
            SELECT coalesce(pa, 0) / n.da / sup.departures AS dpd_a, coalesce(pb, 0) / n.db / sup.departures AS dpd_b, support
            FROM w JOIN sup USING (segment_id, bucket), n""").df()
        res = {"weeks": [WEEKS[i], WEEKS[i + 1]], "segment_buckets": len(df)}
        bins = [(0, 10), (10, 30), (30, 100), (100, 300), (300, 10**9)]
        res["by_support"] = []
        for lo, hi in bins:
            d = df[(df.support >= lo) & (df.support < hi)]
            if len(d) < 50:
                continue
            la, lb = level(d.dpd_a.to_numpy()), level(d.dpd_b.to_numpy())
            res["by_support"].append({
                "tickets_in_both_weeks": f"[{lo}, {hi if hi < 10**9 else 'inf'})", "segment_buckets": len(d),
                "pearson_log": round(float(np.corrcoef(np.log1p(d.dpd_a), np.log1p(d.dpd_b))[0, 1]), 3),
                "same_crowd_level": round(float(np.mean(la == lb)), 3),
                "within_one_level": round(float(np.mean(np.abs(la - lb) <= 1)), 3)})
        la, lb = level(df.dpd_a.to_numpy()), level(df.dpd_b.to_numpy())
        w = df.support.to_numpy()
        res["all"] = {"pearson_log": round(float(np.corrcoef(np.log1p(df.dpd_a), np.log1p(df.dpd_b))[0, 1]), 3),
                      "same_crowd_level": round(float(np.mean(la == lb)), 3),
                      "same_crowd_level_ticket_weighted": round(float(np.average(la == lb, weights=w)), 3)}
        pairs.append(res)
    return {"unit": "segment x 30-min bucket, weekdays without holidays; crowd level from demand per scheduled departure "
                    "with the model's thresholds", "pairs": pairs}


if __name__ == "__main__":
    rep = {"daily_shape": daily_shape(), "day_types": day_types(), "tidal_flow": tidal_flow(),
           "busy_corridors": corridors(), "week_to_week": stability(),
           "holidays": {str(k): v for k, v in HOLIDAYS.items()}}
    out = S.metrics_dir / "pattern_checks.json"
    out.write_text(json.dumps(rep, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
    short = {k: v for k, v in rep["daily_shape"].items()}
    for v in short.values():
        v.pop("per_hour")
    print(json.dumps({"daily_shape": short, "day_types": rep["day_types"], "tidal_flow": rep["tidal_flow"],
                      "busy_corridors": rep["busy_corridors"], "week_to_week": rep["week_to_week"]}, indent=1, ensure_ascii=False))
    print("wrote", out)
