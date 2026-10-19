# Open-source licenses

## Debug-only ARCore capability experiment — 2026-10-19

Maven dependency `com.google.ar:core:1.57.0` is included in debug only, not release.
Version verified against [Google Maven metadata](https://dl.google.com/dl/android/maven2/com/google/ar/core/maven-metadata.xml)
and [official SDK release](https://github.com/google-ar/arcore-android-sdk/releases/tag/v1.57.0).
This is Google's SDK under its applicable terms, not code we relicense as our own.
The current DUMP-protected capability check acquires zero camera/depth frames,
requests no install, and configures no cloud/key/billing feature. Google-provided
ARCore functionality is subject to [ARCore Additional Terms](https://developers.google.com/ar/develop/terms),
[Google Terms](https://policies.google.com/terms) and [Google Privacy Policy](https://policies.google.com/privacy).
Any user-facing live ARCore integration/public distribution must include the
required in-app terms/privacy disclosures before that next feature is enabled.
No APK is published here; service privacy is separate from this app's absent
INTERNET permission. Installed support is not safety or accuracy certification.

Depth Anything V2 Small and the Apache-2.0 Android/ONNX demo are researched
fallbacks only: no new weights/source copied or included. Existing MOSAIC and
ADE20K educational diagnostic restrictions below remain unchanged.

## TriGuide original code

License not chosen by the project owner yet. Do not publish the APK as if a license were already assigned.

## Studied, not copied into `android-app/`

| Project | License | Use |
| --- | --- | --- |
| GuideLens-Android-App (`nrai18`) | GPL-3.0 | Local audit only. Kotlin and ONNX assets stay in `research/upstream/` and are gitignored. |
| MediaPipe samples (`google-ai-edge`) | Apache-2.0 | Pattern for CameraX live-stream detection and Image Segmenter LIVE_STREAM (confidence/category masks). Sample project is not compiled into this APK. Commit inspected 2026-10-19: `cb208b3c5e6ef1586a23dee940f2413c6c97a510`. |
| Sailens Android (`wnbotoo/sailens-android`) | Apache-2.0 | Architecture ideas only (model contracts, provenance, scheduling, freshness, replay). No source vendored. Commit inspected 2026-10-19: `7d6be56036663c6621f4c50585fe1fa1e2ae9bcc`. |
| PaddleSeg / PP-LiteSeg (`PaddlePaddle/PaddleSeg`) | Apache-2.0 (code) | Candidate training/export source only. No code or weights copied. Weight/dataset redistribution for APK **not cleared**. Branch `release/2.10` tip `3c4db66de1d9d59d0628ed87590b6308a2f4aa2a`. |
| BlindAssist (`violetljj/blind-assist`) | AGPL-3.0 | Research/evaluation ideas only. **No code copied.** |
| OpenBot (`ob-f`) | MIT | Architecture notes only (phone as brain, separate controller). Android sources were not checked out. Physical body is out of scope. |

## App dependencies (Maven, not vendored)

AndroidX, Jetpack Compose BOM `2024.10.01`, CameraX `1.4.0`, Kotlin `2.0.21`, JUnit `4.13.2`, MediaPipe Tasks Vision `1.1.1` (Apache-2.0). Their own licenses apply.

## Bundled model

| File | Source | License |
| --- | --- | --- |
| `android-app/app/src/main/assets/models/efficientdet_lite0.tflite` | `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite` | Apache-2.0 (Google / MediaPipe EfficientDet-Lite0 int8). Training data is COCO 2017; COCO annotations are CC BY 4.0 |

SHA-256 `0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB`, 4,602,795 bytes.

## Relative-depth benchmark candidate — NOT bundled

`midas_small_256_fp16.tflite` is recorded only as a future local benchmark
candidate in `RelativeDepthModelContract`. The candidate card identifies the
MiDaS v2.1 small conversion, MIT MiDaS weights, Apache-2.0 EfficientNet-Lite3
backbone, NHWC `1x256x256x3` float input, and `1x256x256` relative inverse-depth
output. The expected SHA-256 is
`bec9bce704789e504ec306196fcb0aabe90fd25c2b9d7db382339741950890ca`.

This is not a release clearance or a Redmi performance result. Before any APK
bundling, verify the exact bytes and provenance again, benchmark GPU/CPU startup
and latency on the Redmi, and keep the output HOLD-only diagnostic evidence.

YOLOv8-family weights found inside GuideLens were not imported. Treat them as encumbered (GPL-3.0 repo, and Ultralytics YOLO weights are commonly AGPL-3.0) until a lawyer or the user clears a specific file.

## Candidate floor model — NOT bundled (primary-source recheck, 2026-10-18)

The [TensorFlow DeepLab repository](https://github.com/tensorflow/models/blob/master/research/deeplab/README.md)
says its **code** is covered by the repository license (Apache-2.0). Its
[model zoo](https://github.com/tensorflow/models/blob/master/research/deeplab/g3doc/model_zoo.md)
publishes a MobileNetV2 ADE20K checkpoint, but that page does not separately
state the checkpoint's redistribution terms. The [ADE20K image terms](https://ade20k.csail.mit.edu/terms/)
limit supplied images to non-commercial research/education; whether and how
those terms affect distributing derived weights in a future app needs an
explicit rights review. Do **not** infer that checkpoint weights are Apache-2.0
merely because the code is. No floor-model weights were copied into the APK.

`research/04-floor-segmentation-comparison.md` is a candidate-screening report,
not a phone benchmark or license clearance. The referenced converted model and
raw benchmark artifacts are not present in this checkout. Before bundling any
candidate, verify its exact bytes/hash, weight and data rights, conversion
output, class mapping, and Redmi performance on labeled clips.

## Phase 2 candidate TFLite hash copies (NOT in APK assets)

| File (gitignored under `research/model-candidates/`) | SHA-256 | Notes |
| --- | --- | --- |
| `deeplab_v3.tflite` (MediaPipe) | `FF36E24D40547FE9E645E2F4E8745D1876D6E38B332D39A82F0BF0F5D1D561B3` | Pascal VOC taxonomy; not indoor floor evidence. Ledger: `docs/PHASE2_SEGMENTER_CANDIDATE_BENCHMARK.md` |
| `selfie_multiclass_256x256.tflite` (MediaPipe) | `C6748B1253A99067EF71F7E26CA71096CD449BAEFA8F101900EA23016507E0E0` | Person parts only; not floor evidence |

DeepLabV3 ADE20K converted weights remain **unbundled** pending rights review.
