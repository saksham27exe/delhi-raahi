"""Admin side of DelhiRaahi: admin accounts, sessions and the audit log (more tables join in later phases).

One SQLite file, `data/admin/admin.db` (git-ignored). Admin accounts exist only here — they are created
with `scripts/create_admin.py`, never through the app — and sign in to the website at /admin.

Passwords: scrypt (n=2^14, r=8, p=1) with a random salt. Sessions: a random token in an HttpOnly cookie;
only its SHA-256 is stored. They end after 30 idle minutes or 12 hours, whichever comes first.
Roles: owner (everything, incl. managing admins) > agent (support, users) > viewer (read only).

Audit ledger (the "blockchain" on the Admins page): every audit entry is a block whose hash covers its own
fields and the previous block's hash, signed with HMAC-SHA256 using a key kept outside the database
(`data/admin/ledger_key.txt`). Changing, removing or reordering any past entry breaks every hash after it,
and without the key nobody can quietly recompute the chain. The latest block number and hash are also
written to `data/admin/ledger_head.json`, so cutting entries off the end is caught too.
"""
from __future__ import annotations

import hashlib
import hmac
import json
import secrets
import sqlite3
import threading
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROLES = ("viewer", "agent", "owner")
IDLE = timedelta(minutes=30)
MAX_AGE = timedelta(hours=12)
LOCK_AFTER = 5                      # failed sign-ins …
LOCK_FOR = timedelta(minutes=15)    # … lock the account for this long
MIN_PASSWORD = 12

SCHEMA = """
CREATE TABLE IF NOT EXISTS admins (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  email TEXT NOT NULL UNIQUE COLLATE NOCASE,
  name TEXT NOT NULL,
  role TEXT NOT NULL,
  pw_salt BLOB NOT NULL,
  pw_hash BLOB NOT NULL,
  must_change INTEGER NOT NULL DEFAULT 1,
  disabled INTEGER NOT NULL DEFAULT 0,
  failed INTEGER NOT NULL DEFAULT 0,
  locked_until TEXT,
  created_at TEXT NOT NULL,
  last_login TEXT
);
CREATE TABLE IF NOT EXISTS admin_sessions (
  token_hash TEXT PRIMARY KEY,
  admin_id INTEGER NOT NULL REFERENCES admins(id) ON DELETE CASCADE,
  csrf TEXT NOT NULL,
  created_at TEXT NOT NULL,
  last_seen TEXT NOT NULL,
  ip TEXT
);
CREATE TABLE IF NOT EXISTS audit (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  at TEXT NOT NULL,
  admin_id INTEGER,
  admin_email TEXT,
  action TEXT NOT NULL,
  target TEXT,
  detail TEXT,
  ip TEXT
);
CREATE INDEX IF NOT EXISTS ix_audit_at ON audit(at);
"""


class AdminError(Exception):
    def __init__(self, msg: str, status: int = 400):
        super().__init__(msg)
        self.status = status


@dataclass
class Admin:
    id: int
    email: str
    name: str
    role: str
    must_change: bool
    csrf: str = ""

    def can(self, role: str) -> bool:
        return ROLES.index(self.role) >= ROLES.index(role)

    def public(self) -> dict:
        return {"id": self.id, "email": self.email, "name": self.name, "role": self.role, "must_change": self.must_change}


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(d: datetime) -> str:
    return d.isoformat(timespec="seconds")


def _hash(password: str, salt: bytes) -> bytes:
    return hashlib.scrypt(password.encode("utf-8"), salt=salt, n=2 ** 14, r=8, p=1, dklen=32)


def check_password_strength(pw: str) -> None:
    if len(pw or "") < MIN_PASSWORD:
        raise AdminError(f"Use at least {MIN_PASSWORD} characters.", 422)
    if pw.lower() == pw or pw.upper() == pw or not any(c.isdigit() for c in pw):
        raise AdminError("Mix upper- and lower-case letters and at least one digit.", 422)


