"""DelhiRaahi API.

    cd backend && ../.venv/Scripts/python -m uvicorn app.main:app --port 8000
Docs at /docs. Heavy artifacts load once at startup; the GTFS-RT poller starts if TS_API_KEY is set.
"""
from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.deps import get_state
from app.api.routes import account, crowd, insights, journey, stops, tickets, voice
from app.api.routes import live as live_routes
from app.core.config import get_settings
from app.services.state import AppState, _json, load_state, start_live

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")


def create_app(state: AppState | None = None, live: bool = True) -> FastAPI:
    """`state` lets tests inject a small in-memory state; `live=False` skips the GTFS-RT poller."""

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        st = state or load_state()
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
    for r in (journey, stops, live_routes, tickets, account, crowd, insights, voice):
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

    return app


app = create_app()
