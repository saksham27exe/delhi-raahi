"""Score the local LLM on the voice test set (tests/data/voice_phrases.jsonl).

    python scripts/eval_voice_llm.py [model-file.gguf ...]

A sentence counts as right only when every field matches: intent, from, to, from-here, command,
number, preference and time. Prints accuracy, the misses, and the time per sentence.
"""
from __future__ import annotations

import json
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.services import voice_llm  # noqa: E402

DATA = Path(__file__).resolve().parents[1] / "tests" / "data" / "voice_phrases.jsonl"
FIELDS = ["intent", "from", "to", "from_here", "command", "number", "preference", "leave_in_min", "leave_at", "tomorrow"]


def expected(row: dict) -> dict:
    return {**voice_llm.EMPTY, **{k: v for k, v in row.items() if k != "text"}}


def diff(want: dict, got: dict) -> list[str]:
    return [f"{k}: want {want[k]!r} got {got.get(k)!r}" for k in FIELDS if want[k] != got.get(k)]


def run(model_file: str) -> None:
    rows = [json.loads(l) for l in DATA.read_text(encoding="utf-8").splitlines() if l.strip()]
    t0 = time.time()
    m = voice_llm.get(Path(__file__).resolve().parents[2] / "models", model_file)
    if m is None:
        print(f"{model_file}: not found"); return
    print(f"\n== {model_file} (load {time.time() - t0:.1f} s)")
    m.understand("warm up")                       # fills the prompt cache, as the first real request would
    ok, times, misses = 0, [], []
    for r in rows:
        got = m.understand(r["text"])
        times.append(got["ms"])
        d = diff(expected(r), got)
        if d:
            misses.append(f"  ✗ {r['text']}\n      " + "; ".join(d))
        else:
            ok += 1
    print(f"accuracy {ok}/{len(rows)} = {100 * ok / len(rows):.0f}%   "
          f"time per sentence: median {statistics.median(times) / 1000:.1f} s, max {max(times) / 1000:.1f} s")
    print("\n".join(misses))
    voice_llm._instance = None                     # so the next model can load


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for f in sys.argv[1:] or [voice_llm.MODEL_FILE]:
        run(f)
