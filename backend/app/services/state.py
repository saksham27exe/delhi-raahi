"""Application state: heavy artifacts are loaded once at startup, never per request."""
from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import pandas as pd

from app.core.config import Settings, get_settings
from app.demand_engine.route_demand import DemandPanel

log = logging.getLogger(__name__)


@dataclass
class AppState:
    settings: Settings
    panel: DemandPanel | None = None
    network: object | None = None
    crowd: object | None = None
    journeys: object | None = None
    live: object | None = None        # LiveTracker
    fares: object | None = None       # FareService
    tickets: object | None = None     # TicketService (demo)
    geo: object | None = None         # OsmGeo (place search, walking routes) or None
    accounts: object | None = None    # AccountStore (users, demo wallet, tickets, passes)
    mailer: object | None = None      # Mailer (SMTP) for account and support emails, if set up
    live_poller: object | None = None
    routes: pd.DataFrame | None = None
    route_mapping: pd.DataFrame | None = None
    model_report: dict | None = None
    segment_report: dict | None = None
    quality_report: dict | None = None
    load_errors: dict[str, str] = field(default_factory=dict)
    load_seconds: float = 0.0

    @property
    def gtfs_ready(self) -> bool:
        return self.network is not None

    def route_key_for(self, route: str) -> str | None:
        """Accept a GTFS route_id or a ticket designation ('717AUP'); return the panel route key."""
        if self.route_mapping is not None:
            m = self.route_mapping[self.route_mapping["gtfs_route_id"].astype(str) == str(route)]
            if not m.empty:
                return str(m.iloc[0]["route_key"])
        key = "".join(ch for ch in route.upper() if ch.isalnum())
        if self.panel is not None and key in set(self.panel.routes):
            return key
        return None

    def gtfs_route_for_key(self, key: str) -> str | None:
        if self.route_mapping is None:
            return None
        m = self.route_mapping[(self.route_mapping["route_key"] == key) & self.route_mapping["gtfs_route_id"].notna()]
        return None if m.empty else str(m.iloc[0]["gtfs_route_id"])


def _json(p: Path) -> dict | None:
    return json.loads(p.read_text()) if p.exists() else None


def load_state(settings: Settings | None = None) -> AppState:
    s = settings or get_settings()
    st = AppState(settings=s)
    t0 = time.time()
    P = s.processed_dir
    try:
        st.panel = DemandPanel.load(P / "route_panel.npz")
    except Exception as e:  # noqa: BLE001
        st.load_errors["demand_panel"] = f"{type(e).__name__}: {e}. Run scripts/run_pipeline.py."
    st.model_report = _json(s.metrics_dir / "route_model_evaluation.json")
    st.segment_report = _json(s.metrics_dir / "segment_evaluation.json")
    st.quality_report = _json(P / "data_quality.json")
    if (P / "route_mapping.parquet").exists():
        st.route_mapping = pd.read_parquet(P / "route_mapping.parquet")
    try:
        from app.journey_engine.network import load_network
        st.network = load_network(P)
        st.routes = st.network.routes
    except Exception as e:  # noqa: BLE001
        st.load_errors["gtfs_network"] = (f"{type(e).__name__}: {e}. Put the static GTFS in data/gtfs/ and run "
                                          "scripts/run_pipeline.py.")
    try:
        from app.services.crowd_service import CrowdService
        st.crowd = CrowdService(P, s.artifacts_dir)
    except Exception as e:  # noqa: BLE001
        st.load_errors["crowd_model"] = f"{type(e).__name__}: {e}. Run scripts/train_model.py."
    # One persistent secret signs tickets, passes and login tokens (env var wins; else a git-ignored file).
    from app.services.accounts import AccountStore, load_or_create_secret
    acc_dir = s.crowdwise_data_dir / "accounts"
    secret = s.demo_ticket_secret or load_or_create_secret(acc_dir / "server_secret.txt")
    if s.smtp_user and s.smtp_password:
        from app.services.mailer import Mailer
        st.mailer = Mailer(s.smtp_host, s.smtp_port, s.smtp_user, s.smtp_password, s.mail_from_name)
    try:
        if s.firebase_credentials.is_file():
            from app.services.firebase_accounts import connect
            st.accounts = connect(str(s.firebase_credentials), st.mailer)
        else:
            st.accounts = AccountStore(acc_dir / "accounts.db", secret)
    except Exception as e:  # noqa: BLE001
        st.load_errors["accounts"] = f"{type(e).__name__}: {e}"
    if st.network is not None:
        from app.fare_engine.fares import FareService
        from app.services.journey_service import JourneyService
        from app.services.ticket_service import TicketService
        st.fares = FareService(st.network, P)
        if not st.fares.profile:
            st.load_errors["fare_profile"] = "route_fare_profile.parquet missing: bus type unknown. Run scripts/validate_fares.py."
        st.tickets = TicketService(st.fares, secret)
        if s.osm_services_enabled:
            from app.services.geo import OsmGeo
            st.geo = OsmGeo()
        st.journeys = JourneyService(st.network, st.crowd, st.fares, st.geo)
        from app.services.live_service import LiveTracker
        st.live = LiveTracker(st.network)
    st.load_seconds = round(time.time() - t0, 1)
    log.info("state loaded in %ss; errors: %s", st.load_seconds, st.load_errors or "none")
    return st


def start_live(st: AppState) -> bool:
    """Start the GTFS-RT poller (called by the API on startup). Without a key the app runs on timetables."""
    s = st.settings
    if st.live is None or not s.live_enabled or not s.ts_api_key:
        if not s.ts_api_key:
            st.load_errors["live_feed"] = "TS_API_KEY not set in .env: live bus positions disabled, timetable only."
        return False
    from app.services.live_service import LivePoller, transport_stack_fetcher
    st.live_poller = LivePoller(st.live, transport_stack_fetcher(s.ts_api_base, s.ts_api_key), s.live_poll_seconds)
    st.live_poller.start()
    return True


def bucket_of(hhmm: str, bucket_minutes: int) -> int:
    h, m = (int(x) for x in hhmm.split(":")[:2])
    if not (0 <= h < 24 and 0 <= m < 60):
        raise ValueError("time must be HH:MM")
    return (h * 60 + m) // bucket_minutes


def np_round(a, n=3):
    return [None if (x is None or (isinstance(x, float) and np.isnan(x))) else round(float(x), n) for x in a]
