"""Sends DelhiRaahi's account emails over SMTP (e.g. a Gmail account with an app password).

Settings come from .env (git-ignored): SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, MAIL_FROM_NAME.
The password is never logged.
"""
from __future__ import annotations

import smtplib
import ssl
from email.message import EmailMessage
from email.utils import formataddr, make_msgid


class Mailer:
    def __init__(self, host: str, port: int, user: str, password: str, from_name: str = "DelhiRaahi"):
        self.host, self.port, self.user, self._password, self.from_name = host, port, user, password, from_name

    def send(self, to: str, subject: str, html: str, text: str, reply_to: str | None = None) -> None:
        msg = EmailMessage()
        msg["From"] = formataddr((self.from_name, self.user))
        msg["To"] = to
        msg["Subject"] = subject
        msg["Message-ID"] = make_msgid(domain=self.user.split("@")[-1])
        if reply_to:
            msg["Reply-To"] = reply_to
        else:
            msg["Auto-Submitted"] = "auto-generated"      # tells mail servers not to auto-reply
        msg.set_content(text)
        msg.add_alternative(html, subtype="html")
        ctx = ssl.create_default_context()
        if self.port == 465:
            with smtplib.SMTP_SSL(self.host, self.port, context=ctx, timeout=20) as s:
                s.login(self.user, self._password)
                s.send_message(msg)
        else:
            with smtplib.SMTP(self.host, self.port, timeout=20) as s:
                s.starttls(context=ctx)
                s.login(self.user, self._password)
                s.send_message(msg)
