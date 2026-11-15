"""Fallback understanding for the voice assistant with a small local LLM (no paid API, no internet).

The app's own rules handle a sentence first. Only when they fail does the app ask this service to
turn the sentence into fields: from / to / time / preference / command. The model never answers in
free text: llama.cpp constrains its output to the JSON schema below, and every place name it returns
is still looked up in our stop search by the app, so it cannot invent stops or bus times.

Model: Qwen2.5-1.5B-Instruct (Apache-2.0), 4-bit GGUF, run on the CPU with llama.cpp.
Download it with scripts/fetch_llm.py. Without the file the endpoint answers 503 and the app
simply keeps its rules-only behaviour.
"""
from __future__ import annotations

import json
import logging
import threading
import time
from pathlib import Path

log = logging.getLogger(__name__)

MODEL_FILE = "qwen2.5-1.5b-instruct-q4_k_m.gguf"

COMMANDS = ["next", "previous", "repeat", "show", "stop", "help", "yes", "no", "direct_only", "with_change",
            "walk_only", "alert", "guide", "new_trip", "number"]

# Only "intent" is required: the model writes just the fields that apply, which keeps answers short
# (generation speed on a laptop CPU is the main cost).
SCHEMA = {
    "type": "object",
    "properties": {
        "intent": {"type": "string", "enum": ["trip", "command", "settings", "off_topic", "unclear"]},
        "from": {"type": "string"},
        "to": {"type": "string"},
        "from_here": {"type": "boolean"},
        "command": {"type": "string", "enum": COMMANDS},
        "number": {"type": "integer"},
        "preference": {"type": "string", "enum": ["fastest", "least_crowded", "balanced"]},
        "leave_in_min": {"type": "integer"},
        "leave_at": {"type": "string"},
        "tomorrow": {"type": "boolean"},
    },
    "required": ["intent"],
}

SYSTEM = """You read one sentence said to a Delhi bus app and fill in JSON fields. Never answer the question.

intent:
- "trip": names a place to go to or start from (also "book a bus from X to Y").
- "command": controls the results: next/more options, previous, repeat, show on screen, stop/bye, help, yes, no/wrong,
  only direct buses (direct_only), buses with a change (with_change), walk instead (walk_only),
  alert me when to get off (alert), guide me to the stop (guide), start over (new_trip), "option 2" (number).
- "settings": only changes the time or the preference (fastest / least crowded / balanced).
- "off_topic": not about bus travel in Delhi (coding, jokes, weather, news, music, general questions).
- "unclear": noise, a lone filler word, or cannot tell.

Rules:
- from / to: the place name exactly as said, in English letters (Latin script), lower case. Transliterate Hindi
  (साकेत -> saket, इंडिया गेट -> india gate, कनॉट प्लेस -> connaught place). Keep sector/block numbers.
- Drop request words from place names: jaana, jaane, jana hai, kaise jaun, ka rasta, batao, ke liye, tak, bus, please.
- Hindi "X se Y" means from X to Y. "yahan se", "from here", "where I am" -> from_here true, from null.
- preference: fastest (jaldi, quickest, tez), least_crowded (kam bheed, empty, khali), balanced; else null.
- leave_in_min: "in 30 minutes" -> 30. leave_at: 24-hour "HH:MM" ("6 pm" -> "18:00", "subah 9 baje" -> "09:00").
  tomorrow: true for "tomorrow" / "kal". "now" / "abhi" -> all time fields null/false.
- command only when intent is "command". number only for command "number".
- Write only the fields that apply; leave out empty ones.
"""

EMPTY = {"intent": "unclear", "from": None, "to": None, "from_here": False, "command": None, "number": None,
         "preference": None, "leave_in_min": None, "leave_at": None, "tomorrow": False}


def _ex(text: str, **fields) -> tuple[str, str]:
    return text, json.dumps(fields, ensure_ascii=False, separators=(",", ":"))


