"""Admin website: operations dashboard (one call) and CSV exports of users and the audit log."""
from __future__ import annotations

import csv
import io
import json
from datetime import datetime

from fastapi import APIRouter, Depends, Request
from fastapi.responses import Response

from app.api.deps import get_state
from app.api.routes.admin import audit, require
from app.services.admin_store import Admin
from app.services.state import AppState

router = APIRouter(prefix="/api/v1/admin", tags=["admin: operations"])


def _safe(fn):
    try:
        return fn()
    except Exception as e:  # noqa: BLE001  (one broken part must not blank the whole dashboard)
        return {"error": f"{type(e).__name__}: {e}"}


def _model_summary(st: AppState) -> dict | None:
    rm, sm = st.model_report or {}, st.segment_report or {}
    if not rm and not sm:
        return None
    test = rm.get("test", {})
    w = lambda k: (test.get(k, {}).get("service_hours") or {}).get("wape")  # noqa: E731
    return {"version": (rm.get("meta") or {}).get("model_version"), "training_period": (rm.get("meta") or {}).get("training_period"),
            "route_wape": w("lightgbm"), "route_wape_baseline": w("naive_last_week"),
            "interval_nominal": (rm.get("interval_coverage_test") or {}).get("nominal"),
            "interval_coverage": (rm.get("interval_coverage_test") or {}).get("service_hours"),
            "segment_f1": (sm.get("category_macro_f1") or {}).get("model"),
            "segment_f1_baseline": (sm.get("category_macro_f1") or {}).get("historical_average")}


@router.get("/overview")
def overview(a: Admin = Depends(require("viewer")), st: AppState = Depends(get_state)) -> dict:
    from app.services import voice_llm
    s = st.settings
    comps = {"gtfs_network": st.network is not None, "crowd_model": st.crowd is not None,
             "fares": st.fares is not None and bool(getattr(st.fares, "profile", None)), "demo_tickets": st.tickets is not None,
             "accounts": st.accounts is not None, "support_desk": st.desk is not None}
    live = _safe(lambda: st.live.status()) if st.live is not None else None
    voice = {"model_file": voice_llm.MODEL_FILE, "installed": (s.crowdwise_models_dir / "llm" / voice_llm.MODEL_FILE).is_file(),
             "loaded": voice_llm._instance is not None}
    alerts = st.admin_data.list_alerts() if st.admin_data is not None else []
    acc = st.accounts
    return {
        "generated_at": datetime.now().astimezone().isoformat(timespec="seconds"),
        "system": {"components": comps, "load_errors": st.load_errors, "load_seconds": st.load_seconds,
                   "accounts_store": getattr(acc, "kind", "local") if acc is not None else None,
                   "email": {"configured": st.mailer is not None, "support_inbox_set": bool((s.support_email or s.smtp_user).strip())},
                   "live": live, "voice": voice},
        "users": _safe(lambda: acc.admin_stats(30)) if acc is not None and hasattr(acc, "admin_stats") else None,
        "support": _safe(lambda: st.desk.stats(30)) if st.desk is not None else None,
        "alerts": {"live": sum(x["state"] == "live" for x in alerts), "scheduled": sum(x["state"] == "scheduled" for x in alerts),
                   "live_titles": [{"id": x["id"], "title": x["title_en"], "severity": x["severity"]} for x in alerts if x["state"] == "live"][:5]},
        "model": _model_summary(st),
        "data": {"routes": int(len(st.network.routes)) if st.network is not None else None,
                 "stops": int(len(st.network.stops)) if st.network is not None else None,
                 "data_window": ((st.model_report or {}).get("meta") or {}).get("training_period")},
    }


def _csv(rows: list[list], name: str) -> Response:
    out = io.StringIO()
    w = csv.writer(out)
    for r in rows:
        w.writerow([("'" + v) if isinstance(v, str) and v[:1] in "=+-@" else ("" if v is None else v) for v in r])
    return Response("﻿" + out.getvalue(), media_type="text/csv; charset=utf-8",
                    headers={"Content-Disposition": f'attachment; filename="{name}-{datetime.now():%Y%m%d-%H%M}.csv"'})


@router.get("/users/export.csv")
def export_users(request: Request, a: Admin = Depends(require("agent")), st: AppState = Depends(get_state)) -> Response:
    rows, cursor = [["id", "name", "email", "phone", "joined", "demo_balance", "disabled"]], None
    for _ in range(100):                                    # up to 10,000 users
        page = st.accounts.admin_users("", 100, cursor)
        rows += [[u["id"], u["name"], u["email"], u["phone"], u["created_at"], u["balance"], u["disabled"]] for u in page["users"]]
        cursor = page["next"]
        if not cursor:
            break
    audit(request, a, "users_exported", None, {"rows": len(rows) - 1})
    return _csv(rows, "delhiraahi-users")


@router.get("/audit/export.csv")
def export_audit(request: Request, a: Admin = Depends(require("owner")), st: AppState = Depends(get_state)) -> Response:
    rows = [["id", "at", "admin", "action", "target", "detail", "ip"]]
    rows += [[e["id"], e["at"], e["admin_email"], e["action"], e["target"], json.dumps(e["detail"], ensure_ascii=False) if e["detail"] else "", e["ip"]]
             for e in st.admin.audit_log(500)]
    audit(request, a, "audit_exported", None, {"rows": len(rows) - 1})
    return _csv(rows, "delhiraahi-audit")
