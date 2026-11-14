"""User accounts, DEMO wallet, purchased tickets and passes (SQLite, standard library only).

Security notes
- Passwords: scrypt (n=2^14, r=8, p=1) with a 16-byte random salt; only the hash is stored.
- Login tokens: `<base64url(json)>.<hmac-sha256>` with user id, token version and expiry. Logging out
  bumps the version, which invalidates every token issued before.
- Repeated wrong passwords lock that phone number for a few minutes (in memory).
- Money is DEMO only: balances are integers in paise, top-ups take no payment.
"""
from __future__ import annotations

import base64
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

IST = timezone(timedelta(hours=5, minutes=30))
TOKEN_DAYS = 30
MAX_FAILS, LOCK_SECONDS = 5, 300
MAX_BALANCE_PAISE = 5000_00
TOPUP_MIN_PAISE, TOPUP_MAX_PAISE = 10_00, 2000_00

PHONE_RE = re.compile(r"^[6-9]\d{9}$")


class AccountError(ValueError):
    """Bad input or a rule violation; `status` is the HTTP status to use."""

    def __init__(self, message: str, status: int = 422):
        super().__init__(message)
        self.status = status


def _b64(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def _unb64(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def normalise_phone(raw: str) -> str:
    digits = re.sub(r"\D", "", raw or "")
    if len(digits) == 12 and digits.startswith("91"):
        digits = digits[2:]
    elif len(digits) == 11 and digits.startswith("0"):
        digits = digits[1:]
    if not PHONE_RE.match(digits):
        raise AccountError("Enter a 10-digit Indian mobile number.")
    return digits


def mask_phone(phone: str) -> str:
    return "XXXXXX" + phone[-4:]


def check_password(pw: str) -> None:
    if not (8 <= len(pw or "") <= 128) or not re.search(r"[A-Za-z]", pw) or not re.search(r"\d", pw):
        raise AccountError("Password needs at least 8 characters, with a letter and a number.")


def _hash(pw: str, salt: bytes) -> bytes:
    return hashlib.scrypt(pw.encode(), salt=salt, n=2 ** 14, r=8, p=1, maxmem=64 * 1024 * 1024, dklen=32)


def load_or_create_secret(path: Path) -> str:
    """Server secret kept in a git-ignored file so tickets and logins survive restarts."""
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        return path.read_text(encoding="utf-8").strip()
    s = secrets.token_hex(32)
    path.write_text(s, encoding="utf-8")
    return s


@dataclass
class User:
    id: int
    phone: str
    name: str
    woman_traveller: bool
    created_at: str

    @property
    def holder(self) -> str:
        """How the holder is shown on a ticket or pass (masked)."""
        return mask_phone(self.phone)

    def public(self) -> dict:
        return {"id": self.id, "phone": self.phone, "phone_masked": mask_phone(self.phone), "name": self.name,
                "woman_traveller": self.woman_traveller, "created_at": self.created_at}


SCHEMA = """
CREATE TABLE IF NOT EXISTS users (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  phone TEXT UNIQUE NOT NULL,
  name TEXT NOT NULL,
  pw_salt BLOB NOT NULL,
  pw_hash BLOB NOT NULL,
  token_version INTEGER NOT NULL DEFAULT 1,
  woman_traveller INTEGER NOT NULL DEFAULT 0,
  balance_paise INTEGER NOT NULL DEFAULT 0,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS wallet_tx (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,              -- topup | ticket | pass
  amount_paise INTEGER NOT NULL,   -- + credit, - debit
  balance_after_paise INTEGER NOT NULL,
  ref TEXT,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS items (
  id TEXT PRIMARY KEY,             -- ticket_id or pass_id
  user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,              -- ticket | pass
  payload TEXT NOT NULL,           -- JSON returned to the app (includes signed qr_payload)
  valid_until TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS feedback (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id INTEGER,
  text TEXT NOT NULL,
  created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS ix_tx_user ON wallet_tx(user_id, id);
CREATE INDEX IF NOT EXISTS ix_items_user ON items(user_id, created_at);
"""


class AccountStore:
    def __init__(self, db_path: Path | str, secret: str):
        if str(db_path) != ":memory:":
            Path(db_path).parent.mkdir(parents=True, exist_ok=True)
        self._db = sqlite3.connect(str(db_path), check_same_thread=False, isolation_level=None)
        self._db.row_factory = sqlite3.Row
        self._db.execute("PRAGMA foreign_keys = ON")
        self._db.executescript(SCHEMA)
        self._lock = threading.Lock()
        self._key = hmac.new(secret.encode(), b"delhiraahi-auth-v1", hashlib.sha256).digest()
        self._fails: dict[str, tuple[int, float]] = {}

    # ---------- helpers ----------
    @staticmethod
    def _now() -> str:
        return datetime.now(IST).isoformat(timespec="seconds")

    def _user(self, row: sqlite3.Row | None) -> User | None:
        if row is None:
            return None
        return User(row["id"], row["phone"], row["name"], bool(row["woman_traveller"]), row["created_at"])

    def _row(self, uid: int) -> sqlite3.Row | None:
        return self._db.execute("SELECT * FROM users WHERE id = ?", (uid,)).fetchone()

    def _token(self, row: sqlite3.Row) -> str:
        exp = int(time.time()) + TOKEN_DAYS * 86400
        raw = json.dumps({"u": row["id"], "v": row["token_version"], "exp": exp}, separators=(",", ":")).encode()
        return f"{_b64(raw)}.{_b64(hmac.new(self._key, raw, hashlib.sha256).digest())}"

    # ---------- auth ----------
    def register(self, phone: str, password: str, name: str) -> tuple[str, User]:
        phone = normalise_phone(phone)
        check_password(password)
        name = (name or "").strip()
        if not 1 <= len(name) <= 60:
            raise AccountError("Enter your name (up to 60 characters).")
        salt = secrets.token_bytes(16)
        with self._lock:
            try:
                cur = self._db.execute(
                    "INSERT INTO users (phone, name, pw_salt, pw_hash, created_at) VALUES (?, ?, ?, ?, ?)",
                    (phone, name, salt, _hash(password, salt), self._now()))
            except sqlite3.IntegrityError:
                raise AccountError("An account with this number already exists. Log in instead.", 409) from None
            row = self._row(cur.lastrowid)
        return self._token(row), self._user(row)

    def login(self, phone: str, password: str) -> tuple[str, User]:
        phone = normalise_phone(phone)
        fails, until = self._fails.get(phone, (0, 0.0))
        if until > time.time():
            raise AccountError(f"Too many attempts. Try again in {int(until - time.time()) // 60 + 1} min.", 429)
        row = self._db.execute("SELECT * FROM users WHERE phone = ?", (phone,)).fetchone()
        # Hash even for unknown numbers so timing does not reveal which numbers have accounts.
        salt = row["pw_salt"] if row else b"\0" * 16
        ok = hmac.compare_digest(_hash(password or "", salt), row["pw_hash"] if row else b"\0" * 32)
        if not (row and ok):
            fails += 1
            self._fails[phone] = (0, time.time() + LOCK_SECONDS) if fails >= MAX_FAILS else (fails, 0.0)
            raise AccountError("Wrong mobile number or password.", 401)
        self._fails.pop(phone, None)
        return self._token(row), self._user(row)

    def user_for_token(self, token: str) -> User | None:
        try:
            raw_b64, sig_b64 = token.strip().split(".")
            raw = _unb64(raw_b64)
            if not hmac.compare_digest(hmac.new(self._key, raw, hashlib.sha256).digest(), _unb64(sig_b64)):
                return None
            body = json.loads(raw)
        except (ValueError, json.JSONDecodeError):
            return None
        if body.get("exp", 0) < time.time():
            return None
        row = self._row(int(body["u"]))
        if row is None or row["token_version"] != body.get("v"):
            return None
        return self._user(row)

    def logout(self, uid: int) -> None:
        with self._lock:
            self._db.execute("UPDATE users SET token_version = token_version + 1 WHERE id = ?", (uid,))

    def update_profile(self, uid: int, name: str | None = None, woman_traveller: bool | None = None) -> User:
        with self._lock:
            if name is not None:
                name = name.strip()
                if not 1 <= len(name) <= 60:
                    raise AccountError("Enter your name (up to 60 characters).")
                self._db.execute("UPDATE users SET name = ? WHERE id = ?", (name, uid))
            if woman_traveller is not None:
                self._db.execute("UPDATE users SET woman_traveller = ? WHERE id = ?", (int(woman_traveller), uid))
            return self._user(self._row(uid))

    def change_password(self, uid: int, old: str, new: str) -> str:
        row = self._row(uid)
        if not hmac.compare_digest(_hash(old or "", row["pw_salt"]), row["pw_hash"]):
            raise AccountError("Current password is wrong.", 401)
        check_password(new)
        salt = secrets.token_bytes(16)
        with self._lock:
            self._db.execute("UPDATE users SET pw_salt = ?, pw_hash = ?, token_version = token_version + 1 WHERE id = ?",
                             (salt, _hash(new, salt), uid))
            return self._token(self._row(uid))

    def delete(self, uid: int, password: str) -> None:
        """Permanently delete the account with its wallet history, tickets, passes and feedback."""
        row = self._row(uid)
        if row is None or not hmac.compare_digest(_hash(password or "", row["pw_salt"]), row["pw_hash"]):
            raise AccountError("Password is wrong.", 401)
        with self._lock:
            self._db.execute("DELETE FROM feedback WHERE user_id = ?", (uid,))
            self._db.execute("DELETE FROM users WHERE id = ?", (uid,))     # cascades to wallet_tx and items

    # ---------- DEMO wallet ----------
    def wallet(self, uid: int, limit: int = 50) -> dict:
        bal = self._row(uid)["balance_paise"]
        tx = self._db.execute("SELECT kind, amount_paise, balance_after_paise, ref, created_at FROM wallet_tx "
                              "WHERE user_id = ? ORDER BY id DESC LIMIT ?", (uid, limit)).fetchall()
        return {"balance": bal / 100, "demo": True,
                "transactions": [{"kind": r["kind"], "amount": r["amount_paise"] / 100,
                                  "balance_after": r["balance_after_paise"] / 100, "ref": r["ref"],
                                  "created_at": r["created_at"]} for r in tx]}

    def topup(self, uid: int, amount: float) -> dict:
        paise = round(amount * 100)
        if not TOPUP_MIN_PAISE <= paise <= TOPUP_MAX_PAISE:
            raise AccountError(f"Add between ₹{TOPUP_MIN_PAISE // 100} and ₹{TOPUP_MAX_PAISE // 100}.")
        with self._lock:
            bal = self._row(uid)["balance_paise"] + paise
            if bal > MAX_BALANCE_PAISE:
                raise AccountError(f"Demo wallet limit is ₹{MAX_BALANCE_PAISE // 100}.")
            self._db.execute("BEGIN")
            self._db.execute("UPDATE users SET balance_paise = ? WHERE id = ?", (bal, uid))
            self._db.execute("INSERT INTO wallet_tx (user_id, kind, amount_paise, balance_after_paise, ref, created_at) "
                             "VALUES (?, 'topup', ?, ?, 'demo top-up, no payment', ?)", (uid, paise, bal, self._now()))
            self._db.execute("COMMIT")
        return self.wallet(uid)

    def purchase(self, uid: int, kind: str, price: float, make_item) -> dict:
        """Debit the wallet and store the item atomically. `make_item()` builds the signed item dict."""
        paise = round(price * 100)
        with self._lock:
            bal = self._row(uid)["balance_paise"]
            if bal < paise:
                raise AccountError(f"Not enough balance in your demo wallet (₹{bal / 100:g}). Add money first.", 402)
            item = make_item()
            item_id = item.get("ticket_id") or item["pass_id"]
            self._db.execute("BEGIN")
            try:
                self._db.execute("UPDATE users SET balance_paise = ? WHERE id = ?", (bal - paise, uid))
                self._db.execute("INSERT INTO wallet_tx (user_id, kind, amount_paise, balance_after_paise, ref, created_at) "
                                 "VALUES (?, ?, ?, ?, ?, ?)", (uid, kind, -paise, bal - paise, item_id, self._now()))
                self._db.execute("INSERT INTO items (id, user_id, kind, payload, valid_until, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                                 (item_id, uid, kind, json.dumps(item, ensure_ascii=False), item["valid_until"], self._now()))
                self._db.execute("COMMIT")
            except Exception:
                self._db.execute("ROLLBACK")
                raise
        return item

    def items(self, uid: int, kind: str) -> list[dict]:
        rows = self._db.execute("SELECT payload FROM items WHERE user_id = ? AND kind = ? ORDER BY created_at DESC LIMIT 100",
                                (uid, kind)).fetchall()
        return [json.loads(r["payload"]) for r in rows]

    def item_on_record(self, item_id: str) -> bool:
        return self._db.execute("SELECT 1 FROM items WHERE id = ?", (item_id,)).fetchone() is not None

    def add_feedback(self, uid: int | None, text: str) -> None:
        text = (text or "").strip()
        if not 3 <= len(text) <= 2000:
            raise AccountError("Write between 3 and 2000 characters.")
        with self._lock:
            self._db.execute("INSERT INTO feedback (user_id, text, created_at) VALUES (?, ?, ?)", (uid, text, self._now()))
