"""Crowd views for the app: a route through the day, a route along its stops, network hotspots."""
from __future__ import annotations

from datetime import date

import numpy as np

from app.journey_engine.network import Network
from app.services.crowd_service import CATEGORIES, CrowdService


def primary_pattern(net: Network, route_id: str) -> int | None:
    ks = [k for k, p in enumerate(net.patterns) if str(p.route_id) == str(route_id)]
    return max(ks, key=lambda k: len(net.patterns[k].trip_ids)) if ks else None


def _hhmm(bucket: int, minutes: int) -> str:
    m = bucket * minutes
    return f"{m // 60:02d}:{m % 60:02d}"


def route_day_profile(net: Network, crowd: CrowdService, route_id: str, d: date) -> dict | None:
    """Per time bucket: share of the route's segments in each crowd category, mean crowd index and
    the busiest segment — answers 'when is this bus least crowded?'."""
    k = primary_pattern(net, route_id)
    if k is None:
        return None
    p = net.patterns[k]
    segs = [f"{p.route_id}:{a}>{b}" for a, b in zip(p.stops[:-1], p.stops[1:])]
    B = 1440 // crowd.bucket_minutes
    rows = []
    for b in range(B):
        preds = [crowd.predict(s, d, b) for s in segs]
        known = [x for x in preds if x.crowd_index is not None]
        if not any(x.scheduled_departures for x in preds):
            continue                                   # no bus in this half hour
        if not known:
            rows.append({"bucket": b, "time": _hhmm(b, crowd.bucket_minutes), "category": "UNKNOWN",
                         "mean_crowd_index": None, "busiest_segment": None, "share": None})
            continue
        ci = float(np.mean([x.crowd_index for x in known]))
        top = max(known, key=lambda x: x.value)
        a, bb = top.segment_id.split(":", 1)[1].split(">")
        share = {c: round(sum(x.category == c for x in known) / len(known), 3) for c in CATEGORIES}
        rows.append({"bucket": b, "time": _hhmm(b, crowd.bucket_minutes),
                     "category": CATEGORIES[int(np.searchsorted([0.5, 0.8, 0.95], ci, side="right"))],
                     "peak_category": top.category, "mean_crowd_index": round(ci, 3),
                     "busiest_segment": f"{net.stop_name(a)} → {net.stop_name(bb)}", "share": share,
                     "evidence": max(known, key=lambda x: x.support).evidence})
    known_rows = [r for r in rows if r["mean_crowd_index"] is not None]
    quiet = min(known_rows, key=lambda r: r["mean_crowd_index"]) if known_rows else None
    busy = max(known_rows, key=lambda r: r["mean_crowd_index"]) if known_rows else None
    return {"route_id": str(p.route_id), "date": d.isoformat(), "pattern_stops": len(p.stops), "buckets": rows,
            "quietest": quiet and quiet["time"], "busiest": busy and busy["time"]}


def route_along(net: Network, crowd: CrowdService, route_id: str, d: date, bucket: int) -> dict | None:
    k = primary_pattern(net, route_id)
    if k is None:
        return None
    p = net.patterns[k]
    out = []
    for a, b in zip(p.stops[:-1], p.stops[1:]):
        x = crowd.predict(f"{p.route_id}:{a}>{b}", d, bucket)
        out.append({"from_stop_id": a, "from_stop": net.stop_name(a), "to_stop_id": b, "to_stop": net.stop_name(b),
                    "lat": float(net.stops.at[a, "stop_lat"]), "lon": float(net.stops.at[a, "stop_lon"]),
                    "to_lat": float(net.stops.at[b, "stop_lat"]), "to_lon": float(net.stops.at[b, "stop_lon"]),
                    "category": x.category, "crowd_index": None if x.crowd_index is None else round(x.crowd_index, 3),
                    "dpd": None if x.value is None else round(x.value, 3), "evidence": x.evidence})
    return {"route_id": str(p.route_id), "date": d.isoformat(), "time": _hhmm(bucket, crowd.bucket_minutes),
            "segments": out}


def hotspots(net: Network, crowd: CrowdService, d: date, bucket: int, limit: int = 50,
             min_support: int | None = None) -> list[dict]:
    """Busiest segments network-wide at one time, restricted to well-supported estimates."""
    a = crowd.predict_all(d, bucket)
    min_support = crowd.evidence_cut["MEDIUM"] if min_support is None else min_support
    v = np.where((a["support"] >= min_support) & (a["fallback_level"] == 1), a["value"], np.nan)
    order = np.argsort(np.nan_to_num(-v, nan=np.inf))[:limit]
    ids = np.asarray(list(crowd.seg_index.keys()))
    out = []
    for i in order:
        if np.isnan(v[i]):
            break
        sid = str(ids[i])
        rid, ab = sid.split(":", 1)
        s_a, s_b = ab.split(">")
        if s_a not in net.stops.index or s_b not in net.stops.index:
            continue
        r = net.routes.loc[rid] if rid in net.routes.index else None
        out.append({"segment_id": sid, "route_id": rid, "route": None if r is None else str(r["route_short_name"]),
                    "from_stop": net.stop_name(s_a), "to_stop": net.stop_name(s_b),
                    "lat": float(net.stops.at[s_a, "stop_lat"]), "lon": float(net.stops.at[s_a, "stop_lon"]),
                    "to_lat": float(net.stops.at[s_b, "stop_lat"]), "to_lon": float(net.stops.at[s_b, "stop_lon"]),
                    "dpd": round(float(v[i]), 3), "category": crowd.category(float(v[i])),
                    "crowd_index": round(crowd.crowd_index(float(v[i])), 3), "support": int(a["support"][i])})
    return out
