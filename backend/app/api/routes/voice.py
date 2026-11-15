"""Voice assistant fallback: a small local LLM reads a sentence the app's rules could not."""
from __future__ import annotations

import threading

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.core.config import get_settings
from app.services import voice_llm

router = APIRouter(prefix="/api/v1/voice", tags=["voice"])


class UnderstandReq(BaseModel):
    text: str = Field(..., min_length=1, max_length=300)
    context: str | None = Field(None, max_length=200)


def preload() -> None:
    """Load the model in the background at startup so the first voice request doesn't wait for it."""
    def load() -> None:
        m = voice_llm.get(get_settings().crowdwise_models_dir)
        if m is not None:
            m.understand("warm up")   # reads the fixed instructions + examples once; they stay cached
    threading.Thread(target=load, daemon=True).start()


@router.get("/status")
def status() -> dict:
    return {"model": voice_llm.MODEL_FILE, "loaded": voice_llm._instance is not None}


@router.post("/understand")
def understand(req: UnderstandReq) -> dict:
    """Fields only (intent, from, to, command, preference, time); never free text. 503 if no model here."""
    m = voice_llm.get(get_settings().crowdwise_models_dir)
    if m is None:
        raise HTTPException(503, detail="The voice model is not installed on this server.")
    return m.understand(req.text, req.context)
