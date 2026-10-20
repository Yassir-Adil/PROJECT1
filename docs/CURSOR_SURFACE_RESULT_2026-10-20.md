# CURSOR_SURFACE_RESULT — SurfaceProbePolicy (tests first)

**Status:** `TESTS_READY`
**Worker:** Cursor S
**Date:** 2026-10-20
**Baseline:** e4bc1ec / 0.9.27 / 499 full tests (queue active pair: SDK surfaces and visible camera)
**Scope executed:** S tests ONLY. Helper source not implemented.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/SurfaceProbePolicyTest.kt` **(new)**
2. `docs/CURSOR_SURFACE_RESULT_2026-10-20.md` **(new)**

No other files touched. No `SurfaceProbePolicy.kt` (or any debug source). No Gradle, shell, ADB, Git, Activity, navigation, speech, model, permission, network, or private-output edits.

## Target APIs expected by tests (Codex implements after RED)

```kotlin
internal enum class SurfaceKind {
    UNKNOWN,
    VERTICAL_SURFACE,
    HORIZONTAL_UPWARD,
    HORIZONTAL_DOWNWARD,
}

internal data class SurfaceHitCandidate(
    val kind: SurfaceKind,
    val distanceMeters: Float,
    val tracking: Boolean,
    val subsumed: Boolean,
    val insidePolygon: Boolean,
)

internal data class SurfaceProbeReading(
    val kind: SurfaceKind,
    val distanceMeters: Float?,
    val reason: String,
)

internal object SurfaceProbePolicy {
    fun select(
        candidates: List<SurfaceHitCandidate>,
        tracking: Boolean,
    ): SurfaceProbeReading
}

internal data class SurfaceProbeRay(
    val name: String,
    val xPx: Float,
    val yPx: Float,
)

