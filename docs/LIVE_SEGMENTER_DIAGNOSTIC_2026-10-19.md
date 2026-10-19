# Live segmenter diagnostic and bounded frame handling — 2026-10-19

**Scope: two improvements, debug-only.** A real CameraX/MOSAIC diagnostic with
measured stages and cheaper RGB packing; and a tested one-in-flight gate that
invalidates late/stale results. Neither is wired to navigation or speech.

## Reuse and boundaries

- Reuses the existing `CameraXSource` (RGBA, KEEP_ONLY_LATEST, sole ImageProxy
  close owner), existing `FrameCrops`, existing hash-pinned MOSAIC weights,
  Interpreter/NNAPI, and existing validated INT32 decoder. No dependencies,
  toolchain, source model, or production camera code were changed.
- Uses CameraX's maintained
  [ImageProxy.toBitmap](https://developer.android.com/reference/androidx/camera/core/ImageProxy#toBitmap())
  instead of adding a custom plane/stride parser. Explicit crop and upright
  rotation follow conversion. Rear camera was measured at 640x480, crop
  (2,67)-(638,413), rotation 90 degrees, then centered gray-128 512x512 canvas.
- The existing [CameraX latest-only strategy](https://developer.android.com/media/camera/camerax/analyze)
  remains responsible for its internal latest buffer. The diagnostic adds no
  work queue; it admits at most one result computation and caps admission at
  10/s. Internal CameraX drops are not observable or invented in the report.
- The new activity is debug-manifest-only, protected by `android.permission.DUMP`,
  absent from release, excluded from Recents, and never resumed automatically.
  It has a visible Stop button, 5..60-second duration, startup timeout, and
  closes on pause/destruction. No camera images/masks are saved, no cloud API
  or Internet permission is added, and no TTS or navigation API is called.

## Shared runtime and performance fix

`DiagnosticSegmenter` centralizes the three model hash pins, tensor checks,
reusable image/input/output buffers, and both historical input encodings.
The static diagnostic now uses this same runtime, preventing live/static
preprocessing drift. Float normalization and legacy UINT8 quantization remain
available. All buffers/interpreter access are serialized and native teardown
waits for current inference, while result invalidation happens first.

The first live run exposed costly scalar direct-buffer packing. Raw UINT8 now
packs RGB into a reusable byte array and performs one `ByteBuffer.put(array)`.
Four new tests check channel/unsigned boundaries, 4,096 deterministic pixels
against the previous scalar byte sequence, reused-buffer overwrite, and wrong
destination length. Alpha is ignored exactly as before; no channel normalization
or model decision was changed.

The static reference image's logged class fractions are unchanged before/after:
wall 52.05%, floor 22.91%, and the same other top classes/regions. This is an
additional regression observation, not a full-mask accuracy score. The pure
packing test, rather than rounded class proportions, checks byte equality.

## Frame/session gate

`DiagnosticFrameGate` rejects stopped, invalid, duplicate/out-of-order, busy,
cadence-limited, and rollback samples. Tickets are session-specific and checked
by identity; a duplicate/old completion cannot release the current slot.
Completion beyond **250 ms since analyzer receipt** is rejected. Stop invalidates
all tickets before CameraX unbind or waiting for native inference teardown.

**Clock limitation:** camera timestamps establish sample ordering only. Receipt
and completion use elapsedRealtimeNanos. Their difference is callback-to-map
latency, not sensor capture age. Logs explicitly report capture-to-result as
unavailable; they do not subtract timestamps from unverified clock epochs.

Sixteen new gate tests cover the above, exact age/cadence boundaries, rollback
recovery, restart, forged/repeated completion, concurrent offers, and differing
camera/receipt clock epochs. Production evidence fusion is untouched.

## Observed Redmi results

Two 20-second foreground runs used rear CameraX + MOSAIC NNAPI, four threads,
first two accepted frames excluded from timing samples, and a 10/s admission
cap. All 100/100 model nodes were delegated in one NNAPI partition. This does
not identify a particular GPU/NPU; the driver also warned that diagnostic
callback registration was unsupported.

| Metric | Scalar packing run | Bulk RGB run |
| --- | ---: | ---: |
| Measured accepted samples | 159 | 135 |
| Prepare p50 / p90 ms | 56.81 / 73.11 | 8.00 / 8.62 |
| Inference p50 / p90 ms | 20.18 / 26.72 | 20.10 / 22.00 |
| Decode/count p50 / p90 ms | 2.20 / 5.19 | 2.01 / 2.13 |
| Callback-to-map p50 / p90 ms | 81.65 / 99.87 | 30.15 / 32.17 |
| Delivered analyzer callbacks/s | 18.85 | 13.70 |
| Measured accepted results/s | 7.95 | 6.75 |

The code-specific packing test proves byte parity. The phone runs demonstrate
lower observed preparation/response latency, **not higher camera/result FPS**:
scene, camera cadence, thermal state, charging, JIT, and system load were not
controlled as an identical-scene A/B. The measured throughput actually fell
between runs and must not be hidden. The admission cap and startup time also
contribute to these sustained result-rate denominators. No detector was running
concurrently, so this is not the combined production perception budget.

The scalar run rejected one over-budget result and one completion after stop;
the bulk run reported no stale completion. Pixel fractions on arbitrary live
scenes are not confidence, distance, floor accuracy, or walkability.

Both builds also passed an explicit background/pause check: launch with a
60-second limit, send Home after approximately four seconds, observe
`status=INTERRUPTED reason=paused`, and wait three more seconds. No subsequent
LIVE_SAMPLE/LIVE_START appeared. This verifies the sampled pause path, not
every hardware/lifecycle combination. No heard-audio or walking test was done.

The final shared runtime also passed four additional static contract smoke
batches (three measured calls plus two warmups each): DeepLab float/CPU,
DeepLab UINT8 raw/CPU, DeepLab UINT8 legacy/CPU, and MOSAIC legacy/NNAPI.
Their tensor/hash checks, INT32 decoding, and no-voice completion all passed.
Three measured calls are a regression smoke test, not a latency benchmark.
The final smoke cycle again restored and hash-verified the original APK.

Raw evidence:

- Baseline: `outputs/reports/redmi-evidence/20261019-live-1792393242952/`
- Optimized: `outputs/reports/redmi-evidence/20261019-live-1792393242952-bulk-rgb/`
- Shared-runtime smoke: `outputs/reports/redmi-evidence/20261019-live-1792393242952-shared-runtime-smoke/`

The optimized directory also contains a machine-readable `summary.json`.

Wireless discovery also exposed duplicate service names with a `(2)` suffix:
the older advertised endpoint refused connection while the newer endpoint
worked. Do not blindly select the first mDNS row. Restrict candidates to the
known serial, try its current advertised connect endpoints, and verify the
actual device serial before installation. Wireless went offline again after
the final successful smoke restoration/hash check; no later phone modification
was attempted and the verified original backup remains available.

Battery level rose during the sessions; do not infer power consumption from
these point readings. No long-duration thermal, memory-peak, or accuracy
validation is claimed.

## Build, isolation, and restoration

- **311 app JVM tests pass**, including 20 new tests; unchanged sim-core suite
  remains **10/10**. Final debug/release build succeeded; the last build took 23 s.
- Final diagnostic staging APK:
  `outputs/apk/TriGuideAI-0.9.21-live-segmenter-diagnostic-bulk-rgb-20261019.apk`.
- Diagnostic SHA-256:
  `1390D008DE44E1F2A48DE202F7AE63F5A385FE77CADBAED5473BFA52BFE517A4`.
- Release APK is byte-unchanged:
  `5B2D6B483624277B032F1F3FC3C10DF6DA928C6D820C31BDE0E62EC505C40540`.
- Each completed phone test cycle restores original **0.9.21/34** and verifies
  the installed APK pull against the original SHA-256:
  `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
  No app data was cleared and the original app is left stopped.
- Cursor was dispatched a bounded read-only lifecycle review via its CLI.
  It returned no review result after roughly nine minutes; only that CLI child
  was stopped, leaving the editor/other agent untouched. No independent Cursor
  approval or finding is claimed. Codex reviewed the code and executed the
  recorded tests; no Cursor code edits were accepted.

Launch explicitly with an authorized shell:

```powershell
adb -s <current-paired-endpoint> shell am start -W `
  -n com.triguide.ai/.diagnostics.LiveSegmenterDiagnosticActivity `
  --ei triguide.diagnostic.duration_seconds 20
```

Follow-up: `docs/COMBINED_VISION_PREVIEW_2026-10-19.md` records an explicit
debug combined object-box/on-demand-description mode and its bounded Redmi
smoke checks. Default mode remains silent. That follow-up does not validate
sustained combined production load or navigation.

Next gates remain separate: calibrated camera timing, sustained combined detector/model
load, labeled phone accuracy, independent step/depth hazards, and only then
review of any positive-path policy. The documented Top31 stair failure remains
unresolved; no CLEAR or turn can be inferred from this diagnostic.
