# CURSOR_HEIGHT_RESULT — SurfaceHeightProfile (tests first)

**Status:** `SOURCE_READY` (implementation appended below; tests-first section retained)
**Worker:** Cursor H
**Date:** 2026-10-20
**Baseline:** 28fd899 / 0.9.30/43 (queue active pair: texture-depth projection and surface-height diagnostics)
**Scope executed:** H tests then source after Codex RED. GREEN not claimed here.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/SurfaceHeightProfileTest.kt` **(new)**
2. `docs/CURSOR_HEIGHT_RESULT_2026-10-20.md` **(new)**

No other files touched. No `SurfaceHeightProfile.kt` (or any debug source). No Gradle, shell, ADB, Git, Activity, navigation, speech, model, permission, network, or private-output edits. Existing `SurfaceInventoryPolicy` left unread-as-edit (reference only).

## Target APIs expected by tests (Codex implements after RED)

```kotlin
internal data class SurfaceHeightCandidate(
    val kind: SurfaceKind,
    val centerWorldY: Double,
    val tracking: Boolean,
    val subsumed: Boolean,
)

internal data class SurfaceHeightSnapshot(
    val status: String,
    val inspected: Int,
    val eligibleUpward: Int,
    val ignored: Int,
    val invalidHeight: Int,
    val minCameraAboveMeters: Double?,
    val maxCameraAboveMeters: Double?,
    val truncated: Boolean,
)

internal object SurfaceHeightProfile {
    const val MAX_PLANES: Int = 256

