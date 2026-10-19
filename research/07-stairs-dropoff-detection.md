# 07 — Stairs-down, drop-off and curb detection on the Redmi (TriGuide AI)

Grok Bot, 2026-10-19 14:52 MYT (UTC+8). Target: Redmi Note 12 Pro 5G **22101316G** ("ruby"), Dimensity 1080 / Mali-G68, Android 14, offline, no INTERNET permission, phone possibly chest-mounted.
Builds on reports 03 (depth), 03-dropoff (ARCore note), 04 (floor seg), 06 (Top-31 labels) and Codex's `docs/ADE20K_OFFLINE_EVALUATION_2026-10-19.md`, `KNOWN_ISSUES.md` STAIRS-TOP31-20261019, `RELATIVE_DEPTH_EVIDENCE_CHECKPOINT_2026-10-19.md`, `RelativeDepthEvidence.kt` (all read only, nothing written on the PC).

**Safety rule used throughout:** a stair / drop-off cue can only **raise HOLD**. No cue, or a "no drop seen" result, is **never** evidence the way is safe. State stays UNKNOWN; the cane stays mandatory.

**Provenance labels**
- **BOX** = run by me on the x86 box (MiDaS small fp16 TFLite via ai-edge-litert/XNNPACK). **Not phone numbers; not walking tests.**
- **REDMI (read-only adb, me)** = read-only `adb shell` queries today; no app run, nothing installed.
- **PUBLISHED** = paper/README/vendor claim, not reproduced.
- **CODEX** = numbers from Codex's docs on the PC, not re-run by me.

---

## 1. Bottom line

| Rank | Option | Why |
|---|---|---|
| **Recommend (primary drop-off sensor)** | **ARCore Raw Depth + gravity-aligned floor plane** ("points below the floor plane ahead → HOLD") | Only on-device option with **metric depth + per-pixel confidence + gravity + camera pose**, which is what a step-down test actually needs (Cloix 2016, Pérez-Yus, Wang 2023 all use metric depth + IMU). **New today:** Google Play Services for AR **1.57.262640093 is installed on the exact 22101316G** (REDMI read-only adb). Depth support itself is still **unverified** until `isDepthModeSupported(RAW_DEPTH_ONLY)` runs on the phone. |
| **Runner-up / ship-first bonus cue** | **MiDaS v2.1 small (MIT) + "floor-line break" rule** (inverse depth of a flat floor is linear in image row; an abrupt extra drop in the corridor = possible edge → HOLD) | Already chosen in report 03, MIT, cheap. BOX prototype: caught **3/12** held-out stair-down photos and gave **2/12** false cues on flat corridors (§4). Weak recall, so it's a bonus alarm only, never a detector to rely on. |
| Later (needs our own training) | **Dedicated stair detector** trained by us with a permissive detector (EfficientDet-Lite via MediaPipe Model Maker, YOLOX, RT-DETR, MIT "YOLO" by MultimediaTechLab) on CC-BY data | Can recognise stairs as an object/scene, both up and down. No ready-made permissive Android model exists. Roboflow-hosted models are usually Ultralytics (**AGPL-3.0**). |
| Context only | IMU (gravity/pitch gating, step events) | Needed to turn depth into "height below floor" and to fail closed when the phone isn't mounted right. IMU alone only detects a step-down **after** the foot drops, which is too late. |
| Do not use for stairs | ADE20K Top-31 segmenters (MOSAIC R4, MLCommons DeepLab) | No stairs class. CODEX: 19.45 % / 15.21 % / 20.00 % of 1,799,391 stair pixels → `floor` (report 06 + Codex eval). Their `floor` is never no-stairs evidence. |

---

## 2. Option-by-option

### (a) Depth-geometry from monocular relative depth (MiDaS small)

