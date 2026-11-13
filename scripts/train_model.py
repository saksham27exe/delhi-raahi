"""Train + evaluate the route demand model, persist forecasts, calibrate segment crowd levels.

    python scripts/train_model.py [--skip-train]
"""
import argparse
import json

import _bootstrap  # noqa: F401
from app.core.config import get_settings
from app.demand_engine.route_demand import DemandPanel
from app.ml_engine.forecast import build_forecasts
from app.ml_engine.segment_calibration import calibrate_and_evaluate
from app.ml_engine.train import train

if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--skip-train", action="store_true", help="reuse the saved model, only rebuild forecasts/calibration")
    args = ap.parse_args()
    s = get_settings()
    panel = DemandPanel.load(s.processed_dir / "route_panel.npz")
    if not args.skip_train:
        r = train(panel, s.artifacts_dir, s.metrics_dir)
        print(json.dumps({"test_service_hours": {k: v["service_hours"] for k, v in r["test"].items()},
                          "interval": r["interval_coverage_test"], "noise": r["noise_analysis"]}, indent=2))
    print(build_forecasts(panel, s.artifacts_dir, s.processed_dir / "route_forecasts.npz"))
    if (s.processed_dir / "segment_supply.parquet").exists():
        c = calibrate_and_evaluate(s.processed_dir, s.artifacts_dir, s.metrics_dir)
        print(json.dumps(c, indent=2, default=str))
    else:
        print("Segment tables not found (run scripts/run_pipeline.py with GTFS first); skipped segment calibration.")
