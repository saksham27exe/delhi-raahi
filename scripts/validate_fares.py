"""Validate slab fares against ONDC e-ticket amounts and build route_fare_profile.parquet.

    python scripts/validate_fares.py
"""
import json

import _bootstrap  # noqa: F401
from app.core.config import get_settings
from app.fare_engine.fares import validate_fares
from app.journey_engine.network import load_network

if __name__ == "__main__":
    s = get_settings()
    rep = validate_fares(load_network(s.processed_dir), s.processed_dir, s.metrics_dir)
    print(json.dumps(rep, indent=2))
