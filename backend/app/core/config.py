"""Central configuration. All paths resolve relative to the repository root."""
from __future__ import annotations

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

REPO_ROOT = Path(__file__).resolve().parents[3]
TIMEZONE = "Asia/Kolkata"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=REPO_ROOT / ".env",  # git-ignored
        env_file_encoding="utf-8",
        extra="ignore",
    )

    ts_api_key: str = ""
    ts_api_base: str = "https://dts-backend.transportstack.in/api/dataset/otd/get-file"

    crowdwise_data_dir: Path = REPO_ROOT / "data"
    crowdwise_models_dir: Path = REPO_ROOT / "models"
    crowdwise_bucket_minutes: int = 30
    live_enabled: bool = True          # poll GTFS-RT when an API key is present
    live_poll_seconds: float = 30.0
    osm_services_enabled: bool = True  # Photon place search + OSRM foot routing (server-side)
    demo_ticket_secret: str = ""       # HMAC key for demo ticket QR codes; random per run if empty
    # Firebase service-account key (git-ignored). If the file exists, accounts use Firebase Auth + Firestore;
    # otherwise they use the local SQLite store.
    firebase_credentials: Path = REPO_ROOT / "backend" / "firebase-service-account.json"
    # Branded account emails over SMTP (e.g. Gmail + app password). Empty user/password = not set up.
    smtp_host: str = "smtp.gmail.com"
    smtp_port: int = 465
    smtp_user: str = ""
    smtp_password: str = ""
    mail_from_name: str = "DelhiRaahi"
    crowdwise_cors_origins: str = "http://localhost:5173,http://127.0.0.1:5173"

    # ---- derived paths -------------------------------------------------
    @property
    def raw_dir(self) -> Path:
        return self.crowdwise_data_dir / "raw"

    @property
    def tickets_csv(self) -> Path:
        return self.raw_dir / "October-Delhi.csv"

    @property
    def gtfs_dir(self) -> Path:
        return self.crowdwise_data_dir / "gtfs"

    @property
    def processed_dir(self) -> Path:
        return self.crowdwise_data_dir / "processed"

    @property
    def mappings_dir(self) -> Path:
        return self.crowdwise_data_dir / "mappings"

    @property
    def artifacts_dir(self) -> Path:
        return self.crowdwise_models_dir / "artifacts"

    @property
    def metrics_dir(self) -> Path:
        return self.crowdwise_models_dir / "metrics"

    @property
    def cors_origins(self) -> list[str]:
        return [o.strip() for o in self.crowdwise_cors_origins.split(",") if o.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
