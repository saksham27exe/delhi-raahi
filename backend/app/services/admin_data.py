"""Service alerts and data fixes that admins make on the website; the app and planner use them live.

- Alerts: notices (diversion, stop closed, strike, …) for everyone, some routes or some stops, with a
  start and optional end, in English and Hindi. The app shows the ones that apply.
- Search aliases: another name people use for a stop ("AIIMS" → "AIIMS (Ring Road)"), so stop search finds it.
- Bus type overrides: correct a route's AC / non-AC tag where the e-ticket estimate is wrong.
"""
from __future__ import annotations

import json
from datetime import datetime, timezone

from app.data_pipeline.mapping.normalize import split_route_direction
from app.services.admin_store import Admin, AdminError, AdminStore

SCHEMA = """
CREATE TABLE IF NOT EXISTS alerts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  severity TEXT NOT NULL,           -- info | warning | critical
  title_en TEXT NOT NULL, title_hi TEXT,
  body_en TEXT, body_hi TEXT,
  routes TEXT NOT NULL DEFAULT '[]',   -- route short names; empty with no stops = everyone
  stops TEXT NOT NULL DEFAULT '[]',    -- [{"id":..,"name":..}]
  starts_at TEXT NOT NULL, ends_at TEXT,
  created_by TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS stop_aliases (
  alias TEXT PRIMARY KEY COLLATE NOCASE,
  stop_name TEXT NOT NULL,
  created_by TEXT, created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS bus_type_overrides (
  route TEXT PRIMARY KEY COLLATE NOCASE,   -- route short name, e.g. 534
  bus_type TEXT NOT NULL,                  -- ac | non_ac | mixed
  note TEXT, created_by TEXT, created_at TEXT NOT NULL
);
"""
SEVERITIES = ("info", "warning", "critical")


def route_base(r: str) -> str:
    """'534UP' / '534 down' / '534' -> '534' (GTFS has one route per direction; admins think in bus numbers)."""
    return split_route_direction(str(r))[0] if str(r).strip() else ""
BUS_TYPES = ("ac", "non_ac", "mixed")


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(d: datetime) -> str:
    return d.astimezone(timezone.utc).isoformat(timespec="seconds")


def _parse(s: str | None) -> datetime | None:
    if not s:
        return None
    d = datetime.fromisoformat(s.replace("Z", "+00:00"))
    return d if d.tzinfo else d.replace(tzinfo=timezone.utc)


