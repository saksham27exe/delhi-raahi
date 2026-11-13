from __future__ import annotations

from fastapi import APIRouter, Depends

from app.api.deps import get_state
from app.services.state import AppState, _json

router = APIRouter(prefix="/api/v1/insights", tags=["insights"])

LIMITATIONS = [
    "Crowding is estimated from ONDC e-tickets, a sample of riders: it is a relative pressure index "
    "(demand per scheduled departure), not a passenger count.",
    "E-ticket uptake differs by route, so comparisons between routes are partly confounded.",
    "Supply is the GTFS timetable, not observed bus runs.",
    "Dates after the data window use the recurring weekday pattern.",
    "Live arrival estimates assume the current delay persists; live trip ids do not match the timetable.",
]


@router.get("/model")
def model_card(st: AppState = Depends(get_state)) -> dict:
    """How good are the numbers? Evaluation against baselines, fare validation, data quality, limitations."""
    return {"route_model": st.model_report, "segment_model": st.segment_report,
            "fare_validation": _json(st.settings.metrics_dir / "fare_validation.json"),
            "data_quality": st.quality_report, "limitations": LIMITATIONS}
