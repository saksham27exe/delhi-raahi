"""Fare engine: distance-slab fares per bus leg.

The GTFS `fare_stage` column is a 0/1 flag that is 0 for most stops and `stop_dist` is empty, so
fares come from ride distance along the stop pattern (straight line between consecutive stops).
Each bus needs its own ticket, so a journey's fare is the sum of its legs.
Bus type (AC / non-AC) is not in the GTFS; it is inferred per route from the share of AC e-tickets
(route_fare_profile.parquet, built by validate_fares). When unknown, both fares are returned.
"""
from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pandas as pd

from app.journey_engine.network import Network, haversine_m

RULES = json.loads((Path(__file__).with_name("fare_rules.json")).read_text(encoding="utf-8"))


def slab_fare(km: float, ac: bool, rules: dict = RULES) -> int:
    r = rules["ac" if ac else "non_ac"]
    return int(r["fares"][int(np.searchsorted(r["slabs_km"], km, side="left"))])  # "up to 4 km" includes 4.0


def bus_type_of(ac_share: float, rules: dict = RULES) -> str:
    cut = rules["bus_type_from_ticket_share"]
    return "ac" if ac_share >= cut["ac_if_at_least"] else "non_ac" if ac_share <= cut["non_ac_if_at_most"] else "mixed"


def digital_fare(fare: float, rules: dict = RULES) -> float:
    return round(fare * (100 - rules["digital_discount_pct"]) / 100, 2)


class FareService:
    def __init__(self, net: Network, processed_dir: Path | None = None, rules: dict = RULES):
        self.net, self.rules = net, rules
        self._cum: dict[int, np.ndarray] = {}
        self.pattern_index = {p.pattern_id: k for k, p in enumerate(net.patterns)}
        self.profile: dict[str, dict] = {}
        f = processed_dir / "route_fare_profile.parquet" if processed_dir else None
        if f is not None and f.exists():
            self.profile = pd.read_parquet(f).set_index("gtfs_route_id").to_dict("index")

    def cum_km(self, k: int) -> np.ndarray:
        if k not in self._cum:
            s = self.net.stops.loc[self.net.patterns[k].stops]
            lat, lon = s["stop_lat"].to_numpy(float), s["stop_lon"].to_numpy(float)
            seg = haversine_m(lat[:-1], lon[:-1], lat[1:], lon[1:]) / 1000
            self._cum[k] = np.r_[0.0, np.cumsum(seg)]
        return self._cum[k]

    def bus_type(self, route_id: str) -> tuple[str, float | None]:
        p = self.profile.get(str(route_id))
        if not p:
            return "unknown", None
        share = float(p["ac_share"])
        return bus_type_of(share, self.rules), share

    def leg_fare(self, k: int, board_pos: int, alight_pos: int) -> dict:
        p = self.net.patterns[k]
        cum = self.cum_km(k)
        km = float(cum[alight_pos] - cum[board_pos])
        btype, share = self.bus_type(p.route_id)
        non_ac, ac = slab_fare(km, False, self.rules), slab_fare(km, True, self.rules)
        fare = {"non_ac": non_ac, "ac": ac, "unknown": None, "mixed": None}[btype]
        prof = self.profile.get(str(p.route_id), {})
        nonstandard = prof.get("standard_share", 1.0) < self.rules["nonstandard_route_if_standard_share_below"]
        return {"route_id": str(p.route_id), "distance_km": round(km, 2), "bus_type": btype,
                "ac_ticket_share": None if share is None else round(share, 3),
                "fare": fare, "fare_digital": None if fare is None else digital_fare(fare, self.rules),
                "fare_non_ac": non_ac, "fare_ac": ac,
                "fare_non_ac_digital": digital_fare(non_ac, self.rules), "fare_ac_digital": digital_fare(ac, self.rules),
                "note": ("Interstate / special route: the actual fare can be higher than the city slab." if nonstandard else None)}

    def journey_fare(self, legs: list[dict]) -> dict:
        """legs: leg_fare() results. Totals per bus type; 'expected' uses each leg's known type,
        else its non-AC (low) and AC (high) fare as a range."""
        lo = sum(l["fare"] if l["fare"] is not None else l["fare_non_ac"] for l in legs)
        hi = sum(l["fare"] if l["fare"] is not None else l["fare_ac"] for l in legs)
        return {"currency": self.rules["currency"], "legs": legs,
                "total_min": lo, "total_max": hi,
                "total_digital_min": digital_fare(lo, self.rules), "total_digital_max": digital_fare(hi, self.rules),
                "tickets_needed": len(legs),
                "basis": "DTC/cluster distance slabs; digital = e-ticket price (10% off), as seen in ONDC data",
                "concessions": self.rules["concessions"]}


