# Local ADE20K segmenter Redmi diagnostics (2026-10-19)

## Scope

This is a local educational diagnostic, not a navigation feature. It uses the
same owner-provided still image and 512×512 static replay as the float probe in
[`DEEPLAB_REDMI_STATIC_PROBE_2026-10-19.md`](DEEPLAB_REDMI_STATIC_PROBE_2026-10-19.md).
The model and image are kept under the debug asset directory and excluded by a
machine-local Git ignore rule. They are not release assets. ADE20K-derived
weights/data are not cleared for redistribution; see the [ADE20K terms](https://ade20k.csail.mit.edu/terms/)
and the official [MLCommons model repository](https://github.com/mlcommons/mobile_models).

## Candidate artifact

- Model: `deeplabv3_mnv2_ade20k_uint8.tflite` from MLCommons `mobile_models/v0_7/tflite`.
- Local size: 2,348,952 bytes.
- Local SHA-256: `2D58C681D81B5E181816B81CC815B82EDF3DAAD905B4D7D35AFADE80AEE93B5F`.
- The diagnostic activity pins this hash and refuses to run on a mismatch.
- The candidate is only available in the debug source set. The release build
  does not include the debug activity or these local assets.
- The follow-on MOSAIC R4 artifact and its Redmi measurements are recorded
  below; it is also debug-only and locally hash-pinned.

## Diagnostic behavior

`DeepLabDiagnosticActivity` accepts
`triguide.diagnostic.variant=float|uint8|mosaic`; the default is `float`. All
three variants in the recorded batches used the same image, 512×512 aspect-fit
resize, gray-128 padding, RGB order, `[-1,1]` real input domain, four CPU
threads, two warmups, and 20 measured inferences. Those uint8 batches encoded
the same real input using the exact input tensor scale/zero point reported by the
TFLite Interpreter. It logs each tensor's shape, data type, byte count, and
quantization parameters before running. Output class IDs are logged with
ADE20K Top-31 names from the upstream taxonomy and MLPerf evaluation contract.
ID 0 is ignored/background and IDs 1–31 are the Top-31 classes. This source
mapping does not validate predictions on the owner-provided still. Subsequent
graph inspection confirmed **32 classifier channels**, with a direct
`[1,512,512] INT32` output in every pinned artifact. The current activity accepts
only that exact class-map contract and IDs 0–31; the unused 31-channel logits
branch was removed.

The current default UINT8 encoding is raw `reference-rgb`, matching the
upstream evaluator. To replay the historical encoding used for every timing
below, set `triguide.diagnostic.input_encoding=normalized-quantized`. The
centered static canvas itself is unchanged. Full reference-derived evaluation
on 2,000 public labeled pairs, encoding/geometry checks, the latest APK hash,
and device verification boundary are recorded in
[`ADE20K_OFFLINE_EVALUATION_2026-10-19.md`](ADE20K_OFFLINE_EVALUATION_2026-10-19.md).

The activity has no camera, network, voice, or navigation integration. The
debug manifest retains the system `DUMP` permission gate, and the merged debug
manifest has no `INTERNET` permission. The quantized values do not prove
walkability, clearance, or safety.

## Verification status

### Redmi static A/B results

The exact same 1440×2560 still was replayed twice per model, with model order
reversed on the second pass. Every batch used two warmups and 20 timed CPU
inferences, four Interpreter threads, and identical preprocessing. These are
latency/output-repeatability measurements, not accuracy measurements.

| Pass order | Model | p50 | p90 | Mean | Sequential rate |
|---|---|---:|---:|---:|---:|
| float → uint8 | float | 395.53 ms | 402.33 ms | 391.58 ms | 2.55 FPS |
| float → uint8 | uint8 | 744.44 ms | 756.55 ms | 747.83 ms | 1.34 FPS |
| uint8 → float | uint8 | 738.67 ms | 742.66 ms | 739.33 ms | 1.35 FPS |
| uint8 → float | float | 414.31 ms | 432.29 ms | 409.20 ms | 2.44 FPS |

The `uint8` input contract was confirmed on the phone as
`[1,512,512,3] UINT8`, scale `0.0078125`, zero point `128`; its output is the
same `[1,512,512] INT32` class map as the float model. Its replay was about
1.8× slower in both orderings, despite the artifact being roughly 74% smaller.
Each variant reproduced its own class-ID distribution across both replays, but
the variants differed substantially in the lower-center region (float: floor
94.76%; uint8: floor 39.12%, wall 38.67%, sea 12.46%, water 9.75%). The
source-backed Top-31 names improve interpretability, but the scene has no
ground-truth mask, so this divergence is a diagnostic discrepancy—not a
measured accuracy loss.
Decision: do not integrate this uint8 artifact for Redmi runtime or guidance;
retain it only in the local debug A/B harness while evaluating other candidates.

### Follow-on candidate: MLCommons MOSAIC R4

- Official artifact: `mobile_segmenter_r4_quant_argmax_uint8.tflite` from
  [MLCommons mobile_open](https://github.com/mlcommons/mobile_open/tree/main/vision/mosaic/models_and_checkpoints/R4),
  local size 1,978,000 bytes, SHA-256
  `DA8B3822A22FDE45ECAF0FF32022287EFF58B500096971B04F3C1B292DA66A3C`.
- The official [MOSAIC README](https://github.com/mlcommons/mobile_open/blob/main/vision/mosaic/README.md)
  reports ADE20K-Top31 mIoU 60.10% and 116ms CPU on Pixel 4. Those are
  published figures, not Redmi performance or proof of this artifact's
  accuracy on the owner-provided image. The upstream
  [conversion source](https://github.com/mlcommons/mobile_app_open/blob/master/mobile_back_apple/models/mosaic.py)
  specifies 512×512×3 input, a 512×512 class-map output, and uint8 conversion.
- Redmi Interpreter contract: input `[1,512,512,3] UINT8`, scale
  `0.007843138`, zero point `128`; output `[1,512,512] INT32`. The
  historical batches used normalized `[-1,1]` RGB followed by quantization.
  Later source review established that the reference evaluator copies UINT8
  RGB bytes directly; quantization parameters alone did not establish that
  earlier encoding as the reference. The current raw-RGB default and explicit
  legacy option are described in the newer public-data report.
- Three MOSAIC batches: p50 `359.49`, `359.80`, `361.20` ms; p90 `364.96`,
  `363.47`, `364.72` ms. Median p50 is `359.80` ms and median p90 is
  `364.72` ms. Three float batches had p50 `395.53`, `414.31`, `411.98` ms
  (median `411.98`) and p90 `402.33`, `432.29`, `416.79` ms (median
  `416.79`). This is about a 12.7% median-p50 improvement for MOSAIC in
  static CPU replay. It is promising but modest; no live-camera, GPU/NPU,
  end-to-end FPS, or labeled-accuracy measurement was made.
- All three MOSAIC runs reproduced the same class-ID distribution on this
  one still: full frame `wall 60.53%`, `floor 27.60%`, `curtain 5.03%`,
  `door 2.57%`, `person 1.99%`; lower-center `floor 97.52%`, `wall 2.48%`.
  The source-backed mapping combines the official [ADE20K category ordering](https://github.com/CSAILVision/sceneparsing/blob/master/objectInfo150.txt),
  the [MOSAIC Top-31 protocol](https://github.com/mlcommons/mobile_open/blob/main/vision/mosaic/README.md),
  and the [MLPerf evaluator's IDs 1 through `num_classes`](https://github.com/mlcommons/mobile_app_open/blob/master/flutter/cpp/datasets/ade20k.cc).
  These names explain the numeric mask only; repeatability is not evidence of
  correct predictions or traversability. Do not interpret `floor` as walkable.
- During one in-flight batch, one process snapshot read `175,032 KiB` PSS and
  `293,668 KiB` RSS; one `top` sample showed 100% CPU. Battery was 39% and
  reported temperature 35.0°C. This is a point-in-time observation, not a
  peak/average memory or thermal test.
- Decision: keep MOSAIC local/debug-only as the better next evaluation
  candidate. Do not connect it to navigation, turn/clear wording, or safety
  cues. ADE20K-related assets remain local pending redistribution review.

### MOSAIC R4 NNAPI delegate probe

The same local image, preprocessing, and model hash were used with TFLite's
NNAPI delegate on the Redmi. Logcat reported that the delegate replaced
`100/100` graph nodes and formed one NNAPI partition. A vendor diagnostic
callback warning was emitted but inference completed; it is not evidence of a
failed delegate.

| Batch | Timed inferences | p50 | p90 | Mean | Sequential inference rate |
|---|---:|---:|---:|---:|---:|
| NNAPI repeat 1 | 20 | 17.62 ms | 17.89 ms | 17.95 ms | 55.71 FPS |
| NNAPI repeat 2 | 20 | 17.72 ms | 18.68 ms | 18.33 ms | 54.54 FPS |
| NNAPI sustained static replay | 1,000 | 17.39 ms | 22.36 ms | 18.24 ms | 54.82 FPS |

The 1,000-run batch took about 18.6 seconds of inference time. Its lower-center
class distribution matched both short NNAPI batches (`floor 96.99%`,
`wall 3.01%`). There is no ground-truth mask, so this checks output
repeatability only. It does not validate scene understanding,
accuracy, connected walkable area, or safe movement. The 54.82 figure is
sequential model-inference throughput for repeatedly processing one already
prepared image; it excludes camera capture, image conversion, application
pipeline work, and user-visible latency, and must not be presented as live
camera FPS.

During the long batch, a single early process snapshot reported PSS
`137,586 KiB`, RSS `255,184 KiB`, and a `top` sample of 117% CPU. Battery and
reported temperature were 40% and 34.5°C at the sampled checkpoints. These
are point observations, not peak/average memory or controlled thermal data.
The CPU MOSAIC median-p50 was `359.80 ms`; the observed NNAPI median-p50 was
`17.62 ms` across the short batches and `17.39 ms` in the sustained batch.
This is a strong hardware-acceleration lead for a later evaluation, not a
reason to change navigation behavior or enable the model in live guidance.

- Device over all static batches: battery 40%→39%; reported temperature
  ranged from 34.0°C to 35.4°C. This was not a controlled thermal test and
  does not attribute heat to any model.
- Original installed `0.9.21` / versionCode `34` APK was pulled before the test
  and restored after the 1,000-run batch. Pre/post SHA-256 matched exactly:
  `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
- Local recovery copies are in `outputs/reports/redmi-evidence/`:
  `installed-0.9.21-before-deeplab-ab-20261019.apk` and
  `installed-0.9.21-after-deeplab-ab-20261019.apk` and
  `installed-0.9.21-after-mosaic-20261019.apk` and
  `installed-0.9.21-after-nnapi-20261019.apk`. The pre-test and final installed
  copies have the same SHA-256.

- Debug compile and `:app:assembleDebug`: passed.
- Fixed the debug class-map upper bound using the official MLPerf evaluator
  contract (ignored/background 0 and 31 semantic IDs 1–31); added regression
  tests for IDs 31 and 32. Added the source-backed Top-31 label table for
  diagnostic output only.
- App JVM tests: 287 passed, 0 failed.
- Simulator core tests: 10 passed.
- Historical debug APK SHA-256 after adding all three local candidates, NNAPI
  selection, bounded sustained-run option, class-31 fix, and diagnostic labels:
  `A8BD6CA1A72FDA333772F4468B5D9A6262CAC5A46A2570C92769CD98670A8076`.
- `:app:assembleRelease` and release vital lint: passed. Release APK SHA-256:
  `5B2D6B483624277B032F1F3FC3C10DF6DA928C6D820C31BDE0E62EC505C40540`.
- Merged release manifest contains neither `INTERNET` nor the diagnostic
  activity. The release APK contains none of the three debug candidates or
  diagnostic assets; its pre-existing `efficientdet_lite0.tflite` remains the
  only packaged TFLite file.
- Redmi ADB is online and the original installed app has been restored. Its
  version remains `0.9.21` / versionCode `34`; the pulled post-restore APK
  SHA-256 exactly matches the pre-test backup.

## Next measurement gate

Next, verify the exact reference resize/padding pipeline, then collect multiple
consented, stationary images with labeled walkable regions and obstacles. The
NNAPI static-replay result warrants a separate live-camera pipeline benchmark
with capture-to-result latency, dropped frames, sustained temperature/memory,
and delegate failure handling. Until
those gates and labeled-accuracy checks pass, keep all segmentation output
diagnostic-only; never connect it to camera guidance, speech, turns, `CLEAR`,
or a “safe path” cue.
