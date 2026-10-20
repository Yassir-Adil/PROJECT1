# Cursor H — semantic ground rejection reasons (2026-10-20)

**Status: SOURCE_READY**

Exclusive scope: `SemanticGroundDiagnostic.kt` only (plus this note).
Codex owns RED/GREEN, new reason tests, Activity/overlay metrics, shared docs,
and review. No shell, build, unit-test run, ADB, Git, or network in this scope.
No runtime or test outcome is claimed here.

## Problem (0.9.33 context)

Measured 0.9.33 owner-ready run: 13 fresh fusion / 0 candidate, support count
observed in 0..6. Old `status` alone could not say why six supports were still
rejected (`insufficient_support` covered span, area, and height failures alike).
Point clouds from that run were not recorded, so that session’s exact geometric
failure mode cannot be reconstructed and is not claimed here.

## Change (diagnostic only)

- Added `reason: String` on `SemanticGroundReading` (default `""` keeps prior
  constructors source-compatible).
- `evaluate` fills precise reasons; **statuses, thresholds, support/component
  admission, reference rules, and unknown-when-ref-absent (=64) are unchanged.**
- `reason` has no authority, lifetime, or navigation effect.

### Reasons required by the new Codex tests

| `reason` | Typical prior `status` |
| --- | --- |
| `no_bottom_floor_component` | `no_floor_component` |
| `too_few_geometry_samples` | `insufficient_support` |
| `insufficient_xz_span` | `insufficient_support` |
| `collinear_or_small_area` | `insufficient_support` |
| `non_horizontal_floor_samples` | `insufficient_support` |
| `single_frame_floor_like_only` | `floor_like_candidate` |

### Additional honest structure / arithmetic reasons

Invalid structure and nonfinite arithmetic no longer borrow a fake
“too few samples” story; status stays `invalid` or `insufficient_support` as
before, with specific reasons such as:

- `invalid_cell_count`
- `invalid_cell_structure`
- `partial_or_nonfinite_geometry`
- `nonfinite_support_coordinates`
- `nonfinite_xz_span`
- `nonfinite_median_y`
- `nonfinite_height_delta`

## Explicit non-claims

- No compile/test/device result asserted by this worker.
- No reconstruction of the 0.9.33 geometric reject cause.
- No overlay, Activity, threshold, model, permission, or navigation change.
- Candidate remains single-frame diagnostic only (`single_frame_floor_like_only`).

## Hand-off

SOURCE_READY for Codex to compile against `SemanticGroundReasonTest` and own
GREEN / integration / review.