class AdminData:
    def __init__(self, store: AdminStore):
        self.s = store
        store.migrate(SCHEMA)

    # ---------- alerts ----------
    @staticmethod
    def _alert(r) -> dict:
        d = dict(r)
        d["routes"], d["stops"] = json.loads(d["routes"]), json.loads(d["stops"])
        now = _now()
        st, en = _parse(d["starts_at"]), _parse(d["ends_at"])
        d["state"] = "scheduled" if st > now else "ended" if en and en <= now else "live"
        d["scope"] = "everyone" if not d["routes"] and not d["stops"] else "targeted"
        return d

    def _check(self, a: dict) -> dict:
        if a.get("severity") not in SEVERITIES:
            raise AdminError("Severity must be info, warning or critical.", 422)
        if not (a.get("title_en") or "").strip():
            raise AdminError("Give the alert an English title.", 422)
        st, en = _parse(a.get("starts_at")) or _now(), _parse(a.get("ends_at"))
        if en and en <= st:
            raise AdminError("The end must be after the start.", 422)
        routes = sorted({route_base(r) for r in a.get("routes") or [] if route_base(r)})
        stops = [{"id": str(s["id"]), "name": str(s.get("name") or s["id"])} for s in a.get("stops") or [] if s.get("id")]
        return {"severity": a["severity"], "title_en": a["title_en"].strip()[:120], "title_hi": (a.get("title_hi") or "").strip()[:120] or None,
                "body_en": (a.get("body_en") or "").strip()[:1000] or None, "body_hi": (a.get("body_hi") or "").strip()[:1000] or None,
                "routes": json.dumps(routes), "stops": json.dumps(stops, ensure_ascii=False),
                "starts_at": _iso(st), "ends_at": _iso(en) if en else None}

    def list_alerts(self, state: str | None = None) -> list[dict]:
        rows = [self._alert(r) for r in self.s.q("SELECT * FROM alerts ORDER BY id DESC LIMIT 500")]
        return [r for r in rows if not state or r["state"] == state]

    def get_alert(self, aid: int) -> dict:
        rows = self.s.q("SELECT * FROM alerts WHERE id = ?", (aid,))
        if not rows:
            raise AdminError("No such alert.", 404)
        return self._alert(rows[0])

    def create_alert(self, a: dict, admin: Admin) -> dict:
        v, now = self._check(a), _iso(_now())
        cur = self.s.x(f"INSERT INTO alerts ({', '.join(v)}, created_by, created_at, updated_at) VALUES ({', '.join('?' * len(v))}, ?, ?, ?)",
                       (*v.values(), admin.email, now, now))
        return self.get_alert(cur.lastrowid)

    def update_alert(self, aid: int, a: dict) -> dict:
        cur = self.get_alert(aid)
        # None means "not sent", except for ends_at, where an explicit null means "until ended by hand".
        merged = {**cur, **{k: v for k, v in a.items() if v is not None or k == "ends_at"}}
        v = self._check(merged)
        self.s.x(f"UPDATE alerts SET {', '.join(f'{k} = ?' for k in v)}, updated_at = ? WHERE id = ?", (*v.values(), _iso(_now()), aid))
        return self.get_alert(aid)

    def end_alert(self, aid: int) -> dict:
        self.get_alert(aid)
        self.s.x("UPDATE alerts SET ends_at = ?, updated_at = ? WHERE id = ?", (_iso(_now()), _iso(_now()), aid))
        return self.get_alert(aid)

    def delete_alert(self, aid: int) -> None:
        self.get_alert(aid)
        self.s.x("DELETE FROM alerts WHERE id = ?", (aid,))

    def live_alerts(self, routes: list[str] | None = None, stops: list[str] | None = None, everyone: bool = True) -> list[dict]:
        """Alerts in force now that apply to these routes / stops (plus the ones for everyone)."""
        rq = {route_base(r) for r in routes or [] if route_base(r)}
        sq = {s.strip() for s in stops or [] if s.strip()}
        out = []
        for a in self.list_alerts("live"):
            hit = (a["scope"] == "everyone" and everyone) or rq & set(a["routes"]) or sq & {s["id"] for s in a["stops"]}
            if hit:
                out.append({k: a[k] for k in ("id", "severity", "title_en", "title_hi", "body_en", "body_hi", "routes", "stops",
                                              "starts_at", "ends_at", "scope")})
        order = {"critical": 0, "warning": 1, "info": 2}
        return sorted(out, key=lambda a: (order[a["severity"]], -a["id"]))

    # ---------- search aliases ----------
    def aliases(self) -> list[dict]:
        return [dict(r) for r in self.s.q("SELECT * FROM stop_aliases ORDER BY alias")]

    def add_alias(self, alias: str, stop_name: str, admin: Admin) -> None:
        alias = " ".join(alias.split())[:60]
        if len(alias) < 2:
            raise AdminError("Enter the other name (2+ characters).", 422)
        self.s.x("INSERT OR REPLACE INTO stop_aliases (alias, stop_name, created_by, created_at) VALUES (?,?,?,?)",
                 (alias, stop_name, admin.email, _iso(_now())))

    def delete_alias(self, alias: str) -> None:
        if self.s.x("DELETE FROM stop_aliases WHERE alias = ?", (alias,)).rowcount == 0:
            raise AdminError("No such alias.", 404)

    # ---------- bus type overrides ----------
    def bus_overrides(self) -> list[dict]:
        return [dict(r) for r in self.s.q("SELECT * FROM bus_type_overrides ORDER BY route")]

    def set_bus_override(self, route: str, bus_type: str, note: str | None, admin: Admin) -> None:
        if bus_type not in BUS_TYPES:
            raise AdminError("Bus type must be ac, non_ac or mixed.", 422)
        if not route_base(route):
            raise AdminError("Enter a bus number.", 422)
        self.s.x("INSERT OR REPLACE INTO bus_type_overrides (route, bus_type, note, created_by, created_at) VALUES (?,?,?,?,?)",
                 (route_base(route), bus_type, (note or "").strip()[:200] or None, admin.email, _iso(_now())))

    def delete_bus_override(self, route: str) -> None:
        if self.s.x("DELETE FROM bus_type_overrides WHERE route = ?", (route_base(route),)).rowcount == 0:
            raise AdminError("No such override.", 404)


def apply_fixes(st) -> None:
    """Push the current aliases and bus type overrides into the running planner and fare engine."""
    data = getattr(st, "admin_data", None)
    if data is None:
        return
    if st.journeys is not None and hasattr(st.journeys, "set_aliases"):
        st.journeys.set_aliases({a["alias"]: a["stop_name"] for a in data.aliases()})
    if st.fares is not None and hasattr(st.fares, "set_overrides") and st.network is not None:
        by_base = {o["route"]: o["bus_type"] for o in data.bus_overrides()}
        routes = st.network.routes
        ids = {str(rid): by_base[route_base(short)] for rid, short in zip(routes["route_id"], routes["route_short_name"])
               if route_base(short) in by_base} if by_base else {}
        st.fares.set_overrides(ids)
