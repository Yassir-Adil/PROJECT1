# CURSOR_INVENTORY — SurfaceInventoryPolicy (tests first)

**Status:** `TESTS_READY`
**Worker:** Cursor P
**Date:** 2026-10-20
**Baseline:** 5b4986a / 0.9.28 / 539 full tests (queue active pair: bounded SDK plane inventory)
**Scope executed:** P tests ONLY. Helper source not implemented.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/SurfaceInventoryPolicyTest.kt` **(new)**
2. `docs/CURSOR_INVENTORY_2026-10-20.md` **(new)**

No other files touched. No `SurfaceInventoryPolicy.kt` (or any debug source). No Gradle, shell, ADB, Git, Activity, navigation, speech, model, permission, network, or private-output edits. Unrelated dirty owner-request-voice-ux rule preserved (not opened).

## Target APIs expected by tests (Codex implements after RED)

Reuses existing `SurfaceKind` from `SurfaceProbePolicy.kt`. New types live in
`SurfaceInventoryPolicy.kt` only after Codex records compile RED.

```kotlin
internal enum class SurfacePlaneTracking {
    TRACKING,
    PAUSED,
    STOPPED,
}

internal data class SurfacePlaneCandidate(
    val kind: SurfaceKind,
    val tracking: SurfacePlaneTracking,
    val subsumed: Boolean,
)

internal data class SurfaceInventorySnapshot(
    val status: String,
    val inspected: Int,
    val truncated: Boolean,
    val trackingUpward: Int,
    val trackingVertical: Int,
    val trackingDownward: Int,
    val paused: Int,
    val stopped: Int,
    val subsumed: Int,
    val unknownKind: Int,
)

internal object SurfaceInventoryPolicy {
    const val MAX_PLANES: Int = 256

