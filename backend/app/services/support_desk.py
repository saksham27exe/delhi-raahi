"""Support desk: every complaint / question from the app, its status, priority, owner and history.

Stored in the admin database. Each request has events: the person's message, internal notes,
email replies, and status / priority / assignee changes. Deleting an app account deletes its requests.
"""
from __future__ import annotations

import csv
import io
import json
import re
from datetime import datetime, timezone

from app.services.admin_store import Admin, AdminError, AdminStore
from app.services.support import KINDS, csv_cell

STATUSES = ("new", "in_progress", "waiting", "resolved")
PRIORITIES = ("low", "normal", "high", "urgent")
KINDS = ("complaint", "problem", "ticket", "idea")

SCHEMA = """
CREATE TABLE IF NOT EXISTS support_requests (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  ref TEXT NOT NULL UNIQUE,
  kind TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'new',
  priority TEXT NOT NULL DEFAULT 'normal',
  safety INTEGER NOT NULL DEFAULT 0,
  message TEXT NOT NULL,
  email TEXT,
  user_id TEXT,
  route TEXT,
  when_text TEXT,
  place TEXT,
  app_version TEXT,
  lang TEXT NOT NULL DEFAULT 'en',
  assignee_id INTEGER REFERENCES admins(id) ON DELETE SET NULL,
  emailed INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  resolved_at TEXT
);
CREATE INDEX IF NOT EXISTS ix_sr_status ON support_requests(status, created_at);
CREATE INDEX IF NOT EXISTS ix_sr_user ON support_requests(user_id);
CREATE TABLE IF NOT EXISTS support_events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  request_id INTEGER NOT NULL REFERENCES support_requests(id) ON DELETE CASCADE,
  at TEXT NOT NULL,
  admin_id INTEGER,
  admin_name TEXT,
  kind TEXT NOT NULL,          -- note | reply | status | priority | assign
  body TEXT,
  meta TEXT
);
CREATE INDEX IF NOT EXISTS ix_se_req ON support_events(request_id, id);
"""

# Words that make a complaint a safety matter (English, Hinglish, Hindi): raised to urgent and flagged.
SAFETY = re.compile(
    r"harass|molest|grop|touch(ed|ing)? me|stalk|assault|attack|fight|beat|hit me|injur|accident|crash|bleed|unsafe|danger|"
    r"drunk|drinking|rash|over ?speed|fire|smoke|theft|stole|robbed|pickpocket|knife|weapon|threat|abuse|"
    r"chhed|chhedkhani|maar ?pit|ladai|nashe|chori|"
    r"छेड़|छेड|मारपीट|लड़ाई|चोट|दुर्घटना|नशे|चोरी|धमकी|गाली",
    re.I,
)


MIRRORED = re.compile(r"^\[(DR-\d{4}-[A-Z0-9]{4})\] (\w+)(?: \((.*?)\))?: (.*)$", re.S)


