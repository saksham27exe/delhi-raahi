"""Load a static GTFS feed (folder or zip) into typed Parquet tables + route stop patterns.

Delhi feed observations (Transport Stack 'Delhi Buses: GTFS Static Data'):
* each direction is its own route (route_short_name ends in UP/DOWN), direction_id is blank;
* stop_times has extra columns fare_stage and stop_dist;
* calendar.txt holds a single all-days service.
The loader does not depend on those quirks: it works with any standard feed.
"""
from __future__ import annotations

import hashlib
import html
import io
import logging
import zipfile
from pathlib import Path

import numpy as np
import pandas as pd

log = logging.getLogger(__name__)

REQUIRED_FILES = ["routes.txt", "stops.txt", "trips.txt", "stop_times.txt"]
OPTIONAL_FILES = ["agency.txt", "calendar.txt", "calendar_dates.txt", "shapes.txt", "feed_info.txt", "frequencies.txt"]


class GTFSNotFound(FileNotFoundError):
    pass


def _find_feed_root(path: Path) -> Path | zipfile.ZipFile:
    """Return the folder (or open zip) that contains stops.txt, searching one level of nesting."""
    if path.is_file() and path.suffix == ".zip":
        return zipfile.ZipFile(path)
    if (path / "stops.txt").exists():
        return path
    for sub in sorted(path.glob("**/stops.txt")):
        return sub.parent
    zips = sorted(path.glob("*.zip"))
    if zips:
        return zipfile.ZipFile(zips[0])
    raise GTFSNotFound(
        f"No GTFS feed found under {path}. Download 'Delhi Buses: GTFS Static Data' from "
        "https://delhi.transportstack.in (Data & Services) and unzip it into data/gtfs/."
    )


def _read(root: Path | zipfile.ZipFile, name: str) -> pd.DataFrame | None:
    if isinstance(root, zipfile.ZipFile):
        match = [n for n in root.namelist() if n.rsplit("/", 1)[-1] == name]
        if not match:
            return None
        with root.open(match[0]) as f:
            return pd.read_csv(io.TextIOWrapper(f, encoding="utf-8-sig"), dtype=str, keep_default_na=False)
    p = root / name
    if not p.exists():
        return None
    return pd.read_csv(p, dtype=str, keep_default_na=False, encoding="utf-8-sig")


def gtfs_time_to_seconds(s: pd.Series) -> pd.Series:
    """'25:10:00' -> 90600. GTFS times may exceed 24h. Blank -> NaN."""
    parts = s.str.strip().str.split(":", expand=True)
    if parts.shape[1] < 3:
        return pd.Series(np.nan, index=s.index)
    h = pd.to_numeric(parts[0], errors="coerce")
    m = pd.to_numeric(parts[1], errors="coerce")
    sec = pd.to_numeric(parts[2], errors="coerce")
    return h * 3600 + m * 60 + sec


