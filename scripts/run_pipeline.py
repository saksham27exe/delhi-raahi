"""Data pipeline: tickets + GTFS -> mappings -> segment demand -> data-quality report.

    python scripts/run_pipeline.py [--skip-tickets]
"""
import argparse
import json

import _bootstrap  # noqa: F401
from app.data_pipeline.pipeline import run

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-tickets", action="store_true", help="reuse data/processed/tickets_clean.parquet")
    r = run(skip_tickets=ap.parse_args().skip_tickets)
    print(json.dumps({k: r[k] for k in ("tickets", "route_mapping", "od_resolution",
                                         "pct_usable_for_segment_analysis", "segments", "pipeline_seconds")},
                     indent=2, default=str))
