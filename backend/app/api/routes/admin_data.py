"""Service alerts (admin + public feed for the app) and data fixes (stop search aliases, bus type overrides)."""
from __future__ import annotations

from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.api.deps import get_state
from app.api.routes.admin import audit, err, require
from app.services.admin_data import AdminData, apply_fixes
from app.services.admin_store import Admin, AdminError
from app.services.state import AppState

router = APIRouter(tags=["admin: alerts & data fixes"])
A = "/api/v1/admin"


def data(st: AppState = Depends(get_state)) -> AdminData:
    if st.admin_data is None:
        raise HTTPException(503, detail="Alerts and data fixes are not available on this server.")
    return st.admin_data


# ---------- public: what the app shows ----------
@router.get("/api/v1/alerts", tags=["alerts"])
def public_alerts(routes: str = "", stops: str = "", everyone: bool = True, st: AppState = Depends(get_state)) -> dict:
    """Service alerts in force now. `routes` = bus numbers (comma-separated), `stops` = stop ids; alerts for
    everyone are included unless `everyone=false`."""
    if st.admin_data is None:
        return {"alerts": []}
    return {"alerts": st.admin_data.live_alerts(routes.split(",")[:50], stops.split(",")[:100], everyone)}


# ---------- admin: alerts ----------
class StopRef(BaseModel):
    id: str = Field(..., max_length=40)
    name: str = Field("", max_length=120)


class AlertReq(BaseModel):
    severity: Literal["info", "warning", "critical"] | None = None
    title_en: str | None = Field(None, max_length=120)
    title_hi: str | None = Field(None, max_length=120)
    body_en: str | None = Field(None, max_length=1000)
    body_hi: str | None = Field(None, max_length=1000)
    routes: list[str] | None = Field(None, max_length=50)
    stops: list[StopRef] | None = Field(None, max_length=100)
    starts_at: str | None = None
    ends_at: str | None = None


@router.get(f"{A}/alerts")
def list_alerts(state: str | None = None, a: Admin = Depends(require("viewer")), d: AdminData = Depends(data)) -> dict:
    return {"alerts": d.list_alerts(state)}


@router.post(f"{A}/alerts")
def create_alert(req: AlertReq, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data)) -> dict:
    body = req.model_dump()
    body["stops"] = [s for s in body.get("stops") or []]
    try:
        out = d.create_alert(body, a)
    except (AdminError, ValueError) as e:
        raise (err(e) if isinstance(e, AdminError) else HTTPException(422, detail="Use ISO dates, e.g. 2026-11-16T09:00:00+05:30.")) from None
    audit(request, a, "alert_created", f"alert:{out['id']}", {"title": out["title_en"], "severity": out["severity"], "routes": out["routes"]})
    return out


@router.patch(f"{A}/alerts/{{aid}}")
def update_alert(aid: int, req: AlertReq, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data)) -> dict:
    try:
        out = d.update_alert(aid, req.model_dump(exclude_unset=True))
    except (AdminError, ValueError) as e:
        raise (err(e) if isinstance(e, AdminError) else HTTPException(422, detail="Use ISO dates.")) from None
    audit(request, a, "alert_updated", f"alert:{aid}", {"title": out["title_en"]})
    return out


@router.post(f"{A}/alerts/{{aid}}/end")
def end_alert(aid: int, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data)) -> dict:
    try:
        out = d.end_alert(aid)
    except AdminError as e:
        raise err(e) from None
    audit(request, a, "alert_ended", f"alert:{aid}")
    return out


@router.delete(f"{A}/alerts/{{aid}}")
def delete_alert(aid: int, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data)) -> dict:
    try:
        d.delete_alert(aid)
    except AdminError as e:
        raise err(e) from None
    audit(request, a, "alert_deleted", f"alert:{aid}")
    return {"ok": True}


# ---------- admin: look-ups for pickers ----------
@router.get(f"{A}/lookup/stops")
def lookup_stops(q: str, a: Admin = Depends(require("viewer")), st: AppState = Depends(get_state)) -> dict:
    if st.journeys is None:
        raise HTTPException(503, detail="The bus network is not loaded.")
    return {"results": [{"name": r["name"], "stop_ids": r["stop_ids"], "routes_served": r["routes_served"], "alias": r.get("alias")}
                        for r in st.journeys.search_stops(q, 10)]}


