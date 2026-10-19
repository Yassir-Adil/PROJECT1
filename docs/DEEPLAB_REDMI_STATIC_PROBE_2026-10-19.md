# DeepLab ADE20K — Redmi static-image probe (2026-10-19)

## Scope and safety boundary

This was a local educational diagnostic using one still image supplied by the
owner. It did not open CameraX, send a network request, speak, or call any
navigation/guidance code. The image and candidate model remain local under
`android-app/app/src/debug/assets/`, excluded through the machine-local
`.git/info/exclude`; neither is a release asset. No walking or user movement
was involved. A semantic `floor` label is not a traversability or safety
claim.

## Exact model and runtime contract

- Candidate: `deeplabv3_mnv2_ade20k_float.tflite`, from the MLCommons
  `mobile_models/v0_7/tflite` release path.
- Local SHA-256: `12A2141F28928A9BCCCE5063D0F868211C9D2633A263B9141594FF300DAEAFC0`
- Redmi Interpreter inspection: input `[1,512,512,3] FLOAT32`; output
  `[1,512,512] INT32` category map. The activity checks the exact asset hash
  and fails closed if it differs.
- Probe preprocessing used aspect-fit to 512×512, gray-128 padding, RGB
  conversion, and `(channel / 127.5) - 1` normalization. Tensor shape/type and
  hash are verified. TensorFlow's official DeepLab MobileNetV2 preprocessing
  maps RGB values from `[0,255]` to `[-1,1]`, which supports the normalization
  used here; the exact MLPerf image resize/padding reference for this particular
  binary still needs comparison. Treat the mask/label result as exploratory
  until that is checked.
- The project’s separate TensorFlow Zoo conversion script describes a 257
  input/logit model. That is a different artifact; its tensor contract must not
  be substituted for the MLCommons binary above.
- ADE20K-derived weights are not approved for redistribution in the app.
  The model was used only in this local educational diagnostic; no model/data
  was committed, published, or placed in the release APK.

## Redmi result

Device: Redmi Note 12 Pro 5G (`22101316G`, serial `55aqpfqkvw9xbafe`). Input:
the owner-provided 1440×2560 room photo (`wall-scene.jpg`). Two 20-inference
static batches were logged:

| Batch | p50 | p90 | Mean | Sequential rate |
|---|---:|---:|---:|---:|
| 1 | 406.34 ms | 694.91 ms | 554.31 ms | 1.80 FPS |
| 2 | 410.22 ms | 415.52 ms | 401.94 ms | 2.49 FPS |

Both runs reported the same semantic distribution: whole image `wall 74.80%`,
`floor 24.14%`, and unmapped `class_13 1.06%`; lower-center model cells were
`floor 94.76%`, `wall 5.24%`. There is no ground-truth mask for this one photo,
and a rug/mat may be confused with floor. This is not an accuracy score.

The observed 1.80–2.49 sequential FPS at 512² CPU/4 threads is too slow to
qualify this float model for live guidance. It remains disconnected from the
navigation system regardless of its labels. No thermal delta was measured;
post-run sensors showed CPU/GPU/NPU around 59°C and battery around 35°C, which
must not be treated as a controlled thermal result.

## Verification and device restoration

- `:app:testDebugUnitTest`: 285 passed, 0 failed.
- `:sim-core:test`: 10 passed, 0 failed.
- `:app:assembleDebug`: successful. Diagnostic APK SHA-256:
  `903B24CA2E6106B94248C662D36C6C32A96A286F358EB6737399A70B3E9D7514`
  (115,825,765 bytes; debug-only, not for distribution).
- The diagnostic APK was temporarily installed with `adb install -r` and the
  activity was started directly. The original installed 0.9.21/versionCode 34
  APK was pulled before testing and successfully restored afterward. Backup:
  `outputs/reports/redmi-evidence/installed-0.9.21-before-deeplab-probe-20261019.apk`
  (SHA-256 `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`).
- The debug Activity is guarded by the system `DUMP` permission for ADB launch;
  the merged debug manifest adds no `INTERNET` permission. No app behavior,
  safety policy, or release manifest was changed for this probe.

## Next gate

Before any live-camera integration, verify the exact preprocessing against the
artifact’s reference, then compare an optimized/quantized candidate on Redmi
using multiple consented, labeled stationary scenes. Keep the mask diagnostic
only; current evidence does not authorize `CLEAR`, `STRAIGHT`, turns, or a
“safe path” cue.
