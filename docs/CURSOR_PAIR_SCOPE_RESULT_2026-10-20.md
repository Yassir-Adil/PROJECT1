# Cursor P — pair-scope / sample-gate tests — 2026-10-20

Role: Cursor P (tests-first only). Baseline context: WORK_QUEUE active pair
(same-Frame provenance). No helpers implemented. No Gradle/ADB/Git/shell.
Exclusive writes this phase:

- `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/RawDepthSampleGateTest.kt`
- `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/RawDepthPairScopeTest.kt`
- `docs/CURSOR_PAIR_SCOPE_RESULT_2026-10-20.md`

Implementation files (`RawDepthPairScope.kt`, `RawDepthSampleGate.kt`) untouched.
Tests call the target APIs below and must fail compilation until Codex records RED
and releases implementation.

## Target APIs (contract under test)

### `RawDepthPairScope.withFrame`

```kotlin
internal object RawDepthPairScope {
  fun <F, D : AutoCloseable, C : AutoCloseable, R> withFrame(
    frame: F,
    timestampOf: (F) -> Long,
    acquireDepth: (F) -> D,
    acquireConfidence: (F) -> C,
    block: (RawDepthPairProof, D, C) -> R,
  ): R
}
```

Expected behaviour:

1. Read frame timestamp; require `> 0` (`IllegalArgumentException` if not) before any acquire.
2. Acquire depth, then confidence, both from the captured frame object.
3. Re-read timestamp; require identical positive value (`IllegalStateException` if changed).
4. Invoke `block` with a private-constructor **active** [RawDepthPairProof], depth, confidence.
5. On all exits: invalidate proof; close **confidence then depth**, each exactly once.
6. Propagate block return value / acquire / process / close failures without skipping cleanup.

### `RawDepthPairProof`

```kotlin
internal class RawDepthPairProof private constructor(...) {
  val frameTimestampNs: Long
  val isActive: Boolean
}
```

Active only inside `withFrame`’s `block`. Inactive after scope exit (success or failure).

### `RawDepthSampleGate`

```kotlin
internal class RawDepthSampleGate(
  private val maxProcessingAgeNs: Long = 250_000_000L, // 250ms; no maxSensorLagNs
) {
  fun accept(
    pair: RawDepthPairProof,
    depthTimestampNs: Long,
    confidenceTimestampNs: Long,
    receivedElapsedNs: Long,
    completedElapsedNs: Long,
    tracking: Boolean,
  ): String

  fun stop()
}
```

Admission reasons expected by tests:

| Result | Meaning |
| --- | --- |
| `accepted` | Active proof + tracking + valid stamps + new frame/depth + fresh elapsed age |
| `inactive_proof` | Proof used after scope exit |
| `stopped` | After `stop()` |
| `not_tracking` | `tracking == false` |
| `invalid_sensor_timestamp` | depth or confidence `<= 0` |
| `invalid_elapsed_clock` | negative received, completed &lt; received, or completed &lt; last accepted completed |
| `processing_stale` | completed − received &gt; maxProcessingAgeNs (inclusive bound at equality) |
| `camera_not_new` | `pair.frameTimestampNs` not strictly newer than last accepted frame stamp |
| `depth_not_new` | depth stamp not strictly newer than last accepted depth stamp (covers replay/reproject) |

Must **not** return / require: `confidence_mismatch`, `sensor_stale`, `depth_in_future`.
Must **not** subtract frame↔depth or depth↔confidence across clocks. Confidence stamp:
positive only (no equality to depth, no separate monotonic confidence history).
Rejections must not poison a later valid admission (except terminal `stop`).

## Tests written

### `RawDepthPairScopeTest` (12)

1. `blockReceivesActiveProofSameFrameResourcesAndReturnValue`
2. `proofIsInvalidatedAfterBlockEvenOnSuccess`
3. `nonPositiveFrameTimestampIsRejectedBeforeAcquire`
4. `changedFrameTimestampDuringAcquisitionRejectsWithoutBlockAndClosesConfidenceThenDepth`
5. `changedFrameTimestampDuringConfidenceAcquireRejectsAndClosesConfidenceThenDepth`
6. `depthAcquireFailureDoesNotAcquireConfidenceOrInvokeBlock`
7. `confidenceAcquireFailureClosesDepthOnceAndSkipsBlock`
8. `blockFailureStillClosesConfidenceThenDepthExactlyOnce`
9. `confidenceCloseFailureStillClosesDepthExactlyOnce`
10. `depthCloseFailurePropagatesAfterConfidenceClosedExactlyOnce`
11. `successfulPathNeverDoubleClosesResources`
12. `frameObjectIdentityIsStableAcrossTimestampAndAcquireCallbacks`

### `RawDepthSampleGateTest` (23)

Retained / adapted regressions: tracking, processing-age inclusive 250ms, elapsed
rollback, no sensor↔elapsed subtraction, advancing pairs, stop terminal, negative
`maxProcessingAgeNs`, poison-free rejections.

