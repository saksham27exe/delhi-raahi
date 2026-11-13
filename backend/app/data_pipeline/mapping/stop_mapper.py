"""Map ticket stop names to GTFS stop_ids, constrained to the stops the mapped route serves.

Constraining to the route is what makes name matching defensible: Delhi has many
stops sharing a name (both kerbs of a road, or same-named places in different
districts), but along one directed route a name almost always identifies one stop.

Methods (confidence):
  exact_on_route         normalised names equal, unique on the route            (1.00)
  exact_on_route_multi   equal name appears >1 time on route (loop) - first kept (0.70)
  fuzzy_on_route         token_sort_ratio >= 90 against the route's stops      (score/100)
  compact_on_route       equal after removing spaces ('shadi pur' = 'shadipur') (0.95)
  compact_fuzzy_on_route space-free ratio >= 88 ('begam pur' ~ 'begumpur')      (score*0.9)
  subset_on_route        one name's words contained in the other's, unique    (0.85)
  subset_on_route_colocated  several such stops, all within COLOCATED_M      (0.75)
  located_by_name_nearest_on_route  name exists elsewhere in GTFS; the route's
                         nearest stop within LOCATED_MAX_M of it               (0.60)
  unmatched              recorded in the audit table, never forced
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
import pandas as pd
from rapidfuzz import fuzz, process

from app.data_pipeline.mapping.normalize import name_variants, normalize_stop_name
from app.journey_engine.network import haversine_m

FUZZY_MIN = 90
COMPACT_FUZZY_MIN = 88
COLOCATED_M = 600
LOCATED_MAX_M = 400


def load_stop_aliases(path: Path) -> dict[str, str]:
    """Reviewed ticket-name -> GTFS-name aliases (data/mappings/stop_aliases.csv), normalised."""
    if not path.exists():
        return {}
    df = pd.read_csv(path, dtype=str, comment="#")
    return {normalize_stop_name(a): normalize_stop_name(b) for a, b in zip(df["ticket_name"], df["gtfs_name"])}


def build_stop_mapping(pairs: pd.DataFrame, patterns: pd.DataFrame, stops: pd.DataFrame,
                       aliases: dict[str, str] | None = None) -> pd.DataFrame:
    """pairs: distinct (gtfs_route_id, stop_norm, stop_raw) seen in tickets.

    GTFS names are indexed by every variant ('A / B' -> 'a / b', 'a', 'b').
    Returns columns: gtfs_route_id, stop_norm, stop_raw, gtfs_stop_id, gtfs_stop_name,
    mapping_method, mapping_confidence
    """
    aliases = aliases or {}
    st = stops[["stop_id", "stop_name", "stop_lat", "stop_lon"]].copy()
    st["stop_norm"] = st["stop_name"].map(name_variants)
    st = st.explode("stop_norm").dropna(subset=["stop_norm"])
    route_stops = (
        patterns[["route_id", "stop_id"]].drop_duplicates()
        .merge(st, on="stop_id", how="left")
    )
    lookup: dict[str, pd.DataFrame] = {rid: g for rid, g in route_stops.groupby("route_id")}
    # city-wide name index for the 'located by name' fallback
    city = st.dropna(subset=["stop_lat", "stop_lon"]).groupby("stop_norm")[["stop_lat", "stop_lon"]].apply(
        lambda g: g.to_numpy()).to_dict()

    out = []
    for rid, grp in pairs.groupby("gtfs_route_id"):
        rs = lookup.get(rid)
        if rs is None:
            for _, r in grp.iterrows():
                out.append((rid, r.stop_norm, r.stop_raw, None, None, "route_has_no_stops", 0.0))
            continue
        name_to_ids = rs.groupby("stop_norm")["stop_id"].apply(lambda s: list(dict.fromkeys(s))).to_dict()
        names = list(name_to_ids.keys())
        id_to_name = dict(zip(rs["stop_id"], rs["stop_name"]))
        coords = rs.set_index("stop_id")[["stop_lat", "stop_lon"]]
        coords = coords[~coords.index.duplicated()]
        for _, r in grp.iterrows():
            norm = aliases.get(r.stop_norm, r.stop_norm)
            res = _match_one(norm, name_to_ids, names, id_to_name, coords, city)
            if norm != r.stop_norm and res[0] is not None:
                res = (res[0], res[1], "alias_" + res[2], round(min(res[3], 0.9), 3))
            out.append((rid, r.stop_norm, r.stop_raw, *res))
    return pd.DataFrame(out, columns=["gtfs_route_id", "stop_norm", "stop_raw", "gtfs_stop_id",
                                      "gtfs_stop_name", "mapping_method", "mapping_confidence"])


GENERIC_TOKENS = {"terminal", "station", "metro", "village", "market", "chowk", "colony", "nagar", "vihar",
                  "sector", "block", "road", "marg", "depot", "hospital", "school", "park", "gate", "border"}


def _is_subset_match(a: set[str], b: set[str]) -> bool:
    """One token set contains the other and they share at least one non-generic word."""
    if not (a <= b or b <= a):
        return False
    return bool((a & b) - GENERIC_TOKENS)


def _colocated(ids: list[str], coords: pd.DataFrame) -> bool:
    xy = coords.loc[ids].to_numpy()
    if len(xy) < 2:
        return True
    d = haversine_m(xy[:, None, 0], xy[:, None, 1], xy[None, :, 0], xy[None, :, 1])
    return float(np.nanmax(d)) <= COLOCATED_M


def _match_one(norm: str, name_to_ids: dict, names: list[str], id_to_name: dict,
               coords: pd.DataFrame, city: dict) -> tuple:
    """Returns (stop_id, stop_name, method, confidence) - see module docstring for the ladder."""
    if not norm:
        return None, None, "unmatched", 0.0
    ids = name_to_ids.get(norm)
    if ids:
        if len(ids) == 1:
            return ids[0], id_to_name[ids[0]], "exact_on_route", 1.0
        return ids[0], id_to_name[ids[0]], "exact_on_route_multi", 0.7
    best = process.extractOne(norm, names, scorer=fuzz.token_sort_ratio) if names else None
    if best and best[1] >= FUZZY_MIN:
        sid = name_to_ids[best[0]][0]
        return sid, id_to_name[sid], "fuzzy_on_route", round(best[1] / 100, 3)
    # space-insensitive: 'shadi pur metro station' == 'shadipur metro station', 'tb hospital' == 't b hospital'
    comp = norm.replace(" ", "")
    compact = {n.replace(" ", ""): n for n in names}
    if comp in compact:
        sid = name_to_ids[compact[comp]][0]
        return sid, id_to_name[sid], "compact_on_route", 0.95
    cbest = process.extractOne(comp, list(compact), scorer=fuzz.ratio) if len(comp) >= 7 and compact else None
    if cbest and cbest[1] >= COMPACT_FUZZY_MIN:
        sid = name_to_ids[compact[cbest[0]]][0]
        return sid, id_to_name[sid], "compact_fuzzy_on_route", round(cbest[1] / 100 * 0.9, 3)
    # one name's words fully contained in the other's ('badarpur border' <-> 'badarpur border terminal')
    tok = set(norm.split())
    if len(norm) >= 4:
        sub = [n for n in names if _is_subset_match(tok, set(n.split()))]
        cand = [i for n in sub for i in name_to_ids[n]]
        if cand and _colocated(cand, coords):
            method = "subset_on_route" if len(sub) == 1 else "subset_on_route_colocated"
            return cand[0], id_to_name[cand[0]], method, 0.85 if len(sub) == 1 else 0.75
    # name exists elsewhere in the city: take the route's nearest stop if close enough
    pts = city.get(norm)
    if pts is not None and len(coords):
        rxy = coords.to_numpy()
        d = haversine_m(pts[:, None, 0], pts[:, None, 1], rxy[None, :, 0], rxy[None, :, 1])
        i, j = np.unravel_index(np.nanargmin(d), d.shape)
        if d[i, j] <= LOCATED_MAX_M:
            sid = coords.index[j]
            return sid, id_to_name[sid], "located_by_name_nearest_on_route", 0.6
    return None, None, "unmatched", 0.0
