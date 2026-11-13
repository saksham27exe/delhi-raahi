"""Evaluation metrics. All functions take plain arrays and are unit tested."""
from __future__ import annotations

import numpy as np
from sklearn.metrics import f1_score


def mae(y: np.ndarray, p: np.ndarray) -> float:
    return float(np.mean(np.abs(y - p)))


def rmse(y: np.ndarray, p: np.ndarray) -> float:
    return float(np.sqrt(np.mean((y - p) ** 2)))


def wape(y: np.ndarray, p: np.ndarray) -> float | None:
    """sum|y-p| / sum|y|. Zero-volume periods contribute their error to the numerator only;
    if the whole set has zero volume WAPE is undefined and None is returned."""
    denom = float(np.sum(np.abs(y)))
    if denom == 0:
        return None
    return float(np.sum(np.abs(y - p)) / denom)


def interval_coverage(y: np.ndarray, lo: np.ndarray, hi: np.ndarray) -> float:
    return float(np.mean((y >= lo) & (y <= hi)))


def classify(values: np.ndarray, thresholds: list[float]) -> np.ndarray:
    """Bin values with ascending thresholds -> class index 0..len(thresholds)."""
    return np.searchsorted(np.asarray(thresholds), values, side="right")


def macro_f1(y_cls: np.ndarray, p_cls: np.ndarray) -> float:
    return float(f1_score(y_cls, p_cls, average="macro", zero_division=0))


def summary(y: np.ndarray, p: np.ndarray) -> dict:
    return {"mae": round(mae(y, p), 4), "rmse": round(rmse(y, p), 4),
            "wape": None if wape(y, p) is None else round(wape(y, p), 4), "n": int(len(y)),
            "total_actual": float(np.sum(y)), "total_predicted": round(float(np.sum(p)), 1)}
