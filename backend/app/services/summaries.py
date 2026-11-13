"""Short user-facing summaries in English and Hindi.

Every summary is a plain sentence built from the structured response, so the same text can be
shown on screen or read aloud by the (later) voice assistant. Add a language by adding a key.
"""
from __future__ import annotations

CROWD_WORD = {
    "en": {"LOW": "low", "MODERATE": "moderate", "HIGH": "high", "VERY_HIGH": "very high", "UNKNOWN": "unknown"},
    "hi": {"LOW": "कम", "MODERATE": "मध्यम", "HIGH": "ज़्यादा", "VERY_HIGH": "बहुत ज़्यादा", "UNKNOWN": "अज्ञात"},
}
BUS_TYPE = {"en": {"ac": "AC", "non_ac": "non-AC", "mixed": "AC or non-AC", "unknown": "AC or non-AC"},
            "hi": {"ac": "एसी", "non_ac": "नॉन-एसी", "mixed": "एसी या नॉन-एसी", "unknown": "एसी या नॉन-एसी"}}
LANGS = ("en", "hi")


def _rupees(lo: float, hi: float) -> str:
    f = lambda x: f"{x:g}"  # noqa: E731
    return f"₹{f(lo)}" if lo == hi else f"₹{f(lo)}–{f(hi)}"


def journey(j: dict) -> dict[str, str]:
    routes = [l["route"] for l in j["legs"]]
    dur = round(j["duration_minutes"])
    crowd = j.get("crowd_level", "UNKNOWN")
    fare = j.get("fare")
    out = {}
    for lang in LANGS:
        c = CROWD_WORD[lang][crowd]
        if lang == "en":
            how = f"Take bus {routes[0]}" + (f", then change to {routes[1]} at {j['legs'][1]['board_stop']}" if len(routes) > 1 else "")
            s = f"{how}. Leave {j['departure']}, arrive {j['arrival']} ({dur} min). Expected crowding: {c}."
            if fare:
                s += f" Fare {_rupees(fare['total_min'], fare['total_max'])}."
        else:
            how = f"बस {routes[0]} लें" + (f", फिर {j['legs'][1]['board_stop']} पर बस {routes[1]} बदलें" if len(routes) > 1 else "")
            s = f"{how}। {j['departure']} पर निकलें, {j['arrival']} पर पहुँचें ({dur} मिनट)। अनुमानित भीड़: {c}।"
            if fare:
                s += f" किराया {_rupees(fare['total_min'], fare['total_max'])}।"
        out[lang] = s
    return out


def arrivals(board: dict) -> dict[str, str]:
    rows = board["arrivals"][:3]
    if not rows:
        return {"en": f"No buses due at {board['stop']} in the next hour.",
                "hi": f"अगले एक घंटे में {board['stop']} पर कोई बस नहीं है।"}

    def item(r, lang):
        m = max(0, round(r["eta_min"]))
        live = r["basis"].startswith("live")
        if lang == "en":
            return f"{r['route']} in {m} min" + (" (live)" if live else " (timetable)")
        return f"{r['route']} {m} मिनट में" + (" (लाइव)" if live else " (समय-सारणी)")
    return {"en": f"At {board['stop']}: " + ", ".join(item(r, "en") for r in rows) + ".",
            "hi": f"{board['stop']} पर: " + ", ".join(item(r, "hi") for r in rows) + "।"}


def fare_quote(q: dict) -> dict[str, str]:
    n = q["passengers"]
    return {"en": f"Total {_rupees(q['total'], q['total'])} for {n} passenger{'s' if n > 1 else ''} "
                  f"({len(q['legs'])} ticket{'s' if len(q['legs']) > 1 else ''}, digital fare). Demo only, no payment.",
            "hi": f"{n} यात्री के लिए कुल {_rupees(q['total'], q['total'])} ({len(q['legs'])} टिकट, डिजिटल किराया)। "
                  f"केवल डेमो, कोई भुगतान नहीं।"}


def leg_fare(f: dict, route: str) -> dict[str, str]:
    bt = f["bus_type"]
    if f["fare"] is not None:
        en = f"Bus {route}, {f['distance_km']:.1f} km, {BUS_TYPE['en'][bt]}: ₹{f['fare']} (₹{f['fare_digital']:g} with a digital ticket)."
        hi = f"बस {route}, {f['distance_km']:.1f} किमी, {BUS_TYPE['hi'][bt]}: ₹{f['fare']} (डिजिटल टिकट से ₹{f['fare_digital']:g})।"
    else:
        en = f"Bus {route}, {f['distance_km']:.1f} km: ₹{f['fare_non_ac']} non-AC or ₹{f['fare_ac']} AC."
        hi = f"बस {route}, {f['distance_km']:.1f} किमी: नॉन-एसी ₹{f['fare_non_ac']} या एसी ₹{f['fare_ac']}।"
    return {"en": en, "hi": hi}


def crowd(category: str, where: str, when: str) -> dict[str, str]:
    return {"en": f"Expected crowding on {where} at {when}: {CROWD_WORD['en'][category]}.",
            "hi": f"{when} पर {where} में अनुमानित भीड़: {CROWD_WORD['hi'][category]}।"}
