# Phase 2 — Segmenter candidate ledger (max 3)

**Date:** 2026-10-19  
**Device target:** Redmi Note 12 Pro 5G (`22101316G`). Emulator figures are non-authoritative.  
**APK:** No new segmenter weights bundled in this phase.

---

## Selection posture

None of the three candidates is cleared for **indoor walkable** navigation speech.  
Class mapping remains **UNMAPPED** in TriGuide. Pixel 6 / Snapdragon published ms are **not** Redmi FPS.

---

## Candidate A — MediaPipe DeepLab-V3 (Pascal VOC) TFLite

| Field | Value |
| --- | --- |
| URL | `https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite` |
| Official guide | https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter |
| Local hash copy | `research/model-candidates/deeplab_v3.tflite` (**not** in APK assets) |
| Size | 2,780,176 bytes |
| SHA-256 | `FF36E24D40547FE9E645E2F4E8745D1876D6E38B332D39A82F0BF0F5D1D561B3` |
| Input | 257×257 float32 |
| Output | category / confidence masks (MediaPipe Image Segmenter) |
| Classes | Pascal VOC-style (background, aeroplane, …, person, …). **No floor / road / stairs** |
| Pre/post | MediaPipe Tasks ImageSegmenter LIVE_STREAM; rotate/crop via existing CameraX mapping |
| Weight license | Distributed by Google MediaPipe models storage; treat as Apache-2.0 with MediaPipe Tasks (confirm model card before commercial ship) |
| Training data | Pascal VOC (research terms historically) |
| Conversion | Official TFLite; no conversion step |
| APK size impact if bundled | ~+2.7 MB |
| Redmi p50/p90 / FPS / drops / memory / 10-min thermal | **NOT MEASURED** |
| Failure behavior | Missing asset → NO_MODEL; init fail → UNAVAILABLE (Phase 1 seam) |
| Indoor navigation fitness | **Unsuitable as floor evidence** (wrong taxonomy). Useful only as LIVE_STREAM API smoke test |

---

## Candidate B — MediaPipe SelfieMulticlass 256×256

| Field | Value |
| --- | --- |
| URL | `https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_multiclass_256x256/float32/latest/selfie_multiclass_256x256.tflite` |
| Local hash copy | `research/model-candidates/selfie_multiclass_256x256.tflite` (**not** in APK) |
| Size | 16,371,837 bytes |
| SHA-256 | `C6748B1253A99067EF71F7E26CA71096CD449BAEFA8F101900EA23016507E0E0` |
| Input | 256×256 float32 |
| Classes | 0 background, 1 hair, 2 body-skin, 3 face-skin, 4 clothes, 5 others |
| Weight license | Google MediaPipe model distribution (Apache-2.0 ecosystem; confirm card) |
| Redmi metrics | **NOT MEASURED** (Pixel 6 published ~218 ms CPU / ~71 ms GPU — **not** Redmi) |
| Indoor navigation fitness | **Unsuitable** (person parts only; background ≠ walkable floor) |

---

## Candidate C — DeepLabV3 MobileNetV2 ADE20K (TF zoo → self-converted TFLite)

| Field | Value |
| --- | --- |
| Primary research | `research/04-floor-segmentation-comparison.md` |
| Upstream code | tensorflow/models DeepLab (Apache-2.0) |
| Checkpoint family | `deeplabv3_mnv2_ade20k_train_2018_12_03` (model zoo) |
| Exact TriGuide-converted bytes | **Not present in this checkout** — SHA-256 **pending** when conversion is re-run and archived |
| Input / output | Typically 257²; ADE20K 150 classes including floor/road/sidewalk/stairs* |
| Weight redistribution | **Not cleared for APK**: ADE20K *images* are non-commercial research/education; effect on derived weights needs explicit rights review (`OPEN_SOURCE_LICENSES.md`) |
| Box CPU (not Redmi) | fp32 ~8.8 ms median @257² / 4 threads (research 04) — **do not cite as Redmi** |
| Redmi metrics | **NOT MEASURED** |
| Indoor navigation fitness | **Best class taxonomy among reviewed options**, still not permission to walk; stairs mislabels remain a known failure mode in research 04 |

\*Class IDs must be locked in a TriGuide mapping doc before any confidence→floorLike conversion.

---

## Explicitly not selected (this round)

| Option | Why |
| --- | --- |
| PP-LiteSeg Cityscapes weights | Outdoor driving; Cityscapes **non-commercial**; no indoor floor/stairs |
| PP-MobileSeg ONNX | Prior conversion produced constant class (research 04) |
| SegFormer-B0 NVIDIA | Non-commercial source license |
| GuideLens / Ultralytics weights | GPL/AGPL / unknown provenance |

---

## Scene matrix (required before any “useful for walking” claim)

| Scene | Status |
| --- | --- |
| open corridor | Not run on Redmi with segmenter |
| closed door/wall | Detector-only capture exists (`closed-door-wall`); **no** segmenter mask |
| chair partial | Not run |
| mat/shadow | Not run |
| step/drop | Not run |
| low light | Not run |
| motion blur | Not run |
| person crossing | Not run |
| branching view | Not run |

---

## Phase 2 decision

| Decision | Value |
| --- | --- |
| Bundle into APK now? | **No** |
| Preferred future runtime API | MediaPipe Image Segmenter LIVE_STREAM (Candidate A/B plumbing) |
| Preferred future **taxonomy** (if rights clear) | Candidate C ADE20K-style floor-relevant classes |
| Next measured work | Redmi protocol: 30 s warm-up, ≥300 inferences, p50/p90, drops, memory, 10-min thermal — only after a rights-cleared TFLite is archived with SHA-256 |

Hash copies under `research/model-candidates/` are for ledger integrity only; they must stay out of `android-app/app/src/main/assets/`.
