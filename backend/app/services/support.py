"""Support requests and complaints: each one gets a reference number, is kept with the account's feedback
(so deleting the account deletes it too) and is emailed to the support inbox. If the person gave an
email address they get a copy with the reference, and the inbox can reply to them directly.

The inbox is SUPPORT_EMAIL from .env, or the SMTP sending address when that is not set.
"""
from __future__ import annotations

import re
import secrets
import threading
import time
from datetime import datetime
from html import escape
from zoneinfo import ZoneInfo

from app.services.email_templates import BRAND, CARD, INK, LINE, MUTED, PAPER, SAND

KINDS = {
    "complaint": ("Complaint", "शिकायत"),
    "problem": ("App problem", "ऐप में दिक्कत"),
    "ticket": ("Ticket or wallet", "टिकट या वॉलेट"),
    "idea": ("Suggestion", "सुझाव"),
}
EMAIL_RE = re.compile(r"^[^@\s]{1,64}@[^@\s]+\.[A-Za-z]{2,}$")
IST = ZoneInfo("Asia/Kolkata")


class SupportError(Exception):
    def __init__(self, msg: str, status: int = 422):
        super().__init__(msg)
        self.status = status


class RateLimit:
    """At most `n` requests per `window` seconds per key (client address); stops a script filling the inbox."""

    def __init__(self, n: int = 5, window: float = 600.0):
        self.n, self.window, self._hits, self._lock = n, window, {}, threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.monotonic()
        with self._lock:
            hits = [t for t in self._hits.get(key, []) if now - t < self.window]
            ok = len(hits) < self.n
            if ok:
                hits.append(now)
            self._hits[key] = hits
            return ok


def new_ref() -> str:
    """DR-1115-7K3Q: date (MMDD) + 4 characters without look-alikes (0/O, 1/I)."""
    abc = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    return f"DR-{datetime.now(IST):%m%d}-" + "".join(secrets.choice(abc) for _ in range(4))


def clean_email(email: str | None) -> str | None:
    email = (email or "").strip()
    if not email:
        return None
    if not EMAIL_RE.match(email) or len(email) > 254:
        raise SupportError("That email address doesn't look right.")
    return email


def _rows(fields: list[tuple[str, str]]) -> str:
    return "".join(f'<tr><td style="padding:4px 12px 4px 0;color:{MUTED};vertical-align:top;white-space:nowrap;">{escape(k)}</td>'
                   f'<td style="padding:4px 0;color:{INK};">{escape(v)}</td></tr>' for k, v in fields if v)


def _wrap(title: str, intro: str, fields: list[tuple[str, str]], message: str, foot: str) -> str:
    msg = escape(message).replace("\n", "<br>")
    return f"""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:{PAPER};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{PAPER};padding:32px 12px;font-family:'Segoe UI',Roboto,Helvetica,Arial,'Noto Sans Devanagari',sans-serif;">
<tr><td align="center"><table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:560px;">
  <tr><td style="padding:0 4px 16px 4px;font-size:22px;font-weight:700;color:{SAND};">{BRAND}</td></tr>
  <tr><td style="background:{CARD};border:1px solid {LINE};border-radius:14px;padding:28px;">
    <h1 style="margin:0 0 12px 0;font-size:20px;color:{INK};">{escape(title)}</h1>
    <p style="margin:0 0 16px 0;font-size:15px;line-height:1.55;color:{INK};">{intro}</p>
    <table role="presentation" cellpadding="0" cellspacing="0" style="font-size:14px;line-height:1.5;margin-bottom:16px;">{_rows(fields)}</table>
    <div style="border-left:3px solid {SAND};padding:8px 14px;font-size:15px;line-height:1.55;color:{INK};background:{PAPER};">{msg}</div>
  </td></tr>
  <tr><td style="padding:16px 6px 0 6px;font-size:12px;line-height:1.6;color:{MUTED};">{foot}</td></tr>
</table></td></tr></table></body></html>"""


def _text(title: str, intro: str, fields: list[tuple[str, str]], message: str, foot: str) -> str:
    strip = lambda s: re.sub(r"<[^>]+>", "", s)  # noqa: E731
    lines = [title, "", strip(intro), ""] + [f"{k}: {v}" for k, v in fields if v] + ["", message, "", "—", strip(foot)]
    return "\n".join(lines)


def inbox_email(ref: str, kind: str, message: str, fields: list[tuple[str, str]], reply_to: str | None) -> tuple[str, str, str]:
    label = KINDS[kind][0]
    subject = f"[{ref}] {label}: {message.strip().splitlines()[0][:60]}"
    intro = f"New {label.lower()} from the app." + ("" if reply_to else " No email given, so it can't be answered by email.")
    foot = "Reply to this email to answer the person directly." if reply_to else f"Sent from the {BRAND} app."
    return subject, _wrap(f"{label} · {ref}", intro, fields, message, foot), _text(f"{label} · {ref}", intro, fields, message, foot)


def copy_email(ref: str, kind: str, message: str, fields: list[tuple[str, str]], lang: str) -> tuple[str, str, str]:
    if lang == "hi":
        label = KINDS[kind][1]
        subject = f"आपका संदेश मिल गया ({ref})"
        title = f"{label} मिल गई · {ref}"
        intro = (f"आपका संदेश {BRAND} सहायता को मिल गया है। आगे बात करते समय यह संदर्भ संख्या बताएँ: <b>{ref}</b>. "
                 "जवाब इसी ईमेल पते पर आएगा।")
        foot = f"यह आपके भेजे संदेश की प्रति है। अगर यह आपने नहीं भेजा, तो इसे अनदेखा करें। · {BRAND}"
    else:
        label = KINDS[kind][0]
        subject = f"We got your message ({ref})"
        title = f"{label} received · {ref}"
        intro = (f"Your message reached {BRAND} support. Quote this reference if you write again: <b>{ref}</b>. "
                 "Any reply will come to this email address.")
        foot = f"This is a copy of what you sent. If you didn't send it, ignore this email. · {BRAND}"
    return subject, _wrap(title, intro, fields, message, foot), _text(title, intro, fields, message, foot)


def received_at() -> str:
    return datetime.now(IST).strftime("%d %b %Y, %H:%M IST")