    fun summarize(
        candidates: Iterable<SurfacePlaneCandidate>,
        cameraTracking: Boolean,
    ): SurfaceInventorySnapshot
}
```

### Contract encoded by the tests

**`SurfaceInventoryPolicy.summarize`**

1. `cameraTracking == false` → status `not_tracking`, all counters zero, `truncated == false`. **Must not** obtain an iterator or call `next` / `hasNext` on the candidates iterable (including exploding iterables).
2. Otherwise consume **at most** `MAX_PLANES` (256) candidates via the iterator. After the bound, detect one extra with `iterator.hasNext()` **without** calling `next()` on that extra element.
3. Count precedence on each consumed candidate:
   - `subsumed == true` → count `subsumed` only (excluded from paused / stopped / orientation / unknownKind).
   - else `PAUSED` → `paused` only (excluded from orientation / unknownKind).
   - else `STOPPED` → `stopped` only (excluded from orientation / unknownKind).
   - else `TRACKING` + known kind → `trackingUpward` / `trackingVertical` / `trackingDownward` by `SurfaceKind`.
   - else `TRACKING` + `UNKNOWN` → `unknownKind` only (not orientation).
4. `inspected` = number of candidates actually consumed (includes rejected / subsumed / paused / stopped / unknown).
5. `truncated == true` iff at least one further candidate remains after the bound (`hasNext`); status then `bounded`. Otherwise status `observed` (including empty input while tracking).
6. Exactly 256 → `observed`, not truncated. 257+ → `bounded`, truncated, `inspected == 256`.
7. Counts are deterministic for the same input sequence. No SDK / Frame / Plane objects retained (primitive fixtures only).
8. Diagnostic inventory only: never floor / wall / clearance / walkable / safe semantics in status or counters. Orientation names remain geometry labels only.

## Tests written (16)

| Test | Intent |
| --- | --- |
| `emptyWhileTrackingIsObservedWithZeroCounts` | empty → `observed`, zeros |
| `cameraNotTrackingReturnsNotTrackingAndZerosWithoutIteration` | negative state; CountingIterable untouched |
| `cameraNotTrackingDoesNotTouchThrowingIterable` | no `iterator()` when not tracking |
| `mixedPrecedenceSubsumedExcludedBeforePausedStoppedAndOrientation` | subsumed → paused/stopped → orientation |
| `subsumedPausedOrStoppedStillCountOnlyAsSubsumed` | subsumed wins over tracking state / unknown |
| `pausedAndStoppedAreExcludedFromOrientationCounts` | non-TRACKING excluded from orientations |
| `trackingUnknownKindIncrementsUnknownNotOrientation` | TRACKING UNKNOWN → `unknownKind` |
| `pausedOrStoppedUnknownDoesNotIncrementUnknownKind` | unknown only when TRACKING |
| `maxPlanesConstantIsTwoHundredFiftySix` | `MAX_PLANES == 256` |
| `exactlyMaxPlanesIsObservedNotTruncated` | 256 inclusive → `observed` |
| `moreThanMaxPlanesIsBoundedTruncatedAndInspectsOnlyMax` | 259 → `bounded`, inspected 256 |
| `lazyUnboundedIterableConsumesAtMostMaxPlanesAndPeeksOneExtra` | lazy unbounded; ≤256 `next`; hasNext peek |
| `truncatedDoesNotConsumeTheExtraCandidateViaNext` | 257th detected without `next` |
| `deterministicCountsAreStableAcrossRepeatedSummarize` | equal snapshots; mixed bucket counts |
| `surfacePlaneTrackingEnumExposesExactContractValues` | enum membership / order |
| `snapshotStatusNeverImpliesFloorWallOrClearance` | banned clearance wording; upward ≠ floor |

## Diagnostic-only limitations (honest)

- Counts available **orientation-only** planes versus ray misses elsewhere; this helper does **not** prove floor, wall, clearance, walkability, or safety.
- No ARCore `Session` / `Frame` / `Plane` wiring in this slice (Codex adapter later).
- Truncation is a diagnostic bound (`MAX_PLANES`), not a physical completeness claim.
- JVM unit tests do not prove live phone plane discovery, hit-test agreement, or thermal cost.
- Never CLEAR / turn / navigation / speech authority.

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB/Git/network forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `SurfacePlaneTracking` / `SurfacePlaneCandidate` / `SurfaceInventorySnapshot` / `SurfaceInventoryPolicy` (and `MAX_PLANES` / `summarize`).
- Existing `SurfaceKind` may already resolve; that must not be mistaken for inventory-helper GREEN.
- Codex must independently record missing-helper compile RED **before** adding `SurfaceInventoryPolicy.kt`.

## NEXT

`TESTS_READY` → await Codex to record compile RED on missing helpers, then an **explicit release** for Cursor P to implement `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SurfaceInventoryPolicy.kt` only. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor P released)

**Status:** `IMPLEMENTATION_READY`
**Codex RED (independent):** compile before source — exit 1, `inventory-red.log`; unresolved `SurfacePlaneTracking`, `SurfacePlaneCandidate`, `SurfaceInventoryPolicy`, `SurfaceInventorySnapshot`.

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SurfaceInventoryPolicy.kt` **(new)** — `SurfacePlaneTracking`, `SurfacePlaneCandidate`, `SurfaceInventorySnapshot`, `SurfaceInventoryPolicy`
2. `docs/CURSOR_INVENTORY_2026-10-20.md` **(appended)**

**Unchanged:** `SurfaceInventoryPolicyTest.kt` (no weakening). No shell/Gradle/ADB/Git, Activity, SDK adapter, navigation, speech, models, permissions, network, private data, or other files.

### Implementation notes

- Exact queue API; Kotlin/JDK only; no new dependencies; no SDK objects retained.
- `summarize`: `!cameraTracking` → `not_tracking` + all zeros **before** any iterator access.
- Otherwise iterate at most `MAX_PLANES` (256); after the bound, `truncated = iterator.hasNext()` without reading the extra element.
- Precedence per consumed candidate: subsumed → PAUSED → STOPPED → TRACKING known orientation / TRACKING UNKNOWN (`unknownKind`).
- Status `bounded` iff truncated; else `observed` (including empty while tracking).
- Reuses existing `SurfaceKind` geometry labels only — not floor / wall / clearance / walkable / safe; no speech or navigation authority.
- SDK plane mapping and Activity wiring remain Codex scope.

### GREEN evidence

- **Not run by this worker.** No executed test claim. Codex owns focused/full GREEN and SDK/Activity integration.

### NEXT

`IMPLEMENTATION_READY` → Codex verifies `SurfaceInventoryPolicyTest` GREEN and owns SDK adapter / Activity wiring. This worker stops here.
