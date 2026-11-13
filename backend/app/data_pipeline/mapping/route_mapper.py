"""Map ticketing route designations (e.g. '717AUP') to GTFS route_id.

Methods, in order of preference (each recorded with a confidence):
  exact_short_name   normalised ticket route == normalised route_short_name   (1.00)
  exact_long_name    normalised ticket route == normalised route_long_name    (0.95)
  alias              manual alias table data/mappings/route_aliases.csv       (0.90, verified)
  fuzzy_candidate    best RapidFuzz match >= 92, recorded but NOT used        (score/100)
Ambiguity (one designation -> several route_ids) is recorded, never resolved silently.
"""
from __future__ import annotations

from pathlib import Path

import pandas as pd
from rapidfuzz import fuzz, process

from app.data_pipeline.mapping.normalize import normalize_route, split_route_direction

FUZZY_CANDIDATE_MIN = 92


def load_aliases(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    df = pd.read_csv(path, dtype=str)
    return {normalize_route(a): normalize_route(b) for a, b in zip(df["ticket_route"], df["gtfs_route_short_name"])}


def build_route_mapping(ticket_routes: pd.Series, gtfs_routes: pd.DataFrame, aliases: dict[str, str] | None = None) -> pd.DataFrame:
    """ticket_routes: distinct raw route strings. Returns one row per ticket route."""
    aliases = aliases or {}
    gr = gtfs_routes.copy()
    gr["short_norm"] = gr["route_short_name"].map(normalize_route)
    gr["long_norm"] = gr.get("route_long_name", pd.Series("", index=gr.index)).map(normalize_route)
    by_short = gr.groupby("short_norm")["route_id"].apply(list).to_dict()
    by_long = gr.groupby("long_norm")["route_id"].apply(list).to_dict()
    choices = list(by_short.keys())

    rows = []
    for raw in pd.Series(ticket_routes).dropna().unique():
        norm = normalize_route(raw)
        base, direction = split_route_direction(raw)
        rec = {"ticket_route": raw, "ticket_route_norm": norm, "route_key": base + (direction or ""),
               "gtfs_route_id": None,
               "route_short_name": None, "mapping_method": "unmapped", "mapping_confidence": 0.0,
               "candidates": "", "verified": False}
        target = None
        if norm in by_short:
            target, rec["mapping_method"], rec["mapping_confidence"] = by_short[norm], "exact_short_name", 1.0
        elif norm in by_long:
            target, rec["mapping_method"], rec["mapping_confidence"] = by_long[norm], "exact_long_name", 0.95
        elif norm in aliases and aliases[norm] in by_short:
            target, rec["mapping_method"], rec["mapping_confidence"] = by_short[aliases[norm]], "alias", 0.9
            rec["verified"] = True
        if target is not None:
            if len(target) > 1:
                rec["mapping_method"] += "_ambiguous"
                rec["candidates"] = ";".join(map(str, target))
                rec["mapping_confidence"] *= 0.5
            rec["gtfs_route_id"] = str(target[0])
        elif choices:
            best = process.extract(norm, choices, scorer=fuzz.ratio, limit=3)
            good = [(c, s) for c, s, _ in best if s >= FUZZY_CANDIDATE_MIN]
            if good:
                rec["mapping_method"] = "fuzzy_candidate_unverified"
                rec["mapping_confidence"] = round(good[0][1] / 100, 3)
                rec["candidates"] = ";".join(f"{c}:{s:.0f}" for c, s in good)
        rows.append(rec)
    out = pd.DataFrame(rows)
    short = gr.set_index("route_id")["route_short_name"]
    out["route_short_name"] = out["gtfs_route_id"].map(short)
    return out


def usable(mapping: pd.DataFrame) -> pd.DataFrame:
    """Mappings that downstream steps may use (no unverified fuzzy matches, no ambiguity)."""
    return mapping[mapping["mapping_method"].isin(["exact_short_name", "exact_long_name", "alias"])]
