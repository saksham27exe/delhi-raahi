import json
from datetime import date

import numpy as np
import pandas as pd
import pytest

from app.demand_engine.route_demand import DemandPanel
from app.ml_engine import metrics as M
from app.ml_engine.features import FEATURES, HORIZON_DAYS, features_for_day, future_day_index
from app.ml_engine.train import calibrate_conformal, conformal_interval


# ---- metrics ---------------------------------------------------------------------
def test_metrics_hand_computed():
    y, p = np.array([0.0, 2, 4]), np.array([1.0, 2, 1])
    assert M.mae(y, p) == pytest.approx(4 / 3)
    assert M.rmse(y, p) == pytest.approx(np.sqrt(10 / 3))
    assert M.wape(y, p) == pytest.approx(4 / 6)
    assert M.wape(np.zeros(3), p) is None                    # undefined, not a fake number
    assert M.classify(np.array([0, 5, 10, 50]), [1, 10, 20]).tolist() == [0, 1, 2, 3]
    assert M.interval_coverage(y, y - 1, y + 1) == 1.0


# ---- intervals -------------------------------------------------------------------
def test_conformal_intervals_ordered_nonnegative_and_calibrated():
    rng = np.random.default_rng(1)
    mu = rng.gamma(1.0, 5.0, 40_000)
    y = rng.poisson(mu)
    q = calibrate_conformal(mu[:20_000], y[:20_000], 0.8)
    lo, hi = conformal_interval(mu[20_000:], q)
    assert (lo >= 0).all() and (lo <= mu[20_000:]).all() and (hi >= mu[20_000:]).all()
    cov = M.interval_coverage(y[20_000:], lo, hi)
    assert 0.77 <= cov <= 0.88
    busy = mu[20_000:] > 8
    assert M.interval_coverage(y[20_000:][busy], lo[busy], hi[busy]) >= 0.75   # holds for busy periods too


# ---- features: no leakage ------------------------------------------------------------
def _panel(days=35, routes=3, buckets=48, seed=0):
    rng = np.random.default_rng(seed)
    y = rng.poisson(3, (routes, days, buckets)).astype(np.float32)
    dates = np.arange(np.datetime64("2026-10-01"), np.datetime64("2026-10-01") + np.timedelta64(days, "D"))
    return DemandPanel(np.array(["R1", "R2", "R3"])[:routes], dates, buckets, y, y.astype(np.int32))


def test_features_use_only_data_at_least_horizon_days_old():
    p = _panel()
    t = 21
    base = features_for_day(p, t)[FEATURES]
    tampered = _panel()
    tampered.y[:, t - HORIZON_DAYS + 1:, :] += 1000          # change everything within the horizon and after
    assert features_for_day(tampered, t)[FEATURES].equals(base)
    tampered.y[:, t - HORIZON_DAYS, :] += 1000                # the newest allowed day must matter
    assert not features_for_day(tampered, t)[FEATURES].equals(base)


def test_future_dates_map_to_same_weekday_slot():
    p = _panel(days=31)
    for d in ("2026-11-20", "2026-12-25"):
        t = future_day_index(p, np.datetime64(d))
        slot_day = pd.Timestamp(p.dates[0] + np.timedelta64(t, "D"))
        assert slot_day.day_of_week == pd.Timestamp(d).day_of_week and 31 <= t <= 37
    assert future_day_index(p, np.datetime64("2026-10-10")) == 9