def validate_fares(net: Network, processed_dir: Path, metrics_dir: Path) -> dict:
    """Compare slab fares with what passengers actually paid (resolved e-tickets) and build the
    per-route bus-type profile."""
    import duckdb
    P = processed_dir
    con = duckdb.connect()
    t = con.execute(f"""
        select t.is_ac, t.amount / t.passenger_count as paid, r.gtfs_route_id, r.pattern_id,
               cast(r.o_seq as int) o, cast(r.d_seq as int) d
        from '{P / "ticket_resolution.parquet"}' r join '{P / "tickets_clean.parquet"}' t using (ticket_id)
        where r.od_status like 'resolved%' and t.passenger_count > 0""").df()
    fs = FareService(net, None)
    k = t["pattern_id"].map(fs.pattern_index)
    t = t[k.notna()].copy()
    t["k"] = k[k.notna()].astype(int)
    km = np.empty(len(t))
    for kk, idx in t.groupby("k").indices.items():
        c = fs.cum_km(kk)
        km[idx] = c[t["d"].to_numpy()[idx]] - c[t["o"].to_numpy()[idx]]
    t["km"] = km
    ac = t["is_ac"].to_numpy(bool)
    pred = np.where(ac, [digital_fare(slab_fare(x, True)) for x in km], [digital_fare(slab_fare(x, False)) for x in km])
    t["pred"] = pred
    std_units = {digital_fare(f) for f in RULES["ac"]["fares"] + RULES["non_ac"]["fares"]}
    t["standard"] = t["paid"].round(2).isin(std_units)

    def summ(df):
        return {"tickets": int(len(df)), "exact_match": round(float((df["pred"] == df["paid"]).mean()), 4),
                "within_one_slab": round(float((np.abs(df["pred"] - df["paid"]) <= 5).mean()), 4),
                "mean_abs_error_inr": round(float(np.abs(df["pred"] - df["paid"]).mean()), 3),
                "mean_error_inr": round(float((df["pred"] - df["paid"]).mean()), 3)}
    std = t[t["standard"]]
    report = {
        "method": "slab fare on straight-line stop-to-stop distance x 0.9 (digital) vs amount paid per passenger",
        "all_resolved_tickets": summ(t), "standard_fare_tickets": summ(std),
        "non_ac": summ(std[~std["is_ac"]]), "ac": summ(std[std["is_ac"]]),
        "nonstandard_fare_share": round(float(1 - t["standard"].mean()), 4),
        "note": "Non-standard amounts come from interstate routes (e.g. 8, 33, 34, 392, 443) with higher slabs; "
                "most remaining misses are rides close to a slab boundary (road distance > straight line).",
    }
    prof = t.groupby("gtfs_route_id").agg(ac_share=("is_ac", "mean"), standard_share=("standard", "mean"),
                                          tickets=("paid", "size")).reset_index()
    prof.to_parquet(P / "route_fare_profile.parquet", index=False)
    report["routes_profiled"] = int(len(prof))
    report["bus_type_counts"] = pd.Series([bus_type_of(s) for s in prof["ac_share"]]).value_counts().to_dict()
    metrics_dir.mkdir(parents=True, exist_ok=True)
    (metrics_dir / "fare_validation.json").write_text(json.dumps(report, indent=2))
    return report

