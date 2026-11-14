"""Pareto filtering, preference-weighted ranking and plain-language explanations."""
from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np

CONFIG = json.loads((Path(__file__).parent / "ranking_config.json").read_text())


@dataclass
class ScoredJourney:
    key: str                         # route sequence, for de-duplication
    duration_min: float
    exposure: float                  # sum(CI_i * minutes_i), CI imputed for unknown segments
    transfers: int
    walk_min: float
    in_vehicle_min: float
    mean_crowd_index: float | None   # time-weighted, known segments only
    unknown_time_share: float
    payload: dict = field(default_factory=dict)
    score: float = 0.0
    labels: list[str] = field(default_factory=list)
    explanation: list[str] = field(default_factory=list)


def pareto_front(items: list[ScoredJourney]) -> list[ScoredJourney]:
    """Keep journeys not dominated on (duration, exposure, transfers)."""
    out = []
    for a in items:
        dominated = any(
            (b.duration_min <= a.duration_min and b.exposure <= a.exposure and b.transfers <= a.transfers)
            and (b.duration_min < a.duration_min or b.exposure < a.exposure or b.transfers < a.transfers)
            for b in items if b is not a
        )
        if not dominated:
            out.append(a)
    return out


def _norm(x: np.ndarray) -> np.ndarray:
    span = x.max() - x.min()
    return np.zeros_like(x) if span == 0 else (x - x.min()) / span


def score(items: list[ScoredJourney], mode: str) -> None:
    w = CONFIG["modes"][mode]
    T = _norm(np.array([j.duration_min for j in items], float))
    E = _norm(np.array([j.exposure for j in items], float))
    X = np.array([j.transfers for j in items], float)
    X = X / X.max() if X.max() > 0 else X
    W = _norm(np.array([j.walk_min for j in items], float))
    for j, t, e, x, wk in zip(items, T, E, X, W):
        j.score = round(float(w["w_time"] * t + w["w_crowd"] * e + w["w_transfer"] * x + w["w_walk"] * wk), 4)


def rank(items: list[ScoredJourney], mode: str) -> list[ScoredJourney]:
    if not items:
        return []
    # de-duplicate identical route sequences: keep the earliest arrival
    best: dict[str, ScoredJourney] = {}
    for j in items:
        if j.key not in best or j.duration_min < best[j.key].duration_min:
            best[j.key] = j
    items = list(best.values())
    front = pareto_front(items)
    score(items, mode)
    # keep the Pareto front plus the best-scoring remainder, up to max_results
    chosen = sorted(front, key=lambda j: j.score)
    rest = sorted((j for j in items if j not in front), key=lambda j: j.score)
    chosen = (chosen + rest)[: CONFIG["max_results"]]
    # Always show the best direct trip, the best with one change and the best with two, when they exist.
    for k in sorted({j.transfers for j in items}):
        if not any(j.transfers == k for j in chosen):
            chosen.append(min((j for j in items if j.transfers == k), key=lambda j: j.score))
    score(chosen, mode)  # re-normalise within the shown set
    _label(chosen)
    _explain(chosen)
    return sorted(chosen, key=lambda j: j.score)


def _label(items: list[ScoredJourney]) -> None:
    fastest = min(items, key=lambda j: (j.duration_min, j.exposure))
    fastest.labels.append("fastest")
    limit = fastest.duration_min * CONFIG["least_crowded_max_duration_ratio"]
    eligible = [j for j in items if j.duration_min <= limit] or items
    least = min(eligible, key=lambda j: (j.exposure / max(j.in_vehicle_min, 1), j.duration_min))
    least.labels.append("least_crowded")
    w = CONFIG["modes"]["balanced"]
    T = _norm(np.array([j.duration_min for j in items], float))
    E = _norm(np.array([j.exposure for j in items], float))
    X = np.array([j.transfers for j in items], float)
    X = X / X.max() if X.max() > 0 else X
    bal = np.array([w["w_time"] * t + w["w_crowd"] * e + w["w_transfer"] * x for t, e, x in zip(T, E, X)])
    items[int(np.argmin(bal))].labels.append("balanced")


def _explain(items: list[ScoredJourney]) -> None:
    fastest = next(j for j in items if "fastest" in j.labels)
    for j in items:
        ex = []
        if "fastest" in j.labels:
            ex.append("Fastest scheduled journey among the options found.")
        if "least_crowded" in j.labels:
            ex.append("Lowest expected demand pressure per minute on board among reasonable options.")
        if j is not fastest:
            dt = j.duration_min - fastest.duration_min
            if fastest.exposure > 0:
                de = 100 * (fastest.exposure - j.exposure) / fastest.exposure
                if de >= 10 and dt > 0:
                    ex.append(f"{dt:.0f} min longer than the fastest option, with {de:.0f}% lower expected crowd exposure.")
                elif de <= -10:
                    ex.append(f"{-de:.0f}% higher expected crowd exposure than the fastest option.")
        if j.transfers == 0:
            ex.append("No change of bus.")
        if j.unknown_time_share > 0.3:
            ex.append(f"Crowd evidence is limited for {100 * j.unknown_time_share:.0f}% of the ride (scored conservatively).")
        hs = j.payload.get("high_demand_segments", 0)
        if hs:
            ex.append(f"{hs} stop-to-stop section(s) expected to be High or Very High demand.")
        j.explanation = ex