class AdminStore:
    def __init__(self, path: Path | str):
        if str(path) != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self._db = sqlite3.connect(str(path), check_same_thread=False, isolation_level=None)
        self._db.row_factory = sqlite3.Row
        self._db.execute("PRAGMA foreign_keys = ON")
        self._db.execute("PRAGMA journal_mode = WAL") if str(path) != ":memory:" else None
        self._lock = threading.RLock()
        self.migrate(SCHEMA)
        self._ledger_init(path)

    def migrate(self, sql: str) -> None:
        with self._lock:
            self._db.executescript(sql)

    def q(self, sql: str, args: tuple = ()) -> list[sqlite3.Row]:
        return self._db.execute(sql, args).fetchall()

    def x(self, sql: str, args: tuple = ()) -> sqlite3.Cursor:
        with self._lock:
            return self._db.execute(sql, args)

    # ---------- admins ----------
    def create_admin(self, email: str, name: str, role: str, password: str, must_change: bool = True) -> int:
        email = (email or "").strip().lower()
        if "@" not in email:
            raise AdminError("Enter an email address.", 422)
        if role not in ROLES:
            raise AdminError(f"Role must be one of {', '.join(ROLES)}.", 422)
        check_password_strength(password)
        salt = secrets.token_bytes(16)
        try:
            cur = self.x("INSERT INTO admins (email, name, role, pw_salt, pw_hash, must_change, created_at) VALUES (?,?,?,?,?,?,?)",
                         (email, name.strip() or email.split("@")[0], role, salt, _hash(password, salt), int(must_change), _iso(_now())))
        except sqlite3.IntegrityError:
            raise AdminError("An admin with this email already exists.", 409) from None
        return cur.lastrowid

    def list_admins(self) -> list[dict]:
        return [{k: r[k] for k in ("id", "email", "name", "role", "disabled", "must_change", "created_at", "last_login")}
                for r in self.q("SELECT * FROM admins ORDER BY id")]

    def owners(self) -> int:
        return self.q("SELECT COUNT(*) n FROM admins WHERE role = 'owner' AND disabled = 0")[0]["n"]

    def update_admin(self, admin_id: int, role: str | None = None, disabled: bool | None = None, name: str | None = None) -> None:
        r = self._admin_row(admin_id)
        if r is None:
            raise AdminError("No such admin.", 404)
        if role is not None and role not in ROLES:
            raise AdminError("Unknown role.", 422)
        losing_owner = r["role"] == "owner" and not r["disabled"] and ((role and role != "owner") or disabled)
        if losing_owner and self.owners() <= 1:
            raise AdminError("Keep at least one active owner.", 409)
        with self._lock:
            if role is not None:
                self.x("UPDATE admins SET role = ? WHERE id = ?", (role, admin_id))
            if name is not None:
                self.x("UPDATE admins SET name = ? WHERE id = ?", (name.strip()[:60] or r["name"], admin_id))
            if disabled is not None:
                self.x("UPDATE admins SET disabled = ? WHERE id = ?", (int(disabled), admin_id))
                if disabled:
                    self.x("DELETE FROM admin_sessions WHERE admin_id = ?", (admin_id,))

    def reset_password(self, admin_id: int) -> str:
        """New random password the admin must change at next sign-in; signs them out everywhere."""
        if self._admin_row(admin_id) is None:
            raise AdminError("No such admin.", 404)
        pw = generate_password()
        salt = secrets.token_bytes(16)
        with self._lock:
            self.x("UPDATE admins SET pw_salt = ?, pw_hash = ?, must_change = 1, failed = 0, locked_until = NULL WHERE id = ?",
                   (salt, _hash(pw, salt), admin_id))
            self.x("DELETE FROM admin_sessions WHERE admin_id = ?", (admin_id,))
        return pw

    def change_password(self, admin_id: int, old: str, new: str, keep_token_hash: str | None = None) -> None:
        r = self._admin_row(admin_id)
        if r is None or not hmac.compare_digest(_hash(old or "", r["pw_salt"]), r["pw_hash"]):
            raise AdminError("Current password is wrong.", 401)
        if old == new:
            raise AdminError("Choose a password different from the current one.", 422)
        check_password_strength(new)
        salt = secrets.token_bytes(16)
        with self._lock:
            self.x("UPDATE admins SET pw_salt = ?, pw_hash = ?, must_change = 0 WHERE id = ?", (salt, _hash(new, salt), admin_id))
            self.x("DELETE FROM admin_sessions WHERE admin_id = ? AND token_hash != ?", (admin_id, keep_token_hash or ""))

    def _admin_row(self, admin_id: int) -> sqlite3.Row | None:
        rows = self.q("SELECT * FROM admins WHERE id = ?", (admin_id,))
        return rows[0] if rows else None

    # ---------- sessions ----------
    def login(self, email: str, password: str, ip: str | None) -> tuple[str, Admin]:
        email = (email or "").strip().lower()
        rows = self.q("SELECT * FROM admins WHERE email = ?", (email,))
        now = _now()
        bad = AdminError("Email or password is wrong.", 401)
        if not rows:
            _hash(password or "", b"0" * 16)        # same work either way: no timing hint that the email exists
            raise bad
        r = rows[0]
        if r["locked_until"] and datetime.fromisoformat(r["locked_until"]) > now:
            raise AdminError("Too many wrong passwords. Try again in 15 minutes.", 429)
        if not hmac.compare_digest(_hash(password or "", r["pw_salt"]), r["pw_hash"]):
            failed = r["failed"] + 1
            lock = _iso(now + LOCK_FOR) if failed >= LOCK_AFTER else None
            self.x("UPDATE admins SET failed = ?, locked_until = ? WHERE id = ?", (0 if lock else failed, lock, r["id"]))
            self.audit(None, email, "login_failed", f"admin:{r['id']}", {"locked": bool(lock)}, ip)
            raise bad
        if r["disabled"]:
            raise AdminError("This admin account is disabled.", 403)
        token, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(24)
        with self._lock:
            self.x("UPDATE admins SET failed = 0, locked_until = NULL, last_login = ? WHERE id = ?", (_iso(now), r["id"]))
            self.x("INSERT INTO admin_sessions (token_hash, admin_id, csrf, created_at, last_seen, ip) VALUES (?,?,?,?,?,?)",
                   (token_hash(token), r["id"], csrf, _iso(now), _iso(now), ip))
            self.x("DELETE FROM admin_sessions WHERE created_at < ?", (_iso(now - MAX_AGE),))
        a = Admin(r["id"], r["email"], r["name"], r["role"], bool(r["must_change"]), csrf)
        self.audit(a, None, "login", None, None, ip)
        return token, a

    def session(self, token: str | None) -> Admin | None:
        if not token:
            return None
        th = token_hash(token)
        rows = self.q("SELECT s.*, a.email, a.name, a.role, a.must_change, a.disabled FROM admin_sessions s "
                      "JOIN admins a ON a.id = s.admin_id WHERE s.token_hash = ?", (th,))
        if not rows:
            return None
        r, now = rows[0], _now()
        if r["disabled"] or now - datetime.fromisoformat(r["last_seen"]) > IDLE or now - datetime.fromisoformat(r["created_at"]) > MAX_AGE:
            self.x("DELETE FROM admin_sessions WHERE token_hash = ?", (th,))
            return None
        self.x("UPDATE admin_sessions SET last_seen = ? WHERE token_hash = ?", (_iso(now), th))
        return Admin(r["admin_id"], r["email"], r["name"], r["role"], bool(r["must_change"]), r["csrf"])

    def logout(self, token: str | None) -> None:
        if token:
            self.x("DELETE FROM admin_sessions WHERE token_hash = ?", (token_hash(token),))

    # ---------- audit ----------
    def audit(self, a: Admin | None, email: str | None, action: str, target: str | None, detail: dict | None, ip: str | None) -> None:
        with self._lock:
            self._db.execute("INSERT INTO audit (at, admin_id, admin_email, action, target, detail, ip) VALUES (?,?,?,?,?,?,?)",
                             (_iso(_now()), a.id if a else None, a.email if a else email, action, target,
                              json.dumps(detail, ensure_ascii=False) if detail else None, ip))
            self._seal()

    # ---------- audit ledger (hash chain) ----------
    GENESIS = "0" * 64

    def _ledger_init(self, path: Path | str) -> None:
        cols = {r["name"] for r in self._db.execute("PRAGMA table_info(audit)")}
        with self._lock:
            if "prev_hash" not in cols:
                self._db.execute("ALTER TABLE audit ADD COLUMN prev_hash TEXT")
            if "hash" not in cols:
                self._db.execute("ALTER TABLE audit ADD COLUMN hash TEXT")
        if str(path) == ":memory:":
            self._ledger_key, self._head_path, self._head_mem = secrets.token_bytes(32), None, None
        else:
            key_file = Path(path).parent / "ledger_key.txt"
            if not key_file.is_file():
                key_file.write_text(secrets.token_hex(32), encoding="utf-8")
            self._ledger_key = bytes.fromhex(key_file.read_text(encoding="utf-8").strip())
            self._head_path, self._head_mem = Path(path).parent / "ledger_head.json", None
        with self._lock:
            self._seal()          # older entries (from before the ledger) are sealed once, in order

    def _block_hash(self, prev: str, r) -> str:
        body = json.dumps([prev, r["id"], r["at"], r["admin_id"], r["admin_email"], r["action"], r["target"], r["detail"], r["ip"]],
                          ensure_ascii=False, separators=(",", ":"))
        return hmac.new(self._ledger_key, body.encode("utf-8"), hashlib.sha256).hexdigest()

    def _seal(self) -> None:
        """Hash every entry that has no hash yet, chaining from the last sealed one. Call with the lock held."""
        last = self._db.execute("SELECT hash FROM audit WHERE hash IS NOT NULL ORDER BY id DESC LIMIT 1").fetchone()
        prev = last["hash"] if last else self.GENESIS
        rows = self._db.execute("SELECT * FROM audit WHERE hash IS NULL ORDER BY id").fetchall()
        for r in rows:
            h = self._block_hash(prev, r)
            self._db.execute("UPDATE audit SET prev_hash = ?, hash = ? WHERE id = ?", (prev, h, r["id"]))
            prev = h
            self._write_head(r["id"], h)

    def _write_head(self, block: int, h: str) -> None:
        head = {"block": block, "hash": h, "at": _iso(_now())}
        if self._head_path is None:
            self._head_mem = head
        else:
            self._head_path.write_text(json.dumps(head), encoding="utf-8")

    def _read_head(self) -> dict | None:
        if self._head_path is None:
            return self._head_mem
        try:
            return json.loads(self._head_path.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            return None

    def ledger(self, limit: int = 20) -> list[dict]:
        rows = self.q("SELECT id, at, admin_email, action, target, prev_hash, hash FROM audit ORDER BY id DESC LIMIT ?", (limit,))
        return [dict(r) for r in rows]

    def verify_ledger(self) -> dict:
        """Recompute the whole chain. Returns ok, how many blocks, the head, and the first broken block if any."""
        with self._lock:
            rows = self._db.execute("SELECT * FROM audit ORDER BY id").fetchall()
        prev, count = self.GENESIS, 0
        for r in rows:
            if r["prev_hash"] != prev:
                return {"ok": False, "blocks": count, "broken_at": r["id"], "reason": "A block before this one was changed or removed."}
            if r["hash"] != self._block_hash(prev, r):
                return {"ok": False, "blocks": count, "broken_at": r["id"], "reason": "This block's contents were changed."}
            prev, count = r["hash"], count + 1
        head = self._read_head()
        last_id = rows[-1]["id"] if rows else None
        if head and (head.get("block") != last_id or head.get("hash") != prev):
            return {"ok": False, "blocks": count, "broken_at": head.get("block"),
                    "reason": "The newest blocks were removed (the chain is shorter than the saved head)."}
        return {"ok": True, "blocks": count, "head": prev, "head_block": last_id, "broken_at": None, "reason": None}

    def audit_log(self, limit: int = 100, before: int | None = None, action: str | None = None, admin: str | None = None) -> list[dict]:
        sql, args = "SELECT * FROM audit WHERE 1=1", []
        if before:
            sql += " AND id < ?"; args.append(before)
        if action:
            sql += " AND action = ?"; args.append(action)
        if admin:
            sql += " AND admin_email = ?"; args.append(admin.lower())
        sql += " ORDER BY id DESC LIMIT ?"; args.append(min(max(limit, 1), 500))
        return [{**dict(r), "detail": json.loads(r["detail"]) if r["detail"] else None} for r in self.q(sql, tuple(args))]


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def generate_password() -> str:
    """16 characters that always pass check_password_strength."""
    while True:
        pw = secrets.token_urlsafe(12)[:16]
        try:
            check_password_strength(pw)
            return pw
        except AdminError:
            continue