**Method idea, with sources.** For a flat ground plane and a pinhole camera, disparity / inverse depth of the ground is a **linear function of the image row** (the "v-disparity" idea used for stereo obstacle/ground detection: Labayrade et al., IEEE IV 2002, https://doi.org/10.1109/IVS.2002.1188024). MiDaS outputs **affine-invariant inverse depth** (scale + shift unknown; Ranftl et al., TPAMI 2022, https://arxiv.org/abs/1907.01341), and an affine map keeps a line a line. So along the walking corridor, the near-floor rows should follow a straight line, and a **sudden drop below that line** (floor much farther than predicted) is a possible edge. A descending stair or curb edge shows up that way only if the model actually draws a break.
Related published work uses **metric** depth plus IMU, not monocular relative depth:
- Cloix et al. 2016: stereo depth → height above ground, then "danger / warning / safe". **>94 % of dangers separated from safe scenes, 91 % overall**, real-time, a day on a smartphone battery (PUBLISHED). https://doi.org/10.1186/s13640-016-0133-6
- ToF on a rollator, descending steps (IEEE IV 2015). https://doi.org/10.1109/ivs.2015.7225712
- Wang et al. 2023 (StairNetV2), RGB-D + IMU attitude for stair parameters. https://pmc.ncbi.nlm.nih.gov/articles/PMC9965719/
- Monocular-only: projective-chirp stair detection (Pattern Recognition Letters 2019, https://doi.org/10.1016/j.patrec.2019.03.007). It detects stair texture, not step-down height.
- I found **no peer-reviewed result showing that MiDaS-class relative depth reliably flags step-downs or curbs.** Report 03 already rated it "weak".

**Licence:** MiDaS code + weights MIT, EfficientNet-Lite3 backbone Apache-2.0 (report 03). Our rule is our own code.
**Mobile feasibility:** good. MiDaS small fp16 @256 BOX 18.7 ms (4 thr); Qualcomm w8a8 10.4 ms (report 03). PUBLISHED Pixel 8a GPU 19.9 ms. **No Redmi number.** The rule itself is one per-row median plus a scan (µs).
**Failure modes (seen on box + expected):** smooth "ramp-like" depth over real stairs (most misses, e.g. dn_14…dn_20 outdoor flights); curbs (missed 1/1); edges hidden by a balustrade or parapet; object top edges (pillar, poster stand) fire false cues (fl_11, fl_12); relative depth has no scale, so it can't tell a 2 cm lip from a 20 cm step; glossy and dark floors; per-frame normalisation tricks.

### (b) ARCore Depth / Raw Depth + plane detection

- **Device status (22101316G):** Google's ARCore list (re-parsed today, https://developers.google.com/ar/devices) still shows "Redmi Note 12 Pro — Supports Depth API" but **no row named "Redmi Note 12 Pro 5G"**. The Play catalogue (https://storage.googleapis.com/play_public/supported_devices.csv, today) maps **22101316G and 22101316C both to device `ruby`**, and 22101316C is "Redmi Note 12 Pro". **REDMI read-only adb today:** model 22101316G, device ruby, Android 14, `com.google.ar.core` **versionName 1.57.262640093** installed. Accelerometer and gyroscope features are present. Play normally only installs/updates ARCore on compatible devices, so this is **strong circumstantial evidence**, still **not proof of Depth support**. The decisive check is `ArCoreApk.checkAvailability()` = `SUPPORTED_INSTALLED` and `session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)` = true.
- **What it gives:** depth-from-motion (no ToF needed), **best ~0.5–5 m**, needs the user to move; Raw Depth comes with a confidence image (https://developers.google.com/ar/develop/depth , https://developers.google.com/ar/develop/java/depth/raw-depth). Plane detection gives horizontal planes with gravity alignment, and camera pose gives camera height above the detected floor.
- **Drop-off rule (proposed, same as report 03 §5.4):** in the corridor 0.8–2.5 m ahead, confident raw-depth points (conf ≥ ~0.3) projected to world Y **≥ 8–10 cm below the floor plane** → POSSIBLE_DROP → HOLD. "Floor plane missing ahead" or "no confident points in the corridor" → UNKNOWN (never CLEAR).
- **Licence:** ARCore SDK under the **ARCore Additional Terms of Service** plus the required privacy disclosure; Play Services for AR is closed source (https://developers.google.com/ar/develop/terms). The samples are Apache-2.0. The 1.57.0 AAR adds no INTERNET permission (report 03), but depth runs inside Google's app under Google's terms.
- **Mobile feasibility:** ARCore owns the camera, so MediaPipe must take `Frame.acquireCameraImage()` frames or use the Shared Camera API. That is a bigger integration than a TFLite model. ARCore needs arm64-v8a.
- **Failure modes:** blank, texture-less floors (depth-from-motion needs texture); standing still; very dark scenes; glossy floors; edges beyond ~5 m; tracking loss (→ must be UNKNOWN/HOLD, like Project Guideline's `STOP_REASON_TRACKING_STATE_CHANGED`); a phone that bounces on the chest.
- Published accuracy for ARCore drop-off detection: none found with numbers for this use. Must be measured.

### (c) Dedicated stair detection models / datasets

| Item | What | Licence (code / weights / data) | Mobile | Published accuracy / latency | Notes / failure modes |
|---|---|---|---|---|---|
| **StairNet** (Kurbis, Laschowski et al., Toronto) | 4-class *scene classifier*: level ground, LG→incline stairs, incline stairs, incline→LG. 515,452 chest-cam images (ExoNet, iPhone XS Max, 5 fps) | Data: IEEE DataPort (https://doi.org/10.21227/12jm-e336). DataPort terms say uploads are Creative-Commons; licence variant **to confirm on the dataset page**. Code/weights: no public repo found | TFLite iOS app | **98.4 %** accuracy; **2.75 ms on iPhone 11** (PUBLISHED, https://doi.org/10.1109/tmrb.2024.3349602 ; review https://link.springer.com/article/10.1186/s12938-024-01216-0) | **Ascending ("incline") stairs only, no stairs-down class.** Exoskeleton use. Useful as a chest-cam dataset idea, not for our hazard |
| **StairNet v1/v2/v3** (Chen Wang et al., Beihang) | Stair *line* detection (convex/concave edges), RGB or RGB-D; v3 monocular depth-aware | **CC BY-NC 3.0** (code; README) → **non-commercial only**. GitHub API shows no SPDX file. RGB-D stair dataset on Mendeley (https://doi.org/10.17632/6kffmjt7g2.1), licence not checked | PyTorch, 512² | v1 "ultra-fast" (Sci Rep 2022, https://doi.org/10.1038/s41598-022-20667-w); v3 +3.4 % IoU (https://arxiv.org/abs/2308.06715) | Robot stair *climbing* (mostly ascending views). OK for a graduation demo, **not** for a release |
| Roboflow "up-down stairs" (yolo-h2ner) | 1.3k imgs, classes downstairs / upstairs / object | **CC BY 4.0** per Roboflow page (search snippet; re-check at download) | Train our own | none for this copy | Small, unknown viewpoints. **Don't use Roboflow-trained weights** (usually Ultralytics YOLO = AGPL) |
| Roboflow "up-down stairs" (stairs-n9kkx) | 1,289 imgs, same classes | **Licence not shown** → don't use until checked | — | Roboflow page: mAP50 88.3 %, P 91.6 %, R 82.7 % (vendor-hosted model, PUBLISHED) | Same caveat |
| Roboflow "stairs" (AccessRater) | 300 imgs, down/up | Not checked | — | — | Too small |
| Roboflow-100 "road-traffic" (HF LibreYOLO) | has a `stairs` class among 12 | CC-BY-4.0 | — | — | Aerial/road imagery, not egocentric |
| Android SSD-MobileNet stairs (AIMS 2025) | Stairs up/down + pinhole distance | Paper only, no code found | Android | "Stairs Up" **98.91 %** (PUBLISHED); stairs-down number not in abstract (https://doi.org/10.1109/aims66189.2025.11229605) | Unverifiable |
| Curb: NYC curb dataset + YOLOv8-Seg (arXiv 2408.14578; Disabil. Rehabil. Assist. Technol. 2025) | ~3,000 pedestrian-view curb polygons | Model = Ultralytics (**AGPL**); dataset release **not confirmed** | real-time on wearable | P 0.84, R 0.74, IoU 0.83 on 563 test images (PUBLISHED, https://doi.org/10.1080/17483107.2025.2463541) | Outdoor NYC only |
| Curb foundation-model study (arXiv 2406.07202) | 2,133 curb images, tests foundation models | not checked | no | shows foundation models are unreliable on curbs | Supports "never trust a single model" |
| ADE20K-150 classes (our DeepLab-257, report 04) | has stairs 54 / stairway 60 / step 122 / escalator 97 (1-based) | Apache-2.0 weights; ADE20K images NC | runs | BOX: one flight `stairway` 72 %, other flight `floor`; SegFormer 91 % `floor` on stairs-down | Can **raise** HOLD when stair pixels appear; useless as "no stairs" |

**Detector licence map (GitHub API, today):** Ultralytics `ultralytics` **AGPL-3.0**; THU-MIG YOLOv10 **AGPL-3.0**; WongKinYiu YOLOv7 / YOLOv9 **GPL-3.0**; YOLOX **Apache-2.0**; RT-DETR (lyuwenyu) **Apache-2.0**; MMDetection **Apache-2.0**; MultimediaTechLab/YOLO **MIT**; LibreYOLO "NOASSERTION" (unclear); `mediapipe-model-maker` 0.2.1.4 **Apache-2.0** (EfficientDet-Lite export to TFLite, matches our current MediaPipe runtime).

### (d) IMU / tilt and phone mounting

- Android gives `TYPE_GRAVITY` / `TYPE_ROTATION_VECTOR` (fused) for camera pitch and roll (https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion). The Redmi reports accelerometer + gyroscope (REDMI adb).
- Uses: (1) **gating**: only trust floor-geometry rules when pitch and roll are inside the mount range (e.g. pitch 15–45° below horizontal, |roll| < 15°). Outside the range → UNKNOWN. (2) **Horizon row** from pitch, which tells which image rows can be floor at all. (3) With a known camera height (chest ~1.3 m) and pitch, the flat-floor inverse-depth line is *predicted* rather than only fitted, so the "floor line" check gets a second, independent reference. Project Guideline uses a similar idea, aligning monocular depth to ARCore geometry with RANSAC (`depth/depth_align_ransac.cc`, Apache-2.0). (4) Motion: depth-from-motion needs movement; pure-IMU gait/step-event detection (e.g. https://doi.org/10.3390/electronics12204319; I did not verify a stairs-down accuracy figure from it) only fires **after** the first step down, so it's useful for logging, not prevention.
- **Mounting constraints:** chest mount gives a stable pitch and sees the floor about 0.7–3 m ahead. A hand-held phone swings and often points at the horizon (no floor rows), and a pocket gives nothing. The camera must see the floor **≥ 1.5 m ahead** so a HOLD at walking speed (~1 m/s) leaves more than 1 s to react. Chest-mount bounce while walking blurs frames, so take the median over frames.

### (e) What assistive products and research do for drop-offs

| System | Drop-off handling | Licence / availability |
|---|---|---|
| **Stanford Augmented Cane** (Slade et al., Sci. Robotics 2021, https://www.science.org/doi/10.1126/scirobotics.abg6594) | 2D LIDAR (horizontal plane, 5 Hz) + camera + IMU + GPS; the paper says there are **no dedicated drop-off / head-height sensors** (an ultrasonic could be added). The cane tip itself still finds drop-offs. In the demo the user switched modes manually after going down stairs | Repo https://github.com/pslade2/AugmentedCane: README says "completely open-source", but **no LICENSE file** (GitHub API `license: null`) → treat as all rights reserved; ideas only |
| **Project Guideline** (Google, Apache-2.0) | Outdoor line-following with ARCore + segmentation + monocular depth + occupancy map. A keyword search of the C++ found **no stair / curb / drop-off logic**; its stop reasons are tracking/line based (`proto/control_signal.proto`). Uses a sighted spotter | Apache-2.0 |
| **Glide (Glidance)** | Two stereo-depth cameras + sensors on a wheeled guide. Vendor FAQ: detects stairs up/down and **brakes before them**; curbs/drop-offs/potholes → steers around or stops with voice + haptics; "stops before the down-curb" at crossings (https://www.glidance.io/frequently-asked-questions) | Proprietary; vendor claims, no published accuracy |
| Smart canes (WeWALK etc.) | Ultrasonic aimed at chest/head height obstacles; drop-offs are left to the physical cane | Proprietary |
| Research rollators | Stereo or ToF depth → "danger/warning/safe" (Cloix 2016; IEEE IV 2015) | Papers |

**Pattern:** every system that claims drop-off detection uses **metric depth (stereo / ToF / LiDAR / depth-from-motion) plus gravity**, and the reaction is always **brake / stop / warn**, never "path clear". A camera-only phone app is the weakest version of this, which is why ARCore is the primary recommendation.

---

## 3. Codex docs: what's already covered (no duplication)

- Codex covered the ADE20K Top-31 stair → floor statistic (2,000 val images), terms, and taxonomy. I reuse its numbers (CODEX) and did not redo them.
- `RELATIVE_DEPTH_EVIDENCE_CHECKPOINT_2026-10-19.md`: a synthetic "possible drop" case exists, and the Redmi closed-door run gave 0 HOLD. No real stair data, no ARCore probe yet.
- **Review finding on `RelativeDepthEvidence.kt` (0.9.21 tree, read only):** `POSSIBLE_DROP` fires when the **middle band is NEARER** (≥ 0.55) than the lower band (≤ 0.35), with "larger = closer". In a forward-facing view the near floor is at the **bottom**, and a drop makes the area **beyond** the edge **farther**, so this condition describes something rising in front (or an inverted image), not a drop. BOX check: I re-implemented the rule, with per-frame min-max normalisation as my assumption since the app's normaliser isn't wired yet. On all **40** images (dev + held-out), it returned **DIAGNOSTIC_NO_HAZARD 40/40**: 0 drops and 0 walls, including every stair photo (`reports/box_stairs_codex_rule_07.json`). The synthetic test passes because the synthetic map was built to match the rule. → Replace it with the floor-line-break rule (§6).

---

## 4. Box prototype: MiDaS small floor-line-break cue (BOX only)

Script `stairs_depth_proto_07.py` (SHA-256 `23943667…1b84`). Model `midas_small_256_fp16.tflite` (SHA-256 `bec9bce7…0ca`), ImageNet normalisation, 256×256 squash resize.
Rule v1 (thresholds **frozen before** the held-out run): corridor columns 0.35–0.65 → per-row median inverse depth, normalised by the bottom 8 % rows. Then k = 4 % of rows, G(y) = s(y+k) − s(y). The baseline is the median G over the near band, then a running median over rows already scanned. Scanning from row 0.97 up to 0.40, flag **POSSIBLE_DROP** at the first row where G ≥ 2.5 × baseline **and** G ≥ 0.15. A floor gradient that is missing or inverted → FLOOR_NOT_CONFIRMED (→ UNKNOWN).
v0 (a straight-line residual on a fitted floor line) was tried first and **failed**: it called almost every stair photo "wall", because MiDaS bends the far field. It's recorded here as a negative result.

Images: Wikimedia Commons, licences per file in `models/stairs07*/SOURCES.json` (PD / CC0 / CC BY / CC BY-SA). Kept on the box only, not redistributed, not in any repo. Selection for the held-out set was made by eye **before** running v1. Excluded: off-topic, aerial, Muybridge plates, an escalator, and one exact duplicate of a dev image (dn_05 = down_03).

| Set | Stairs-down photos | POSSIBLE_DROP (recall) | Flat corridors | False POSSIBLE_DROP | Other |
|---|---|---|---|---|---|
| Dev (rule designed while looking at these) | 8 | 3 (down_02, 03, 05) | 6 | 0 | down_09 (stairwell from above) → FLOOR_NOT_CONFIRMED; curb 0/1; canal-lock photo not counted |
| **Held-out (frozen rule)** | **12** | **3 / 12 = 25 %** (dn_02, dn_08, dn_13) | **12** | **2 / 12 = 17 %** (fl_11, fl_12: pillar / poster-stand edges in a corridor) | — |

Raw: `reports/box_stairs_proto_07.json` (v0), `box_stairs_v1_dev_07.json`, `box_stairs_v1_heldout_07.json`, `box_stairs_codex_rule_07.json`. Depth PNGs in `models/stairs07/out/`.
**How to read this:** a tiny set of internet photos, not chest-mounted, varied pitch and height, no walking, no phone. It shows the cue is **possible but weak**. It misses three out of four flights, so it may only ever add HOLD, and its silence means nothing. The false cues come from object edges, which in practice are obstacles, so HOLD is an acceptable result there.

---

## 5. Licence risks (summary)

1. **AGPL-3.0:** Ultralytics YOLOv5/v8/v11 (and most Roboflow-trained weights), YOLOv10. **GPL-3.0:** YOLOv7/YOLOv9 (WongKinYiu). Bundling any of these makes the APK (A)GPL. Avoid.
2. **CC BY-NC 3.0:** StairNet v1–v3 (Chen Wang) code. Demo-only, never in a release.
3. **ADE20K images NC** (every ADE20K-trained model; reports 04/06). Same caveat as before.
4. **ARCore Additional ToS** + mandatory privacy disclosure; closed Play Services for AR.
5. **No licence:** Stanford Augmented Cane repo (`license: null`), VisionAId (earlier). Don't copy code.
6. **Roboflow datasets:** licence per project; only the yolo-h2ner set is CC BY 4.0 (attribution required). Re-check at download.
7. **Permissive and OK:** MiDaS (MIT), our rule code, YOLOX / RT-DETR / MMDetection (Apache-2.0), MultimediaTechLab YOLO (MIT), MediaPipe Model Maker (Apache-2.0), Project Guideline (Apache-2.0, ideas such as RANSAC depth alignment).

---

## 6. Narrow Cursor slice — **STAIR-0: "Possible step down → HOLD" (HOLD-only)**

**Prerequisite:** the Depth v0 MiDaS wiring from report 03 §6 (depth thread at 3–5 Hz, GPU→CPU fallback, `RelativeDepthFrame` produced from camera frames). STAIR-0 does **not** add ARCore, a new model, the INTERNET permission, or any clearance language.

Scope:
- **A. Fix the drop rule** in `RelativeDepthEvidenceAnalyzer`: replace the current middle-vs-lower `POSSIBLE_DROP` condition with the floor-line-break rule (§4 v1: corridor x 0.35–0.65, per-row median, k = 4 % rows, jump ≥ 2.5 × running baseline and ≥ 0.15 of the near-floor value, scan rows 0.97→0.40). Missing or inverted floor gradient → `FLOOR_NOT_CONFIRMED` (UNKNOWN).
- **B. IMU gate:** a new pure `MountPoseGate(pitchDeg, rollDeg, ageMs)` from `TYPE_GRAVITY`. Outside pitch 15–45° below horizontal, |roll| > 15°, or sample older than 200 ms → depth drop rule is not evaluated → `UNKNOWN` with reason `POSE_OUT_OF_RANGE`.
- **C. Temporal latch:** `POSSIBLE_DROP` in ≥ 2 of the last 3 depth frames → HOLD, latched for ≥ 3 s after the last positive. Clearing the latch returns to **UNKNOWN**, never STRAIGHT/CLEAR.
- **D. Fusion and speech:** `risk = max(detector, segmentation-stairs-pixels, depth)`. One spoken cue per episode, interrupting lower-priority speech: EN "Possible step down. Stop." / AR «ممكن في درجة نازلة. أقيف.». Same ≥ 30 s episode suppression as the obstacle cue. STOP button still wins.
- **E. Optional, same slice if trivial:** if the ADE20K-150 diagnostic segmenter is running, stairs/stairway/step/escalator pixels (1-based 54/60/122/97) ≥ 2 % of the lower-centre corridor → same HOLD. Top-31 models are **excluded** (no stairs class).
- **F. Logging:** per depth frame, `TriGuideDrop` line with frameId, pose, baseline, max jump, row, state, latency. No images saved.

Acceptance criteria:
1. **JVM tests (synthetic grids):** linear floor gradient → `DIAGNOSTIC_NO_HAZARD` and nav state stays `UNKNOWN` (never CLEAR); floor gradient with an abrupt extra drop at row 0.6 → `POSSIBLE_DROP`; the **old inverted case** (middle nearer than lower) → **not** `POSSIBLE_DROP` (regression test for §3); inverted/flat gradient → `FLOOR_NOT_CONFIRMED`; pose out of range / stale pose → `UNKNOWN`; 2-of-3 latch and 3 s hold timing with a fake clock; latch release → `UNKNOWN`; depth `POSSIBLE_DROP` can't be lowered by detector or segmentation "floor".
2. **Replay test:** a JVM fixture with **at least six real MiDaS output grids** (exported from the box prototype as 64×64 float CSV: 3 held-out hits, 1 miss, 2 flats), each giving the same state as the box JSON. I can export these on request.
3. **Clearance-language ban test** passes. No "clear", "safe", «فاضي», «آمن» in any new string.
4. **Manifest:** still no `android.permission.INTERNET`. No new model asset beyond MiDaS small (already licensed in report 03). `OPEN_SOURCE_LICENSES.md` unchanged except a note that the rule is our own code.
5. **Redmi latency (report 02 protocol):** depth + drop rule p50 / p90 at 3–5 Hz, accelerator logged, end-to-end detector FPS not below the 0.9.21 baseline (~8–10 FPS) by more than 20 %. Labelled "Redmi measured".
6. **Flat-floor false-HOLD check (Redmi, owner or Codex):** 10 slow walks of 5 m down a known flat corridor, chest mount. Count `TriGuideDrop` HOLDs and report the rate. No threshold change without logging it.
7. **Owner real stairs-down test (Redmi, with a sighted helper; safety first):**
   - Only with a **sighted helper** present who stands **between the owner and the top step**, eyes **open**, no blindfold. The owner holds the cane and the helper's arm or the handrail. Daylight, dry stairs, at least one handrail.
   - Phone chest-mounted at the test pitch. Start **3 m** from the top edge and walk slowly (≤ 0.5 m/s). The helper stops the owner at a **taped line 1.0 m** from the edge whatever the app says, and nobody goes closer than 1 m.
   - 10 approaches straight on, 5 at about 30° angle, plus 5 approaches to a curb (or a single step) if one is available.
   - Record for each: HOLD heard (yes/no, by ear), the distance on the floor where it was first heard (helper reads the tape), and the logcat line.
   - **Pass for STAIR-0 = the procedure runs safely and the numbers are recorded honestly.** It's not a detection-rate target. A miss is an expected, documented limitation (box recall was 25 %), not a failure to hide. Any HOLD first heard **closer than 1.5 m** is logged as "too late to rely on".
   - Nobody walks down the stairs as part of the test, and the app's silence never counts as permission to go on.
8. Docs: KNOWN_ISSUES entry "STAIRS-MONO-DEPTH: monocular drop cue recall is low; cane mandatory". Nothing is marked "eyes-closed ready".

**Next slice after STAIR-0 (separate):** **ARCORE-0 probe** (debug screen, log only): `checkAvailability`, `isDepthModeSupported(RAW_DEPTH_ONLY)`, raw-depth FPS and confidence histogram on the 22101316G. If supported, **STAIR-1**: ARCore floor-plane "points ≥ 8–10 cm below floor ahead → HOLD", with the same owner test, compared against STAIR-0 on the same stairs.
