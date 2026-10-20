# CURSOR_BACKGROUND_RESULT — DiagnosticCameraBackground (tests first)

**Status:** `IMPLEMENTATION_READY`
**Worker:** Cursor B
**Date:** 2026-10-20
**Baseline:** e4bc1ec / 0.9.27 / 40,499 (queue: SDK surfaces and visible camera)
**Scope executed:** B tests (prior phase) + `DiagnosticCameraBackground.kt` source after Codex RED.

## Changed files (exact)

1. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/DiagnosticCameraBackgroundTest.kt` **(new)**
2. `docs/CURSOR_BACKGROUND_RESULT_2026-10-20.md` **(new)**

No other files touched. No `DiagnosticCameraBackground.kt`, no GLES default, no Activity,
Gradle, shell, ADB, Git, private outputs, assets, dependencies, navigation, or
permission edits.

## Target API expected by tests (Codex implements next)

```kotlin
internal interface CameraBackgroundGl {
    fun createProgram(): Int
    fun draw(
        program: Int,
        textureId: Int,
        positions: FloatBuffer,
        texCoords: FloatBuffer,
    )
    fun deleteProgram(program: Int)
}

internal class DiagnosticCameraBackground(
    private val gl: CameraBackgroundGl = GlesCameraBackgroundGl(),
) {
    fun onSurfaceCreated()
    fun draw(
        frameTimestampNs: Long,
        textureId: Int,
        transformUv: (FloatBuffer, FloatBuffer) -> Unit,
    ): Boolean
    fun releaseOnGlThread()
}
```

`GlesCameraBackgroundGl` is the production GLES adapter (not under JVM test). Tests
inject a fake `CameraBackgroundGl` with zero Android GL calls.

### Contract encoded by the tests

| Area | Expected behaviour |
| --- | --- |
| Init | `onSurfaceCreated()` calls `createProgram()`; positive handle required or throw (`IllegalStateException` for ≤0; propagate create failures). |
| New context | Second `onSurfaceCreated()` creates a new program and **must not** `deleteProgram` the old handle (invalid in the new context). |
| Draw gates | `draw` returns `false` before init, after release, for non-positive `frameTimestampNs`, or non-positive `textureId`; no `gl.draw`. |
| Quad | Positions are the four NDC corners in Google BackgroundRenderer 2D triangle-strip order: `(-1,-1), (-1,+1), (+1,-1), (+1,+1)`. |
| Buffers | Same direct `FloatBuffer` instances reused across frames; native byte order; capacity ≥ 8 floats each; **rewind before transform and before `gl.draw`**. |
| UVs | `transformUv(positions, texCoords)` supplies TEXTURE_NORMALIZED values (Activity/SDK maps `Frame.transformCoordinates2d`; not manual rotation/crop in the helper). Incomplete (&lt; 8 floats) or non-finite UVs → `false`, no GL draw, **never reuse prior UVs**. |
| Errors | Transform and `gl.draw` failures propagate to the caller; no silent swallow. |
| Release | `releaseOnGlThread()` deletes the **current** program exactly once, disables draw; idempotent; safe if never initialized. |
| Re-init | After release, a new `onSurfaceCreated()` may create another program and draw again. Recreate-then-release deletes only the latest handle. |

Out of B scope (Codex): Activity / `GLSurfaceView` wiring, ARCore `Frame.transformCoordinates2d`,
OES shader port from pinned Google shared_camera `BackgroundRenderer`, licence /
SDK pin notices, depth visualization, shared-camera framework, new assets.

## Tests written (21)

| Test | Intent |
| --- | --- |
| `drawReturnsFalseBeforeOnSurfaceCreated` | missing program / pre-init |
| `onSurfaceCreatedCreatesPositiveProgram` | creation + first successful draw |
| `onSurfaceCreatedRecreateForgetsOldHandleWithoutDeletingInNewContext` | recreate without delete |
| `onSurfaceCreatedThrowsWhenCreateProgramReturnsNonPositive` | 0 / negative program |
| `onSurfaceCreatedPropagatesCreateProgramFailure` | create error propagation |
| `drawRejectsNonPositiveFrameTimestampWithoutGlDraw` | zero / negative stamp |
| `drawRejectsNonPositiveTextureIdWithoutGlDraw` | zero / negative texture |
| `drawPassesFourNdcCornersInGoogleTriangleStripOrder` | quad corners |
| `drawReusesSameDirectBuffersAndRewindsBeforeTransformAndDraw` | buffer identity + rewind |
| `changedUvTransformsReachGlDrawEachFrame` | changed UV transformations |
| `incompleteUvWriteIsNotDrawnAndDoesNotReusePriorUvs` | incomplete UVs |
| `nonfiniteUvsAreNotDrawnAndAreNeverReused` | NaN / ±Inf UVs |
| `transformUvErrorPropagatesWithoutGlDraw` | transform errors |
| `glDrawErrorPropagatesToCaller` | GL draw errors |
| `releaseDeletesCurrentProgramExactlyOnceAndDisablesDraw` | release once |
| `releaseIsIdempotent` | double/triple release |
| `releaseWithoutInitIsSafeAndIdempotent` | release before create |
| `reInitAfterReleaseCreatesNewProgramAndDrawsAgain` | re-init after release |
| `recreateThenReleaseDeletesOnlyLatestProgramHandle` | forget-then-release |
| `directBuffersHaveNativeByteOrderAndCapacityForFourCorners` | direct / order / capacity |
| `successfulDrawDoesNotAllocateFreshBuffersEachFrame` | no per-frame buffer alloc |

## RED / GREEN evidence

- **Not run by this worker** (shell / build / ADB / Git forbidden in this phase).
- Expected first Codex compile: **RED** — unresolved `CameraBackgroundGl`,
  `DiagnosticCameraBackground`, and default `GlesCameraBackgroundGl`.
- Codex must record that compile RED **before** adding
  `android-app/app/src/debug/java/com/triguide/ai/diagnostics/DiagnosticCameraBackground.kt`
  (and any GLES default in the same exclusive B source scope).

## Genuine unverified limits

- JVM fake GL does not prove real OES external-texture sampling, shader compile/link,
  or EGL context loss behaviour on the Redmi.
- UV transform correctness depends on later Codex Activity mapping of
  `Frame.transformCoordinates2d` (OPENGL_NORMALIZED_DEVICE_COORDINATES →
  TEXTURE_NORMALIZED); this helper must not invent rotation/crop math.
- Visible camera preview is diagnostic only; no CLEAR / walkability / navigation authority.
- No INTERNET, recording, cloud, or main guidance changes in this slice.

## NEXT

**TESTS_READY** — stop. Await Codex compile-RED record, then an **explicit**
source-implementation release for Cursor B on
`DiagnosticCameraBackground.kt` (+ GLES default) only. Do not implement source
until that release.

---

## Implementation — 2026-10-20 (post RED release)

Codex recorded offline compile RED (`:app:testDebugUnitTest --tests
'*DiagnosticCameraBackgroundTest'`, exit 1, unresolved `CameraBackgroundGl` /
`DiagnosticCameraBackground`, `background-red.log`). Cursor B released for
source only. The 21 tests were **not** modified.

### Files written this phase

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/DiagnosticCameraBackground.kt` **(new)**
2. `docs/CURSOR_BACKGROUND_RESULT_2026-10-20.md` **(append only)**

