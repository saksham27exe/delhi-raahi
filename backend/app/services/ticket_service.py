"""DEMO ticket booking. No payment is taken and tickets are NOT valid for travel.

The server keeps no ticket store (no accounts in v1): a ticket is a signed payload that the
app keeps in its on-device wallet. The QR encodes `<base64url(json)>.<hmac>` so the app (or a
demo 'conductor' screen) can verify it was issued by this server and has not been edited.
Fares are always recomputed on the server from route + stops; client prices are never trusted.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import uuid
from datetime import datetime, timedelta
from zoneinfo import ZoneInfo

from app.fare_engine.fares import FareService, digital_fare

IST = ZoneInfo("Asia/Kolkata")
VALID_MINUTES = 120
MAX_PASSENGERS = 6
WATERMARK = {"en": "DEMO TICKET — not valid for travel, no payment taken",
             "hi": "डेमो टिकट — यात्रा के लिए मान्य नहीं, कोई भुगतान नहीं लिया गया"}


class BookingError(ValueError):
    pass


def _b64(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def _unb64(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


class TicketService:
    def __init__(self, fares: FareService, secret: str = ""):
        self.fares = fares
        self.net = fares.net
        # without a configured secret, tickets verify only until the server restarts (fine for a demo)
        self._key = (secret or secrets.token_hex(32)).encode()

    def _locate(self, route_id: str, board: str, alight: str) -> tuple[int, int, int]:
        for k, pos in self.net.stop_patterns.get(board, []):
            p = self.net.patterns[k]
            if str(p.route_id) != str(route_id):
                continue
            later = [j for j in range(pos + 1, len(p.stops)) if p.stops[j] == alight]
            if later:
                return k, pos, later[0]
        raise BookingError(f"Route {route_id} does not run from stop {board} to stop {alight} in that direction.")

    def quote(self, legs: list[dict], passengers: int = 1, women_free: int = 0,
              bus_types: list[str | None] | None = None) -> dict:
        if not legs:
            raise BookingError("At least one bus leg is required.")
        if not (1 <= passengers <= MAX_PASSENGERS):
            raise BookingError(f"passengers must be 1-{MAX_PASSENGERS}.")
        if not (0 <= women_free <= passengers):
            raise BookingError("women_free must be between 0 and passengers.")
        out, total = [], 0.0
        for i, leg in enumerate(legs):
            k, a, b = self._locate(leg["route_id"], leg["board_stop_id"], leg["alight_stop_id"])
            f = self.fares.leg_fare(k, a, b)
            chosen = (bus_types[i] if bus_types and i < len(bus_types) else None) or f["bus_type"]
            if chosen not in ("ac", "non_ac"):
                chosen = "ac"           # mixed/unknown fleet: price the higher (AC) fare so nobody is undercharged
            unit = f["fare_ac"] if chosen == "ac" else f["fare_non_ac"]
            paying = passengers - women_free
            leg_total = digital_fare(unit) * paying
            total += leg_total
            p = self.net.patterns[k]
            r = self.net.routes.loc[p.route_id] if p.route_id in self.net.routes.index else None
            out.append({"route_id": str(p.route_id), "route": str(r["route_short_name"]) if r is not None else str(p.route_id),
                        "board_stop_id": p.stops[a], "board_stop": self.net.stop_name(p.stops[a]),
                        "alight_stop_id": p.stops[b], "alight_stop": self.net.stop_name(p.stops[b]),
                        "distance_km": f["distance_km"], "bus_type": chosen, "fare_per_passenger": unit,
                        "digital_fare_per_passenger": digital_fare(unit), "paying_passengers": paying,
                        "women_free": women_free, "leg_total": round(leg_total, 2), "note": f["note"]})
        return {"currency": "INR", "legs": out, "passengers": passengers, "women_free": women_free,
                "total": round(total, 2), "demo": True, "watermark": WATERMARK}

    def book(self, legs: list[dict], passengers: int = 1, women_free: int = 0,
             bus_types: list[str | None] | None = None, now: datetime | None = None, holder: str | None = None) -> dict:
        q = self.quote(legs, passengers, women_free, bus_types)
        now = (now or datetime.now(IST)).astimezone(IST)
        body = {"v": 1, "demo": True, "ticket_id": "DEMO-" + uuid.uuid4().hex[:10].upper(),
                "issued_at": now.isoformat(timespec="seconds"),
                "valid_until": (now + timedelta(minutes=VALID_MINUTES)).isoformat(timespec="seconds"),
                "passengers": passengers, "women_free": women_free, "total": q["total"], "currency": "INR",
                "legs": [{k: l[k] for k in ("route", "route_id", "board_stop_id", "board_stop", "alight_stop_id",
                                            "alight_stop", "bus_type", "leg_total")} for l in q["legs"]]}
        if holder:
            body["holder"] = holder      # masked phone: a forwarded QR still shows whose ticket it is
        return {**body, "qr_payload": self._sign(body), "watermark": WATERMARK, "payment": "none (demo)",
                "fare_breakdown": q["legs"]}

    def issue_pass(self, kind: str, bus_type: str, holder: str, now: datetime | None = None) -> dict:
        """DEMO bus pass, signed like a ticket. Price comes from the server catalogue only."""
        from app.services import passes
        if kind not in passes.CATALOG or bus_type not in ("ac", "non_ac"):
            raise BookingError("Unknown pass type.")
        c = passes.CATALOG[kind]
        now = (now or datetime.now(IST)).astimezone(IST)
        body = {"v": 1, "demo": True, "type": "pass", "pass_id": "PASS-" + uuid.uuid4().hex[:10].upper(),
                "kind": kind, "bus_type": bus_type, "price": c["price"][bus_type], "currency": "INR",
                "price_verified": c["verified"], "holder": holder,
                "issued_at": now.isoformat(timespec="seconds"),
                "valid_until": passes.validity(kind, now).isoformat(timespec="seconds")}
        return {**body, "qr_payload": self._sign(body), "watermark": WATERMARK, "payment": "demo wallet (no real money)"}

    def _sign(self, body: dict) -> str:
        raw = json.dumps(body, separators=(",", ":"), ensure_ascii=False, sort_keys=True).encode()
        sig = hmac.new(self._key, raw, hashlib.sha256).digest()[:16]
        return f"{_b64(raw)}.{_b64(sig)}"

    def verify(self, qr_payload: str, now: datetime | None = None) -> dict:
        now = (now or datetime.now(IST)).astimezone(IST)
        try:
            raw_b64, sig_b64 = qr_payload.strip().split(".")
            raw = _unb64(raw_b64)
            ok = hmac.compare_digest(hmac.new(self._key, raw, hashlib.sha256).digest()[:16], _unb64(sig_b64))
            body = json.loads(raw)
        except (ValueError, json.JSONDecodeError):
            return {"valid_signature": False, "status": "unreadable", "demo": True, "watermark": WATERMARK}
        if not ok:
            return {"valid_signature": False, "status": "tampered_or_foreign", "demo": True, "watermark": WATERMARK}
        expired = now > datetime.fromisoformat(body["valid_until"])
        return {"valid_signature": True, "status": "expired" if expired else "active_demo", "demo": True,
                "ticket": body, "watermark": WATERMARK}
