# Cursor R — pair scope / coverage review — 2026-10-20

Codex condensed repetitive review passes here; full worker streams remain local,
ignored under outputs/reports/raw-depth-pair-20261020/. This preserves findings
and final disposition, not a new worker execution claim.

## Review scope and evidence

Cursor R independently reviewed WORK_QUEUE design, PairScope/Gate/Coverage,
Activity and tests. It changed only this review document; no shell, build, ADB,
Git, product files or private outputs. Its external re-fetch was approval-blocked.
Codex independently fetched the official contracts in this round and owns all
RED/GREEN, build, install and device evidence.

Primary references:
[ARCore Frame](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame),
[Android Image timestamps](https://developer.android.com/reference/android/media/Image#getTimestamp()),
[pinned SDK sample](https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/DepthData.java).

## Blocking design findings and current disposition

| Finding | Required boundary | Corrected source disposition |
| --- | --- | --- |
| B1 unsupported clock inference | Remove depth/confidence equality, Frame-depth ordering/lag, and maxSensorLagNs; compare only histories of the same source | Satisfied; Gate/tests use per-source stamps and local250ms processing age |
| B2 invalid proof reuse | Private construction only after successful co-acquisition on unchanged Frame; invalidate before resource cleanup; reject closed proof without state mutation | Satisfied; active callback proof and inactive-proof tests |
| B3 image lifetime on failure | Confidence then depth, exactly once, even when acquire/block/close throws; preserve primary exception | Satisfied by nested Kotlin use and scope finally; CPU cleanup tests present |
| B4 processing age is not sensor age | Never label sensor freshness, geometry accuracy, adequate spatial coverage or navigation eligibility as established | Activity SAMPLE/REJECT/RESULT carry unknown/unverified diagnostic-only labels |
| B5 observed coverage is not admission | Count decoded zero/sparse/rejected/reprojected pairs before pixel gate; distinguish null from zero; bounded immutable scalar state | Satisfied; confidence threshold remains128, observed fields distinct from admitted fields |

First admitted depth is a probe baseline, not proof of a new physical estimate.
Later admitted depth must advance relative to prior admitted raw depth; repeated/
backward estimates reject without poisoning valid recovery. Observed totals may
include repeated/reprojected data; they are not independent geometry or new-depth
rate. No tolerance or common-clock assumption is justified.

## Compiler/source blocker K1 and correction

Initial Cursor P source incorrectly assumed an enclosing Kotlin object could
access its nested Proof private constructor/invalidate. R flagged K1; Codex
independently confirmed compiler failure. Do not call that intermediate source
accepted.

Codex moved the full co-acquisition operation into Proof.companion.acquireAndUse,
where private construction/invalidation are legal. Outer withFrame delegates.
The entry still requires acquisition callbacks and stamp checks, not a standalone
mint(timestamp). Constructor and invalidate remain private. Kotlin use closes
both resources after proof invalidation, including exception paths.

R re-read corrected source and accepted it. Same-module callbacks are trusted
adapter code, not an adversarial security boundary. Activity supplies both SDK
methods on the same Frame and calls Gate within the live callback.

## Tests and verification limits

Scope tests cover changed timestamps, acquire/process/close failures, exact-once
cleanup and inactive proof. Gate tests cover unequal source stamps, per-source
replay/backward order, local age boundaries, rollback, tracking loss, terminal
stop and non-poisoning rejections. Coverage tests cover empty versus missing,
sparse/weighted totals, validation, capacity, immutable snapshots and stop.

Codex reports48 focused tests passed; R did not execute them. Full gate,
phone results, metric calibration and walking safety are not established by
this review. No source relaxation of confidence, permission, models, dependencies,
main guidance or speech was authorized by this review.

## Final source disposition

No remaining blocker found for this bounded corrected debug design/integration.
This is source review acceptance only, not build/device/accuracy acceptance.
See RAW_DEPTH_PAIRING_AND_COVERAGE_2026-10-20.md for Codex execution evidence.