No other files touched. No shell / build / ADB / Git / NOTICE / Activity /
private outputs / assets / dependencies.

### API landed (matches queue + tests)

- `CameraBackgroundGl`: `createProgram()`, `draw(...)`, `deleteProgram(...)`
- `DiagnosticCameraBackground(gl = GlesCameraBackgroundGl())`:
  `onSurfaceCreated()`, `draw(frameTimestampNs, textureId, transformUv)`,
  `releaseOnGlThread()`
- `GlesCameraBackgroundGl`: OES screenquad-style program; call only with a
  current GL context. CPU tests inject a fake (zero Android GL calls).

### Lifecycle / safety choices

| Rule | Implementation |
| --- | --- |
| New context | `onSurfaceCreated` sets `program = 0` **before** `createProgram`; never deletes the forgotten handle |
| Create failure | Non-positive → `IllegalStateException`; create exceptions propagate; state stays 0 |
| Release | Capture handle, set `program = 0` + invalidate UVs, **then** `deleteProgram` (idempotent; safe if never init) |
| UV freshness | Fill texCoords with NaNs before every `transformUv`; reject incomplete (`remaining`/`position`) or non-finite; no GL draw; re-invalidate on reject |
| Buffers | Stable direct native-order `FloatBuffer`s; NDC quad rewritten + rewind; rewind before GL draw; no per-frame `allocateDirect` |
| GLES draw | Depth test/write saved, disabled for the quad, restored; attrib/uniform locations cached at create; shaders deleted after link; program/shaders deleted on all create failure paths; `glGetError` → throw to Activity |
| Texture | OES id supplied/owned by Activity; helper never gen/delete camera texture |
| Attribution | File header: Copyright 2017 Google LLC, Copyright 2026 TriGuide, Apache-2.0, pointer to v1.57.0 `shared_camera` `BackgroundRenderer.java`. No framework/depth-viz/assets imported. NOTICE update is Codex. |

### Explicit non-claims

- No build, unit-test GREEN, or device preview verification by this worker.
- No Activity / `preserveEGLContextOnPause=false` / pause-queue wiring (Codex).
- No navigation, CLEAR, or main-guidance change.

### Hand-off

**IMPLEMENTATION_READY** — Codex owns NOTICE pin, Activity lifecycle mapping
(`transformCoordinates2d`, OES texture, draw-before-geometry), full gate, and
any GREEN record.
