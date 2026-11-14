"""DelhiRaahi's account emails (confirm email, reset password), in English and Hindi.

Each render returns (subject, html, text). HTML is one table with inline styles and no images,
which displays the same in Gmail, Outlook and phone mail apps; the plain-text part is for clients
that don't show HTML (and helps the email avoid spam folders).
"""
from __future__ import annotations

from html import escape

BRAND = "DelhiRaahi"
INK, PAPER, CARD, SAND, MUTED, LINE = "#221C18", "#F5EFE4", "#FFFDF8", "#A63D2A", "#6B625A", "#E6DCCB"

T = {
    "en": {
        "hi": "Hi {name},",
        "verify_subject": f"Confirm your email for {BRAND}",
        "verify_title": "Confirm your email address",
        "verify_intro": f"Thanks for signing up for {BRAND}. Please confirm that <b>{{email}}</b> is your email address. "
                        "Once it's confirmed you can add money to your wallet, book tickets and buy passes.",
        "verify_button": "Confirm email address",
        "verify_after": "This link can be used once and expires in a few days.",
        "verify_why": f"You're getting this email because an account was created on {BRAND} with this address. "
                      "If that wasn't you, ignore this email and the address won't be confirmed.",
        "reset_subject": f"Reset your {BRAND} password",
        "reset_title": "Reset your password",
        "reset_intro": f"We got a request to reset the password for your {BRAND} account <b>{{email}}</b>. "
                       "Choose a new password using the button below.",
        "reset_button": "Reset password",
        "reset_after": "This link can be used once and expires in one hour. Your current password keeps working until you change it.",
        "reset_why": "If you didn't ask to reset your password, ignore this email. Nothing will change.",
        "fallback": "If the button doesn't work, copy this link into your browser:",
        "auto": "This is an automated message, so replies aren't read.",
        "tagline": "Bus journeys across Delhi, planned by time and crowd.",
    },
    "hi": {
        "hi": "नमस्ते {name},",
        "verify_subject": f"{BRAND} के लिए अपना ईमेल पक्का करें",
        "verify_title": "अपना ईमेल पता पक्का करें",
        "verify_intro": f"{BRAND} से जुड़ने के लिए धन्यवाद। कृपया पक्का करें कि <b>{{email}}</b> आपका ईमेल पता है। "
                        "इसके बाद आप वॉलेट में पैसे जोड़ सकेंगे, टिकट बुक कर सकेंगे और पास ले सकेंगे।",
        "verify_button": "ईमेल पक्का करें",
        "verify_after": "यह लिंक एक ही बार काम करेगा और कुछ दिनों में समाप्त हो जाएगा।",
        "verify_why": f"यह ईमेल इसलिए आया है क्योंकि इस पते से {BRAND} पर खाता बनाया गया। "
                      "अगर यह आपने नहीं किया, तो इस ईमेल को अनदेखा करें; पता पक्का नहीं होगा।",
        "reset_subject": f"अपना {BRAND} पासवर्ड बदलें",
        "reset_title": "पासवर्ड बदलें",
        "reset_intro": f"आपके {BRAND} खाते <b>{{email}}</b> का पासवर्ड बदलने का अनुरोध मिला है। "
                       "नीचे दिए बटन से नया पासवर्ड चुनें।",
        "reset_button": "पासवर्ड बदलें",
        "reset_after": "यह लिंक एक ही बार काम करेगा और एक घंटे में समाप्त हो जाएगा। जब तक आप नहीं बदलते, पुराना पासवर्ड चलता रहेगा।",
        "reset_why": "अगर आपने पासवर्ड बदलने को नहीं कहा, तो इस ईमेल को अनदेखा करें। कुछ नहीं बदलेगा।",
        "fallback": "अगर बटन काम न करे, तो यह लिंक ब्राउज़र में खोलें:",
        "auto": "यह अपने-आप भेजा गया संदेश है, इसके जवाब पढ़े नहीं जाते।",
        "tagline": "दिल्ली की बस यात्राएँ, समय और भीड़ के हिसाब से।",
    },
}


def _page(w: dict, kind: str, name: str, email: str, link: str) -> str:
    e_link = escape(link, quote=True)
    intro = w[f"{kind}_intro"].format(email=escape(email))
    return f"""<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
<body style="margin:0;padding:0;background:{PAPER};">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:{PAPER};padding:32px 12px;font-family:'Segoe UI',Roboto,Helvetica,Arial,'Noto Sans Devanagari',sans-serif;">
<tr><td align="center">
<table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="max-width:520px;">
  <tr><td style="padding:0 4px 16px 4px;font-size:22px;font-weight:700;color:{SAND};">{BRAND}</td></tr>
  <tr><td style="background:{CARD};border:1px solid {LINE};border-radius:14px;padding:32px 28px;">
    <h1 style="margin:0 0 16px 0;font-size:22px;line-height:1.3;color:{INK};font-weight:700;">{w[f"{kind}_title"]}</h1>
    <p style="margin:0 0 12px 0;font-size:16px;line-height:1.55;color:{INK};">{w["hi"].format(name=escape(name))}</p>
    <p style="margin:0 0 24px 0;font-size:16px;line-height:1.55;color:{INK};">{intro}</p>
    <table role="presentation" cellpadding="0" cellspacing="0"><tr><td style="border-radius:10px;background:{SAND};">
      <a href="{e_link}" style="display:inline-block;padding:14px 26px;font-size:16px;font-weight:700;color:#FFFFFF;text-decoration:none;border-radius:10px;">{w[f"{kind}_button"]}</a>
    </td></tr></table>
    <p style="margin:24px 0 0 0;font-size:14px;line-height:1.55;color:{MUTED};">{w[f"{kind}_after"]}</p>
    <p style="margin:20px 0 6px 0;font-size:13px;line-height:1.5;color:{MUTED};">{w["fallback"]}</p>
    <p style="margin:0;font-size:12px;line-height:1.5;word-break:break-all;"><a href="{e_link}" style="color:{SAND};">{e_link}</a></p>
  </td></tr>
  <tr><td style="padding:18px 6px 0 6px;font-size:12px;line-height:1.6;color:{MUTED};">
    {w[f"{kind}_why"]}<br>{w["auto"]}<br>&copy; {BRAND} &middot; {w["tagline"]}
  </td></tr>
</table>
</td></tr></table>
</body></html>"""


def _text(w: dict, kind: str, name: str, email: str, link: str) -> str:
    strip = lambda s: s.replace("<b>", "").replace("</b>", "")  # noqa: E731
    return "\n\n".join([
        w["hi"].format(name=name), strip(w[f"{kind}_intro"].format(email=email)),
        f"{w[f'{kind}_button']}: {link}", w[f"{kind}_after"], "—", w[f"{kind}_why"], w["auto"], f"{BRAND} · {w['tagline']}",
    ])


def render(kind: str, lang: str, name: str, email: str, link: str) -> tuple[str, str, str]:
    """kind: "verify" or "reset". Returns (subject, html, text)."""
    w = T.get(lang, T["en"])
    name = (name or "").strip() or email.split("@")[0]
    return w[f"{kind}_subject"], _page(w, kind, name, email, link), _text(w, kind, name, email, link)
