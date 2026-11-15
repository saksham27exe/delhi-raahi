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

    def list_users(self, limit: int, after: str | None = None, email_prefix: str | None = None) -> list[tuple[str, dict]]:
        rows = sorted(self.users.items(), key=lambda kv: (kv[1].get("created_at", ""), kv[0]), reverse=True)
        if email_prefix:
            rows = sorted([kv for kv in rows if kv[1].get("email", "").startswith(email_prefix)], key=lambda kv: kv[1].get("email", ""))
        if after:
            ids = [k for k, _ in rows]
            rows = rows[ids.index(after) + 1:] if after in ids else []
        return [(k, dict(v)) for k, v in rows[:limit]]

    def get_item(self, item_id: str) -> dict | None:
        v = self.items.get(item_id)
        return dict(v) if v else None

    def update_item(self, item_id: str, fields: dict) -> None:
        with self._lock:
            self.items[item_id].update(fields)

    def count(self, col: str, field: str | None = None, op: str = "==", value: Any = None) -> int:
        src = list(self.users.values()) if col == "users" else list(self.items.values())
        if field is None:
            return len(src)
        cmp = {"==": lambda a: a == value, ">=": lambda a: a is not None and a >= value}[op]
        return sum(1 for r in src if cmp(r.get(field)))

    def since(self, col: str, iso: str) -> list[dict]:
        src = self.users.values() if col == "users" else self.items.values()
        return [dict(r) for r in src if r.get("created_at", "") >= iso]

    def all_tx_sums(self) -> dict[str, int]:
        out: dict[str, int] = {}
        for txs in self.tx.values():
            for r in txs:
                out[r["kind"]] = out.get(r["kind"], 0) + r["amount_paise"]
        return out

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

    def list_users(self, limit: int, after: str | None = None, email_prefix: str | None = None) -> list[tuple[str, dict]]:
        col = self.db.collection("users")
        if email_prefix:      # single-field range: Firestore's automatic index is enough
            q = col.where(filter=self._fs.FieldFilter("email", ">=", email_prefix)) \
                   .where(filter=self._fs.FieldFilter("email", "<", email_prefix + "\uf8ff")).order_by("email")
        else:
            q = col.order_by("created_at", direction=self._fs.Query.DESCENDING)
        if after:
            snap = col.document(after).get()
            if snap.exists:
                q = q.start_after(snap)
        return [(d.id, d.to_dict()) for d in q.limit(limit).stream()]

    def get_item(self, item_id: str) -> dict | None:
        s = self.db.collection("items").document(item_id).get()
        return s.to_dict() if s.exists else None

    def update_item(self, item_id: str, fields: dict) -> None:
        self.db.collection("items").document(item_id).update(fields)

    def count(self, col: str, field: str | None = None, op: str = "==", value: Any = None) -> int:
        q = self.db.collection(col)
        if field is not None:
            q = q.where(filter=self._fs.FieldFilter(field, op, value))
        return int(q.count().get()[0][0].value)

    def since(self, col: str, iso: str) -> list[dict]:
        q = self.db.collection(col).where(filter=self._fs.FieldFilter("created_at", ">=", iso))
        return [d.to_dict() for d in q.stream()]

    def all_tx_sums(self) -> dict[str, int]:
        out: dict[str, int] = {}
        for d in self.db.collection_group("wallet_tx").stream():
            r = d.to_dict()
            out[r["kind"]] = out.get(r["kind"], 0) + r.get("amount_paise", 0)
        return out

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

    def __init__(self, repo, verify: Callable[[str], dict], delete_auth: Callable[[str], None] = lambda uid: None,
                 mailer=None, verify_link: Callable[[str], str] | None = None, reset_link: Callable[[str], str] | None = None,
                 user_name: Callable[[str], str | None] = lambda email: None,
                 set_auth_disabled: Callable[[str, bool], None] = lambda uid, d: None,
                 auth_info: Callable[[str], dict] = lambda uid: {}):
        self.repo = repo
        self._verify = verify
        self._delete_auth = delete_auth
        # Our own branded emails: Firebase makes the one-time link, we send the email.
        self.mailer = mailer
        self._verify_link = verify_link
        self._reset_link = reset_link
        self._user_name = user_name
        self._set_auth_disabled = set_auth_disabled
        self._auth_info = auth_info
        self._sent: dict[str, list[float]] = {}
        self._mail_lock = threading.Lock()

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
        if d.get("disabled"):
            return None
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

    # ---------- branded account emails ----------
    def _throttle(self, key: str) -> None:
        """At most one email a minute and five an hour per address (stops abuse and spam flags)."""
        now = time.time()
        with self._mail_lock:
            hist = [t for t in self._sent.get(key, []) if now - t < 3600]
            if hist and now - hist[-1] < 60:
                raise AccountError(f"An email was just sent. Try again in {int(60 - (now - hist[-1])) + 1} s.", 429)
            if len(hist) >= 5:
                raise AccountError("Too many emails to this address. Try again in an hour.", 429)
            self._sent[key] = hist + [now]

    def _need_mailer(self) -> None:
        if self.mailer is None or self._verify_link is None:
            raise AccountError("Email sending is not set up on this server.", 503)

    def send_verification(self, u: FbUser, lang: str = "en") -> bool:
        """Send the 'confirm your email' email. False if the address is already confirmed."""
        from app.services.email_templates import render
        self._need_mailer()
        if u.verified:
            return False
        self._throttle("v:" + u.email.lower())
        subject, html, text = render("verify", lang, u.name, u.email, self._verify_link(u.email))
        self.mailer.send(u.email, subject, html, text)
        return True

    def send_reset(self, email: str, lang: str = "en") -> None:
        """Send a reset link if an account exists. Never says whether it does (no account guessing)."""
        from app.services.email_templates import render
        self._need_mailer()
        email = (email or "").strip()
        if "@" not in email or len(email) > 254:
            raise AccountError("Enter a valid email address.")
        self._throttle("r:" + email.lower())
        try:
            link = self._reset_link(email)
        except Exception:  # noqa: BLE001  (no such user): answer the same way
            return
        subject, html, text = render("reset", lang, self._user_name(email) or "", email, link)
        self.mailer.send(email, subject, html, text)

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
        return [{**r["payload"], **({"voided": True} if r.get("voided_at") else {})} for r in rows[:100]]

    def item_status(self, item_id: str) -> str | None:
        r = self.repo.get_item(item_id)
        return None if r is None else "voided" if r.get("voided_at") else "active"

    # ---------- admin website ----------
    @staticmethod
    def _admin_user_row(uid: str, d: dict) -> dict:
        return {"id": uid, "email": d.get("email", ""), "phone": "", "name": d.get("name", ""), "created_at": d.get("created_at", ""),
                "balance": d.get("balance_paise", 0) / 100, "disabled": bool(d.get("disabled")), "email_verified": None}

    def admin_users(self, q: str = "", limit: int = 25, cursor: str | None = None) -> dict:
        q = (q or "").strip()
        if q and "@" not in q and len(q) >= 20:             # looks like a Firebase uid
            d = self.repo.get_user(q)
            return {"users": [self._admin_user_row(q, d)] if d else [], "next": None}
        rows = self.repo.list_users(limit + 1, cursor, q.lower() if q else None)
        return {"users": [self._admin_user_row(k, d) for k, d in rows[:limit]], "next": rows[limit - 1][0] if len(rows) > limit else None}

    def admin_user(self, uid: str) -> dict:
        d = self.repo.get_user(uid)
        if d is None:
            raise AccountError("No such user.", 404)
        info = {}
        try:
            info = self._auth_info(uid) or {}
        except Exception:  # noqa: BLE001  (sign-in record gone)
            pass
        items = sorted(self.repo.list_items(uid), key=lambda r: r["created_at"], reverse=True)[:200]
        return {**self._admin_user_row(uid, d), "email_verified": info.get("email_verified"), "last_sign_in": info.get("last_sign_in"),
                "wallet": self.wallet(uid, 100),
                "items": [{"id": r["payload"].get("ticket_id") or r["payload"].get("pass_id"), "kind": r["kind"], "created_at": r["created_at"],
                           "valid_until": r["valid_until"], "voided_at": r.get("voided_at"), "void_reason": r.get("void_reason"),
                           "payload": r["payload"]} for r in items]}

    def admin_set_disabled(self, uid: str, disabled: bool) -> None:
        if self.repo.get_user(uid) is None:
            raise AccountError("No such user.", 404)
        self.repo.update_user(uid, {"disabled": bool(disabled)})
        self._set_auth_disabled(uid, bool(disabled))

    def admin_adjust(self, uid: str, amount: float, reason: str) -> dict:
        if self.repo.get_user(uid) is None:
            raise AccountError("No such user.", 404)
        paise = round(amount * 100)

        def fn(u):
            bal = u.get("balance_paise", 0) + paise
            if not 0 <= bal <= MAX_BALANCE_PAISE:
                raise AccountError(f"The demo balance must stay between ₹0 and ₹{MAX_BALANCE_PAISE // 100}.")
            return {"balance_paise": bal}, [self._tx("adjust", paise, bal, f"support: {reason}"[:200])], []

        self.repo.atomic(uid, fn)
        return self.wallet(uid)

    def admin_void(self, item_id: str, reason: str) -> None:
        r = self.repo.get_item(item_id)
        if r is None:
            raise AccountError("No such ticket or pass.", 404)
        if r.get("voided_at"):
            raise AccountError("Already voided.", 409)
        self.repo.update_item(item_id, {"voided_at": self._now(), "void_reason": reason[:200]})

    def admin_find_item(self, item_id: str) -> str | None:
        r = self.repo.get_item(item_id)
        return r["user_id"] if r else None

    def admin_delete(self, uid: str) -> None:
        if self.repo.get_user(uid) is None:
            raise AccountError("No such user.", 404)
        self.delete(uid)

    def admin_resend_verification(self, uid: str, lang: str = "en") -> bool:
        d = self.repo.get_user(uid)
        if d is None:
            raise AccountError("No such user.", 404)
        return self.send_verification(self._user(uid, d), lang)

    def admin_stats(self, days: int = 30) -> dict:
        from datetime import timedelta
        now = datetime.now(IST)
        since, week = (now - timedelta(days=days)).isoformat(timespec="seconds"), (now - timedelta(days=7)).isoformat(timespec="seconds")
        per_day: dict[str, dict] = {}
        for r in self.repo.since("users", since):
            per_day.setdefault(r["created_at"][:10], {"day": r["created_at"][:10], "users": 0, "tickets": 0, "passes": 0})["users"] += 1
        recent = self.repo.since("items", since)
        for r in recent:
            k = "tickets" if r["kind"] == "ticket" else "passes"
            per_day.setdefault(r["created_at"][:10], {"day": r["created_at"][:10], "users": 0, "tickets": 0, "passes": 0})[k] += 1
        sums = self.repo.all_tx_sums()
        return {"users": self.repo.count("users"), "users_7d": self.repo.count("users", "created_at", ">=", week),
                "disabled": self.repo.count("users", "disabled", "==", True),
                "tickets": self.repo.count("items", "kind", "==", "ticket"), "passes": self.repo.count("items", "kind", "==", "pass"),
                "tickets_7d": sum(1 for r in recent if r["kind"] == "ticket" and r["created_at"] >= week),
                "voided": sum(1 for r in recent if r.get("voided_at")),
                "demo_topups": sums.get("topup", 0) / 100, "demo_spent": -(sums.get("ticket", 0) + sums.get("pass", 0)) / 100,
                "per_day": sorted(per_day.values(), key=lambda d: d["day"])}

    def item_on_record(self, item_id: str) -> bool:
        return self.repo.has_item(item_id)

    def add_feedback(self, uid: str | None, text: str) -> None:
        text = (text or "").strip()
        if not 3 <= len(text) <= 2000:
            raise AccountError("Write between 3 and 2000 characters.")
        self.repo.add_feedback({"user_id": uid, "text": text, "created_at": self._now()})


