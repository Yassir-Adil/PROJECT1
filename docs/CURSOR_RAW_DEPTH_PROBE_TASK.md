# Cursor D3 — isolated real Raw Depth probe

Write scope ONLY new
`android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthDiagnosticActivity.kt`
and `docs/CURSOR_RAW_DEPTH_PROBE_RESULT.md`. Read WORK_QUEUE, AGENTS, latest bridge,
existing DepthCapabilityDiagnosticActivity and the D2 decoder contract. Codex
owns manifest, APK policy, launch wiring, pure admission gate and all Gradle/ADB.
Other Cursor workers are writing storage and RawDepthFrameStats files; do not
edit them or wait for them. No Git, dependency, model, navigation or voice edits.

## Scope

Debug DUMP-protected activity, invoked deliberately by local QA. Real ARCore
RAW_DEPTH_ONLY with existing SDK 1.57.0. No camera permission/install prompts,
network/cloud features/keys, anchors, recording, RGB acquisition, files or
CameraX. Missing permission, unsupported/not-installed availability or invalid
duration (5..60 sec, default20; extra `triguide.diagnostic.duration_seconds`)
must fail closed and finish with a clear log. Restored activity must finish;
background/onPause is terminal, no resume or camera restart.

Use official raw_depth_java sample lifecycle/GLSurfaceView pattern, not the huge
sample build/dependency tree. Source:
https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/RawDepthActivity.java
and https://developers.google.com/ar/reference/java/com/google/ar/core/Frame .
Retain Apache-2.0 attribution for adapted sample patterns in source header.
Do NOT copy its `containsNewDepthData = oldTimestamp == newTimestamp` condition:
the API reference requires different/new timestamps. Sixteen-bit API is full
unsigned LE 0..65535 mm; old 13-bit mask is wrong. Confidence 0..255; 128 is
diagnostic selection only, not proof of accuracy/height/clear path.

## Renderer/session/lifecycle

- Use platform GLSurfaceView EGL2; create a valid GL_TEXTURE_EXTERNAL_OES camera
  texture in renderer's context and pass it to session.setCameraTextureNames.
  Do not acquire RGB. Dark cleared surface is acceptable; this is a metrics
  instrument, not a new live navigation view. Update display geometry from
  surface dimensions and actual display rotation. Use
  Config.UpdateMode.LATEST_CAMERA_IMAGE, RAW_DEPTH_ONLY, autofocus if supported
  (otherwise retain default), no plane finder requirement/cloud/geospatial.
- Resume session before renderer as official sample; onPause stop flag FIRST,
  pause GLSurfaceView BEFORE session.pause, then close session onDestroy. Never
  call session.update on paused/closed session; no GL/main monitor deadlock.
  Explicit stop marks stopping immediately and finishes via UI; the timeout is
  wall elapsed since session start and never extended by frame arrival. Keep
  screen on only during this bounded foreground probe, release on stop.
- All session frame/image use serialized on GL thread. Both acquired Images are
  closed in nested use/finally on every path, even confidence acquire fails.
  Normal NotYetAvailable and tracking loss are counters, not crashes/log floods.
  Unexpected camera/config error stops probe with reason; catch Exception, not
  VM fatal errors. No retained image or unbounded metric/frame list.
- Count distinct camera timestamps (latest mode can repeat frames). Depth stats
  sampling cap10Hz at most, using elapsed clock for admission cadence; label the
  cap so accepted sample rate is not presented as full camera/depth FPS.
- Use RawDepthFrameStats.summarize with actual Image plane buffer position,
  rowStride/pixelStride. Validate equal image dimensions and expected formats
  (D_16 depth / Y8 confidence) where SDK exposes them. Do not rescale or invent
  transforms. Capture frame/depth/conf timestamps; no SystemClock vs AR epoch
  subtraction. Call Codex-owned `RawDepthSampleGate.accept` after stats with
  actual tracking and receipt/completion elapsed ns. Only accepted samples
  update displayed latest distances/coverage. Reprojection/repeat/out-of-order,
  mismatched confidence, future/stale and stopped results never publish.

Gate API (Codex will create; do not implement it):
`internal class RawDepthSampleGate(maxProcessingAgeNs: Long = 250_000_000L,
maxSensorLagNs: Long = 500_000_000L)`;
`fun accept(cameraTimestampNs: Long, depthTimestampNs: Long,
confidenceTimestampNs: Long, receivedElapsedNs: Long, completedElapsedNs: Long,
tracking: Boolean): String` returns `accepted` or reason slug;
`fun stop()` terminal. Gate compares depth/conf against each other and camera
epoch, receipt/completion only to each other. Stop may come from main thread.

## UI / logs / metrics

Compact local Arabic/English status, >=18sp and Stop >=56dp. Explain stationary
testing, small hand motion may be necessary, no walking instructions, missing
depth means unknown. Show ARCore Google terms/privacy URLs/disclosure (plain
text sufficient): https://developers.google.com/ar/develop/terms and
https://policies.google.com/privacy . No agreement bypass or login flow.

Log tag `TriGuideRawDepth` with `RAW_DEPTH_START`, throttled `RAW_DEPTH_SAMPLE`
at most1/s, final `RAW_DEPTH_RESULT` exactly once, plus `RAW_DEPTH_CLOSED` after
session close. Include unique frames, tracking/nontracking, accepted/new depth,
notYetAvailable/rejections, dimensions/strides/first timestamps, valid/confident
pixel fractions, median/p90 mm labeled NOT calibrated, receipt-to-summary latency
p50/p90 over bounded last600 samples, duration, reason, no_image_storage=true,
navigation=none, metric_accuracy=unverified. Zero accepted means NO_DEPTH, not
success. Early stop/background is INTERRUPTED, startup/runtime error FAIL;
COMPLETE only full timeout with accepted samples. Post-stop samples forbidden.
Update UI max1/s; no raw pixel/depth buffer file or pointcloud export.

Write result IMPLEMENTATION_READY with exact files/assumptions/lifecycle risks;
do not claim physical test or build. Do not modify other workers or shared docs.