Replaced undocumented equality/lag expectations with provenance tests:

1. `freshSameFrameTrackingPairIsAccepted`
2. `unequalButValidDepthAndConfidenceStampsAreAccepted`
3. `largeFrameVersusDepthNumericGapDoesNotImposeCrossClockLag`
4. `inactiveProofAfterScopeExitIsRejected`
5. `inactiveProofRejectionDoesNotPoisonLaterValidAdmission`
6. `repeatedOrReprojectedDepthOnNewFrameIsRejected`
7. `sameFrameTimestampAndBackwardFrameAreRejected`
8. `advancingDistinctFrameAndDepthPairsAreAccepted`
9. `backwardDepthRelativeToLastAcceptedDepthIsRejected`
10. `invalidSourceStampRejectionDoesNotPoisonTheNextCorrectPair`
11. `zeroAndNegativeSensorTimesAreRejected`
12. `missingTrackingRejectsEvenPlausibleDepth`
13. `completionAgeHasAnInclusiveBoundInElapsedClockOnly`
14. `clockRollbackAndNegativeElapsedClockAreRejected`
15. `noSubtractionOfSensorEpochAndElapsedEpochIsRequired`
16. `elapsedClockCannotGoBackwardsBetweenAcceptedPairs`
17. `trackingLossRejectionDoesNotPoisonLaterValidAdmission`
18. `processingStaleRejectionDoesNotPoisonLaterValidAdmission`
19. `stopIsTerminalAndIdempotent`
20. `stopRejectionDoesNotRequirePoisoningSemanticsBeyondTerminalStop`
21. `negativeAgeConfigurationIsRejected`
22. `defaultProcessingAgeRemainsTwoHundredFiftyMilliseconds`
23. `acceptNeverReturnsRemovedCrossClockReasons`

Removed obsolete expectations: `maxSensorLagNs` constructor / `negativeLagConfigurationIsRejected`,
`confidence_mismatch`, inclusive sensor-lag bound, `depth_in_future`.

## Design notes / issues for Codex

1. **Accept inside scope.** Because the proof is invalidated after `block`, production
   code (and these tests) must call `gate.accept(pair, …)` inside `withFrame`’s
   block. Activity wiring is Codex-owned later; not done here.
2. **Bracket semantics.** Frame timestamp is checked positive before acquires and
   unchanged after both acquires. Mid-acquire mutation still fails the after-check;
   both resources are closed; block does not run. No mid-acquire short-circuit is required.
3. **Close order.** Confidence then depth, exact-once, including when `block` or
   `close` throws. Close-failure suppressed-exception details are not over-specified.
4. **`isActive` + `frameTimestampNs`.** Required by tests; implement as read-only API
   on the private-constructor proof.
5. **Confidence monotonicity.** Not gated; only `> 0`. Depth and frame each have
   their own last-accepted stamp.
6. **No navigation authority.** Diagnostic admission strings only.

## Status

**TESTS_READY** — awaiting Codex compile RED on the missing
`RawDepthPairScope` / `RawDepthPairProof` / revised `RawDepthSampleGate.accept`
APIs, then an explicit resume of Cursor P for implementation in the two source
files only.

---

## Implementation — 2026-10-20 (post RED release)

Codex recorded compile RED (`pair-red.log`, exit 1, missing PairScope/Proof/new
accept API). Cursor P released to implement source only. Tests left unchanged.

### Files written

- `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthPairScope.kt`
  (includes nested private-ctor `Proof`; `typealias RawDepthPairProof`)
- `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthSampleGate.kt`
  (revised; no `maxSensorLagNs`; no legacy `cameraTimestampNs` overload)

### B1–B5 (source-relevant)

| ID | How satisfied |
| --- | --- |
| B1 | Removed equality, `depth_in_future`, `sensor_stale`, and `maxSensorLagNs`. Frame stamp only vs prior frame; depth only vs prior depth; confidence positive only. |
| B2 | Proof private nested ctor (no create/forge factory). Minted only after both acquires + unchanged positive stamp. `invalidate()` in `finally` before `.use` closes resources. Gate returns `inactive_proof` without advancing last stamps. |
| B3 | Nested `depth.use { confidence.use { … } }`: conf then depth, exact-once; primary exception preserved with suppressed close failures. |
| B4 | Processing age = elapsed receipt→complete only (default 250 ms). No absolute sensor-age claim in API. Debug admission strings only. |
| B5 | N/A to P source (CoverageStats is Cursor C). |

### Explicit non-claims (retained)

Accepted pairs do not claim sensor age, calibrated mm, coverage sufficiency,
navigation/CLEAR/turn, or compatible cross-source clocks.

### Hand-off

**IMPLEMENTATION_READY** — Codex owns Activity wiring (old `accept(cameraTimestampNs=…)`
will not compile until adapted), full gate GREEN, and integration. No shell/build
run by Cursor P this phase.
