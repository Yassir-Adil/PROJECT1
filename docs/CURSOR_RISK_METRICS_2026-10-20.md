# CURSOR_RISK_METRICS — semantic_risk_metrics

**Status:** `SOURCE_READY` (await independent Codex GREEN; this worker does not claim GREEN)
**Worker:** Cursor E
**Date:** 2026-10-20
**Baseline:** 5b4986a / 0.9.28 / 539 tests (queue active pair: reuse research and failure-focused evaluation)
**Scope executed:** E Stage-1 tests, then Phase-2 implementation after Codex RED (`FileNotFoundError` for `semantic_risk_metrics.py`, exit 1; private log path not read by this worker).

## Changed files (exact)

1. `research/tests/test_semantic_risk_metrics.py` — Stage-1 contract + overflow-boundary regressions
2. `research/semantic_risk_metrics.py` — **(new)** Phase-2 implementation
3. `docs/CURSOR_RISK_METRICS_2026-10-20.md` — this report

No other files touched. No shell, build, ADB, Git, network, model, Android, guidance, permission, or private-output access.

## Implemented API

```python
GT_CLASS_COUNT = 151   # ADE20K objectInfo150 IDs 0..150 (0 = unlabeled/other)
PRED_CLASS_COUNT = 32  # Top-31 channels 0..31
FLOOR_PREDICTION_ID = 4

def full_confusion(prediction, ground_truth) -> np.ndarray:
    """int64 matrix shape (151, 32); rows=GT, cols=pred. No input mutation."""

def metrics(matrix) -> dict:
    """JSON-safe diagnostic dict; no input mutation; Python-int aggregates."""
```

### Behaviour notes

- Label maps: nonempty same-size 2D integer arrays; pred `0..31`, GT `0..150`; reject bool/float/size0/mismatch/out-of-range.
- Flat bin index uses **int64 copies** of labels so `uint8`/`uint16` cannot wrap on `gt * 32 + pred`.
- GT0 counted in the full matrix; excluded from labeled floor precision / non-floor-as-floor denominators.
- `metrics` validates shape `(151, 32)`, integer non-bool dtype, every cell in `0..INT64_MAX`, and **matrix total** via Python `int` also ≤ `INT64_MAX`.
- Floor diagnostics only; no hazard/safe/walkable/clear categories. Rugs/stairs remain caller-interpreted `per_gt` rows.

## Tests (16)

| Test | Intent |
| --- | --- |
| `test_constants_match_ade150_by_top31_layout` | constants |
| `test_full_confusion_preserves_stair_and_top31_gt_rows` | GT 54/60/97/122 + wall/floor/rug |
| `test_unlabeled_gt0_is_counted_but_not_precision_error` | GT0 not precision error |
| `test_all_empty_denominators_are_none` | empty → `None` denominators |
| `test_all_floor_gt_mispredicted_yields_recall_zero` | recall `0.0` when GT floor missed |
| `test_metrics_reports_diagnostic_floor_fields_and_per_gt` | full diagnostic field set |
| `test_no_implicit_hazard_or_safe_categories` | no hazard/safe keys |
| `test_rejects_shape_dtype_range_and_size_zero` | validation battery |
| `test_metrics_rejects_invalid_matrix_without_mutation` | bad matrix / no mutation |
| `test_full_confusion_does_not_mutate_inputs` | pred/gt unchanged |
| `test_deterministic_confusion_and_metrics` | determinism |
| `test_metrics_json_safe_with_allow_nan_false` | JSON `allow_nan=False` |
| `test_uint8_label_maps_do_not_wrap_flat_indices` | unsigned index wrap regression |
| `test_metrics_rejects_entry_above_int64_max` | uint64 cell > int64 max |
| `test_metrics_rejects_aggregate_sum_above_int64_max` | two large cells, sum overflow |
| `test_metrics_accepts_int64_max_single_cell` | boundary cell accepted |

## RED / GREEN evidence

- **RED (Codex, independent):** confirmed missing module / `FileNotFoundError` before Phase-2 source (private log not read here).
- **GREEN:** **not claimed by Cursor E.** Codex must re-run the unittest file offline and record the result.

## Genuine unverified limits

- No ADE20K images, `.tflite` models, or private fixtures accessed.
- Diagnostics only: never CLEAR, walkability, navigation authority, or TTS.
- Rugs/stairs are not auto-classified as hazard or safe.
- Unit tests do not prove on-device segmenter behaviour or safety acceptance.

## NEXT

1. Codex runs `research/tests/test_semantic_risk_metrics.py` offline and records GREEN or failures.
2. Independent review; keep module off main guidance path.
3. Integration remains Codex-owned.
