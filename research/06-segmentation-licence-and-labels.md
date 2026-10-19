# 06 — Segmentation models: licences, ADE20K label IDs, tensor contracts (TriGuide AI)

*Grok Bot review, 2026-10-19 ~14:00 MYT (UTC+8). I read Codex's/Cursor's files on Yassir-JR **read-only** (nothing on that computer was changed). The model files were re-downloaded from the official MLCommons repos onto the box, and their SHA-256 hashes match Codex's pins exactly.*
*Files reviewed: `docs/DEEPLAB_REDMI_STATIC_PROBE_2026-10-19.md`, `docs/DEEPLAB_UINT8_REDMI_AB_SETUP_2026-10-19.md`, `docs/BRIDGE_TO_GPT.md` (CURSOR_REVIEW), `android-app/app/src/debug/java/com/triguide/ai/diagnostics/{DeepLabDiagnosticActivity.kt, Ade20kTop31ClassIds.kt}` (versions of 13:45), `research/convert_deeplab_ade20k.py`, `docs/PHASE2_SEGMENTER_CANDIDATE_BENCHMARK.md`, `FloorSegmenterModelContract.kt`.*

> **No benchmarks in this report.** All the Redmi latency numbers quoted below are **Codex's** measurements, taken from its doc. I did not re-run them. My box runs below are **label sanity checks only**: 5 public images, no timing.

---
## 0. Summary
1. **MOSAIC R4 (the uint8 file Codex used)**
   - Code and weights are under the **MLCommons Association License, which is the unaltered Apache-2.0 text**.
   - Redistribution inside an APK is **allowed** if we keep the licence copy and attribution.
   - Training data: **ADE20K SceneParsing (ADEChallengeData2016, 150 classes) reduced to "Top-31"**, plus an ImageNet-pretrained backbone. Both datasets have *non-commercial research/education* image terms. This is a residual, non-contractual risk for a commercial product. It is fine for a graduation project.
2. **All four MLCommons files are Top-31 models, not 150-class models.** That is the DeepLab float, DeepLab uint8, DeepLab int8 and MOSAIC R4.
   - They have 32 output channels and return a class map `[1,512,512] INT32` with values 0..31.
   - The IDs are the **1-based ADE20K objectInfo150 indices 1..31**. ID 0 is ignore/"other", and the models never actually output it.
3. **Safety: Top-31 has no `stairs`, `stairway`, `step`, `escalator` or `path` class.**
   - Those classes were set to "ignore" during training. So MOSAIC and the MLCommons DeepLab **cannot label stairs at all**.
   - Every stair pixel comes out as some other class. On our public staircase image it came out as wall, floor or chair.
   - **MOSAIC can never be used as stairs/drop-off evidence.** Its "floor" output is even weaker evidence than the 150-class model's.
4. **The 512 vs 257 "mismatch" is two different artifacts, and each contract is right for its own file.**
   - The MLCommons files are 512² in, with an argmax class map out.
   - Our self-converted TF-zoo file is 257² in, with logits `[1,65,65,151]` out.
   - Codex's probe matches the MLCommons files. It must never be pointed at our 257 file, and the reverse is also true.
5. **Problems found in the probe and docs** (§5): a logits branch with 31 channels instead of 32, stale "unmapped" wording, a centred letterbox where the reference pads at the top-left, and 0-based IDs in my own report 04.

