# CURSOR_COVERAGE_RESULT — RawDepthCoverageStats (tests first)

**Status:** `TESTS_READY`
**Worker:** Cursor C
**Date:** 2026-10-20
**Baseline:** f72e54e / 0.9.26 (queue active pair)
**Scope executed:** C tests ONLY. Helper source not implemented.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/RawDepthCoverageStatsTest.kt` **(new)**
2. `docs/CURSOR_COVERAGE_RESULT_2026-10-20.md` **(new)**

No other files touched. Existing `RawDepthFrameStats.kt` / `RawDepthSummary` unread for mutation. No Gradle, shell, ADB, Git, Activity, policy, navigation, network, or permission edits.

## Target API expected by tests (Codex implements next)

```kotlin
internal class RawDepthCoverageStats(maxPairs: Int = 10_000) {
    fun record(summary: RawDepthSummary): Boolean
    fun snapshot(): RawDepthCoverageSnapshot
    fun stop()
}

internal data class RawDepthCoverageSnapshot(
    val decodedPairs: Long,
    val sampledPixels: Long,
    val validDepthPixels: Long,
    val confidentPixels: Long,
    val pairsWithConfidentPixels: Long,
    val validFraction: Double?,       // null before any successful record
    val confidentFraction: Double?,   // null before any successful record
    val capacityReached: Boolean,
)
```

### Contract encoded by the tests

- Count **all successfully recorded decoded pairs**, not gate-accepted samples and not unique depth estimates. `medianDepthMm` / `p90DepthMm` are ignored for aggregates.
- Validate **before mutation**: `sampledPixels` in `1..1_048_576` and `0 <= confidentPixels <= validDepthPixels <= sampledPixels`. Invalid → `record` returns `false`, snapshot unchanged.
- Fractions are **weighted** over cumulative pixel totals: `valid/sampled` and `confident/sampled`. Never the mean of per-pair fractions.
- **Missing vs observed zero:** fractions are `null` before any successful `record`; after an all-zero valid/confident summary they are `0.0`.
- `pairsWithConfidentPixels` increments only when that summary’s `confidentPixels > 0`.
- Bounded `maxPairs` (default 10_000): at capacity `capacityReached == true` and further `record` returns `false` without mutation.
- `stop()` is terminal and idempotent; further `record` returns `false` without mutation. Snapshots remain readable.
- Snapshot values are immutable scalars; later records must not alter a previously returned snapshot.
- No Images/buffers held (CPU fixtures only via existing `RawDepthSummary`).

## Tests written (13)

| Test | Intent |
| --- | --- |
| `beforeAnyRecordFractionsAreMissingNotZero` | null fractions / zero counters / not at capacity |
| `observedAllZeroDepthIsZeroFractionNotMissing` | observed zero ≠ missing |
| `weightedFractionsUseAllDecodedPixelTotalsNotPerPairAverage` | 10/100 not mean of pair fractions |
| `sparseSummariesAccumulateAcrossDecodedPairs` | sparse/empty-valid pairs accumulate |
| `countsEveryDecodedPairNotAcceptedSamplesOrUniqueEstimates` | duplicate summaries count twice |
| `malformedSampledCountIsRejectedWithoutMutation` | 0 / negative / >1_048_576 sampled |
| `malformedPixelOrderingIsRejectedWithoutMutation` | confident/valid/sampled ordering |
| `boundarySampledOneAndMaxAreAccepted` | sampled 1 and 1_048_576 |
| `capacityBoundRejectsFurtherRecordsAndSetsCapacityReached` | maxPairs=2 |
| `defaultMaxPairsIsTenThousand` | default capacity 10_000 |
| `snapshotIsImmutableAfterLaterSuccessfulRecords` | prior snapshot unchanged |
| `stopIsTerminalAndIdempotent` | stop rejects; double-stop safe |
| `stopAfterCapacityRemainsRejectedAndIdempotent` | capacity then stop |

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `RawDepthCoverageStats` / `RawDepthCoverageSnapshot`.
- Codex must record that compile RED **before** adding `RawDepthCoverageStats.kt`.

## Genuine unverified limits

- No device frames, ARCore Images, or gate admission wiring.
- Diagnostic coverage only; never CLEAR / walkability / navigation authority.
- JVM unit tests do not prove live probe logging or thermal cost.

## NEXT

`TESTS_READY` → await Codex to record compile RED, then release Cursor C (or Codex) to implement `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthCoverageStats.kt` only. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor C released)

**Status:** `IMPLEMENTATION_READY`
**Codex RED (independent):** offline `:app:testDebugUnitTest --tests '*RawDepthCoverageStatsTest'` ~20s — unresolved `RawDepthCoverageStats`, helper absent (other-scope reds may also appear; not owned here).

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthCoverageStats.kt` **(new)** — `RawDepthCoverageSnapshot` + `RawDepthCoverageStats`
2. `docs/CURSOR_COVERAGE_RESULT_2026-10-20.md` **(appended)**

**Unchanged:** `RawDepthCoverageStatsTest.kt` (no weakening). No shell/Gradle/ADB/Git, Activity, policy, navigation, speech, buffers/Images, or other files.

### Implementation notes

- Exact queue API: `RawDepthCoverageStats(maxPairs: Int = 10_000)`, `record(summary): Boolean`, `snapshot(): RawDepthCoverageSnapshot`, `stop()`.
- `require(maxPairs > 0)` at construction.
- All public mutators/readers `synchronized` on a private lock.
- Validate `sampled ∈ 1..1_048_576` and `0 ≤ confident ≤ valid ≤ sampled` **before** any counter mutation; invalid / capacity / stopped → `false`.
- Scalar immutable snapshot; fractions `null` until first successful record, then weighted `valid/sampled` and `confident/sampled` (including observed `0.0`).
- `capacityReached` when `decodedPairs >= maxPairs`; further records rejected without mutation.
- `stop()` terminal and idempotent; snapshots remain readable.
- Long counters: each accepted add is at most `MAX_SAMPLED_PIXELS` (Int) and pair count ≤ `maxPairs` (Int), so totals ≤ `maxPairs * MAX_SAMPLED_PIXELS` and cannot overflow Long under these rules.
- Existing Kotlin/JDK only; no new dependencies; no Images/buffers/speech/navigation held.

### GREEN evidence

- **Not run by this worker.** No executed test claim. Codex owns integration and GREEN verification of `RawDepthCoverageStatsTest`.

### NEXT

`IMPLEMENTATION_READY` → Codex runs focused/full debug unit tests and integrates if desired. This worker stops here.
