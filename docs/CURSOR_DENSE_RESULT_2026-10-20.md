# CURSOR_DENSE_RESULT — depth-only valid counts (tests first)

**Status:** `TESTS_READY`
**Worker:** Cursor D
**Date:** 2026-10-20
**Baseline:** 68aa9b5 / 0.9.29 (WORK_QUEUE M/D scope)
**Scope executed:** D phase-1 tests ONLY. `RawDepthFrameStats.kt` source not edited.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/RawDepthFrameStatsTest.kt` **(appended depth-only tests only)**
2. `docs/CURSOR_DENSE_RESULT_2026-10-20.md` **(new)**

**Untouched:** `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthFrameStats.kt` and every pre-existing raw+confidence test method. No Gradle, shell, ADB, Git, Activity, policy, navigation, network, permission, private-output, or other-file edits.

## Target API expected by tests (Codex implements after real RED)

```kotlin
internal data class DepthOnlySummary(
    val sampledPixels: Int,
    val validDepthPixels: Int,
)

// on existing internal object RawDepthFrameStats:
fun summarizeDepthOnly(
    width: Int,
    height: Int,
    depth: ByteBuffer,
    depthRowStride: Int,
    depthPixelStride: Int,
): DepthOnlySummary
```

### Contract encoded by the tests

- Dense depth-only diagnostic: **no** confidence plane, **no** median/p90, **no** freshness, **no** admission, **no** floor/clear/turn fields.
- Reuse the same private validation and UINT16 LE decode primitives as `summarize` when source is released (`validatePlane` / `requiredBytes` / `readUint16Le`); do not invent a parallel decoder.
- Positive `width`/`height`; `width * height` (Long) ≤ `1_048_576`.
- Depth element size 2; `pixelStride >= 2`; no row overlap; last-row-without-trailing-pad layout accepted; truncated buffers rejected via `IllegalArgumentException`.
- Bounded Long arithmetic for spans; remaining bytes measured from current buffer position; absolute indices must not overflow Int.
- Decode unsigned 16-bit **little-endian** millimetres independent of caller `ByteOrder`; **no** 13-bit mask; `0xFFFF` (65535) is a valid nonzero depth.
- Zero depth is missing/invalid; every nonzero UINT16 increments `validDepthPixels`. Counts are confidence-independent.
- Do not mutate caller buffer position, limit, order, or bytes (success or failure). Read-only and direct buffers supported.

## Tests written (17 new; all prior summarize tests preserved)

| Test | Intent |
| --- | --- |
| `depthOnlyAllZeroYieldsZeroValidPixels` | dense all-zero → valid=0 |
| `depthOnlyNonzeroCountsAreConfidenceIndependent` | mixed zero/nonzero; no confidence plane |
| `depthOnlyCountsFullUint16Including65535WithoutThirteenBitMask` | 0xFFFF / 0x2000 / 0x1FFF valid; zero invalid |
| `depthOnlyLittleEndianIndependentOfCallerByteOrder` | BIG_ENDIAN caller order; LE payload |
| `depthOnlyPaddedRowsAndWidePixelStridesSkipPadding` | poison padding ignored |
| `depthOnlyNonzeroPositionAndLimitPreservedOnSuccess` | slice position/limit unchanged |
| `depthOnlyNonzeroPositionAndLimitPreservedOnFailure` | reject keeps position/limit/order |
| `depthOnlyInvalidDimensionsAreRejected` | 0 / negative width or height |
| `depthOnlyPixelCountAboveCapIsRejectedWithoutOverflow` | 1024×1025 > 1_048_576 |
| `depthOnlyExtremeDimensionsThatOverflowIntProductAreRejected` | Int.MAX_VALUE×2 |
| `depthOnlyOverlappingStridesAreRejected` | pixelStride&lt;2; row overlap |
| `depthOnlyTruncatedLastRowIsRejected` | short last-row bytes |
| `depthOnlyLastRowWithoutTrailingPadIsAccepted` | exact last-row layout |
| `depthOnlyReadOnlyAndDirectBuffersSupportedWithoutMutation` | direct + read-only |
| `depthOnlyDoesNotMutateCallerBufferBytesOrOrder` | bytes/order/pos/limit immutable |
| `depthOnlyRemainingBytesAreMeasuredFromCurrentPosition` | remaining from position |
| `depthOnlyNegativeRowStrideIsRejected` | rowStride &lt; 0 |

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB/Git/network forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `DepthOnlySummary` / `summarizeDepthOnly`.
- Codex must record that compile RED **before** editing `RawDepthFrameStats.kt`.

## Genuine unverified limits

- No device frames, ARCore Images, AUTOMATIC mode, or gate/admission wiring.
- Depth-only counts are diagnostic coverage of nonzero millimetres, never CLEAR / walkability / navigation authority.
- JVM unit tests do not prove live dense probe logging or thermal cost.
- Existing pair `summarize` behaviour is unchanged by design; not re-executed here.

## NEXT

`TESTS_READY` → await Codex to record compile RED, then explicit release for Cursor D (or Codex) to implement `DepthOnlySummary` + `summarizeDepthOnly` in the existing `RawDepthFrameStats.kt` only, reusing private validation/UINT16 LE helpers. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor D phase-2 released)

**Status:** `SOURCE_READY`
**Codex RED (independent, combined motion/dense):** offline test compile exit 1 — unresolved `DepthOnlySummary` / `summarizeDepthOnly` (and motion types in peer scope). Recorded before this source edit.

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthFrameStats.kt` — added `DepthOnlySummary` + `summarizeDepthOnly`
2. `docs/CURSOR_DENSE_RESULT_2026-10-20.md` **(appended)**

**Unchanged:** `RawDepthFrameStatsTest.kt` (no weakening). Pre-existing `summarize` / `RawDepthSummary` behaviour unmodified. No shell/Gradle/ADB/Git/network, Activity, policy, navigation, speech, private outputs, or other files.

### Implementation notes

- Exact API: `DepthOnlySummary(sampledPixels, validDepthPixels)`; `summarizeDepthOnly(width, height, depth, depthRowStride, depthPixelStride)`.
- Reuses private `validatePlane` / `requiredBytes` / `readUint16Le` only (no parallel decoder, no synthetic confidence buffer).
- Same dimension/cap/stride/truncation/overflow rules as the depth half of `summarize`.
- Valid = nonzero full UINT16 LE mm (incl. 65535); zero invalid; no 13-bit mask; no median/p90/admission/floor/clear/turn.
- Absolute indexed reads; caller buffer position/limit/order/bytes untouched.
- Existing Kotlin/JDK only; no new dependencies; no Images/buffers retained.

### GREEN evidence

- **Not run by this worker.** No executed test claim. Codex owns focused/full debug unit-test execution.

### NEXT

`SOURCE_READY` → Codex runs dense (and combined) debug unit tests. This worker stops here.