def _now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class SupportDesk:
    def __init__(self, store: AdminStore):
        self.s = store
        store.migrate(SCHEMA)

    # ---------- from the app ----------
    def add(self, ref: str, kind: str, message: str, email: str | None, user_id: str | None, route: str | None,
            when_text: str | None, place: str | None, app_version: str | None, lang: str, created_at: str | None = None) -> int:
        safety = kind == "complaint" and bool(SAFETY.search(message))
        priority = "urgent" if safety else "high" if kind == "complaint" else "normal"
        now = _now()
        created_at = created_at or now
        cur = self.s.x(
            "INSERT INTO support_requests (ref, kind, priority, safety, message, email, user_id, route, when_text, place, app_version, lang, "
            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (ref, kind, priority, int(safety), message, email, user_id, route or None, when_text or None, place or None, app_version, lang, created_at, now))
        return cur.lastrowid

    def has_ref(self, ref: str) -> bool:
        return bool(self.s.q("SELECT 1 FROM support_requests WHERE ref = ?", (ref,)))

    def import_feedback(self, rows: list[dict], email_of=lambda uid: None) -> int:
        """Copy feedback saved by the app into the desk. Safe to run on every start: each row gets a fixed
        reference, so nothing is added twice. Messages from the support form ("[DR-…] kind: text") keep
        their own reference and type."""
        added = 0
        for r in rows:
            text = (r.get("text") or "").strip()
            if not text:
                continue
            m = MIRRORED.match(text)
            if m:
                ref, kind, extra, message = m.group(1), m.group(2), m.group(3), m.group(4)
                kind = kind if kind in KINDS else "feedback"
                details = dict(p.split(": ", 1) for p in (extra or "").split("; ") if ": " in p)
                route, when_text, place = details.get("Bus route"), details.get("When"), details.get("Stop or area")
            else:
                ref, kind, message, route, when_text, place = f"FB-{str(r['id'])[-8:].upper()}", "feedback", text, None, None, None
            if self.has_ref(ref):
                continue
            uid = r.get("user_id")
            email = None
            if uid:
                try:
                    email = email_of(uid)
                except Exception:  # noqa: BLE001  (account gone)
                    email = None
            self.add(ref, kind, message, email, str(uid) if uid else None, route, when_text, place, None, "en",
                     created_at=r.get("created_at") or None)
            added += 1
        return added

    def mark_emailed(self, rid: int) -> None:
        self.s.x("UPDATE support_requests SET emailed = 1 WHERE id = ?", (rid,))

    def delete_user(self, user_id: str) -> int:
        return self.s.x("DELETE FROM support_requests WHERE user_id = ?", (str(user_id),)).rowcount

    # ---------- admin ----------
    def _where(self, status=None, kind=None, priority=None, assignee=None, q=None, route=None, user_id=None, safety=None):
        sql, args = " WHERE 1=1", []
        if status == "open":
            sql += " AND r.status != 'resolved'"
        elif status:
            sql += " AND r.status = ?"; args.append(status)
        if kind:
            sql += " AND r.kind = ?"; args.append(kind)
        if priority:
            sql += " AND r.priority = ?"; args.append(priority)
        if assignee == "none":
            sql += " AND r.assignee_id IS NULL"
        elif assignee:
            try:
                sql += " AND r.assignee_id = ?"; args.append(int(assignee))
            except ValueError:
                raise AdminError("assignee must be an admin id or 'none'.", 422) from None
        if route:
            sql += " AND lower(r.route) = lower(?)"; args.append(route.strip())
        if user_id:
            sql += " AND r.user_id = ?"; args.append(str(user_id))
        if safety:
            sql += " AND r.safety = 1"
        if q:
            like = f"%{q.strip()}%"
            sql += " AND (r.ref LIKE ? OR r.message LIKE ? OR r.email LIKE ? OR r.route LIKE ? OR r.place LIKE ?)"
            args += [like] * 5
        return sql, args

    def list(self, page: int = 1, size: int = 25, sort: str = "priority", **filters) -> dict:
        where, args = self._where(**filters)
        order = {
            "priority": "CASE r.priority WHEN 'urgent' THEN 0 WHEN 'high' THEN 1 WHEN 'normal' THEN 2 ELSE 3 END, "
                        "CASE r.status WHEN 'new' THEN 0 WHEN 'in_progress' THEN 1 WHEN 'waiting' THEN 2 ELSE 3 END, r.id DESC",
            "newest": "r.id DESC", "oldest": "r.id ASC", "updated": "r.updated_at DESC",
        }.get(sort, "r.id DESC")
        size = min(max(size, 1), 100)
        total = self.s.q(f"SELECT COUNT(*) n FROM support_requests r{where}", tuple(args))[0]["n"]
        rows = self.s.q(
            f"SELECT r.*, a.name assignee_name FROM support_requests r LEFT JOIN admins a ON a.id = r.assignee_id{where} "
            f"ORDER BY {order} LIMIT ? OFFSET ?", tuple(args) + (size, (max(page, 1) - 1) * size))
        return {"items": [dict(r) for r in rows], "total": total, "page": page, "pages": max(1, -(-total // size))}

    def counts(self) -> dict:
        by = {r["status"]: r["n"] for r in self.s.q("SELECT status, COUNT(*) n FROM support_requests GROUP BY status")}
        out = {s: by.get(s, 0) for s in STATUSES}
        out["open"] = sum(v for k, v in out.items() if k != "resolved")
        out["urgent_open"] = self.s.q("SELECT COUNT(*) n FROM support_requests WHERE status != 'resolved' AND priority = 'urgent'")[0]["n"]
        out["unassigned_open"] = self.s.q("SELECT COUNT(*) n FROM support_requests WHERE status != 'resolved' AND assignee_id IS NULL")[0]["n"]
        return out

    def get(self, rid: int) -> dict:
        rows = self.s.q("SELECT r.*, a.name assignee_name FROM support_requests r LEFT JOIN admins a ON a.id = r.assignee_id WHERE r.id = ?", (rid,))
        if not rows:
            raise AdminError("No such request.", 404)
        events = [{**dict(e), "meta": json.loads(e["meta"]) if e["meta"] else None}
                  for e in self.s.q("SELECT * FROM support_events WHERE request_id = ? ORDER BY id", (rid,))]
        other = []
        r = dict(rows[0])
        if r["user_id"] or r["email"]:
            other = [dict(x) for x in self.s.q(
                "SELECT id, ref, kind, status, created_at FROM support_requests WHERE id != ? AND (user_id = ? OR (email IS NOT NULL AND email = ?)) "
                "ORDER BY id DESC LIMIT 10", (rid, r["user_id"] or "\0", r["email"] or "\0"))]
        return {**r, "events": events, "other_requests": other}

    def event(self, rid: int, a: Admin | None, kind: str, body: str | None = None, meta: dict | None = None) -> None:
        now = _now()
        with self.s._lock:
            self.s.x("INSERT INTO support_events (request_id, at, admin_id, admin_name, kind, body, meta) VALUES (?,?,?,?,?,?,?)",
                     (rid, now, a.id if a else None, a.name if a else None, kind, body, json.dumps(meta) if meta else None))
            self.s.x("UPDATE support_requests SET updated_at = ? WHERE id = ?", (now, rid))

    def update(self, rid: int, a: Admin, status: str | None = None, priority: str | None = None,
               assignee_id: int | None = None, unassign: bool = False) -> dict:
        cur = self.get(rid)
        changes = {}
        if status and status != cur["status"]:
            if status not in STATUSES:
                raise AdminError("Unknown status.", 422)
            self.s.x("UPDATE support_requests SET status = ?, resolved_at = ? WHERE id = ?",
                     (status, _now() if status == "resolved" else None, rid))
            self.event(rid, a, "status", None, {"from": cur["status"], "to": status})
            changes["status"] = status
        if priority and priority != cur["priority"]:
            if priority not in PRIORITIES:
                raise AdminError("Unknown priority.", 422)
            self.s.x("UPDATE support_requests SET priority = ? WHERE id = ?", (priority, rid))
            self.event(rid, a, "priority", None, {"from": cur["priority"], "to": priority})
            changes["priority"] = priority
        if unassign or (assignee_id is not None and assignee_id != cur["assignee_id"]):
            name = None
            if not unassign:
                rows = self.s.q("SELECT name FROM admins WHERE id = ? AND disabled = 0 AND role != 'viewer'", (assignee_id,))
                if not rows:
                    raise AdminError("Assign to an active agent or owner.", 422)
                name = rows[0]["name"]
            self.s.x("UPDATE support_requests SET assignee_id = ? WHERE id = ?", (None if unassign else assignee_id, rid))
            self.event(rid, a, "assign", None, {"to": name})
            changes["assignee"] = name
        if changes and cur["status"] == "new" and "status" not in changes:
            self.s.x("UPDATE support_requests SET status = 'in_progress' WHERE id = ?", (rid,))
            self.event(rid, a, "status", None, {"from": "new", "to": "in_progress"})
        return changes

    def stats(self, days: int = 30) -> dict:
        # created_at is stored with its offset (UTC, or IST for imported feedback); julianday() reads both as
        # instants, and days are counted in Delhi time so a complaint at 02:00 IST lands on the right day.
        since = f"-{int(days)} days"
        by_kind = {r["kind"]: r["n"] for r in self.s.q(
            "SELECT kind, COUNT(*) n FROM support_requests WHERE julianday(created_at) >= julianday('now', ?) GROUP BY kind", (since,))}
        by_route = [dict(r) for r in self.s.q(
            "SELECT route, COUNT(*) n FROM support_requests WHERE route IS NOT NULL AND kind = 'complaint' AND julianday(created_at) >= julianday('now', ?) "
            "GROUP BY lower(route) ORDER BY n DESC LIMIT 10", (since,))]
        per_day = [dict(r) for r in self.s.q(
            "SELECT date(created_at, '+330 minutes') day, COUNT(*) n FROM support_requests WHERE julianday(created_at) >= julianday('now', ?) GROUP BY day ORDER BY day", (since,))]
        first = self.s.q(
            "SELECT AVG((julianday(e.at) - julianday(r.created_at)) * 24) h FROM support_requests r JOIN "
            "(SELECT request_id, MIN(at) at FROM support_events WHERE kind = 'reply' GROUP BY request_id) e ON e.request_id = r.id "
            "WHERE julianday(r.created_at) >= julianday('now', ?)", (since,))[0]["h"]
        resolve = self.s.q(
            "SELECT AVG((julianday(resolved_at) - julianday(created_at)) * 24) h FROM support_requests "
            "WHERE resolved_at IS NOT NULL AND julianday(created_at) >= julianday('now', ?)", (since,))[0]["h"]
        return {"days": days, "by_kind": by_kind, "complaints_by_route": by_route, "per_day": per_day,
                "avg_first_reply_hours": round(first, 1) if first is not None else None,
                "avg_resolve_hours": round(resolve, 1) if resolve is not None else None, **self.counts()}

    def csv(self, **filters) -> str:
        where, args = self._where(**filters)
        rows = self.s.q(f"SELECT r.*, a.name assignee_name FROM support_requests r LEFT JOIN admins a ON a.id = r.assignee_id{where} ORDER BY r.id",
                        tuple(args))
        cols = ["ref", "created_at", "kind", "status", "priority", "safety", "route", "when_text", "place", "email", "assignee_name",
                "resolved_at", "message"]
        out = io.StringIO()
        w = csv.writer(out)
        w.writerow(cols)
        for r in rows:
            w.writerow([csv_cell(r[c]) for c in cols])
        return out.getvalue()