internal object SurfaceProbeLayout {
    fun points(width: Int, height: Int): List<SurfaceProbeRay>
}
```

### Contract encoded by the tests

**`SurfaceProbePolicy.select`**

1. Session `tracking == false` → `UNKNOWN` / `distanceMeters == null` / reason `not_tracking`, regardless of candidate list (including size &gt; 64).
2. Valid candidate requires all of: `kind != UNKNOWN`, `tracking`, `!subsumed`, `insidePolygon`, positive finite `distanceMeters` (reject NaN, ±Infinity, 0, negative).
3. Among valid candidates, select the **minimum** distance; result independent of input order. Kind and distance come from that candidate; reason `observed_surface`.
4. Empty list or no valid candidates → `UNKNOWN` / null distance / `no_valid_surface`.
5. Candidate count **&gt; 64** (with session tracking) → `UNKNOWN` / null distance / `too_many_hits`. Exactly 64 remains selectable.
6. Semantic honesty: `HORIZONTAL_UPWARD` stays that kind with reason `observed_surface` — not floor / clear / walkable. `VERTICAL_SURFACE` is not confirmed wall. No SDK objects retained (primitive fixtures only).

**`SurfaceProbeLayout.points`**

1. Invalid viewport (`width &lt;= 0` or `height &lt;= 0`) → empty list.
2. Valid viewport → exactly four rays in order, VIEW pixels (fractions × size, not raw depth/CPU-image coords):
   - `center` (0.5, 0.5)
   - `lower_left` (0.25, 0.8)
   - `lower_center` (0.5, 0.8)
   - `lower_right` (0.75, 0.8)

## Tests written (19)

| Test | Intent |
| --- | --- |
| `sessionNotTrackingReturnsUnknownRegardlessOfHits` | session tracking false → `not_tracking` |
| `sessionNotTrackingWinsOverTooManyHits` | tracking gate before size bound |
| `candidateNotTrackingIsFiltered` | stopped / non-tracking plane dropped |
| `subsumedPlaneIsFiltered` | subsumed dropped |
| `outsidePolygonHitIsFiltered` | polygon inclusion mandatory |
| `unknownKindIsFiltered` | UNKNOWN type dropped |
| `nonFiniteZeroAndNegativeDistancesAreFiltered` | NaN / ±Inf / 0 / negative |
| `sparseMixOfInvalidPlanesYieldsNoValidSurfaceWhenNoneRemain` | sparse all-invalid → `no_valid_surface` |
| `emptyCandidatesYieldNoValidSurface` | empty → `no_valid_surface` |
| `nearestValidDistanceIsSelectedIndependentOfInputOrder` | min distance, order-independent |
| `invalidCloserHitsDoNotBeatFartherValidCandidate` | filtered closer hits ignored |
| `exactlySixtyFourCandidatesAreAcceptedWhenValid` | bound inclusive at 64 |
| `moreThanSixtyFourCandidatesReturnTooManyHits` | &gt;64 → `too_many_hits` |
| `horizontalUpwardIsObservedSurfaceNotInferredFloor` | no floor/clear/walkable inference |
| `verticalSurfaceIsObservedSurfaceNotConfirmedWall` | no confirmed-wall wording |
| `surfaceKindEnumExposesExactContractValues` | enum membership / order |
| `invalidViewportReturnsEmptyRayList` | 0 / negative width or height |
| `pointsReturnExactlyFourNamedViewPixelRays` | names + fractions × size |
| `viewportMappingUsesViewPixelsNotNormalizedFractionsAsCoordinates` | 1920×1080 pixel check |

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB/Git forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `SurfaceKind` / `SurfaceHitCandidate` / `SurfaceProbeReading` / `SurfaceProbePolicy` / `SurfaceProbeRay` / `SurfaceProbeLayout`.
- Codex must independently record that missing-helper compile RED **before** adding `SurfaceProbePolicy.kt`.

## Genuine unverified limits

- No ARCore `Frame.hitTest` / `Plane` wiring (Codex integration later).
- Diagnostic selection only; never CLEAR, walkability, floor proof, wall confirmation, or navigation/speech authority.
- Viewport fractions match `setDisplayGeometry` VIEW space; not calibrated depth/CPU-image rays.

## NEXT

`TESTS_READY` → await Codex to record compile RED on missing helpers, then an **explicit release** for Cursor S to implement `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SurfaceProbePolicy.kt` only. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor S released)

**Status:** `IMPLEMENTATION_READY`
**Codex RED (independent):** `:app:testDebugUnitTest --tests '*SurfaceProbePolicyTest'` offline — exit 1, `surface-red.log`; missing `SurfaceKind`, `SurfaceHitCandidate`, policy/layout symbols.

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SurfaceProbePolicy.kt` **(new)** — `SurfaceKind`, `SurfaceHitCandidate`, `SurfaceProbeReading`, `SurfaceProbePolicy`, `SurfaceProbeRay`, `SurfaceProbeLayout`
2. `docs/CURSOR_SURFACE_RESULT_2026-10-20.md` **(appended)**

**Unchanged:** `SurfaceProbePolicyTest.kt` (no weakening). No shell/Gradle/ADB/Git, Activity, SDK adapter, navigation, speech, models, permissions, network, private data, or other files.

### Implementation notes

- Exact queue API; Kotlin/JDK only; no new dependencies; no SDK objects retained.
- `select`: session `!tracking` → `not_tracking` first; then size `> 64` → `too_many_hits`; else nearest valid positive finite distance → `observed_surface`; else `no_valid_surface`.
- Valid candidate: `kind != UNKNOWN`, `tracking`, `!subsumed`, `insidePolygon`, `distance.isFinite() && distance > 0`.
- Nearest chosen by strict min distance (order-independent for distinct distances).
- `SurfaceKind` is geometry orientation only — not `DiagnosticSurfaces` wall/floor object semantics; no clear/walkable inference.
- `SurfaceProbeLayout.points`: invalid viewport empty; four VIEW-pixel rays `center` / `lower_left` / `lower_center` / `lower_right` at fractions (0.5,0.5), (0.25,0.8), (0.5,0.8), (0.75,0.8).
- No speech, navigation, or movement authority.

### GREEN evidence

- **Not run by this worker.** No executed test claim. Codex owns focused/full GREEN, SDK adapter, and integration.

### NEXT

`IMPLEMENTATION_READY` → Codex verifies `SurfaceProbePolicyTest` GREEN and owns SDK adapter / Activity wiring. This worker stops here.