    fun summarize(
        candidates: Iterable<SurfaceHeightCandidate>,
        cameraTracking: Boolean,
        cameraWorldY: Double,
    ): SurfaceHeightSnapshot
}
```

Reuses existing `SurfaceKind`. Pure package `com.triguide.ai.diagnostics`.

### Contract encoded by the tests

1. `!cameraTracking` → status `not_tracking`, all zeros / null range / not truncated. Do **not** iterate candidates and do **not** validate `cameraWorldY` (NaN cameraY still `not_tracking`).
2. Tracking with non-finite `cameraWorldY` (NaN / ±Infinity) → status `invalid_camera`, same empty snapshot; do **not** iterate.
3. Otherwise status is always `observed` (including empty and truncated). Consume at most `MAX_PLANES` (256) via lazy `hasNext`/`next`; truncation peeked with `hasNext` only — never `next` on candidate 257.
4. Eligible only when `kind == HORIZONTAL_UPWARD && tracking && !subsumed`. All other candidates are `ignored`, even if `centerWorldY` is non-finite.
5. Eligible with non-finite `centerWorldY`, or non-finite / overflow `cameraWorldY - centerWorldY`, → `invalidHeight` (not eligible). Valid height is the signed difference (negative allowed when the plane is above the camera).
6. `minCameraAboveMeters` / `maxCameraAboveMeters` are min/max over finite eligible diffs only; no eligible finite diffs → both `null`. No magic default camera height; no lowest-plane-as-floor classification.
7. `inspected == ignored + invalidHeight + eligibleUpward`. Stateless pure function; no retained SDK refs / session state / navigation authority.
8. Snapshot / status text never encodes floor / wall / clear / walkable / safe / camera_height semantics.

## Tests written (18)

| Test | Intent |
| --- | --- |
| `emptyWhileTrackingIsObservedWithNullRange` | empty → `observed`, null min/max |
| `cameraNotTrackingReturnsNotTrackingWithoutReadingIterableOrCameraY` | no iterator; NaN cameraY ignored |
| `cameraNotTrackingDoesNotTouchThrowingIterable` | exploding iterable untouched |
| `nonFiniteCameraYWhileTrackingIsInvalidCameraWithoutIteration` | NaN/±Inf → `invalid_camera` |
| `positiveCameraAboveRangeUsesSignedDifferences` | positive camera-above range |
| `negativeRangeWhenPlaneIsAboveCamera` | negative signed diff |
| `mixedPositiveAndNegativeHeightsTrackFiniteMinMax` | mixed min/max |
| `finiteHugeDifferencesRemainEligible` | 1e200 finite remains eligible |
| `overflowDifferenceCountsAsInvalidHeightNotEligible` | MAX_VALUE − (−MAX_VALUE) → invalidHeight |
| `eligibleNonFiniteCenterWorldYIsInvalidHeight` | NaN/±Inf center on eligible |
| `nonEligibleCandidatesAreIgnoredEvenWithInvalidCenterY` | ignore precedence over invalidHeight |
| `maxPlanesConstantIsTwoHundredFiftySix` | `MAX_PLANES == 256` |
| `exactlyMaxPlanesIsObservedNotTruncated` | exact 256, not truncated |
| `lazyUnboundedIterableConsumesAtMostMaxPlanesAndNeverNextBeyond` | cap + hasNext peek, no next 257 |
| `truncatedDoesNotConsumeTheExtraCandidateViaNext` | finite list of 257, nextCalls==256 |
| `permutationPreservesCountsAndMinMax` | order-independent stats |
| `repeatedSummarizeIsStatelessAndEqual` | repeat equality |
| `snapshotFieldsNeverImplyFloorCameraHeightOrClearance` | diagnostic wording ban |

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `SurfaceHeightProfile` / `SurfaceHeightCandidate` / `SurfaceHeightSnapshot`.
- Codex must record that compile RED **before** adding `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SurfaceHeightProfile.kt`.

## Genuine unverified limits

- No device frames, ARCore planes, pose wiring, or Activity integration.
- Relative signed heights only; never floor selection, calibrated camera height, CLEAR, or navigation authority.
- JVM unit tests do not prove live inventory logging, thermal cost, or ground-reference accuracy.

## NEXT

`TESTS_READY` → await Codex to record compile RED, then explicit release of Cursor H (or Codex) to implement `SurfaceHeightProfile.kt` only. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor H released after Codex RED)

**Status:** `SOURCE_READY`
**Codex RED (independent):** pair compile before source — exit 1, unresolved `SurfaceHeightCandidate` / `SurfaceHeightProfile` / `SurfaceHeightSnapshot` (`projection-height-red-20261020.log`).

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SurfaceHeightProfile.kt` **(new)** — `SurfaceHeightCandidate` + `SurfaceHeightSnapshot` + `SurfaceHeightProfile`
2. `docs/CURSOR_HEIGHT_RESULT_2026-10-20.md` **(appended)**

**Unchanged:** `SurfaceHeightProfileTest.kt` (18 cases preserved, no weakening). No shell/Gradle/ADB/Git, Activity, `SurfaceInventoryPolicy`, navigation, speech, SDK types, or other files.

### Implementation notes

- Exact API as contracted: `MAX_PLANES = 256`, `summarize(candidates, cameraTracking, cameraWorldY)`.
- Gate order: `!cameraTracking` → `not_tracking` (no iterable / no cameraY check); non-finite `cameraWorldY` → `invalid_camera` (no iterate); else always status `observed`.
- Lazy bound: consume at most 256 via `hasNext`/`next`; `truncated` from post-cap `hasNext` only.
- Eligible: `HORIZONTAL_UPWARD && tracking && !subsumed`. Others `ignored` even with non-finite center.
- Eligible non-finite center or non-finite/overflow `cameraWorldY - centerWorldY` → `invalidHeight`. Valid signed diffs update min/max; empty eligible → nulls.
- Pure/stateless; no extra fields, floor classification, magic camera height, retained SDK refs, or navigation authority.

### GREEN evidence

- **Not run by this worker.** No invented GREEN. Codex owns focused/full verification of the preserved 18 `SurfaceHeightProfileTest` cases.

### NEXT

`SOURCE_READY` → Codex verifies. This worker stops here.
