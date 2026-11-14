"""DEMO bus pass catalogue. Prices are DTC's published fares where an official source was found.

Checked 2026-11-14:
- Monthly General All Route pass: ₹800 non-AC / ₹1,000 AC on dtc.delhi.gov.in and the DTC pass portal FAQ.
- Daily pass: ₹40 non-AC / ₹50 AC appears only on third-party sites; no official page found, so it is
  marked unverified in the app.
"""
from __future__ import annotations

from datetime import datetime, time, timedelta

CHECKED_ON = "2026-11-14"

CATALOG = {
    "monthly": {
        "name": {"en": "Monthly all-route pass", "hi": "मासिक ऑल-रूट पास"},
        "price": {"non_ac": 800, "ac": 1000},
        "validity": {"en": "30 days from purchase", "hi": "खरीद से 30 दिन"},
        "verified": True,
        "sources": ["https://dtc.delhi.gov.in/dtc/get-dtc-pass", "https://dtcpass.delhi.gov.in/faqs.jsp"],
    },
    "daily": {
        "name": {"en": "Daily pass", "hi": "दैनिक पास"},
        "price": {"non_ac": 40, "ac": 50},
        "validity": {"en": "Until 23:59 today", "hi": "आज रात 23:59 तक"},
        "verified": False,
        "sources": ["https://coachbuildersindia.com/how-to-apply-dtc-bus-pass-online-and-check-dtc-bus-pass-fares/"],
    },
}

COVERAGE = {
    "ac": {"en": "AC and non-AC buses", "hi": "AC और नॉन-AC बसें"},
    "non_ac": {"en": "Non-AC buses only", "hi": "सिर्फ़ नॉन-AC बसें"},
}


def catalog() -> dict:
    return {"checked_on": CHECKED_ON, "demo": True, "coverage": COVERAGE,
            "passes": [{"kind": k, **v} for k, v in CATALOG.items()]}


def validity(kind: str, now: datetime) -> datetime:
    if kind == "monthly":
        return now + timedelta(days=30)
    return datetime.combine(now.date(), time(23, 59, 59), tzinfo=now.tzinfo)
