"""Accounts on Firebase: sign-in is Firebase Authentication (email + password, done in the app);
users, the DEMO wallet, tickets, passes and feedback live in Cloud Firestore.

Same interface as `AccountStore` (SQLite), so the routes and ticket code do not change.
The app sends its Firebase ID token as `Authorization: Bearer <token>`; we verify it with the
Admin SDK. Firestore runs in production mode (no client access): only this server reads or writes it.

Firestore layout
  users/{uid}                    email, name, woman_traveller, balance_paise, created_at
  users/{uid}/wallet_tx/{auto}   kind, amount_paise, balance_after_paise, ref, created_at, ts
  items/{ticket_or_pass_id}      user_id, kind, payload (the signed item), valid_until, created_at
  feedback/{auto}                user_id, text, created_at
"""
from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Callable

from app.services.accounts import (IST, MAX_BALANCE_PAISE, TOPUP_MAX_PAISE, TOPUP_MIN_PAISE, AccountError)


def mask_email(email: str) -> str:
    name, _, domain = (email or "").partition("@")
    if not domain:
        return email
    return (name[:2] + "•" * max(1, len(name) - 2)) + "@" + domain


@dataclass
class FbUser:
    id: str
    email: str
    name: str
    woman_traveller: bool
    created_at: str
    verified: bool = False          # email verified (from the Firebase token)

    @property
    def holder(self) -> str:
        """How the holder is shown on a ticket or pass (masked email)."""
        return mask_email(self.email)

    def public(self) -> dict:
        # phone fields kept (empty) so older app builds still parse the response
        return {"id": self.id, "email": self.email, "email_masked": mask_email(self.email), "phone": "", "phone_masked": "",
                "name": self.name, "woman_traveller": self.woman_traveller, "created_at": self.created_at,
                "email_verified": self.verified}


# ---------------------------------------------------------------------------------------------
# Storage: Firestore in production, an in-memory twin for tests. Both expose the same few calls.
# ---------------------------------------------------------------------------------------------
class MemoryRepo:
    def __init__(self) -> None:
        self.users: dict[str, dict] = {}
        self.tx: dict[str, list[dict]] = {}
        self.items: dict[str, dict] = {}
        self.feedback: list[dict] = []
        self._lock = threading.Lock()

    def get_user(self, uid: str) -> dict | None:
        u = self.users.get(uid)
        return dict(u) if u else None

    def create_user(self, uid: str, data: dict) -> dict:
        with self._lock:
            self.users.setdefault(uid, dict(data))
            return dict(self.users[uid])

    def update_user(self, uid: str, fields: dict) -> None:
        with self._lock:
            self.users[uid].update(fields)

    def atomic(self, uid: str, fn: Callable[[dict], tuple[dict, list[dict], list[tuple[str, dict]]]]) -> Any:
        with self._lock:
            updates, txs, items = fn(dict(self.users[uid]))
            self.users[uid].update(updates)
            self.tx.setdefault(uid, []).extend(txs)
            for k, v in items:
                self.items[k] = v

    def list_tx(self, uid: str, limit: int) -> list[dict]:
        return sorted(self.tx.get(uid, []), key=lambda r: r["ts"], reverse=True)[:limit]

    def list_items(self, uid: str) -> list[dict]:
        return [v for v in self.items.values() if v["user_id"] == uid]

    def has_item(self, item_id: str) -> bool:
        return item_id in self.items

    def add_feedback(self, data: dict) -> None:
        self.feedback.append(data)

    def delete_user_data(self, uid: str) -> None:
        with self._lock:
            self.users.pop(uid, None)
            self.tx.pop(uid, None)
            self.items = {k: v for k, v in self.items.items() if v["user_id"] != uid}
            self.feedback = [f for f in self.feedback if f.get("user_id") != uid]


