"""Download the voice assistant's local model (Qwen2.5-1.5B-Instruct, 4-bit GGUF, ~1.1 GB, Apache-2.0).

    ../.venv/Scripts/python scripts/fetch_llm.py

Also needs: pip install llama-cpp-python --extra-index-url https://abetlen.github.io/llama-cpp-python/whl/cpu
Without the model the backend still runs; the voice assistant then uses its rules only.
"""
from __future__ import annotations

import sys
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.services.voice_llm import MODEL_FILE  # noqa: E402

URL = f"https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/{MODEL_FILE}"
dest = Path(__file__).resolve().parents[2] / "models" / "llm" / MODEL_FILE

if dest.exists():
    print("already there:", dest)
else:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(".part")
    print("downloading", URL)
    urllib.request.urlretrieve(URL, tmp)
    tmp.rename(dest)
    print("saved", dest, f"{dest.stat().st_size / 1e9:.2f} GB")