def load_gtfs(gtfs_path: Path, out_dir: Path) -> dict:
    root = _find_feed_root(gtfs_path)
    out_dir.mkdir(parents=True, exist_ok=True)
    tables: dict[str, pd.DataFrame] = {}
    missing_required = []
    for name in REQUIRED_FILES + OPTIONAL_FILES:
        df = _read(root, name)
        if df is None:
            if name in REQUIRED_FILES:
                missing_required.append(name)
            continue
        df.columns = [c.strip() for c in df.columns]
        tables[name.removesuffix(".txt")] = df
    if missing_required:
        raise GTFSNotFound(f"GTFS feed is missing required files: {missing_required}")

    stops = tables["stops"]
    # some names carry HTML entities ("KISHAN GARH &amp; NPD"); collapse repeated spaces too
    stops["stop_name"] = stops["stop_name"].map(html.unescape).str.replace(r"\s+", " ", regex=True).str.strip()
    stops["stop_lat"] = pd.to_numeric(stops["stop_lat"], errors="coerce")
    stops["stop_lon"] = pd.to_numeric(stops["stop_lon"], errors="coerce")
    bad_coords = int(stops[["stop_lat", "stop_lon"]].isna().any(axis=1).sum())

    st = tables["stop_times"]
    st["arrival_s"] = gtfs_time_to_seconds(st["arrival_time"])
    st["departure_s"] = gtfs_time_to_seconds(st["departure_time"])
    # fill a missing arrival with departure (and vice versa) - common in GTFS
    st["arrival_s"] = st["arrival_s"].fillna(st["departure_s"])
    st["departure_s"] = st["departure_s"].fillna(st["arrival_s"])
    st["stop_sequence"] = pd.to_numeric(st["stop_sequence"], errors="coerce").astype("Int64")
    if "fare_stage" in st:
        st["fare_stage"] = pd.to_numeric(st["fare_stage"], errors="coerce")
    if "stop_dist" in st:
        st["stop_dist"] = pd.to_numeric(st["stop_dist"], errors="coerce")
    st = st.sort_values(["trip_id", "stop_sequence"]).reset_index(drop=True)
    untimed = int(st["arrival_s"].isna().sum())

    trips = tables["trips"]
    patterns, trip_pattern = build_patterns(st, trips)
    trips = trips.merge(trip_pattern, on="trip_id", how="left")

    if "shapes" in tables:
        sh = tables["shapes"]
        for c in ("shape_pt_lat", "shape_pt_lon", "shape_dist_traveled"):
            if c in sh:
                sh[c] = pd.to_numeric(sh[c], errors="coerce")
        sh["shape_pt_sequence"] = pd.to_numeric(sh["shape_pt_sequence"], errors="coerce")
        tables["shapes"] = sh.sort_values(["shape_id", "shape_pt_sequence"])

    tables.update({"stops": stops, "stop_times": st, "trips": trips, "patterns": patterns})
    for name, df in tables.items():
        df.to_parquet(out_dir / f"gtfs_{name}.parquet", index=False)

    report = {
        "feed_files": sorted(tables.keys()),
        "missing_optional_files": [n for n in OPTIONAL_FILES if n.removesuffix(".txt") not in tables],
        "routes": len(tables["routes"]),
        "stops": len(stops),
        "stops_bad_coordinates": bad_coords,
        "trips": len(trips),
        "stop_times": len(st),
        "stop_times_untimed": untimed,
        "patterns": int(patterns["pattern_id"].nunique()),
        "has_fare_stage": "fare_stage" in st,
    }
    log.info("GTFS loaded: %s", report)
    return report


def build_patterns(stop_times: pd.DataFrame, trips: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Group trips by (route, exact stop sequence). Returns (pattern stops, trip->pattern).

    patterns: pattern_id, route_id, seq (0..n-1), stop_id, n_trips, is_primary,
              offset_s (median scheduled seconds from the first stop), fare_stage
    is_primary marks the most-used pattern of a route, used for segment demand.
    """
    seqs = stop_times.groupby("trip_id", sort=False)["stop_id"].agg(tuple)
    tp = seqs.rename("stops").reset_index()
    tp["pattern_key"] = tp["stops"].map(lambda t: hashlib.md5("|".join(t).encode()).hexdigest()[:10])
    tp = tp.merge(trips[["trip_id", "route_id"]], on="trip_id", how="inner")
    orphaned = len(seqs) - len(tp)
    if orphaned:
        log.warning("%d trips in stop_times have no row in trips.txt; they are excluded", orphaned)
    tp["pattern_id"] = tp["route_id"].astype(str) + "_" + tp["pattern_key"]
    counts = tp.groupby("pattern_id").size().rename("n_trips")

    first = tp.drop_duplicates("pattern_id")[["pattern_id", "route_id", "stops"]]
    first = first.merge(counts, on="pattern_id")
    first["rank"] = first.groupby("route_id")["n_trips"].rank(method="first", ascending=False)
    first["is_primary"] = first["rank"] == 1

    rows = first.explode("stops").rename(columns={"stops": "stop_id"})
    rows["seq"] = rows.groupby("pattern_id").cumcount()

    # median offset from the trip start at each position, per pattern
    st = stop_times.merge(tp[["trip_id", "pattern_id"]], on="trip_id")
    st["pos"] = st.groupby("trip_id").cumcount()
    st["offset_s"] = st["arrival_s"] - st.groupby("trip_id")["departure_s"].transform("first")
    agg = {"offset_s": "median"}
    if "fare_stage" in st:
        agg["fare_stage"] = "first"
    if "stop_dist" in st:
        agg["stop_dist"] = "median"
    off = st.groupby(["pattern_id", "pos"]).agg(agg).reset_index().rename(columns={"pos": "seq"})
    patterns = rows.merge(off, on=["pattern_id", "seq"], how="left")
    patterns = patterns.drop(columns=["rank"]).reset_index(drop=True)
    return patterns, tp[["trip_id", "pattern_id"]]