def connect(credentials_path: str, mailer=None) -> FirebaseAccountStore:
    """Start the Admin SDK with a service-account key file and return the Firestore-backed store."""
    import firebase_admin
    from firebase_admin import auth, credentials, firestore
    app = firebase_admin.get_app() if firebase_admin._apps else firebase_admin.initialize_app(credentials.Certificate(credentials_path))
    def name_of(email: str) -> str | None:
        try:
            return auth.get_user_by_email(email, app=app).display_name
        except Exception:  # noqa: BLE001
            return None

    def set_disabled(uid: str, disabled: bool) -> None:
        auth.update_user(uid, disabled=disabled, app=app)
        if disabled:
            auth.revoke_refresh_tokens(uid, app=app)      # signs them out on every device

    def info_of(uid: str) -> dict:
        u = auth.get_user(uid, app=app)
        last = u.user_metadata.last_sign_in_timestamp
        return {"email_verified": u.email_verified, "auth_disabled": u.disabled,
                "last_sign_in": datetime.fromtimestamp(last / 1000, IST).isoformat(timespec="seconds") if last else None}

    return FirebaseAccountStore(FirestoreRepo(firestore.client(app)),
                                verify=lambda tok: auth.verify_id_token(tok, app=app),
                                delete_auth=lambda uid: auth.delete_user(uid, app=app),
                                mailer=mailer,
                                verify_link=lambda email: auth.generate_email_verification_link(email, app=app),
                                reset_link=lambda email: auth.generate_password_reset_link(email, app=app),
                                user_name=name_of,
                                set_auth_disabled=set_disabled, auth_info=info_of)
