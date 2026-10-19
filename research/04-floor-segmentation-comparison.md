# 04 — Floor / walkable-area segmentation comparison (TriGuide AI)

*Grok Bot research, 2026-10-18 ~16:50 MYT (UTC+8). Target: Redmi Note 12 Pro 5G (22101316G, Dimensity 1080, Android 14), on-device only, no INTERNET permission.*
*Input reviewed: Cursor's `docs/WALKABLE_AREA_EVIDENCE_SPIKE_2026-10-18.md` (read-only, not modified).*

> **Safety frame (non-negotiable).** A "walkable floor" mask is **never** proof that walking is safe. It cannot see drop-offs reliably, and it cannot see glass, thin poles, overhangs, wet floors, or things the model mislabels. In our own box test (§5), stairs going down were labelled **"floor"**, and a sidewalk was labelled **"road"**. Floor evidence may only **gate a turn hint**. For example, "slight left" is allowed only if: there is floor evidence on the left, AND there is no detector hazard, AND there is no depth/ARCore hazard on the left, AND all evidence is fresh. Floor evidence can never create STRAIGHT/"clear"/"safe" wording. It must stay **diagnostic-only (no TTS, no movement decision)** until the owner has tested it on the Redmi and it has passed a separate safety review.

---
## 1. Verdict

