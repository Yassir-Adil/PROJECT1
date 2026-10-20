"""Failure-focused ADE150×Top31 semantic risk diagnostics (research only).

Counts every ground-truth class 0..150 against Top-31 predictions 0..31.
GT0 (unlabeled/other) is retained in the full confusion matrix but is not
treated as negative evidence in labeled floor precision / error fractions.

Diagnostic only: no hazard/safe/walkable inference, no models, no guidance.
"""

from __future__ import annotations

import numpy as np

GT_CLASS_COUNT = 151
PRED_CLASS_COUNT = 32
FLOOR_PREDICTION_ID = 4

_INT64_MAX = int(np.iinfo(np.int64).max)
_MATRIX_SHAPE = (GT_CLASS_COUNT, PRED_CLASS_COUNT)


def _require_integer_ndarray(name: str, array: np.ndarray) -> None:
    if not isinstance(array, np.ndarray):
        raise ValueError(f"{name} must be a numpy ndarray")
    if array.dtype == np.bool_ or np.issubdtype(array.dtype, np.bool_):
        raise ValueError(f"{name} must be an integer array, not bool")
    if not np.issubdtype(array.dtype, np.integer):
        raise ValueError(f"{name} must be an integer array")


def _require_label_maps(prediction: np.ndarray, ground_truth: np.ndarray) -> None:
    _require_integer_ndarray("prediction", prediction)
    _require_integer_ndarray("ground_truth", ground_truth)
    if prediction.ndim != 2 or ground_truth.ndim != 2:
        raise ValueError("prediction and ground_truth must be 2D arrays")
    if prediction.shape != ground_truth.shape:
        raise ValueError("prediction and ground_truth shapes must match")
    if prediction.size == 0:
        raise ValueError("prediction and ground_truth must be nonempty (size > 0)")
    if prediction.size > _INT64_MAX:
        raise ValueError("label map pixel count exceeds int64")
    pred_i = prediction.astype(np.int64, copy=False)
    gt_i = ground_truth.astype(np.int64, copy=False)
    if np.any((pred_i < 0) | (pred_i >= PRED_CLASS_COUNT)):
        raise ValueError("prediction contains a class ID outside 0..31")
    if np.any((gt_i < 0) | (gt_i >= GT_CLASS_COUNT)):
        raise ValueError("ground_truth contains a class ID outside 0..150")


def _as_int64_count(value, *, name: str = "count") -> int:
    """Convert one matrix entry to a Python int in 0..INT64_MAX.

    Uses integer conversion only (no float) so values near int64 max are not
    rounded by IEEE-754 mantissa limits.
    """
    if isinstance(value, (bool, np.bool_)):
        raise ValueError(f"{name} must be a nonnegative integer fitting int64")
    if not isinstance(value, (int, np.integer)):
        raise ValueError(f"{name} must be a nonnegative integer fitting int64")
    count = int(value)
    if count < 0 or count > _INT64_MAX:
        raise ValueError(f"{name} must be a nonnegative integer fitting int64")
    return count


def _sum_counts(values, *, name: str = "aggregate") -> int:
    """Sum counts with Python int; reject totals that do not fit int64."""
    total = 0
    for value in np.asarray(values).reshape(-1):
        total += _as_int64_count(value, name=name)
        if total > _INT64_MAX:
            raise ValueError(f"{name} sum exceeds int64")
    return total


def full_confusion(prediction: np.ndarray, ground_truth: np.ndarray) -> np.ndarray:
    """Return int64 confusion matrix shape (151, 32); rows=GT, cols=pred.

    Does not mutate inputs. Index arithmetic uses int64 so uint8/uint16 label
    maps cannot wrap when forming flat bin indices.
    """
    _require_label_maps(prediction, ground_truth)
    # Always copy into int64 so unsigned dtypes cannot wrap on gt*32+pred.
    pred_i = np.asarray(prediction, dtype=np.int64)
    gt_i = np.asarray(ground_truth, dtype=np.int64)
    flat = gt_i.reshape(-1) * np.int64(PRED_CLASS_COUNT) + pred_i.reshape(-1)
    bins = np.bincount(flat, minlength=GT_CLASS_COUNT * PRED_CLASS_COUNT)
    matrix = bins.astype(np.int64, copy=False).reshape(_MATRIX_SHAPE)
    return matrix


def metrics(matrix: np.ndarray) -> dict:
    """JSON-safe floor-false-positive diagnostics from a full confusion matrix.

    Does not mutate ``matrix``. Aggregate pixel totals use Python ``int`` so
    unsigned numpy reductions cannot wrap. Each cell and the matrix total must
    fit in signed int64.
    """
    if not isinstance(matrix, np.ndarray):
        raise ValueError("matrix must be a numpy ndarray")
    _require_integer_ndarray("matrix", matrix)
    if matrix.shape != _MATRIX_SHAPE:
        raise ValueError("matrix shape must be (151, 32)")

    # Validate every cell fits nonnegative int64 without mutating the input.
    for value in matrix.reshape(-1):
        _as_int64_count(value, name="matrix entry")
    _sum_counts(matrix, name="matrix")

    floor = FLOOR_PREDICTION_ID
    floor_col = matrix[:, floor]

    floor_prediction_pixels_all = _sum_counts(floor_col, name="floor_prediction_pixels_all")
    unlabeled_as_floor_pixels = _as_int64_count(floor_col[0], name="unlabeled_as_floor_pixels")
    tp_floor = _as_int64_count(matrix[floor, floor], name="tp_floor")
    floor_prediction_pixels_labeled = _sum_counts(
        floor_col[1:], name="floor_prediction_pixels_labeled"
    )
    non_floor_as_floor_pixels = floor_prediction_pixels_labeled - tp_floor
    if non_floor_as_floor_pixels < 0:
        raise ValueError("inconsistent floor column counts")

    gt_floor_pixels = _sum_counts(matrix[floor, :], name="gt_floor_pixels")

    floor_precision_labeled = (
        tp_floor / floor_prediction_pixels_labeled
        if floor_prediction_pixels_labeled
        else None
    )
    floor_recall = tp_floor / gt_floor_pixels if gt_floor_pixels else None
    non_floor_as_floor_fraction = (
        non_floor_as_floor_pixels / floor_prediction_pixels_labeled
        if floor_prediction_pixels_labeled
        else None
    )

    per_gt = []
    for class_id in range(GT_CLASS_COUNT):
        pixels = _sum_counts(matrix[class_id, :], name=f"per_gt[{class_id}].pixels")
        predicted_floor_pixels = _as_int64_count(
            matrix[class_id, floor], name=f"per_gt[{class_id}].predicted_floor_pixels"
        )
        fraction = (
            predicted_floor_pixels / pixels if pixels else None
        )
        per_gt.append(
            {
                "id": class_id,
                "pixels": pixels,
                "predicted_floor_pixels": predicted_floor_pixels,
                "predicted_floor_fraction": fraction,
            }
        )

    return {
        "diagnostic_only": True,
        "floor_prediction_id": floor,
        "floor_prediction_pixels_all": floor_prediction_pixels_all,
        "floor_prediction_pixels_labeled": floor_prediction_pixels_labeled,
        "floor_precision_labeled": floor_precision_labeled,
        "floor_recall": floor_recall,
        "unlabeled_as_floor_pixels": unlabeled_as_floor_pixels,
        "non_floor_as_floor_pixels": non_floor_as_floor_pixels,
        "non_floor_as_floor_fraction": non_floor_as_floor_fraction,
        "per_gt": per_gt,
    }
