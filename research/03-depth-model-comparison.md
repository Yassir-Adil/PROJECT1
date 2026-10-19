# 03 — Depth options for walls, obstacles and drop-offs (TriGuide AI, Redmi Note 12 Pro 5G)

Grok Bot, 2026-10-18 15:11 MYT (UTC+8). Target: Redmi Note 12 Pro 5G, model 22101316G ("ruby"), Android 14,
MediaTek Dimensity 1080 (Mali-G68 GPU). On-device only; the app has no INTERNET permission.
Companion to `03-dropoff-floor-depth-arcore.md` (13:20). One fact in that note is corrected below (ARCore listing).

**Provenance labels used everywhere below**
- **BOX-CPU (measured by me)** = Intel Xeon x86_64 box, 8 vCPU, ai-edge-litert 2.3.0 (XNNPACK) or onnxruntime 1.31.0, random input,
  3 warm-up + 20 timed runs, median/p90. **These are not phone numbers and cannot be used as Redmi latency.**
  Raw data: `reports/box_cpu_bench_depth.json` (new) and `reports/box_cpu_bench.json` (earlier). Script: `bench_depth_cpu.py`.
- **PUBLISHED** = numbers from a model card, README or paper, on the device they name. Not reproduced by me.
- **Redmi numbers: none exist yet.** Nothing in this report was run on the phone.

---

## 1. Bottom line

| Rank | Option | Why |
|---|---|---|
| **Use now (spike)** | **MiDaS v2.1 small @256 (LiteRT)** | MIT weights + code, GPU-clean TFLite already exists (fp16 33.5 MB, or Qualcomm w8a8 17.8 MB), fastest DNN in my box test (10–19 ms / 4 threads), plenty of published phone numbers. Relative depth only → HOLD-only sensor. |
| **Runner-up / parallel spike** | **ARCore Raw Depth API** | The only option here that gives **metric depth + per-pixel confidence + a floor plane**, which is what a real drop-off test needs. But the exact model 22101316G is **not on Google's list by that name** (see §3), it needs Google Play Services for AR, it owns the camera, and it has its own Terms of Service. Must be proven on the phone first. |
| 3rd | Depth Anything V2 Small (relative, Apache-2.0) | Clearly better depth quality than MiDaS small in the literature, but 3–4× slower than MiDaS on box CPU at the same size and the LiteRT card warns fp16 GPU gave "substantially incorrect" maps. Keep as the upgrade path if MiDaS is too noisy. |
| Keep as benchmark only | Project Guideline `depth.tflite` (Apache-2.0) | Smallest (10.8 MB) and gives inverse *metric* depth after calibration, but its own model card says it was trained outdoors and "may fail … indoor". |
| Not for the phone | DA2-Metric-Indoor-Small, MiDaS v3.1 LeViT/Swin2-T, Metric3D v2 ViT-S, DA3-Small, Apple Depth Pro | Too slow/large, no mobile export, or licence unclear (details §2). |

**MediaPipe** has no depth-estimation task (Tasks Vision covers detection, segmentation, landmarks, etc.), so there is nothing to wait for there:
https://ai.google.dev/edge/mediapipe/solutions/guide . LiteRT is the runtime; models come from the options below.

---

## 2. Comparison table

