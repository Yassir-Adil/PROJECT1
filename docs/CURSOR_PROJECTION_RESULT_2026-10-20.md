# CURSOR_PROJECTION_RESULT — TextureDepthProjection (tests first)

**Status:** `IMPLEMENTATION_READY`; Codex focused21 projection tests PASS after review
**Worker:** Cursor P
**Date:** 2026-10-20
**Baseline:** 28fd899 / 0.9.30/43 (queue active pair: texture-depth projection and surface-height diagnostics)
**Scope executed:** P tests first, then source after Codex RED (implementation section below).

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/TextureDepthProjectionTest.kt` **(new)**
2. `docs/CURSOR_PROJECTION_RESULT_2026-10-20.md` **(new)**

No other files touched. No `TextureDepthProjection.kt` (or any debug source). No Gradle, shell, ADB, Git, Activity, navigation, speech, model, permission, network, or private-output edits. Preserved unrelated dirty `owner-request-voice-ux.mdc` and untracked `research/10-reuse-licence-check.md`.

## Target APIs expected by tests (Codex implements after RED)

```kotlin
internal data class TextureDepthCalibration(
    val textureWidth: Int,
    val textureHeight: Int,
    val depthWidth: Int,
    val depthHeight: Int,
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
)

internal data class DepthCameraPoint(
    val xMeters: Double,
    val yMeters: Double,
    val zMeters: Double,
)

