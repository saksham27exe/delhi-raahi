"""Text normalisation for route designations and stop names.

Ticketing stop names and GTFS stop names are typed by different people, so we
normalise both sides the same way before matching. Rules are deliberately
conservative: they only collapse spelling variants that cannot change which
physical place is meant.
"""
from __future__ import annotations

import re

_ABBREVIATIONS: dict[str, str] = {
    "bdr": "border",
    "brdr": "border",
    "metro stn": "metro station",
    "mtro station": "metro station",
    "metro sation": "metro station",
    "sation": "station",
    "stn": "station",
    "rly": "railway",
    "mkt": "market",
    "hosp": "hospital",
    "xing": "crossing",
    "chk": "chowk",
    "ext": "extension",
    "extn": "extension",
    "sec": "sector",
    "sect": "sector",
    "ph": "phase",
    "inst": "institute",
    "colny": "colony",
    "col": "colony",
    "x ing": "crossing",
    "ps": "police station",
    "p s": "police station",
    "opp": "opposite",
}

_TERMINAL_MARK = re.compile(r"\(\s*t\s*\)")
_NON_ALNUM = re.compile(r"[^a-z0-9]+")
_ROUTE_DIRECTION = re.compile(r"^(?P<base>.*?)(?P<dir>UP|DOWN|DN)$")


def normalize_stop_name(name: str | None) -> str:
    """Lower-case, strip punctuation, expand common abbreviations."""
    if not name:
        return ""
    s = name.lower().strip()
    s = _TERMINAL_MARK.sub(" terminal ", s)
    s = s.replace("&", " and ")
    s = _NON_ALNUM.sub(" ", s)
    tokens = s.split()
    out: list[str] = []
    i = 0
    while i < len(tokens):
        pair = " ".join(tokens[i : i + 2])
        if pair in _ABBREVIATIONS:
            out.extend(_ABBREVIATIONS[pair].split())
            i += 2
            continue
        out.extend(_ABBREVIATIONS.get(tokens[i], tokens[i]).split())
        i += 1
    # "terminal" duplicated by the (T) marker ("x terminal terminal")
    dedup: list[str] = []
    for t in out:
        if not dedup or dedup[-1] != t:
            dedup.append(t)
    return " ".join(dedup)


def name_variants(name: str | None) -> list[str]:
    """Normalised full name plus each '/'-separated part ('Laxmi Nagar X-ing / Laxmi Nagar')."""
    if not name:
        return []
    out = [normalize_stop_name(name)]
    if "/" in name:
        out += [normalize_stop_name(p) for p in name.split("/")]
    return [v for i, v in enumerate(out) if v and v not in out[:i]]


def normalize_route(route: str | None) -> str:
    """Upper-case, drop whitespace/punctuation. '717A UP' -> '717AUP'."""
    if not route:
        return ""
    return re.sub(r"[^A-Z0-9]", "", route.upper())


def split_route_direction(route: str | None) -> tuple[str, str | None]:
    """'8ADOWN' -> ('8A', 'DOWN'); 'AIRPORT-STL' -> ('AIRPORTSTL', None)."""
    norm = normalize_route(route)
    m = _ROUTE_DIRECTION.match(norm)
    if not m or not m.group("base"):
        return norm, None
    direction = "DOWN" if m.group("dir") in ("DOWN", "DN") else "UP"
    return m.group("base"), direction