# ---- crowd service -----------------------------------------------------------------------
@pytest.fixture
def crowd(tmp_path):
    """Route R1UP (gtfs 1) has a forecast: 40 pax at 08:30 vs 30 historically.
    Segment 1:A>B history 20 pax/bucket, 1:B>C history 2; route 2 has no forecast; 3:M>N no history."""
    P, A = tmp_path / "p", tmp_path / "a"
    P.mkdir(), A.mkdir()
    B = 48
    pred = np.zeros((1, 2, B), np.float32)
    pred[0, :, 17] = 40.0
    np.savez(P / "route_forecasts.npz", routes=np.array(["R1UP"]), slot_dates=np.array(["2026-10-30", "2026-10-31"]),
             in_window=np.array([True, True]), pred=pred, lo=pred * 0.5, hi=pred * 1.5)
    hist = np.zeros((1, 3, B))
    hist[0, 0, 17] = 30.0
    np.savez(P / "route_history.npz", routes=np.array(["R1UP"]), hist=hist)
    pd.DataFrame({"segment_id": ["1:A>B", "1:B>C", "2:X>Y", "3:M>N"], "route_id": ["1", "1", "2", "3"]}) \
        .to_parquet(P / "segments.parquet")
    pd.DataFrame({"ticket_route": ["R1UP"], "route_key": ["R1UP"], "gtfs_route_id": ["1"],
                  "mapping_method": ["exact_short_name"], "mapping_confidence": [1.0]}).to_parquet(P / "route_mapping.parquet")
    pd.DataFrame({"segment_id": ["1:A>B", "1:B>C", "2:X>Y", "3:M>N"], "bucket": [17] * 4,
                  "departures": [4, 4, 2, 2]}).to_parquet(P / "segment_supply.parquet")
    pd.DataFrame({"segment_id": ["1:A>B", "1:B>C", "1:A>B"], "daytype": [0, 0, 0], "band": ["am_peak"] * 3,
                  "support": [350, 5, 999], "service_date": pd.to_datetime(["2026-10-20", "2026-10-20", "2026-10-27"])}) \
        .to_parquet(P / "segment_band_daily.parquet")    # the 27 Oct row is after calibration and must be ignored
    hist = pd.DataFrame({"segment_id": ["1:A>B", "1:B>C", "2:X>Y"], "daytype": [0, 0, 0], "bucket": [17, 17, 17],
                         "seg_demand": [20.0, 2.0, 18.0]})
    from app.ml_engine.forecast import RouteForecasts
    from app.ml_engine.segment_calibration import build_crowd_arrays
    build_crowd_arrays(P, hist, RouteForecasts(P / "route_forecasts.npz"))
    (A / "segment_calibration.json").write_text(json.dumps({
        "calibration_period": ["2026-10-01", "2026-10-24"], "category_thresholds": [1.0, 3.0, 8.0],
        "dpd_cdf_knots": list(np.linspace(0, 10, 101)), "evidence_support_thresholds": {"HIGH": 300, "MEDIUM": 100}}))
    from app.services.crowd_service import CrowdService
    return CrowdService(P, A)


def test_weighted_quantile():
    from app.ml_engine.segment_calibration import weighted_quantile
    v, w = np.array([1.0, 2.0, 10.0]), np.array([1.0, 1.0, 8.0])
    assert weighted_quantile(v, w, 0.5) == 10.0           # 80% of the weight sits at 10
    assert weighted_quantile(v, np.ones(3), 0.5) == 2.0


FRI = date(2026, 10, 30)


def test_segment_history_adjusted_by_route_forecast(crowd):
    from app.ml_engine.segment_calibration import RATIO_SHRINK_K as k
    p = crowd.predict("1:A>B", FRI, 17)
    expected = 20 * (40 + k) / (30 + k) / 4
    assert p.fallback_level == 1 and p.value == pytest.approx(expected)
    assert p.lower <= p.value <= p.upper
    assert p.historical_value == pytest.approx(5.0)
    assert p.unusualness_pct == pytest.approx(round(100 * (expected - 5) / 5, 1))
    assert p.category == "HIGH" and p.evidence == "HIGH" and p.support == 350   # post-calibration support ignored


def test_low_support_gives_low_evidence(crowd):
    p = crowd.predict("1:B>C", FRI, 17)
    assert p.fallback_level == 1 and p.evidence == "LOW" and p.category == "LOW"


def test_history_only_and_unknown(crowd):
    h = crowd.predict("2:X>Y", FRI, 17)
    assert h.fallback_level == 3 and h.value == pytest.approx(9.0) and h.evidence == "LOW"
    u = crowd.predict("3:M>N", FRI, 17)
    assert u.category == "UNKNOWN" and u.value is None and u.crowd_index is None and u.fallback_level == 5
    assert crowd.predict("9:no>such", FRI, 17).category == "UNKNOWN"


def test_no_scheduled_bus_is_unknown_not_low(crowd):
    p = crowd.predict("1:A>B", FRI, 3)                        # 01:30, no departures
    assert p.category == "UNKNOWN" and p.method == "no_scheduled_departures_in_bucket"