| Option | Code licence | Weights licence | Mobile export available | Size | Output | Published mobile latency (source) | BOX-CPU (me) 4 thr / 1 thr | Walls | Drop-offs |
|---|---|---|---|---|---|---|---|---|---|
| **MiDaS v2.1 small** (EfficientNet-Lite3, 256²) | MIT (https://github.com/isl-org/MiDaS/blob/master/LICENSE) | MIT (Intel ISL); backbone Apache-2.0 | TFLite fp16 NHWC: https://huggingface.co/litert-community/MiDaS-small ; TFLite FP32 + **w8a8**, ONNX: https://huggingface.co/qualcomm/Midas-V2 (v0.64.0) | fp16 33.5 MB; Qualcomm FP32 66.3 MB, w8a8 17.8 MB | relative inverse depth | Pixel 8a GPU (TFLite OpenCL, benchmark_model) **19.9 ms**; same card says XNNPACK CPU *declined* the fp16 graph on the phone (litert-community card). OnePlus 8 GPU **22 FPS**, CPU 6 FPS (MiDaS mobile README, https://github.com/isl-org/MiDaS/tree/master/mobile). Qualcomm NPU 0.5–5 ms (Snapdragon only; irrelevant to Dimensity). | fp16: **18.7 / 69.1 ms**; Qualcomm FP32 TFLite: 18.4 / 70.5; **Qualcomm w8a8: 10.4 / 25.7**; ONNX FP32: 13.5 / 47.9 | Good: a blank wall filling the corridor is a large near region; works on texture-less walls only partly (monocular models guess) | Weak: relative depth has no scale; a step down looks like a smooth far floor. Only an *abrupt* discontinuity in the floor band is usable, and only as HOLD/UNKNOWN |
| MiDaS v3.1 LeViT-224 / Swin2-T-256 | MIT | MIT | No official TFLite; transformer ops (attention) — conversion and Mali GPU support unverified | 51 M / 42 M params (README) | relative inverse depth | Only desktop: 73 / 64 FPS on **RTX 3090** (MiDaS README table). No phone numbers found. | not run | Better than v2.1 small on benchmarks | same limits as above |
| **Depth Anything V2 Small** (relative) | Apache-2.0 (https://github.com/DepthAnything/Depth-Anything-V2) | **Apache-2.0** for Small only; Base/Large/Giant are **CC-BY-NC-4.0** | TFLite FP32 + dyn-int8 (fixed 518×686 NCHW): https://huggingface.co/litert-community/depth-anything-v2-small ; ONNX: onnx-community; TFLite/ONNX for Snapdragon: https://huggingface.co/qualcomm/Depth-Anything-V2 | 24.8 M params; FP32 99.5 MB, wi8 27.7 MB | relative (disparity-like) | Qualcomm NPU 12–70 ms @518² (Snapdragon only). No Mali/Dimensity number found. VisionAId (DA2-metric INT8, S21 Ultra CPU) ~491 ms (https://arxiv.org/html/2607.02371). | ONNX @252: **56.8 / 143.6 ms**; @266: 64.6 / 167.2; TFLite FP32 @518×686: 264.7 / 1046; wi8: 175.3 / 565.8 | Better edges/structure than MiDaS small | same as MiDaS (relative) |
| DA2-Metric-Indoor-Small (Hypersim fine-tune) | Apache-2.0 | original `.pth` repo tagged **Apache-2.0** (https://huggingface.co/depth-anything/Depth-Anything-V2-Metric-Hypersim-Small); the `-hf` copy we exported from has **no licence tag**; trained on Hypersim (dataset CC-BY-SA-3.0) → **flag: confirm before release** | self-exported ONNX 384→378 (ours) | FP32 99 MB, INT8 27 MB | metric (m), indoor synthetic | VisionAId S21 Ultra CPU ~491 ms INT8 / ~1200 ms FP32 (authors) | (earlier) FP32 @384 **95.1 / 310.1 ms**; INT8 196.9 / 602.5 | Metric distance to a wall is attractive, but metric error on a real phone camera is unknown | metric could support a height test, but needs camera height + tilt; unproven |
| **ARCore Raw Depth API** | ARCore SDK samples Apache-2.0; **SDK under ARCore Additional Terms of Service** (https://developers.google.com/ar/develop/terms) | n/a (Google service) | `com.google.ar:core` 1.57.0 (checked) | needs *Google Play Services for AR* app on phone | **metric depth + confidence image**, plus camera pose, gravity and planes | Google: best at ~0.5–5 m, needs device motion (https://developers.google.com/ar/develop/depth, raw depth https://developers.google.com/ar/develop/java/depth/raw-depth). No ms figure for this phone. | n/a | Good when the user is moving; can fail on blank texture-less walls (depth-from-motion needs texture) | **Best of all options**: floor plane + gravity → points below floor = drop-off |
| Project Guideline `depth.tflite` | Apache-2.0 (repo) | Apache-2.0 (model card v1.0_2023) | TFLite, uint8 in, 192² (`project_guideline/vision/models/depth.tflite`) | 10.8 MB | inverse metric depth after per-camera calibration (`1/(a·out+b)`) | Pixel 7: ~260 FPS Edge TPU, ~45 FPS GPU, ~5 FPS single-core CPU (model card PDF in repo) | **14.8 / 56.1 ms** | Card: "may fail … indoor scene" | outdoor-trained; unproven indoors |
| Metric3D v2 ViT-S | BSD-2-Clause (https://github.com/YvanYin/Metric3D) | weights repo has **no licence tag**; onnx-community repack says CC0 (repackager's claim) → **unclear** | ONNX only (https://huggingface.co/onnx-community/metric3d-vit-small) | ONNX FP32 150.7 MB / FP16 75.8 MB | metric + normals | none mobile found | not run | strong normals → good wall test in theory | too heavy for this phone |
| Depth Anything 3 Small | Apache-2.0 | Apache-2.0 | LiteRT fp16 GPU 896×504: https://huggingface.co/litert-community/Depth-Anything-3-Small-LiteRT | 55 MB | relative | Pixel 8a GPU **~1.8 s / image** (card) | not run | — | — too slow |
| FastDepth | MIT (https://github.com/dwofk/fast-depth) | MIT | none for Android (TVM on Jetson) | small (MobileNet) | metric, NYU-v2 only | paper: 178 fps TX2 GPU / 27 fps TX2 CPU (https://arxiv.org/abs/1903.03273) | not run | NYU-indoor only, old (2019) | weak generalisation |
| Apple Depth Pro | Apple custom licence (https://github.com/apple/ml-depth-pro/blob/main/LICENSE); HF weights tagged `apple-amlr` → **research-style terms, flag** | same | none | ~1.9 GB FP32 | metric | none mobile | not run | — | far too big |
| Distill-Any-Depth Small | MIT (HF tag) | MIT | same arch as DA2-Small → reuse DA2 export | ~99 MB FP32 | relative | none | not run | possible DA2-Small swap later | — |

Sanity check (BOX-CPU, me): MiDaS small fp16 and DA2-Small @252 on `bus.jpg` agree in ranking (Spearman 0.91) and both put
the near ground at the bottom (mean inverse depth bottom rows ≫ top rows). So both exports and their pre-processing work.

Interesting box detail: the litert-community MiDaS fp16 file **did** run on XNNPACK on x86 here, while its card says XNNPACK declined it
on the Pixel 8a. So on the Redmi, CPU fallback for the fp16 file is **unknown**; ship/keep the Qualcomm **w8a8** or FP32 file as the CPU fallback.

---

## 3. ARCore on the exact phone (22101316G)

- Google Play's official device catalogue (https://storage.googleapis.com/play_public/supported_devices.csv, checked 2026-10-18) maps
  **22101316G → "Redmi Note 12 Pro 5G" (device `ruby`)**. `22101316C` (China) is "Redmi Note 12 Pro" (`ruby`), and `2209116AG` (4G, Snapdragon, `sweet`) is also called "Redmi Note 12 Pro".
- The ARCore supported-devices page (https://developers.google.com/ar/devices, parsed 2026-10-18) lists **"Redmi Note 12 Pro — Supports Depth API"**, plus Pro+ 5G, Pro Speed, Turbo, DISCOVERY EDITION. There is **no row named "Redmi Note 12 Pro 5G"**.
- **Correction to the 13:20 note:** I earlier treated "Redmi Note 12 Pro" as covering the user's phone. That is plausible (same `ruby` hardware as 22101316C) but **not confirmed**. Certification is per Play model.
- How to settle it (on the phone, Cursor spike): `ArCoreApk.getInstance().checkAvailability(ctx)` → must be `SUPPORTED_*`; then `session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)` must be `true`. Use **AR Optional** so the app still runs without it.
- Privacy/integration notes: the ARCore 1.57.0 AAR manifest adds only `<queries>` for `com.google.ar.core` and `com.android.vending` (no INTERNET, checked). But depth runs inside the separate *Google Play Services for AR* app under Google's terms, and the app must show the ARCore privacy disclosure. ARCore owns the camera, so MediaPipe would have to be fed ARCore CPU images (`Frame.acquireCameraImage()`) or use the Shared Camera API. Bigger change than a TFLite model.

---

## 4. Checking Cursor's plan (`docs/GUIDANCE_TRUST_IMPROVEMENT_PLAN.md`, read only)

| Claim in plan | Verdict | Evidence |
|---|---|---|
| Project Guideline = ARCore pose + segmentation + monocular depth + RANSAC depth alignment + occupancy map + control/STOP + AAudio | **Accurate.** | Repo (Apache-2.0, last commit 2026-10-15): `android/arcore/*`, `vision/models/{guideline,depth}.tflite`, `depth/depth_align_ransac.cc`, `environment/occupancy_map.cc`, `guidance_system.cc` (STOP reasons), `android/audio/android_audio_output_stream.cc` (AAudio). https://github.com/google-research/project-guideline |
| (missing in plan) | **Add:** it is an *outdoor running* system following a painted purple line, on Pixel 6/7/8 with TPU, waist harness, and **a sighted spotter**; its depth model card warns indoor scenes may fail. Good architecture reference, **not** evidence that monocular depth is safe indoors. | README + depth model card PDF in repo |
| PMC10939328: centre 1–3 m usable; periphery much worse for ML; LiDAR/AR better; ML is soft evidence | **Mostly accurate, over-generalised.** Paper: "Accuracy and Usability of Smartphone-Based Distance Estimation Approaches for Visual Assistive Technology Development", IEEE OJEMB 2024. Centre: all < ±2.5 cm except CoreML ±5.2–6.2 cm at 2–3 m; periphery: CoreML ±41 cm, IR ±32 cm. | https://pmc.ncbi.nlm.nih.gov/articles/PMC10939328/ |
| (caveat to add) | iPhone 13 Pro only; the ML model was an FCRN (ResNet-50, NYU-v2) in CoreML, **not MiDaS/DA2**; one simple scene (white door at end of white corridor), static phone. It says nothing about Android, Mali GPU, drop-offs or walking. | same paper, Methods |
| "MiDaS-small (`litert-community/MiDaS-small`, ~33 MB fp16, NHWC 256²) — verified path" | Size, layout, MIT licence: **correct.** "Verified": **only by the card author** (Pixel 8a). `litert-community` is a Hugging Face community org; the `compiled_model_api/depth_estimation` sample the card mentions did **not** show in the public `litert-samples/compiled_model_api` folder listing when I checked. Not verified on a Dimensity/Mali phone. | HF card; https://github.com/google-ai-edge/litert-samples/tree/main/compiled_model_api |
| "MediaPipe Tasks has no first-class Depth Estimator" | **Correct.** | MediaPipe solutions guide |
| "ARCore Depth API only if Play Services + device support" | **Correct**, and see §3: exact model not listed by name. | |
| §2.3 "HCI literature: vertical planes = hard barriers" | **No citation given.** The idea is sound but cite something real or mark it as our design rule. Examples of real sources on depth+IMU floor/obstacle/stair detection: https://doi.org/10.1186/s13640-016-0133-6 , https://doi.org/10.1109/cscs.2017.46 | |
| Phase C "≤15–20 FPS combined" and "speak distance categories, not numbers" | Sensible. Depth does **not** need detector FPS: 3–5 Hz depth is enough for walking speed, decoupled from the detector. | |
| Phase B "full-frame dark edges → HOLD" | Risky heuristic, many false HOLDs; fine as experimental, never as a clearance source. | |

---

## 5. How depth must feed the policy (conservative, fail-closed)

Rule: **depth can only raise risk. Nothing from depth (or "free floor") can ever produce CLEAR / "path is clear".**
The best depth can give is "no depth hazard seen" → the state stays **UNKNOWN** and speech stays quiet / "go carefully, use your cane".

Per depth frame (MiDaS/DA2 inverse depth, relative):
1. **Normalise robustly**, not min–max per frame (min–max makes a wall filling the frame look like "normal"). Use fixed percentiles of a
   reference band (e.g. bottom 10 % rows = floor at the user's feet with a fixed chest/waist mount) and express the corridor relative to it.
2. **Corridor ROI**: x 0.30–0.70; "ahead" band y 0.25–0.65; "floor" band y 0.65–1.0 (tune on Redmi video).
3. **WALL_OR_LARGE_OBSTACLE** → **HOLD**: in the ahead band ≥ ~40 % of pixels are as near as (or nearer than) the floor reference
   ~1–1.5 m ahead, for ≥ 3 of the last 5 depth frames. A flat wall in front breaks the normal "floor gets farther as you go up" gradient.
4. **POSSIBLE_DROP / FLOOR_NOT_CONFIRMED** → **HOLD**: in the floor band, an abrupt row-to-row drop in inverse depth (floor suddenly much farther)
   or the floor gradient disappears. Monocular depth will miss many real drop-offs; this is a bonus alarm, not a detector to rely on.
   With ARCore: confident points ≥ 5–8 cm below the floor plane within ~2 m → HOLD; no confident floor ahead → UNKNOWN.
5. **Fail closed**: depth model not loaded, GPU+CPU both failed, frame older than ~500 ms, camera stalled, phone tilt outside mount range
   → **UNKNOWN** (never "go carefully" as if sensing worked).
6. **Fusion**: `risk = max(detector_risk, depth_risk)`. Neither path alone may lower risk. Log reason codes (`DEPTH_WALL`, `DEPTH_DROP`, `DEPTH_STALE`).
7. Thresholds above are starting guesses. They are tuned only from Redmi videos with ground truth (protocol in report 02), and every change is logged.

Known blind spots (state them to the user and in the report): glass doors/windows, mirrors, very dark rooms, thin poles, overhanging objects above the camera FOV,
stairs down at a distance, and any drop smaller than a step. The white cane stays mandatory.

---

## 6. Narrow Cursor slice: "Depth v0 — MiDaS small, HOLD-only"

Scope: add one depth sensor that can only raise HOLD for walls/large near obstacles; no new spoken clearance; no ARCore in this slice.

Files (suggested): `perception/depth/DepthEstimator.kt` (interface), `MidasLiteRtDepthEstimator.kt`, `CorridorDepthAnalyzer.kt` (pure Kotlin), fusion in PathRisk/NavigationDecision.

Acceptance criteria:
1. **Asset + licence:** `midas_small_256_fp16.tflite` (SHA-256 `bec9bce704789e504ec306196fcb0aabe90fd25c2b9d7db382339741950890ca`) and CPU fallback Qualcomm `midas` w8a8 (SHA-256 `b3f9de3ae1b2d0b1178f5097cb608b56021cda9dbfb255d14b6d4c0889fff85d`) recorded in `OPEN_SOURCE_LICENSES` (MiDaS MIT + EfficientNet-Lite3 Apache-2.0, source URLs).
2. **Accelerator honesty:** try LiteRT GPU first, then CPU; log which one actually initialised. If both fail → depth state `UNAVAILABLE` → path `UNKNOWN`, app keeps running.
3. **Never blocks detection:** depth runs on its own thread at 3–5 Hz, drops frames, never stalls the preview or EfficientDet.
4. **Unit tests (JVM, synthetic depth maps):** blank wall map → `WALL_OR_LARGE_OBSTACLE` → HOLD; normal floor gradient → `NO_DEPTH_HAZARD` → state still `UNKNOWN`, **never CLEAR**; abrupt floor discontinuity → HOLD; stale frame (> 500 ms) → `UNKNOWN`; depth-only can't lower detector risk.
5. **Clearance-language ban test still passes** (no "path is clear", "طريق فاضي", "safe").
6. **Manifest:** merged manifest still has no `android.permission.INTERNET`. No depth images saved to disk by default.
7. **Redmi measurement (report 02 protocol):** 30 s warm-up, 300 frames, median + p90 for depth inference alone and end-to-end, thermal status logged, accelerator logged. Written to `docs/TEST_RESULTS.md` labelled "Redmi measured".
8. **Owner ear tests on the Redmi:** blank wall at ~1 m and ~2 m → HOLD heard within 1 s; walking toward a closed door → HOLD; open doorway → **not** CLEAR (UNKNOWN/quiet is correct); stairs down → expected to fail, recorded as a known limitation, not claimed.
9. Nothing is marked "eyes-closed ready". The cane remains required.

Separate later spike (do not mix into v0): ARCore availability + `RAW_DEPTH_ONLY` check on 22101316G, log result only.
