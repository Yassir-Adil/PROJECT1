# CURSOR_MOTION_RESULT — CameraMotionStats (tests first)

**Status:** `TESTS_READY`
**Worker:** Cursor M
**Date:** 2026-10-20
**Baseline:** 68aa9b5 / 0.9.29/42 (queue active verification: reconnected phone / zero-depth diagnosis)
**Scope executed:** M tests ONLY. Helper source not implemented.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/CameraMotionStatsTest.kt` **(new)**
2. `docs/CURSOR_MOTION_RESULT_2026-10-20.md` **(new)**

No other files touched. No `CameraMotionStats.kt` (or any debug source). No Gradle, shell, ADB, Git, Activity, navigation, speech, model, permission, network, or private-output edits.

## Target APIs expected by tests (Codex implements after RED)

```kotlin
internal data class CameraMotionSnapshot(
    val trackedSamples: Int,
    val trackingSegments: Int,
    val maxSegmentDisplacementMeters: Double?,
    val capacityReached: Boolean,
    val stopped: Boolean,
)

internal class CameraMotionStats {
    fun record(
        timestampNs: Long,
        tracking: Boolean,
        x: Double,
        y: Double,
        z: Double,
    ): String

    fun snapshot(): CameraMotionSnapshot

    fun stop()

    companion object {
        const val MAX_TRACKED_SAMPLES: Int = 10_000
    }
}
```

### Contract encoded by the tests

1. All public mutators/readers are synchronized; state is bounded scalars only; no SDK types.
2. Timestamps must be **positive and strictly advancing** among accepted inputs. `<= 0` → `invalid_timestamp`; duplicate/rollback vs last accepted → `out_of_order`. Rejected inputs mutate nothing.
3. `tracking == false` **accepts** a valid timestamp, clears the current segment origin, returns `not_tracking`. Coordinates are ignored (including non-finite). No `trackedSamples` increment. Repeated pause does not open a new segment.
4. `tracking == true` requires finite `x,y,z` and finite origin differences / Euclidean hypot; else `invalid_pose` with **no mutation including timestamp cursor**.
5. First tracking sample after start or after a pause opens a new segment and sets its origin. Displacement is Euclidean distance from that segment origin only (not path length). Aggregate `maxSegmentDisplacementMeters` is the max over all segments. One sample → `0.0`; never tracked → `null`.
6. Labels are SDK pose displacement only — not physical calibrated movement, parallax proof, readiness, or walkability.
7. After `MAX_TRACKED_SAMPLES` (10_000) tracked accepts, further `record` returns `capacity` unchanged; `capacityReached` is true exactly at 10_000.
8. `stop()` is terminal and idempotent; further `record` returns `stopped` unchanged. Snapshots remain readable. Prefer `stopped` over `capacity` when both apply.
9. Successful tracking accept returns `accepted` (matches other diagnostic admit helpers).
10. Avoid recording raw coordinates or movement thresholds in logs/state beyond the scalar snapshot.

## Tests written (17)

| Test | Intent |
| --- | --- |
| `initialSnapshotIsZeroNullAndNotStopped` | zero/null initial; `MAX_TRACKED_SAMPLES == 10000` |
| `firstTrackingSampleStartsSegmentWithZeroDisplacement` | first sample → 0.0 displacement |
| `threeFourTwelveDisplacementIsThirteenMeters` | 3-4-12 → 13 m Euclidean |
| `returnToOriginDoesNotAccumulatePathLength` | return to origin does not sum steps |
| `maxDisplacementIsAggregateAcrossSegmentsNotLastOnly` | max across segments; pause then small move |
| `nonPositiveTimestampIsInvalidWithoutMutation` | `invalid_timestamp` |
| `duplicateAndRollbackTimestampsAreOutOfOrderWithoutMutation` | `out_of_order` |
| `rejectedPoseDoesNotAdvanceTimestampCursor` | reject leaves stamp free for earlier next |
| `nonFiniteCoordinatesAreInvalidPoseWithoutMutation` | NaN / ±Infinity → `invalid_pose` |
| `nonFiniteOriginDifferenceOrHypotIsInvalidPoseWithoutMutation` | overflow differences → `invalid_pose` |
| `pausedAcceptsTimestampClearsSegmentAndIgnoresCoordinates` | pause ignores coords; no cross-segment jump |
| `repeatedPausedDoesNotCreateNewSegmentOrTrackedSample` | repeated pause idle |
| `pauseThenTrackStartsFreshSegmentAtNewOrigin` | new segment origin after pause |
| `capacityReachedExactlyAtMaxTrackedSamplesAndThenImmutable` | capacity + immutable |
| `stopIsTerminalIdempotentAndSnapshotsRemainReadable` | stop / `stopped` |
| `stopAfterCapacityStillRejectedAndIdempotent` | stop after capacity |
| `snapshotIsImmutableAfterLaterSuccessfulRecords` | prior snapshot unchanged |

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `CameraMotionStats` / `CameraMotionSnapshot`.
- Codex must record that compile RED **before** adding `android-app/app/src/debug/java/com/triguide/ai/diagnostics/CameraMotionStats.kt`.

## Genuine unverified limits

- No device frames, ARCore `Camera.getPose()`, or Activity wiring.
- Diagnostic SDK pose delta only; never CLEAR / walkability / navigation authority / calibrated meters claim.
- JVM unit tests do not prove live probe logging, thermal cost, or owner lateral-motion correlation.

## NEXT

`TESTS_READY` → await Codex to record compile RED, then release Cursor M (or Codex) to implement `CameraMotionStats.kt` only. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor M released after Codex RED)

**Status:** `IMPLEMENTATION_READY`
**Codex RED (independent):** combined motion/dense Gradle test compile before source — exit 1, unresolved `CameraMotionStats` / `CameraMotionSnapshot`.

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/CameraMotionStats.kt` **(new)** — `CameraMotionSnapshot` + `CameraMotionStats`
2. `docs/CURSOR_MOTION_RESULT_2026-10-20.md` **(appended)**