class FirestoreRepo:
    def __init__(self, client) -> None:
        from google.cloud import firestore  # imported here so tests need no Google libraries
        self._fs = firestore
        self.db = client

    def _u(self, uid: str):
        return self.db.collection("users").document(uid)

    def get_user(self, uid: str) -> dict | None:
        s = self._u(uid).get()
        return s.to_dict() if s.exists else None

    def create_user(self, uid: str, data: dict) -> dict:
        ref = self._u(uid)
        try:
            ref.create(data)            # fails if it already exists (two first requests at once)
        except Exception:  # noqa: BLE001  (AlreadyExists)
            pass
        return ref.get().to_dict()

    def update_user(self, uid: str, fields: dict) -> None:
        self._u(uid).update(fields)

    def atomic(self, uid: str, fn) -> Any:
        ref = self._u(uid)
        tx_col = ref.collection("wallet_tx")
        items = self.db.collection("items")

        @self._fs.transactional
        def run(t):
            snap = ref.get(transaction=t)
            updates, txs, its = fn(snap.to_dict())
            t.update(ref, updates)
            for r in txs:
                t.set(tx_col.document(), r)
            for k, v in its:
                t.set(items.document(k), v)

        run(self.db.transaction())

    def list_tx(self, uid: str, limit: int) -> list[dict]:
        q = self._u(uid).collection("wallet_tx").order_by("ts", direction=self._fs.Query.DESCENDING).limit(limit)
        return [d.to_dict() for d in q.stream()]

    def list_items(self, uid: str) -> list[dict]:
        # Single-field filter only: no composite index to create in the console.
        q = self.db.collection("items").where(filter=self._fs.FieldFilter("user_id", "==", uid))
        return [d.to_dict() for d in q.stream()]

    def has_item(self, item_id: str) -> bool:
        return self.db.collection("items").document(item_id).get().exists

    def add_feedback(self, data: dict) -> None:
        self.db.collection("feedback").add(data)

    def delete_user_data(self, uid: str) -> None:
        ref = self._u(uid)
        for d in ref.collection("wallet_tx").stream():
            d.reference.delete()
        for d in self.db.collection("items").where(filter=self._fs.FieldFilter("user_id", "==", uid)).stream():
            d.reference.delete()
        for d in self.db.collection("feedback").where(filter=self._fs.FieldFilter("user_id", "==", uid)).stream():
            d.reference.delete()
        ref.delete()