| Rank | Model | Why | Main risk |
|---|---|---|---|
| **Recommended** | **DeepLabV3 MobileNetV2 ADE20K** (TF model zoo `deeplabv3_mnv2_ade20k_train_2018_12_03`), self-converted to LiteRT @257 | Code is Apache-2.0 and so is the weight release. Same runtime as the app (LiteRT/XNNPACK, like MiDaS). 9.2 MB fp32 / 2.6 MB dyn-int8. Has floor/road/sidewalk/path/rug/**stairs/stairway/step** classes. On the box: **8.8 ms (4 thr) / 33 ms (1 thr)**. It was the only model in our test that marked one flight of stairs as `stairway`. | Low accuracy (32.0 % mIoU published). Old 2018 graph that needs self-conversion (script saved). ADE20K images are under non-commercial research/education terms (fine for a graduation project; flag before any commercial release). |
| **Runner-up** | **PP-MobileSeg-Tiny ADE20K** (PaddleSeg, Apache-2.0) | Best published mobile accuracy/latency among models with permissive code (36.39 % mIoU, 215 ms on Xiaomi 9 / SD855, 1 thread, PaddleLite, argmax included). Gave correct output under native Paddle on the box (35 / 61 ms @512). | **The ONNX export is broken.** paddle2onnx produced a model that outputs a constant class. The VIM upsampler uses where/gather_nd/scatter_nd ops. We have no LiteRT path, and Paddle Lite would add a second runtime. Same ADE20K data terms. |
| Research/offline reference only | **SegFormer-B0 ADE20K** (nvidia) | Ready-made TFLite (Qualcomm AI Hub) and ONNX (Xenova). Good masks on corridors. Box 34 ms fp32 @512. | **NVIDIA Source Code License: non-commercial, "research or evaluation purposes only"**. Repackagers relabel it Apache/MIT, which is misleading. In our test it labelled descending stairs as **floor (91 %)**. It is also the slowest on mobile CPU (770 ms on SD865, 1 core, per TopFormer paper). Do not bundle. |
| Watch (not run) | **TopFormer-T / SeaFormer-T** | Fastest published (43 ms / 40 ms on SD865, 1 ARM core, 512², TNN). ADE20K 33.6 / 35.0 % mIoU. | No official TFLite/ONNX. Needs mmcv-full 1.3.14 (old). Weights only on Google Drive/Baidu. TopFormer's LICENSE file contains an **unresolved git merge conflict** (Apache-2.0 vs MIT). SeaFormer has no root LICENSE (only `seaformer-seg/` is Apache-2.0). |
| Not suitable | MediaPipe Image Segmenter stock models | Selfie, hair and multiclass-selfie are person-only. **DeepLab-V3 is Pascal VOC (21 classes): no floor/road/stairs.** | Would need a custom floor model with metadata. |
| Not suitable | PP-LiteSeg | Only Cityscapes/CamVid weights (outdoor driving). Cityscapes is **non-commercial**. No floor/stairs class. GPU-only latency published (1080Ti). | — |
| Not suitable | LR-ASPP MobileNetV3 (torchvision / litert-community TFLite) | 21 COCO-VOC classes, no floor. | — |
| Avoid | GuideLens `floor_segmentation_int8.onnx` | Unknown provenance and licence (see log). | — |

**Curb:** none of the ADE20K/Pascal/Cityscapes models has a curb class. Mapillary Vistas has `Curb` and `Curb Cut`, but it is **CC BY-NC-SA 4.0** (non-commercial). Curbs and drop-offs must come from depth/ARCore (report 03), not from segmentation.

---
## 2. Candidate table: licences, formats, classes

| Model | Code licence | Weights licence | Training data terms | Mobile format available | Size | Floor-relevant classes |
|---|---|---|---|---|---|---|
| DeepLabV3 MNv2 ADE20K (TF) | Apache-2.0 ([tensorflow/models LICENSE](https://github.com/tensorflow/models/blob/master/LICENSE) covers `/research`) | Released in the same repo's model zoo ([model_zoo.md](https://github.com/tensorflow/models/blob/master/research/deeplab/g3doc/model_zoo.md)), no separate licence → Apache-2.0 assumed | ADE20K: images **non-commercial research/education only**; annotations/software BSD-style ([ADE20K terms](https://ade20k.csail.mit.edu/terms/)) | Frozen TF graph → **self-converted TFLite** (`convert_deeplab_ade20k.py`) | tar 24.8 MB; TFLite fp32 9.2 MB, dyn-int8 2.6 MB | floor, road, sidewalk, path, rug, earth, grass, **stairs, stairway, step**, escalator, wall, door, railing, bannister |
| DeepLabV3/V3+ MNv2-d8 ADE20K (OpenMMLab, Cursor's reference) | Apache-2.0 | Apache repo, ADE20K data | ADE20K as above | PyTorch `.pth` only; ONNX via mmdeploy | — | same 150 classes; **heavy**: DeepLabV3+ MNv2 25.8 GFLOPs, 1035 ms SD865 1-core ([TopFormer Tab.1](https://arxiv.org/abs/2204.05525)) |
| PP-MobileSeg-Tiny / Base | Apache-2.0 ([PaddleSeg](https://github.com/PaddlePaddle/PaddleSeg/blob/release/2.10/LICENSE)) | Apache repo | ADE20K | Paddle inference export (official zip); ONNX conversion **failed** for us | Tiny params 6.1 MB | ADE20K 150 |
| PP-LiteSeg-T/B | Apache-2.0 | Apache repo | **Cityscapes: non-commercial** ([licence](https://www.cityscapes-dataset.com/license/)); CamVid unclear | Paddle; ONNX via paddle2onnx | — | road, sidewalk, terrain (no floor/stairs) |
| SegFormer-B0 ADE20K | **NVIDIA Source Code License, NC** ([NVlabs/SegFormer LICENSE](https://github.com/NVlabs/SegFormer/blob/master/LICENSE) §3.3) | NC (HF card points to that licence: [nvidia/segformer-b0-finetuned-ade-512-512](https://huggingface.co/nvidia/segformer-b0-finetuned-ade-512-512)). [Qualcomm repack](https://huggingface.co/qualcomm/Segformer-Base) is tagged apache-2.0 and its LICENSE points to the transformers licence: **misleading** | ADE20K | TFLite fp32/w8a8 (Qualcomm AI Hub v0.64.0), ONNX fp32/fp16/int8 ([Xenova](https://huggingface.co/Xenova/segformer-b0-finetuned-ade-512-512)) | TFLite 15.1 MB / 4.4 MB; ONNX 15.3 MB / 4.4 MB | ADE20K 150 |
| TopFormer-T/S/B | LICENSE file has merge conflict (Apache-2.0 vs MIT), both permissive but ambiguous ([repo](https://github.com/hustvl/TopFormer)) | no separate licence | ADE20K | none official (TNN runtime used by authors) | 1.4 M params (T) | ADE20K 150 |
| SeaFormer-T/S/B/L | no root LICENSE; `seaformer-seg/` Apache-2.0 ([repo](https://github.com/fudan-zvg/SeaFormer)) | none stated | ADE20K / Cityscapes(NC) | `convert2onnx.py` in repo (not tried) | 1.7 M params (T) | ADE20K 150 |
| MediaPipe DeepLab-V3 | Apache-2.0 | Google model, Apache | Pascal VOC | TFLite with metadata, MediaPipe task-ready | 2.8 MB | **none** (background, person, chair, sofa, dining table…) |
| Mapillary-Vistas-trained models | varies | varies | **CC BY-NC-SA 4.0** | varies | — | Curb, Curb Cut, Sidewalk, Pedestrian Area |

ADE20K 0-based (150-class) IDs used in our sanity code: wall 0, floor 3, road 6, grass 9, sidewalk 11, earth 13, door 14, rug 28, path 52, **stairs 53**, **stairway 59**, escalator 96, **step 121**, railing 38, bannister 95 (from the model `config.json` `id2label`).

---
## 3. Latency numbers and where they come from

### 3a. Measured by Grok Bot. **BOX CPU (Intel Xeon x86_64, AVX-512), NOT the phone**
Script `bench_seg_cpu.py`, raw data `reports/box_cpu_bench_seg.json`. Each run: 10 warm-up runs, then 50 timed runs on random input of the model's exact shape. Median / p90 in ms.

| Model (format) | Input | 4 threads | 1 thread |
|---|---|---|---|
| **DeepLabV3 MNv2 ADE20K fp32 TFLite (self-conv.)** | 257² | **8.8 / 9.2** | **33.2 / 36.8** |
| DeepLabV3 MNv2 ADE20K dyn-int8 TFLite | 257² | 22.7 / 26.2 | 34.3 / 38.1 |
| DeepLabV3 MNv2 ADE20K fp32 TFLite | 513² | 34.2 / 35.6 | 135.5 / 146.4 |
| SegFormer-B0 ADE20K fp32 TFLite (Qualcomm) | 512² | 34.0 / 37.6 | 137.0 / 144.8 |
| SegFormer-B0 ADE20K w8a8 TFLite (Qualcomm) | 512² | 51.0 / 54.3 | 83.0 / 89.2 |
| SegFormer-B0 ADE20K fp32 ONNX (Xenova) | 512² | 54.4 / 58.5 | 161.3 / 169.4 |
| SegFormer-B0 ADE20K int8-dyn ONNX | 512² | 80.2 / 85.2 | 204.0 / 211.4 |
| SegFormer-B0 ADE20K fp32 ONNX | 256² | 13.8 / 15.2 | 33.7 / 37.0 |
| SegFormer-B0 ADE20K int8-dyn ONNX | 256² | 21.1 / 23.4 | 44.0 / 46.8 |
| PP-MobileSeg-Tiny (native Paddle Inference 2.6.2, real image) | 512² | 35.2 / 38.1 | 60.6 / 66.9 |
| ~~PP-MobileSeg-Tiny self-converted ONNX~~ | 512² | ~~7.1~~ **INVALID**: constant output | — |
| MediaPipe DeepLab-V3 (VOC, reference) | 257² | 2.6 / 2.9 | 9.2 / 9.5 |
| GuideLens floor_seg int8 ONNX (reference) | 256² | 18.1 / 18.8 | 35.1 / 38.6 |

Notes: dynamic-int8 is slower than fp32 on this x86 box, the same as we saw for depth. ARM/XNNPACK on the Dimensity may behave differently, so test both on the Redmi. The DeepLab ADE graph is output-stride 8, which makes it heavy (3.1 GMACs at 257², 12.1 GMACs at 513²). The model zoo's 32.04 % mIoU was evaluated at OS16. An OS16 re-export would be faster, but it needs the TF1 DeepLab training code and has not been tried.

### 3b. Published, NOT reproduced by us
| Claim | Device / runtime | Source |
|---|---|---|
| MediaPipe DeepLab-V3 (VOC) 123.93 ms CPU / 103.30 ms GPU; SelfieSegmenter ~33 ms | Pixel 6, whole MediaPipe pipeline | [MediaPipe Image Segmenter](https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter) |
| TopFormer-T 43 ms (32 ms @448), -S 74, -B 110; SegFormer-B0 770 ms; LR-ASPP MNv3-L 126 ms; DeepLabV3+ MNv2 1035 ms | Snapdragon 865, **1 ARM core**, 512², TNN fp32 | [TopFormer paper Tab.1](https://arxiv.org/abs/2204.05525) |
| SeaFormer-T 40 ms (35.0 / 35.8 mIoU), -S 67 ms, -B 106 ms | SD865, 1 ARM core, 512² | [SeaFormer paper Tab.1](https://arxiv.org/abs/2301.13156) |
| PP-MobileSeg-Tiny 215.3 ms, -Base 265.5 ms, TopFormer-Tiny 490.3, SeaFormer-Tiny 459.0 (argmax included) | Xiaomi 9 (SD855), 1 thread, PaddleLite, 512² | [PP-MobileSeg README](https://github.com/PaddlePaddle/PaddleSeg/blob/release/2.10/configs/pp_mobileseg/README.md) |
| SegFormer-B0 ("Segformer-Base") TFLite fp32 31.8 ms, ONNX w8a16 19.0 ms | Snapdragon 8 Gen 1 **NPU** (Qualcomm AI Hub). Does not apply to Dimensity 1080 | [qualcomm/Segformer-Base](https://huggingface.co/qualcomm/Segformer-Base) |
| PP-LiteSeg-T 273.6 FPS | NVIDIA 1080Ti GPU, TensorRT. Not mobile | [PP-LiteSeg README](https://github.com/PaddlePaddle/PaddleSeg/blob/release/2.10/configs/pp_liteseg/README.md) |

Note that the two papers disagree: TopFormer-T is 43 ms in its own paper and 490 ms in PaddleSeg's measurement (different runtime, thread setup and argmax). **No published number is valid for the Redmi.** Only the protocol in `reports/02-redmi-benchmark-protocol.md` will produce one.

---
## 4. Check of Cursor's plan (claims and sources)
| Plan claim | Verdict |
|---|---|
| "MediaPipe Image Segmenter supports Android live-stream results and confidence masks, but requires a compatible trained model; sample is API reference, not indoor-floor accuracy" | **Accurate.** The Android guide shows `RunningMode.LIVE_STREAM` + `setResultListener` and says "requires a trained model that is compatible with this task". Missing point: **none of the stock MediaPipe models has a floor class.** DeepLab-V3 is Pascal VOC. |
| "MobileNetV2/DeepLabV3 ADE20K checkpoint is only a historical indoor-class baseline" (cites OpenMMLab metafile) | **Partly accurate.** ADE20K is indoor+outdoor, not only indoor. The OpenMMLab MNv2-d8 models it cites are heavy dilated nets (DeepLabV3 34.08 mIoU, DeepLabV3+ ~25.8 GFLOPs, ~1 s on SD865). The lighter and more suitable checkpoint is the **TF model zoo** one (2.6–9 MB TFLite after conversion). |
| "PP-LiteSeg published lightweight examples are primarily outdoor datasets" | **Accurate.** Only Cityscapes and CamVid. Add: Cityscapes is **non-commercial**, and the published speed is desktop-GPU only. PP-MobileSeg (same repo, ADE20K, mobile latency) is the better PaddleSeg option. |
| "ARCore Raw Depth limitations / optional later experiment" | Link valid. See report 03: Redmi Note 12 Pro 5G support is not confirmed until a runtime `isDepthModeSupported` check. |
| Steps 1–5 (typed `WalkableAreaObservation` with UNAVAILABLE/STALE/INVALID, offline labelled-clip evaluator, single CameraX stream, 30 s warm-up + 300 inferences, no movement phrases) | **Sound and matches our safety rules.** Gaps: (a) no licence column for weights/data; (b) no rule that **stairs/step/stairway/escalator pixels inside the corridor force HOLD**; (c) `road` must not count as walkable for a pedestrian; (d) no requirement to log the mask→decision link per frame. |
| All 4 reference URLs | All return HTTP 200 (checked 2026-10-18 16:47 MYT). |

---
## 5. Box sanity test: quality, NOT an accuracy metric
5 public images (Wikimedia CC/PD + bus.jpg). ROI = bottom-centre (x 0.3–0.7, y 0.6–1.0). Script `sanity_seg.py`, data `reports/box_seg_sanity.json`, overlays `models/seg/out/`.

- **Hospital corridor:** all working models gave ROI ≈100 % `floor`.
- **Office hallway with person:** floor 31 % (SegFormer) / 45 % (DeepLab-257); person and wall are separated.
- **Stairs seen from the top (drop-off!):** left flight going down: **SegFormer `floor` 91 %**, DeepLab-513 `floor` 83 %, DeepLab-257 `stairway` 72 %. Right flight going down: all models said mostly `floor`. → **Segmentation alone misses drop-offs.**
- **Curb ramp / sidewalk (CC0):** the foreground sidewalk was labelled **`road` 80–91 %** by every model. The curb edge has no class.
- **PP-MobileSeg ONNX:** 100 % class 0 on every image, so the conversion is broken. Native Paddle output is correct (corridor → floor dominant).

These are 5 images, so this says nothing about accuracy. It does show the failure modes the evaluator must count: stairs→floor, sidewalk→road, low objects.

---
## 6. Proposed narrow Cursor slice (diagnostic-only)
**Slice WALK-0: "Floor evidence, logged only"** (follows Cursor's steps 1–3 and adds our safety rules):
1. Bundle `deeplabv3_mnv2_ade20k_257_f32.tflite` (SHA-256 `9003f85b…7328`) via LiteRT (already a dependency). Add a `THIRD_PARTY_NOTICES` entry: tensorflow/models Apache-2.0 + "trained on ADE20K (MIT CSAIL), non-commercial research/education terms". Keep the dyn-int8 variant (`c83fa9ee…b145`) for an A/B test on the Redmi.
2. `WalkableAreaObservation` as in Cursor's plan, plus per-zone fields for LEFT / CENTER / RIGHT near-field bands (bottom 40 % of the frame): `floorFraction` (floor, sidewalk, path, rug only; **road, grass, earth do NOT count**), `hazardFraction` (stairs 53, stairway 59, step 121, escalator 96 → **hazard**), `frameId`, `timestampMs`, `modelSha`, `state ∈ {OK, UNAVAILABLE, STALE, INVALID}`.
3. Run asynchronously on the existing CameraX analyzer at most every 3rd frame, dropping frames. Never block the detector. Stale if older than 500 ms.
4. **No TTS, no change to `NavigationDecisionEngine`/`PathRiskAnalyzer` outputs.** Write to logcat tag `TriGuideFloor` and to the debug overlay only (a toggle that is off by default).
5. Design hook for later (do not wire it yet): `TurnHintGate.allowSlight(side) = floor[side] ≥ 0.6 && hazard[side] == 0 && hazard[center] == 0 && detectorHazard[side] == NONE && depthHazard[side] == NONE && all evidence fresh && state == OK`. Anything else → `HOLD`/`UNKNOWN`. Floor evidence can never produce STRAIGHT or "clear".

**Acceptance criteria**
- A1. Unit tests: (a) all-floor mask + detector chair in corridor → decision unchanged (HOLD), with no floor-derived speech; (b) stairs pixels ≥ 2 % in any zone → `hazardFraction > 0` logged; (c) road-only mask → `floorFraction == 0`; (d) observation older than 500 ms → `STALE`; (e) model load failure → `UNAVAILABLE`, app keeps running; (f) test that the gate function is not referenced by any TTS/decision code path.
- A2. Redmi run (owner present) per `reports/02-redmi-benchmark-protocol.md`: 30 s warm-up, then ≥300 segmentation inferences. Report median/p90 inference, detector FPS before and after (detector FPS drop ≤ 20 %), XNNPACK vs GPU delegate, fp32 vs dyn-int8, 10-minute thermal status. **These must be numbers measured on the Redmi, labelled as such.**
- A3. Offline replay of ≥ 5 labelled owner clips: open corridor, closed door/wall, rug/shadow, **stairs down**, low object on floor. Report per clip: false-walkable rate in the near field, and whether stairs were ever counted as floor. Cursor's plan already lists this.
- A4. Logcat shows `TriGuideFloor` lines with frameId and zone fractions, matched to the `TriGuideNav` decision of the same frame.
- A5. No new permissions. INTERNET is still absent. No images are saved by default.
- A6. A separate safety review by Grok Bot plus an owner test **before** any gate is wired to speech.

---
## 7. Files (box)
- Models: `/workspace/triguide/models/seg/` (+ `SHA256SUMS`), overlays `models/seg/out/`
- Scripts: `bench_seg_cpu.py`, `sanity_seg.py`, `convert_deeplab_ade20k.py` (venv-conv: Python 3.11, tensorflow-cpu 2.18.1, paddlepaddle 2.6.2, paddle2onnx 1.2.11)
- Data: `reports/box_cpu_bench_seg.json`, `reports/box_seg_sanity.json`
- Test images: Wikimedia Commons, "Hospital corridor in Turkey" (CC BY-SA 4.0), "Office hallway (94641918)" (CC BY 2.0), "Sidewalk ramp to lowered curb… Colchester VT" (CC0), "Indoor staircase of Casa Tribunal de Patnongon - 1" (PD). Used for box testing only, not redistributed.