**Unchanged:** `CameraMotionStatsTest.kt` (no weakening, no typo fix needed). No shell/Gradle/ADB/Git, Activity, policy, navigation, speech, SDK types, coordinate logs, or other files.

### Implementation notes

- Exact public API: `record(timestampNs, tracking, x, y, z): String`, `snapshot()`, `stop()`, companion `MAX_TRACKED_SAMPLES = 10_000`.
- All of `record` / `snapshot` / `stop` synchronized on a private lock.
- Gate order: `stopped` → `capacity` → `invalid_timestamp` (`<= 0`) → `out_of_order` (not strictly after last accepted) → pause / pose path.
- Pause (`tracking == false`): accepts timestamp, clears active segment origin, returns `not_tracking`; coordinates ignored; no tracked-sample or segment increment.
- Tracking: require finite coords; continuing segment requires finite origin deltas and finite Euclidean hypot; failures return `invalid_pose` with **no** timestamp or counter mutation.
- New segment on first tracking after start/pause: origin set, displacement `0.0`, segments++.
- Displacement is distance from current segment origin only (not path length); snapshot max is aggregate across segments. Never tracked → `null`.
- Capacity: after 10_000 tracked accepts, further `record` returns `capacity` unchanged; `capacityReached` true exactly at 10_000.
- Successful tracking returns `accepted`. Scalar snapshot only; no raw coordinate retention beyond current segment origin for the delta math.
- No SDK, thresholds, physical-readiness, or safety/walkability claims.

### GREEN evidence

- **Not run by this worker.** No executed test claim. Codex owns focused/full GREEN verification of `CameraMotionStatsTest`.

### NEXT

`IMPLEMENTATION_READY` → Codex runs focused/full debug unit tests and integrates if desired. This worker stops here.