internal object TextureDepthProjection {
    fun project(
        calibration: TextureDepthCalibration,
        x: Int,
        y: Int,
        depthMm: Int,
    ): DepthCameraPoint?
}
```

Package: `com.triguide.ai.diagnostics`. Source path when released:
`android-app/app/src/debug/java/com/triguide/ai/diagnostics/TextureDepthProjection.kt`.

### Contract encoded by the tests

1. Calibration is **texture** intrinsics of the current physical camera **unrotated GPU texture** only (`fx`/`fy`/`cx`/`cy` in texture pixels). No CPU-image intrinsic fallback.
2. All dimensions must be **positive**. Depth area `depthWidth * depthHeight` (safe `Long`) ≤ **1_048_576**; larger → `null`.
3. Aspect must match **exactly** via safe `Long` cross-products:
   `textureWidth.toLong() * depthHeight == textureHeight.toLong() * depthWidth`.
4. `fx`/`fy` finite and **strictly positive** (reject `0`, `-0.0`, negative, NaN, ±Inf). `cx`/`cy` finite (may lie **off-image**).
5. Depth pixel `(x,y)` inside `[0, depthWidth)` × `[0, depthHeight)`.
6. `depthMm` in **1..65535** (full UINT16; values > 32767 must work).
7. Scale intrinsics texture → depth like Google ARCore 1.57 PointCloudHelper; use **integer** depth `x`/`y` with **no +0.5** pixel-center offset.
8. Metres `m = depthMm / 1000.0`. Then:
   - `z = -m`
   - `x = m * (x - cxScaled) / fxScaled`
   - `y = m * (cyScaled - y) / fyScaled`
   OpenGL camera: +X right, +Y up, −Z forward.
9. Non-finite or underflowed scaled focals, or non-finite result components → `null`.
10. Pure scalar diagnostic only: no world pose, buffers, gates, navigation, CLEAR, or fabricated geometry-accuracy claims.

## Tests written (20)

| Test | Intent |
| --- | --- |
| `matching160x90Against1920x1080ProjectsPrincipalToOriginAtOneMeter` | actual 16:9 sizes; principal → origin at 1 m |
| `matchingAspectCornerAndEdgeSignsFollowOpenGlCameraAxes` | corners; +X right / +Y up / −Z forward signs |
| `unequalFxFyScalesIndependentlyOnDepthAxes` | unequal focals scale independently |
| `depthMmAboveSignedShortUsesFullUint16Range` | 40000 and 65535 mm |
| `offImagePrincipalIsAcceptedWhenFinite` | off-image principal still projects |
| `mismatched640x480Against1920x1080ReturnsNull` | classic 4:3 vs 16:9 reject |
| `aspectCrossProductMismatchRejectsEvenWhenRatiosLookClose` | 161×90 vs 1920×1080 Long cross-product |
| `depthAreaAboveCapReturnsNull` | >1_048_576 reject; exactly at cap accept |
| `nonPositiveOrOverflowingDimensionsReturnNull` | zero/negative dims; large equal-aspect via Long |
| `zeroNegativeAndAboveUint16DepthReturnNull` | 0 / −1 / 65536 / MIN_VALUE |
| `depthMmOneIsMinimumAcceptedPositiveMillimetre` | depthMm=1 → z=−0.001 |
| `pixelOutsideDepthFrameReturnsNull` | x/y out of bounds |
| `edgePixelsInsideFrameAreAccepted` | (0,0) and (W−1,H−1) |
| `nanInfAndNegativeZeroIntrinsicsReturnNull` | NaN/Inf/−0.0/non-positive focals |
| `scaledFocalUnderflowOrNonFiniteYieldsNull` | 1e-320 fx / off-principal; cx=MAX_VALUE → null |
| `integerPixelConventionHasNoHalfPixelOffsetVersusPrincipal` | exact origin; no +0.5 |
| `zMetersIsAlwaysNegatedMillimetresOverThousand` | z and lateral scale with metres |
| `equalAspectFourByThreePairProjectsWithoutCpuIntrinsicFallback` | 4:3 pair; texture-only path |
| `identicalTextureAndDepthSizesLeaveIntrinsicsUnscaled` | 1:1 scale identity |
| `hugePositiveDepthDimensionsRejectViaLongAreaNotIntWrap` | 100k² must not wrap Int area |

## RED / GREEN evidence

- **Not run by this worker** (shell/build/ADB forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `TextureDepthCalibration` / `DepthCameraPoint` / `TextureDepthProjection`.
- Codex must record that compile RED **before** adding `TextureDepthProjection.kt`.

## Genuine unverified limits

- No device frames, ARCore `getTextureIntrinsics()`, pose, or Activity wiring.
- Scalar unprojection only; never CLEAR / walkability / navigation authority / measured metric accuracy.
- JVM unit tests do not prove live Redmi wall/floor error, thermal cost, or depth↔RGB alignment quality.

## NEXT

`TESTS_READY` → await Codex to record compile RED, then explicitly release Cursor P (or Codex) to implement `TextureDepthProjection.kt` only. This worker stops here.

---

## Implementation pass — 2026-10-20 (Cursor P released after Codex RED)

**Status:** `IMPLEMENTATION_READY`
**Codex RED (independent):** projection-height compile before source — exit 1, unresolved `TextureDepthCalibration` / `TextureDepthProjection` (and related types).

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/TextureDepthProjection.kt` **(new)** — `TextureDepthCalibration` + `DepthCameraPoint` + `TextureDepthProjection`
2. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/TextureDepthProjectionTest.kt` **(corrected)** — off-image / overflow / finite-huge cases
3. `docs/CURSOR_PROJECTION_RESULT_2026-10-20.md` **(appended)**

**Unchanged outside P scope:** no shell/Gradle/ADB/Git, Activity, SurfaceHeight*, navigation, speech, SDK types, buffers, world pose, other diagnostics, or private outputs.

### Test correctness fixes (independent re-check)

1. **Earlier `cx = MAX/2` (and `cx = MAX`) with normal fx at depthMm=1000 is finite** after ×1/12 scale — must not expect `null`. Replaced the false null expectation with a **genuinely overflowing** case: `fx = 1e-200` + `cx = 1e200` → non-finite lateral → `null`. Kept separate underflow (`fx = 1e-320`, off-principal) null case.
2. **Added** `finiteHugeLateralResultIsAcceptedWithoutArbitraryBound`: `cx = MAX/2` and `cx = MAX` with default focals must return **finite** points with exact scaled arithmetic — no artificial magnitude cap.
3. **`offImagePrincipalIsAcceptedWhenFinite`**: prior `cx = 0` is the left **edge** after scale (`cxS = 0`), not outside. Now uses **negative** texture principal `cx = -1920`, `cy = -1080` → `cxS = -160`, `cyS = -90` and asserts `x = 1.5`, `y = -135/160`, `z = -1`.

### Implementation notes

- License header: Copyright 2021 Google LLC + Copyright 2026 TriGuide-AI; Apache-2.0; formula adapted from ARCore 1.57 `PointCloudHelper.java` (texture→depth scale, integer pixel, no +0.5).
- Gate order: positive dims → Long depth area ≤ 1_048_576 → Long aspect equality → finite positive fx/fy + finite cx/cy → pixel in frame → depthMm 1..65535 → scale → finite positive fxS/fyS + finite cxS/cyS → metres unprojection → finite result or null.
- Scale: `fxS = fx * depthW/textureW` (same for y/cx/cy). Unproject: `z = -m`, `x = m*(x-cxS)/fxS`, `y = m*(cyS-y)/fyS`.
- No SDK, Image buffer, world pose, CPU-intrinsic fallback, gates, or navigation coupling. No arbitrary `|result|` bound.

### Tests now (21)

Prior 20 plus `finiteHugeLateralResultIsAcceptedWithoutArbitraryBound`. Overflow null case remains inside `scaledFocalUnderflowOrNonFiniteYieldsNull`.

### GREEN evidence

- **Not run by this worker.** No executed test claim. Codex owns focused/full GREEN verification of `TextureDepthProjectionTest`.

### NEXT

`IMPLEMENTATION_READY` → Codex runs focused/full debug unit tests and reviews. This worker stops here.
