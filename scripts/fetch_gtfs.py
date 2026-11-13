"""Download Delhi bus static GTFS (and optionally a GTFS-RT snapshot) from Transport Stack.

Usage:
    python scripts/fetch_gtfs.py            # static GTFS -> data/gtfs/
    python scripts/fetch_gtfs.py --rt       # also save one VehiclePositions.pb snapshot

Needs TS_API_KEY in crowdwise/.env (or ../.env). The key is sent only as the
x-api-key header to dts-backend.transportstack.in and is never printed.
If you already downloaded the GTFS zip manually, just unzip it into data/gtfs/.
"""
from __future__ import annotations

import argparse
import io
import sys
import zipfile
from datetime import datetime
from pathlib import Path

import requests

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.core.config import get_settings  # noqa: E402

# The platform's exact static filename is not documented publicly; try the known patterns.
STATIC_CANDIDATES = [
    ("static_gtfs", "GTFS.zip"),
    ("static", "GTFS.zip"),
    ("static_gtfs", "gtfs.zip"),
    ("gtfs", "GTFS.zip"),
    ("static_gtfs", "google_transit.zip"),
]


def _get(category: str, filename: str, key: str) -> requests.Response:
    s = get_settings()
    return requests.get(
        s.ts_api_base,
        params={"agency": "delhi-buses", "category": category, "filename": filename},
        headers={"x-api-key": key},
        timeout=120,
    )


def fetch_static(key: str, out_dir: Path) -> bool:
    out_dir.mkdir(parents=True, exist_ok=True)
    for category, filename in STATIC_CANDIDATES:
        r = _get(category, filename, key)
        print(f"  {category}/{filename}: HTTP {r.status_code} ({len(r.content)} bytes)")
        if r.status_code == 200 and r.content[:2] == b"PK":
            with zipfile.ZipFile(io.BytesIO(r.content)) as z:
                names = z.namelist()
                z.extractall(out_dir)
            (out_dir / "_source.txt").write_text(
                f"Transport Stack {category}/{filename} downloaded {datetime.now().isoformat()}\n"
            )
            print(f"  extracted {len(names)} files: {', '.join(sorted(names))}")
            return True
        if r.status_code == 200:
            print(f"    (not a zip: {r.content[:120]!r})")
    return False


def fetch_rt(key: str, out_dir: Path) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    r = _get("realtime_gtfs", "VehiclePositions.pb", key)
    print(f"  realtime VehiclePositions.pb: HTTP {r.status_code} ({len(r.content)} bytes)")
    if r.status_code == 200:
        path = out_dir / f"VehiclePositions_{datetime.now():%Y%m%d_%H%M%S}.pb"
        path.write_bytes(r.content)
        print(f"  saved {path}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rt", action="store_true", help="also fetch a GTFS-RT snapshot")
    args = ap.parse_args()
    s = get_settings()
    if not s.ts_api_key:
        print("TS_API_KEY is not set. Add it to crowdwise/.env (see .env.example).")
        return 2
    print("Fetching static GTFS ...")
    ok = fetch_static(s.ts_api_key, s.gtfs_dir)
    if not ok:
        print(
            "Could not find static GTFS with this key. Request the 'Delhi Buses: Static GTFS' "
            "dataset on delhi.transportstack.in, or unzip a manually downloaded GTFS into data/gtfs/."
        )
    if args.rt:
        fetch_rt(s.ts_api_key, s.crowdwise_data_dir / "rt_snapshots")
    return 0 if ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
