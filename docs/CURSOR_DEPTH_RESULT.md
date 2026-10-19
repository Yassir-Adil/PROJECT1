# CURSOR_DEPTH_RESULT — D2 Raw Depth decoder

**Status:** `IMPLEMENTATION_READY`
**Worker:** Cursor RAW DEPTH
**Date:** 2026-10-19
**Scope executed:** D2 only (decoder implementation). Tests not re-run here.

## Changed files (exact)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthFrameStats.kt` **(new)** — `RawDepthSummary` + `RawDepthFrameStats.summarize`
2. `docs/CURSOR_DEPTH_RESULT.md` **(updated)**

Unchanged from D1: `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/RawDepthFrameStatsTest.kt` (no weakening).

No Android API imports, navigation, manifests, policy, Gradle, ADB, or Git actions.

## Implementation notes

- Absolute UINT16 LE depth loads via `get(index)` / `get(index+1)` (ignores caller `ByteOrder`).
- Absolute UINT8 confidence; threshold inclusive; default 128.
- Zero depth counted as missing (not free space); percentiles only over nonzero ∩ `confidence >= threshold`.
- Nearest-rank `ceil(q*n)-1` after sorting the confident subset only.
- Validates positive dimensions, `width*height` Long product capped at 1_048_576, threshold 0..255, non-overlapping strides, remaining bytes from current position with last-row-without-final-pad span; `IllegalArgumentException` on malformation.
- Does not advance or rewrite buffer position, limit, order, or bytes.

## GREEN evidence

- **Not run by this worker.** Prior Codex RED: `outputs/reports/cursor-20261019-next/depth-red.log` (`compileDebugUnitTestKotlin` unresolved symbols). Codex owns post-D2 test execution.

## Genuine unverified limits

- No real ARCore `Image` / plane stride / device size exercised; ByteBuffer fixtures only.
- No phone, ADB, or thermal/FPS measurement.
- Does not close Images, gate timestamps, or feed navigation (D3+).
- Diagnostic counts/percentiles are not calibration, clearance, or walkability.
- `confidentPixels` follows D1 tests: nonzero depth and `confidence >= threshold`.

## NEXT

Hand off to Codex for `:app:testDebugUnitTest` (or focused `RawDepthFrameStatsTest`) GREEN verification. D3 remains queued pending contract review.