# A few worked examples (kept short: each one costs prompt time on every request).
EXAMPLES = [
    _ex("Mujhe Saket se Akshardham jaane ka rasta batao", intent="trip", **{"from": "saket", "to": "akshardham"}),
    _ex("how do I get to karol bagh from lajpat nagar", intent="trip", **{"from": "lajpat nagar", "to": "karol bagh"}),
    _ex("yahan se rohini sector 22 kaise pahunchu", intent="trip", from_here=True, to="rohini sector 22"),
    _ex("मुझे इंडिया गेट जाना है", intent="trip", to="india gate"),
    _ex("least crowded bus to connaught place at 6 pm", intent="trip", to="connaught place", preference="least_crowded", leave_at="18:00"),
    _ex("aur koi bus dikhao", intent="command", command="next"),
    _ex("only direct buses please", intent="command", command="direct_only"),
    _ex("the second one", intent="command", command="number", number=2),
    _ex("kal subah 9 baje", intent="settings", leave_at="09:00", tomorrow=True),
    _ex("write a python function", intent="off_topic"),
    _ex("hmm", intent="unclear"),
]


class VoiceLLM:
    def __init__(self, model_path: Path, threads: int = 6):
        self.name = model_path.name
        from llama_cpp import Llama  # imported lazily: the backend runs without it

        # The system prompt + examples are the same every time; llama.cpp keeps them in its cache between
        # requests, so only the new sentence has to be read.
        self.llm = Llama(model_path=str(model_path), n_ctx=2048, n_threads=threads, n_threads_batch=threads, verbose=False)
        self.lock = threading.Lock()  # one request at a time (the CPU is the bottleneck anyway)

    def understand(self, text: str, context: str | None = None) -> dict:
        msgs = [{"role": "system", "content": SYSTEM}]
        for said, out in EXAMPLES:
            msgs += [{"role": "user", "content": said}, {"role": "assistant", "content": out}]
        user = text.strip()[:300]
        if context:
            user = f"(Conversation so far: {context[:200]})\n{user}"
        msgs.append({"role": "user", "content": user})
        t0 = time.time()
        with self.lock:
            r = self.llm.create_chat_completion(
                messages=msgs, temperature=0.0, max_tokens=80,
                response_format={"type": "json_object", "schema": SCHEMA},
            )
        raw = r["choices"][0]["message"]["content"]
        out = {**EMPTY, **json.loads(raw)}
        out = _clean(out)
        out["ms"] = int((time.time() - t0) * 1000)
        return out


def _clean(o: dict) -> dict:
    """Make the fields consistent whatever the model produced."""
    if o.get("command") not in COMMANDS:
        o["command"] = None
    for k in ("from", "to"):
        v = o.get(k)
        o[k] = v.strip().lower() if isinstance(v, str) and v.strip() else None
    if o["intent"] != "command":
        o["command"], o["number"] = None, None
    if o["intent"] in ("off_topic", "unclear"):
        o.update({"from": None, "to": None, "from_here": False, "preference": None,
                  "leave_in_min": None, "leave_at": None, "tomorrow": False})
    if o["from_here"]:
        o["from"] = None
    la = o.get("leave_at")
    if la is not None:
        try:
            h, m = (int(x) for x in str(la).split(":"))
            o["leave_at"] = f"{h:02d}:{m:02d}" if 0 <= h < 24 and 0 <= m < 60 else None
        except ValueError:
            o["leave_at"] = None
    return o


_instance: VoiceLLM | None = None
_load_lock = threading.Lock()


def get(models_dir: Path, model_file: str = MODEL_FILE) -> VoiceLLM | None:
    """The shared model, loaded on first use (~2 s). None when the file or llama-cpp is missing."""
    global _instance
    if _instance is not None:
        return _instance
    path = models_dir / "llm" / model_file
    if not path.exists():
        return None
    with _load_lock:
        if _instance is None:
            try:
                _instance = VoiceLLM(path)
                log.info("voice LLM loaded from %s", path.name)
            except Exception:  # noqa: BLE001  (llama-cpp not installed, bad file)
                log.exception("voice LLM could not be loaded")
                return None
    return _instance