@router.get(f"{A}/lookup/routes")
def lookup_routes(q: str = "", a: Admin = Depends(require("viewer")), st: AppState = Depends(get_state)) -> dict:
    """Bus numbers with their AC tag: estimated from e-tickets, and the admin override if any."""
    if st.network is None or st.fares is None:
        raise HTTPException(503, detail="The bus network is not loaded.")
    from app.services.admin_data import route_base
    key = route_base(q) if q.strip() else ""
    out: dict[str, dict] = {}
    over = getattr(st.fares, "overrides", {})
    for rid, short in zip(st.network.routes["route_id"], st.network.routes["route_short_name"]):
        base = route_base(short)
        if (key and not base.startswith(key)) or (len(out) >= 40 and base not in out):
            continue
        p = st.fares.profile.get(str(rid))
        e = out.setdefault(base, {"route": base, "directions": [], "estimate": None, "ac_share": None, "override": None})
        e["directions"].append(str(short))
        if p and e["ac_share"] is None:
            from app.fare_engine.fares import bus_type_of
            e["ac_share"] = round(float(p["ac_share"]), 2)
            e["estimate"] = bus_type_of(float(p["ac_share"]), st.fares.rules)
        if str(rid) in over:
            e["override"] = over[str(rid)]
    return {"results": sorted(out.values(), key=lambda r: (len(r["route"]), r["route"]))[:40]}


# ---------- admin: data fixes ----------
class AliasReq(BaseModel):
    alias: str = Field(..., min_length=2, max_length=60)
    stop_name: str = Field(..., min_length=2, max_length=120)


@router.get(f"{A}/data/aliases")
def aliases(a: Admin = Depends(require("viewer")), d: AdminData = Depends(data)) -> dict:
    return {"aliases": d.aliases()}


@router.post(f"{A}/data/aliases")
def add_alias(req: AliasReq, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data),
              st: AppState = Depends(get_state)) -> dict:
    if st.journeys is not None and not any(r["name"] == req.stop_name for r in st.journeys.search_stops(req.stop_name, 5)):
        raise HTTPException(422, detail="Pick the stop from the search results.")
    try:
        d.add_alias(req.alias, req.stop_name, a)
    except AdminError as e:
        raise err(e) from None
    apply_fixes(st)
    audit(request, a, "alias_added", f"stop:{req.stop_name}", {"alias": req.alias})
    return {"aliases": d.aliases()}


@router.delete(f"{A}/data/aliases/{{alias}}")
def delete_alias(alias: str, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data),
                 st: AppState = Depends(get_state)) -> dict:
    try:
        d.delete_alias(alias)
    except AdminError as e:
        raise err(e) from None
    apply_fixes(st)
    audit(request, a, "alias_removed", None, {"alias": alias})
    return {"aliases": d.aliases()}


class BusTypeReq(BaseModel):
    route: str = Field(..., min_length=1, max_length=20)
    bus_type: Literal["ac", "non_ac", "mixed"]
    note: str | None = Field(None, max_length=200)


@router.get(f"{A}/data/bus-types")
def bus_types(a: Admin = Depends(require("viewer")), d: AdminData = Depends(data)) -> dict:
    return {"overrides": d.bus_overrides()}


@router.post(f"{A}/data/bus-types")
def set_bus_type(req: BusTypeReq, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data),
                 st: AppState = Depends(get_state)) -> dict:
    try:
        d.set_bus_override(req.route, req.bus_type, req.note, a)
    except AdminError as e:
        raise err(e) from None
    apply_fixes(st)
    audit(request, a, "bus_type_set", f"route:{req.route}", {"bus_type": req.bus_type, "note": req.note})
    return {"overrides": d.bus_overrides()}


@router.delete(f"{A}/data/bus-types/{{route}}")
def delete_bus_type(route: str, request: Request, a: Admin = Depends(require("agent")), d: AdminData = Depends(data),
                    st: AppState = Depends(get_state)) -> dict:
    try:
        d.delete_bus_override(route)
    except AdminError as e:
        raise err(e) from None
    apply_fixes(st)
    audit(request, a, "bus_type_removed", f"route:{route}")
    return {"overrides": d.bus_overrides()}
