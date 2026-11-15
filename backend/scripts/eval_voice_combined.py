"""Score "rules first, LLM as fallback" on the voice test set, replaying the app's decision.

    1. cd android && ./gradlew testDebugUnitTest --tests '*RulesEvalTest*'   (writes app/build/voice_rules.jsonl)
    2. backend running on :8010 (for the stop/place search, as in the app)
    3. ../.venv/Scripts/python scripts/eval_voice_combined.py

The LLM is asked, as in VoiceSession, when the rules read nothing, read a whole clause as a place
(> 3 words), or their destination is not found by the search (match score below 0.5, as PlaceMatch.kt).
"""
from __future__ import annotations

import json
import re
import statistics
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.services import voice_llm  # noqa: E402
from scripts.eval_voice_llm import DATA, diff, expected  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
RULES = ROOT / "android" / "app" / "build" / "voice_rules.jsonl"
API = "http://127.0.0.1:8010/api/v1"

# --- PlaceMatch.kt, ported ---------------------------------------------------------------
FILLER = set("""a an the of to from me my please bus buses stop station near nearby i want go take get reach need can you is for
at in on and by ka ki ke se tak ko jana jaana hai mujhe chalo wala wali jaane jane rasta raasta kaise batao bataiye dikhao
chahiye liye""".split())


def words(s: str) -> list[str]:
    return [w for w in re.split(r"[^a-z0-9]+", s.lower()) if len(w) >= 2 and w not in FILLER]


def edits(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def close(a: str, b: str) -> bool:
    if a == b:
        return True
    if len(a) < 4 or len(b) < 4:
        return False
    if a.startswith(b) or b.startswith(a):
        return True
    return edits(a, b) <= (2 if min(len(a), len(b)) >= 7 else 1)


def score(said: str, name: str) -> float:
    s = words(said)
    if not s:
        return 0.0
    allw = [w for w in re.split(r"[^a-z0-9]+", name.lower()) if w]
    n = [w for w in allw if w not in FILLER]
    glued = "".join(allw)
    initials = "".join(w[0] for w in n if len(w) > 2 or w.isalpha())
    hit = sum(1 for w in s if any(close(w, x) for x in n) or (len(w) >= 5 and w in glued) or (len(w) >= 3 and w in initials))
    return hit / len(s)


def found(name: str) -> bool:
    names = []
    for path in ("/stops/search", "/places/search"):
        try:
            with urllib.request.urlopen(f"{API}{path}?q={urllib.parse.quote(name)}&limit=8", timeout=20) as r:
                names += [x["name"] for x in json.load(r).get("results", [])]
        except Exception:  # noqa: BLE001
            pass
    return any(score(name, x) >= 0.5 for x in names)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    rows = {json.loads(l)["text"]: json.loads(l) for l in DATA.read_text(encoding="utf-8").splitlines() if l.strip()}
    rules = {json.loads(l)["text"]: json.loads(l)["rules"] for l in RULES.read_text(encoding="utf-8").splitlines() if l.strip()}
    m = voice_llm.get(ROOT / "models")
    m.understand("warm up")
    ok_rules = ok_both = asked = 0
    times, notes = [], []
    for text, row in rows.items():
        want, r = expected(row), {**voice_llm.EMPTY, **rules[text]}
        rules_ok = not diff(want, r)
        ok_rules += rules_ok
        places = [p for p in (r["from"], r["to"]) if p]
        why = None
        if r["intent"] == "off_topic":
            why = None
        elif r["intent"] == "unclear":
            why = "rules read nothing"
        elif any(len(p.split()) > 3 for p in places):
            why = "clause read as a place"
        elif r["intent"] == "trip" and r["to"] and not found(r["to"]):
            why = "place not found"
        final = r
        if why:
            asked += 1
            got = m.understand(text)
            times.append(got["ms"])
            final = got
        good = not diff(want, final)
        ok_both += good
        if why or not good:
            notes.append(f"  {'✓' if good else '✗'} {text}  [{why or 'rules kept'}]" + ("" if good else "\n      " + "; ".join(diff(want, final))))
    n = len(rows)
    print(f"rules only           {ok_rules}/{n} = {100 * ok_rules / n:.0f}%")
    print(f"rules + LLM fallback {ok_both}/{n} = {100 * ok_both / n:.0f}%   (LLM asked for {asked} sentences"
          + (f", median {statistics.median(times) / 1000:.1f} s each)" if times else ")"))
    print("\n".join(notes))


if __name__ == "__main__":
    main()
