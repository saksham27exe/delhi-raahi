"""DelhiRaahi API.

    cd backend && ../.venv/Scripts/python -m uvicorn app.main:app --port 8000
Docs at /docs. Heavy artifacts load once at startup; the GTFS-RT poller starts if TS_API_KEY is set.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.middleware.cors import CORSMiddleware

from app.api.deps import get_state
from app.api.routes import account, admin, admin_data, admin_ops, admin_support, admin_users, crowd, insights, journey, stops, support, tickets, voice
from app.api.routes import live as live_routes
from app.core.config import REPO_ROOT, get_settings
from app.services.state import AppState, _json, load_state, start_live

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def create_app(state: AppState | None = None, live: bool = True) -> FastAPI:
    """`state` lets tests inject a small in-memory state; `live=False` skips the GTFS-RT poller."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        st = state or load_state()
        try:
            from app.services.admin_data import apply_fixes
            apply_fixes(st)          # stop aliases and bus type fixes made on the admin website
        except Exception as e:  # noqa: BLE001
            st.load_errors["admin_fixes"] = f"{type(e).__name__}: {e}"
        if live:
            start_live(st)
            voice.preload()          # local LLM for the voice assistant, if its file is present
        app.state.cw = st
        yield
        if st.live_poller is not None:
            st.live_poller.stop()

    app = FastAPI(title="DelhiRaahi API", version="0.8.0", lifespan=lifespan,
                  description="Delhi bus travel: journeys ranked by time and expected crowding, live buses, "
                              "fares and DEMO tickets. Crowding is a relative index from e-ticket data, not occupancy.")
    origins = [o.strip() for o in get_settings().crowdwise_cors_origins.split(",") if o.strip()]
    app.add_middleware(CORSMiddleware, allow_origins=origins, allow_methods=["GET", "POST", "PATCH"], allow_headers=["*"])
    for r in (journey, stops, live_routes, tickets, account, crowd, insights, voice, support, admin, admin_support, admin_ops, admin_users, admin_data):
        app.include_router(r.router)

    @app.get("/api/v1/health", tags=["meta"])
    def health(st: AppState = Depends(get_state)) -> dict:
        comps = {"gtfs_network": st.network is not None, "crowd_model": st.crowd is not None,
                 "fares": st.fares is not None and bool(getattr(st.fares, "profile", None)),
                 "demo_tickets": st.tickets is not None, "accounts": st.accounts is not None,
                 "live_feed": bool(st.live is not None and st.live.status()["live"])}
        meta = _json(st.settings.artifacts_dir / "route_model_meta.json") or {}
        core = comps["gtfs_network"] and comps["crowd_model"]
        return {"status": "ok" if core else "degraded", "components": comps, "load_errors": st.load_errors,
                "accounts_store": getattr(st.accounts, "kind", "local") if st.accounts is not None else None,
                "load_seconds": st.load_seconds, "model_version": meta.get("model_version"),
                "data_window": meta.get("training_period")}

    @app.middleware("http")
    async def admin_headers(request: Request, call_next):
        resp = await call_next(request)
        p = request.url.path
        if p.startswith("/admin") or p.startswith("/api/v1/admin"):
            resp.headers["X-Frame-Options"] = "DENY"
            resp.headers["X-Content-Type-Options"] = "nosniff"
            resp.headers["Referrer-Policy"] = "no-referrer"
            # Only the hashed build files (/admin/assets/…) may be cached; pages and API answers never are.
            hashed = p.startswith("/admin/assets/") and "text/html" not in resp.headers.get("content-type", "")
            resp.headers["Cache-Control"] = "public, max-age=31536000, immutable" if hashed else "no-store"
            resp.headers["Content-Security-Policy"] = (
                "default-src 'self'; img-src 'self' data: https://*.tile.openstreetmap.org; style-src 'self' 'unsafe-inline'; "
                "connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
        return resp

    @app.get("/admin", include_in_schema=False)
    def admin_root():
        return RedirectResponse("/admin/")

    @app.get("/admin/{path:path}", include_in_schema=False)
    def admin_site(path: str):
        """The admin website (built from admin/ with `npm run build`); unknown paths get index.html (client routing)."""
        dist = (REPO_ROOT / "admin" / "dist").resolve()
        f = (dist / path).resolve()
        if path and f.is_file() and dist in f.parents:
            return FileResponse(f)
        index = dist / "index.html"
        if not index.is_file():
            raise HTTPException(404, detail="Admin website not built. Run: cd admin && npm install && npm run build")
        return FileResponse(index)

    return app


app = create_app()
