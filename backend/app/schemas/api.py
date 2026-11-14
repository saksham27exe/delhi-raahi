"""Request / response schemas (Pydantic)."""
from __future__ import annotations

from datetime import datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator


class Place(BaseModel):
    type: Literal["stop", "stop_name", "coord"]
    id: str | None = Field(None, description="GTFS stop_id when type='stop'")
    name: str | None = Field(None, description="Stop name when type='stop_name'")
    lat: float | None = Field(None, ge=27.5, le=29.5)
    lon: float | None = Field(None, ge=76.5, le=78.0)
    label: str | None = None

    @model_validator(mode="after")
    def _check(self) -> "Place":
        if self.type == "stop" and not self.id:
            raise ValueError("id is required when type='stop'")
        if self.type == "stop_name" and not self.name:
            raise ValueError("name is required when type='stop_name'")
        if self.type == "coord" and (self.lat is None or self.lon is None):
            raise ValueError("lat and lon are required when type='coord' (Delhi NCR bounds)")
        return self


class JourneySearchRequest(BaseModel):
    origin: Place
    destination: Place
    departure_datetime: datetime = Field(..., examples=["2026-11-20T08:30:00+05:30"])
    preference: Literal["fastest", "balanced", "least_crowded"] = "balanced"
    max_transfers: int = Field(1, ge=0, le=1)
    access_radius_m: int = Field(600, ge=200, le=1500, description="Max walk to a boarding stop (straight line)")


class AdvisorRequest(JourneySearchRequest):
    offsets_min: list[int] = Field(default=[-30, -15, 0, 15, 30, 45, 60], max_length=9)


class Health(BaseModel):
    status: Literal["ok", "degraded"]
    components: dict[str, bool]
    load_errors: dict[str, str]
    load_seconds: float
    model_version: str | None
    data_window: list[str] | None


class LegRef(BaseModel):
    route_id: str
    board_stop_id: str
    alight_stop_id: str


class FareQuoteRequest(BaseModel):
    legs: list[LegRef] = Field(..., min_length=1, max_length=3)
    passengers: int = Field(1, ge=1, le=6)
    women_free: int = Field(0, ge=0, le=6)
    bus_types: list[Literal["ac", "non_ac"] | None] | None = None


class TicketVerifyRequest(BaseModel):
    qr_payload: str = Field(..., max_length=4000)