---
## 1. MOSAIC R4: licence and provenance
| Item | Finding | Source |
|---|---|---|
| Exact artifact | `vision/mosaic/models_and_checkpoints/R4/mobile_segmenter_r4_quant_argmax_uint8.tflite`, 1,978,000 B, SHA-256 `da8b3822…6a3c`. **Matches Codex's pin.** Siblings: `_argmax_f32.tflite` (7,510,540 B, `dc49c919…871d`), frozen graph, ckpt-200000 | [mobile_open R4](https://github.com/mlcommons/mobile_open/tree/main/vision/mosaic/models_and_checkpoints/R4) (main @ `404e4c2`, 2026-10-15) |
| Licence (code + weights) | `vision/mosaic/LICENCE.md`: "MLCommons Association License … reproduces without alteration the terms of the Apache License Version 2.0". The repo root `LICENSE.md` is also Apache-2.0. There is no separate weights licence, so the files in that folder are covered | [LICENCE.md](https://github.com/mlcommons/mobile_open/blob/main/vision/mosaic/LICENCE.md) |
| Redistribution in APK | **Allowed** under Apache-2.0 §4. Requirements: (a) ship a copy of the licence; (b) keep attribution notices; (c) mark the file as modified if we change or requantise it. There is no NOTICE file in the folder. Put an entry in `THIRD_PARTY_NOTICES` / `docs/OPEN_SOURCE_LICENSES.md` | Apache-2.0 §4 |
| Repo caveat | `mobile_open/CONFIDENTIALITY.md` asks readers not to share the contents "until the publication of MLPerf Mobile v3.0 results". Those results were published on **2023-04-05**, and the repo is public. Low risk, but cite it in the notices | [CONFIDENTIALITY.md](https://github.com/mlcommons/mobile_open/blob/main/CONFIDENTIALITY.md), [MLCommons 2023-04-05](https://mlcommons.org/2023/04/mlperf-inference-delivers-power-efficiency-and-performance-gains/) |
| Authors / architecture | Google Research (Weijun Wang, Andrew Howard). Backbone is MobileNet Multi-Hardware, **pretrained on ImageNet**, then trained for segmentation | [arXiv 2112.11623 §6.1–6.4](https://arxiv.org/abs/2112.11623) |
| Training data | "We follow the same protocol of MLPerf v1.0 by training and evaluating with the **top-31 classes** instead of the original 150", at 512×512 single scale. The dataset is ADE20K **SceneParsing / ADEChallengeData2016** (20,210 train / 2,000 val, 150 classes). It is *not* the full ADE20K (3,000+ classes) | [MOSAIC README](https://github.com/mlcommons/mobile_open/blob/main/vision/mosaic/README.md), paper §6.4 |
| How Top-31 is built | MLCommons' DeepLab training code: `num_classes=32, ignore_label=0` (`datasets/data_generator.py:77-78`); `label >= num_classes → ignore_label` (`input_preprocess.py:109-113`); eval `gtLabel_[gtLabel_ >= 32] = 0` (`segmentation_inference_eval_seg_tflite.py:128`). The mobile app's eval scores c = 1..31 and skips 0 (`mobile_app_open/flutter/cpp/datasets/ade20k.cc:153-176`) | [mobile_open/vision/deeplab](https://github.com/mlcommons/mobile_open/tree/main/vision/deeplab/models_and_code), [mobile_app_open ade20k.cc](https://github.com/mlcommons/mobile_app_open/blob/master/flutter/cpp/datasets/ade20k.cc) |
| Published quality | 60.10 % mIoU on ADE20K-Top31 and 116 ms CPU on Pixel 4, per the README. MLPerf Mobile lists MOSAIC with "ADE20K (32 classes, 512x512)", 32-class mIoU 59.8. These are **published numbers, not Redmi numbers** | README, [MLPerf Mobile](https://mlcommons.org/benchmarks/inference-mobile/) |

**The MLCommons DeepLab files Codex used** (`mobile_models/v0_7/tflite/deeplabv3_mnv2_ade20k_{float,uint8,int8}.tflite`; float `12a2141f…afc0` and uint8 `2d58c681…3b5f`, both matching Codex's pins):
- The repo `LICENSE.md` is Apache-2.0, and the README says "Public repo, used by the mobile app to download models".
- **Provenance discrepancy.** The `v0_7/tflite/README.md` says the float file was converted from the TF-zoo `deeplabv3_mnv2_ade20k_train_2018_12_03` checkpoint with `--input_shape=1,513,513,3`. The actual file has a **512** input and **32**-channel logits, not 151.
- So it is most likely the MLCommons **Top-31 fine-tune** (`mobile_open/vision/deeplab`, "32 classes, 512x512, OS16", fine-tuned from that TF-zoo checkpoint). That folder is under the same MLCommons Apache-2.0 licence and carries an `INDEPENDENT_WORK.md`.
- Licence verdict either way: **Apache-2.0**, with the ADE20K and ImageNet data caveat.

## 2. ADE20K terms: what they mean for us
Sources: the [ADE20K terms page](https://ade20k.csail.mit.edu/terms/) and the [sceneparsing LICENSE](https://github.com/CSAILVision/sceneparsing/blob/master/LICENSE) (BSD-3-Clause, MIT CSAIL 2019).

**What the terms say**
- **Images:** "MIT, CSAIL does not own the copyright of the images". The Researcher "shall use the Database only for **non-commercial research and educational purposes**". The Researcher also indemnifies MIT.
- **Software and annotations:** BSD-style. Redistribution must keep the copyright notice and the disclaimer, with no endorsement.
- **Trained weights:** the terms say **nothing** about them. They bind whoever downloaded the database (Google/MLCommons), not us. We never accept the ADE20K terms because we never download the images.
- The sceneparsing README separately says *CSAIL's own* released models "can be used for research only". That applies to CSAIL's models, not to MOSAIC or the MLCommons DeepLab. It still shows what the dataset authors intend.
- **ImageNet** (MOSAIC backbone pretraining) has similar non-commercial research/education terms.

**Verdict for the graduation project (non-commercial, educational)**
- **OK** to bundle MOSAIC R4 or the MLCommons DeepLab in the APK. The same goes for our TF-zoo 257 conversion.
- Conditions: (a) Apache-2.0 text and attribution in `THIRD_PARTY_NOTICES`; (b) a line saying "trained on ADE20K SceneParsing (MIT CSAIL; images non-commercial research/education terms) and ImageNet"; (c) no training on or redistribution of ADE20K images ourselves.
- Codex's line "ADE20K-derived weights are not approved for redistribution" (`DEEPLAB_REDMI_STATIC_PROBE_2026-10-19.md:31`) is stricter than the sources require for this use.
- Practical note: **PROJECT1 is public**. Committing `.tflite` files there counts as public redistribution. Apache allows it, but it is cleaner to keep binaries out of git and fetch them with a hash-pinned download script. Either way, add the notice.

**What changes for commercial use (Play Store with ads or paid, a sold product, or a company)**
- The weights themselves stay Apache-2.0, which does allow commercial use.
- **The risk sits on the data side.** The copyright holders of the ADE20K and ImageNet images never licensed commercial use. Whether model weights are a "derivative" of training images is unsettled law.
- Before any commercial release: get a legal opinion, or ask MIT CSAIL, or replace the model with one trained on commercially licensed data. Treat this like the SegFormer and Mapillary/Cityscapes NC cases in report 04, but at lower risk, because the code and weights licence here is permissive.
- *This is a licence reading, not legal advice.*

**Cross-check with report 04**
- Report 04 also said: tensorflow/models Apache-2.0, weights "Apache-2.0 assumed", ADE20K images NC, fine for graduation, flag before commercial use. **That is consistent with this report.**
- Report 04 needs two corrections:
  - The class-ID list at its line 39 and in WALK-0 step 2 is **0-based** (SegFormer/HF `id2label`). The model it recommends (our 257 TFLite) uses **1-based** channels. See §3.
  - It did not cover the MLCommons/MOSAIC Top-31 family.

## 3. Class-ID table, checked against the official list
Source: [`CSAILVision/sceneparsing/objectInfo150.csv`](https://github.com/CSAILVision/sceneparsing/blob/master/objectInfo150.csv) (commit `e202229`). README: "annotations masks contain labels ranging from 0 to 150, where 0 refers to 'other objects'. We do not consider those pixels in our evaluation." The official list is **1-based**, and 0 means other/ignore.

| Class | Official ADE150 Idx (1-based) = annotation PNG value = **our 257/513 TFLite logit channel** | 0-based (HF/SegFormer `id2label`, mmseg `reduce_zero_label`, report 04 line 39) | **MOSAIC R4 / MLCommons DeepLab (Top-31)** |
|---|---|---|---|
| ignore / other | 0 | — (255 = ignore) | 0 (never predicted; see below) |
| wall | 1 | 0 | **1** |
| floor, flooring | 4 | 3 | **4** |
| road, route | 7 | 6 | **7** |
| grass | 10 | 9 | **10** |
| sidewalk, pavement | 12 | 11 | **12** |
| earth, ground | 14 | 13 | **14** |
| door, double door | 15 | 14 | **15** |
| rug, carpet, carpeting | 29 | 28 | **29** |
| path | 53 | 52 | **absent → would be 0** |
| stairs, steps | 54 | 53 | **absent** |
| stairway, staircase | 60 | 59 | **absent** |
| escalator | 97 | 96 | **absent** |
| step, stair | 122 | 121 | **absent** |

The full Top-31 list (IDs 1..31): wall, building, sky, floor, tree, ceiling, road, bed, windowpane, grass, cabinet, sidewalk, person, earth, door, table, mountain, plant, curtain, chair, car, water, painting, sofa, shelf, house, sea, mirror, rug, field, armchair.

**Box label check** (`label_sanity_06.py`, data in `reports/box_seg_label_sanity_06.json`; 5 public images, no timing)

What the IDs come out as:

| Image | DeepLab float, uint8, MOSAIC uint8, MOSAIC f32 |
|---|---|
| Hospital corridor | bottom-centre ROI is 100 % id 4 (floor); id 1 (wall) and id 6 (ceiling) elsewhere |
| bus.jpg | 2 building, 7 road, 13 person, 21 car |
| Curb ramp | 7 road ≈ 97 %, 12 sidewalk (the same sidewalk→road failure as in report 04) |

This confirms the 1-based mapping.

Safety findings from the same run:
- **Stairs image:** the ROI came out as wall, floor and **chair (id 20)**. No stair class exists.
- **ID 0 was predicted on 0.0 % of pixels** in all 5 images, for both DeepLab float and MOSAIC uint8 (`label_sanity_06_id0.py`). "Other" is never output, so unknown objects get forced into one of the 31 classes.
- The MLCommons DeepLab uint8 output **id 31 (armchair)** on the stairs image. This shows that ID 31 really occurs.

**Codex's Redmi logs, re-read with the verified mapping** (one owner photo):
- **MOSAIC:** full frame = wall 60.5 %, floor 27.6 %, curtain 5.0 %, door 2.6 %, person 2.0 %. Lower-centre = floor 97.5 %, wall 2.5 %.
- **DeepLab float:** lower-centre floor 94.8 %. The earlier "class_13 1.06 %" was **person**.
- **DeepLab uint8:** lower-centre floor 39.1 %, wall 38.7 %, **sea (27) 12.5 %, water (22) 9.8 %**. So the uint8 model called part of the indoor floor "sea/water".
  - On the box, uint8 agreed closely with float on all 5 public images, so the on-device divergence is **unexplained**.
  - Next step: replay that same still through the box interpreter and compare. That needs the owner's OK to copy the photo, because it is a private room image.

## 4. Tensor contracts (inspected on the box with `ai_edge_litert`)
| File | Input | Output | Notes |
|---|---|---|---|
| MLCommons `deeplabv3_mnv2_ade20k_float.tflite` | `[1,512,512,3]` float32, [-1,1] | `ArgMax [1,512,512]` int32, 0..31 | logits `ResizeBilinear_1 [1,512,512,32]` before argmax |
| MLCommons `…_uint8.tflite` | `[1,512,512,3]` uint8, scale 0.0078125, zp 128 | same | |
| MLCommons `…_int8.tflite` | `[1,512,512,3]` uint8, scale 0.007843, **zp 127** | same | not used by Codex |
| MOSAIC `r4_quant_argmax_uint8` | `PreprocessedImageTensor [1,512,512,3]` uint8, scale 0.007843, zp 128 | `SemanticPredictions [1,512,512]` int32, 0..31 | logits `resize_logits [1,512,512,32]` |
| MOSAIC `r4_argmax_f32` | `[1,512,512,3]` float32 | same | |
| **Ours** `deeplabv3_mnv2_ade20k_257_f32/_dynint8` | `input [1,257,257,3]` float32, [-1,1] | `logits/semantic/BiasAdd [1,65,65,151]` float32 | channel 0 = other, channel c = ADE150 Idx c; argmax + upsample in app |
| Ours `…_513_f32` | `[1,513,513,3]` | `[1,129,129,151]` | |

**Verdict on Cursor's "512/int32 vs 257/logits" finding (BRIDGE_TO_GPT CURSOR_REVIEW #1)**
- The finding was correct as a warning: it is *a different artifact*, not a broken probe.
- For the MLCommons and MOSAIC files, the probe contract (512, INT32 class map) is **correct**, and Codex confirmed it on the Redmi.
- For our 257 file, the contract in `convert_deeplab_ade20k.py` is correct: 257, float logits, 151 channels, 1-based.
- `FloorSegmenterModelContract.kt` expects **256×256**. That matches neither file, so update it before anything is wired up.

## 5. Concrete fixes for Cursor/Codex (none applied by me)
1. **`DeepLabDiagnosticActivity.kt:313` `CLASS_COUNT = 31`.** It is used in the logits branch at `:157`, `:226` and `:249`. The Top-31 models have **32** channels (0..31), so a logits variant would be rejected or mis-argmaxed. Set the logits channel count to `Ade20kTop31ClassIds.FOREGROUND_CLASS_COUNT + 1` (32). The class-map validation at `:133` is now right: it uses `Ade20kTop31ClassIds.isValid` = 0..31, fixed at 13:45. Before that fix, `0 until 31` would have thrown `RUN_FAILED` on any armchair pixel (id 31).
2. **`Ade20kTop31ClassIds.kt:13-46`.** I **verified** it against objectInfo150 IDs 1..31, and every name matches. Update its KDoc and the log tag at `DeepLabDiagnosticActivity.kt:147` (`mapping=unverified-top31-id-only`) to cite this report. Add a test asserting that the IDs for stairs/stairway/step/escalator/path **do not exist** in Top-31, i.e. that no stair label can ever be produced.
3. **Safety rule:** with a Top-31 model (MOSAIC or MLCommons DeepLab), `floor` (4) or `rug` (29) **must never count as walkable on its own**, and an absent stairs signal must never count as "no stairs". Drop-offs have to come from depth/ARCore (report 03). Never count road (7), grass (10) or earth (14) as walkable. If segmentation is later needed as stairs evidence, only a 150-class model can do it (our 257 TF-zoo conversion: channels 54/60/122/97).
4. **Preprocessing parity.** The MLPerf reference (`deeplab.input_preprocess.preprocess_image_and_label(..., 512,512,512,512)`) resizes the long side to 512 and pads **bottom/right** (offset 0,0) with the mean pixel. The probe letterboxes **centred** (`letterbox()`, `:163-175`). Either pad at the top-left to match the reference, or document that it doesn't. Region stats such as "lower-centre" also shift with the padding.
5. **Docs to correct:**
   - `DEEPLAB_UINT8_REDMI_AB_SETUP_2026-10-19.md:33-36` and `:97-102`, `KNOWN_ISSUES.md:8` (FLOOR-20261018), `DECISIONS.md`: replace "IDs unmapped" with "verified Top-31 = ADE150 Idx 1..31; no stairs class".
   - `DEEPLAB_REDMI_STATIC_PROBE_2026-10-19.md:28-33`: soften "not approved for redistribution" to the §2 verdict.
   - `PHASE2_SEGMENTER_CANDIDATE_BENCHMARK.md:63` ("Typically 257²; ADE20K 150 classes"): true only for the TF-zoo conversion. Add a row for the MLCommons Top-31 512² files.
6. **Report 04 (mine):** line 39 and WALK-0 step 2 list 0-based IDs. For the recommended 257 TFLite, use **1-based** channels: wall 1, floor 4, road 7, grass 10, sidewalk 12, earth 14, door 15, rug 29, path 53, stairs 54, stairway 60, escalator 97, step 122. `research/convert_deeplab_ade20k.py:4` already states it correctly. My box `sanity_seg.py` subtracted 1 (`argmax-1`), so report 04's results are unaffected.
7. **Licence notices:** add MOSAIC R4 and MLCommons DeepLab rows (with SHA-256) to `docs/OPEN_SOURCE_LICENSES.md`. Text: "MLCommons Association License (Apache-2.0); trained on ADE20K SceneParsing Top-31 (MIT CSAIL; images non-commercial research/education) + ImageNet; OK for this non-commercial graduation project; commercial release needs legal review". Keep `.tflite` out of the public PROJECT1 repo, or include the notice if they are committed.
8. **Measurements still owed by Codex** (not done here): a live-camera MOSAIC NNAPI capture-to-result benchmark following `reports/02-redmi-benchmark-protocol.md`, plus a labelled scene set with stairs-down and a rug. Codex's 17.4 ms NNAPI figure is model-only static replay and must stay labelled that way.

## 6. Files (box)
- Repos: `/workspace/triguide/repos/mlc/{mobile_open (404e4c2), mobile_models (1f99633), mobile_app_open, sceneparsing (e202229)}`
- Scripts: `label_sanity_06.py`, `label_sanity_06_id0.py`. Data: `reports/box_seg_label_sanity_06.json`