# ---------------------------------------------------------------------------------------------
class FirebaseAccountStore:
    """`verify(token) -> claims` checks a Firebase ID token; `delete_auth(uid)` removes the sign-in."""

    kind = "firebase"

    def __init__(self, repo, verify: Callable[[str], dict], delete_auth: Callable[[str], None] = lambda uid: None):
        self.repo = repo
        self._verify = verify
        self._delete_auth = delete_auth

    @staticmethod
    def _now() -> str:
        return datetime.now(IST).isoformat(timespec="seconds")

    @staticmethod
    def _user(uid: str, d: dict) -> FbUser:
        return FbUser(uid, d.get("email", ""), d.get("name", ""), bool(d.get("woman_traveller")), d.get("created_at", ""))

    # ---------- sign-in (Firebase does it in the app) ----------
    def register(self, *_a, **_k):
        raise AccountError("Sign-up happens in the app with Firebase. Update the app.", 410)

    login = register

    def change_password(self, *_a, **_k):
        raise AccountError("Change your password in the app (Firebase).", 410)

    def logout(self, uid: str) -> None:
        """Signing out is done in the app; nothing is kept here."""

    def user_for_token(self, token: str) -> FbUser | None:
        try:
            claims = self._verify(token.strip())
        except Exception:  # noqa: BLE001  (expired, revoked, malformed, wrong project)
            return None
        uid = claims.get("uid") or claims.get("sub")
        if not uid:
            return None
        d = self.repo.get_user(uid)
        if d is None:
            # First request after sign-up: create the profile.
            email = claims.get("email", "")
            name = (claims.get("name") or email.split("@")[0] or "Traveller")[:60]
            d = self.repo.create_user(uid, {"email": email, "name": name, "woman_traveller": False,
                                            "balance_paise": 0, "created_at": self._now()})
        u = self._user(uid, d)
        u.verified = bool(claims.get("email_verified"))
        return u

    def update_profile(self, uid: str, name: str | None = None, woman_traveller: bool | None = None) -> FbUser:
        f: dict = {}
        if name is not None:
            name = name.strip()
            if not 1 <= len(name) <= 60:
                raise AccountError("Enter your name (up to 60 characters).")
            f["name"] = name
        if woman_traveller is not None:
            f["woman_traveller"] = bool(woman_traveller)
        if f:
            self.repo.update_user(uid, f)
        return self._user(uid, self.repo.get_user(uid))

    def delete(self, uid: str, _password: str = "") -> None:
        """The app re-checks the password with Firebase first; here: delete data, then the sign-in."""
        self.repo.delete_user_data(uid)
        self._delete_auth(uid)

    # ---------- DEMO wallet ----------
    def wallet(self, uid: str, limit: int = 50) -> dict:
        bal = (self.repo.get_user(uid) or {}).get("balance_paise", 0)
        tx = self.repo.list_tx(uid, limit) if limit else []
        return {"balance": bal / 100, "demo": True,
                "transactions": [{"kind": r["kind"], "amount": r["amount_paise"] / 100, "balance_after": r["balance_after_paise"] / 100,
                                  "ref": r.get("ref"), "created_at": r["created_at"]} for r in tx]}

    def _tx(self, kind: str, amount: int, after: int, ref: str) -> dict:
        return {"kind": kind, "amount_paise": amount, "balance_after_paise": after, "ref": ref,
                "created_at": self._now(), "ts": time.time()}

    def topup(self, uid: str, amount: float) -> dict:
        paise = round(amount * 100)
        if not TOPUP_MIN_PAISE <= paise <= TOPUP_MAX_PAISE:
            raise AccountError(f"Add between ₹{TOPUP_MIN_PAISE // 100} and ₹{TOPUP_MAX_PAISE // 100}.")

        def fn(u):
            bal = u.get("balance_paise", 0) + paise
            if bal > MAX_BALANCE_PAISE:
                raise AccountError(f"Demo wallet limit is ₹{MAX_BALANCE_PAISE // 100}.")
            return {"balance_paise": bal}, [self._tx("topup", paise, bal, "demo top-up, no payment")], []

        self.repo.atomic(uid, fn)
        return self.wallet(uid)

    def purchase(self, uid: str, kind: str, price: float, make_item) -> dict:
        paise = round(price * 100)
        out: dict = {}

        def fn(u):
            bal = u.get("balance_paise", 0)
            if bal < paise:
                raise AccountError(f"Not enough balance in your demo wallet (₹{bal / 100:g}). Add money first.", 402)
            item = out.get("item") or make_item()       # a retried transaction reuses the same signed item
            out["item"] = item
            item_id = item.get("ticket_id") or item["pass_id"]
            rec = {"user_id": uid, "kind": kind, "payload": item, "valid_until": item["valid_until"], "created_at": self._now()}
            return {"balance_paise": bal - paise}, [self._tx(kind, -paise, bal - paise, item_id)], [(item_id, rec)]

        self.repo.atomic(uid, fn)
        return out["item"]

    def items(self, uid: str, kind: str) -> list[dict]:
        rows = [r for r in self.repo.list_items(uid) if r["kind"] == kind]
        rows.sort(key=lambda r: r["created_at"], reverse=True)
        return [r["payload"] for r in rows[:100]]

    def item_on_record(self, item_id: str) -> bool:
        return self.repo.has_item(item_id)

    def add_feedback(self, uid: str | None, text: str) -> None:
        text = (text or "").strip()
        if not 3 <= len(text) <= 2000:
            raise AccountError("Write between 3 and 2000 characters.")
        self.repo.add_feedback({"user_id": uid, "text": text, "created_at": self._now()})


def connect(credentials_path: str) -> FirebaseAccountStore:
    """Start the Admin SDK with a service-account key file and return the Firestore-backed store."""
    import firebase_admin
    from firebase_admin import auth, credentials, firestore
    app = firebase_admin.get_app() if firebase_admin._apps else firebase_admin.initialize_app(credentials.Certificate(credentials_path))
    return FirebaseAccountStore(FirestoreRepo(firestore.client(app)),
                                verify=lambda tok: auth.verify_id_token(tok, app=app),
                                delete_auth=lambda uid: auth.delete_user(uid, app=app))
