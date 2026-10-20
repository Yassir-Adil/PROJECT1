# Phased Development Plan — TriGuide-AI

**Principle:** Software + APK first. No physical robot chassis until the user explicitly asks.

## Current next gate —2026-10-20,0.9.26/39

Corrected a device-exposed D_16 format guard, added bounded tracking/rejection
telemetry, and passed467 tests plus builds/policy. Installed/hash verified.
Lit view reached537/566 tracking frames and146 validated image pairs. Final
20.18s probe has46 pairs,40 insufficient-pixel rejects,6 timing rejects and0
accepted. SDK image timestamp equality is not a documented guarantee found by
review; clock compatibility remains unresolved. Next: tests-first same-Frame
provenance/clock-domain design, independent review, then diagnostic admission
evaluation. Do not invent a tolerance or weaken confidence to get success.
After actual accepted depth, require measured wall/mat/step error before any
navigation integration. No CLEAR/turn/purchases or new owner photo needed now.
Evidence and remaining limits: `docs/RAW_DEPTH_D16_FIX_2026-10-20.md`.

## Current priority pair — 2026-10-20, 0.9.23/36

Storage resilience and the isolated raw-depth instrument are implemented;
463 tests and both APK builds pass, installed candidate hash verified.
Evidence/remaining limits: `docs/STORAGE_AND_RAW_DEPTH_2026-10-20.md`.
Next pair: actual bounded raw-depth/confidence/lifecycle measurements on unlocked
Redmi, then held-out measured wall/flat-mat/raised-edge/step evaluation. Keep
all geometry diagnostic until alignment/ground-reference/error gates pass.
Ready components first; do not replace models, train or buy sensors to mask a
camera/geometry/storage defect. Defer cloud, purchases and broad framework work.
Owner now permits PUBLIC source backup, not private captures or public APKs.
Use the local field-issue workflow rather than asking for a photo every minute.

## Engineering foundation / owner mandate — 2026-10-19

Current source-grounded audit and ranked options: `docs/ENGINEERING_AUDIT_2026-10-19.md`.
This checkpoint supersedes historical checkboxes below, not the fail-closed policy.

- A1/A2 this round: one offline-default local gate (tests, Lint, builds and actual
  APK policy), and private evidence excluded from Git/APK without deleting it.
- Next pair: isolated official Raw Depth/confidence measurement, and bounded,
  atomic local storage/error handling. No geometry-to-guidance switch until
  measured confidence/alignment/ground-reference gates pass.
- Defer Room/DataStore migration, broad UI rewrite, new AI models/training and
  hardware purchase absent evidence. Existing free capabilities first.
- Do not auto-publish/push, upload private scenes or expand sensitive permissions.
  Report implementation, desktop checks and phone/safety evidence separately.

## Current checkpoint — 2026-10-19, 0.9.22/35

- Installed verified debug update retained on Redmi via USB. More offers a
  bounded surface observation lab; main navigation remains fail-closed.
- 339 app/10 sim tests and builds pass. Actual lab entry/Describe/Stop/More/Back
  stays paused; silent regression passes. Surface labels are not walkability.
- Runtime confirms ARCore AUTOMATIC/raw support, but no depth frame/height was
  measured. Next: reuse the official Raw Depth example in an isolated timed
  probe, verify confidence/alignment/physical-reference error before integration.
- Keep Depth Anything V2 Small/ready Android inference as fallback. Sensors and
  paid/cloud options only after free measured options fail the required gate.
- Roadmap: `docs/WALL_RUG_DEPTH_ROADMAP_2026-10-19.md`; actual evidence/limits:
  `docs/SURFACE_LAB_AND_DEPTH_2026-10-19.md`. Older checkpoints below are history.

## Architecture correction initiative — 2026-10-18

**Current checkpoint:** The R0 fail-closed camera contract and distinct-frame
temporal voting are included in the installed Redmi 0.9.14/27 build. Cursor
independently reviewed the 0.9.12 box-extent correction and 0.9.13 diagnostic
floor-mask seam. A 0.9.13 STOP → More → Back auto-restart defect was found on
the phone and fixed/phone-regression-tested in 0.9.14. The historical
phase-status table below is not a claim of current phone safety validation.
Positive walkable-area evidence and junction detection are still unimplemented.
See `docs/PROGRESS.md` and
`docs/PHONE_TEST_CHECKPOINT_2026-10-18.md` for current evidence.

The original video and source review showed camera guidance inferring a direction
from 2D detector boxes, while an empty detector result could become
`CLEAR_OBSERVED` and `STRAIGHT`. R0 removed those live-camera outputs; obtaining
positive path evidence remains the active architecture priority.

- [x] Record the root diagnosis, target boundaries, open-source choices, and migration sequence in `docs/ROOT_ARCHITECTURE_REDESIGN.md`.
- [x] Codex R0 implementation: empty/ignored/off-corridor detector-only results remain `UNKNOWN`; any camera-box-only route stays stopped (`HOLD`) instead of steering around an obstacle without positive walkable-area evidence. Desktop tests passed; current Redmi verification is pending.
- [x] Codex research: Sailens Android + SightBuddy selected as complementary architecture/interaction references, not as “most popular” projects or code to merge wholesale. ARCore depth is an optional experiment; no segmentation model selected. See `docs/ROOT_ARCHITECTURE_REDESIGN.md`.
- [x] Codex simulator review: keep the small on-phone `:sim-core`; Webots is a better high-fidelity desktop reference but not an Android drop-in. Replaced explicit-Euler pose stepping with exact constant-curvature arc integration; the current 10 sim tests passed.
- [x] Cursor: review R0 camera-evidence and exact-arc simulator changes through `docs/BRIDGE_FROM_GPT.md`; the subsequent 0.9.12 geometry review found no High/Medium source defect.
- [ ] Codex: benchmark current perception and legally usable candidate walkable-area models for the actual Redmi before recommending a model swap.
- [ ] Cursor R1: separate frame adaptation and temporal scene state from Compose; preserve `:sim-core` and current camera/output adapters.
- [ ] Cursor R2: add branch candidates only from persistent walkable-area evidence and keep on-demand description independent from guidance.

Current phase details below are retained as history. Do not treat completion of an old checkbox as evidence that a camera-only path has been validated.

## Execution status — 2026-10-18

The prompt’s Phase 1 is the installable shell. Its Phase 2 is the virtual tricycle. The older headings below keep the longer roadmap.

| Work | Status | Evidence |
| --- | --- | --- |
| Environment audit | PASS_WITH_WARNINGS | `docs/ENVIRONMENT_AUDIT.md` |
| Foundation choice | Option B | `docs/DECISIONS.md` |
| Installable debug APK + CameraX shell | PASS (build), launch-only on emulator | `docs/TEST_RESULTS.md` |
| Tricycle kinematics + JVM tests | BASELINE PASS; current exact-arc change unverified | Prior recorded run: 9/9 in `:sim-core:test`; current source/test assertions not run |
| Redmi install | PASS_WITH_WARNINGS | `55aqpfqkvw9xbafe` is `device`. 0.7.0 is installed. Live Vision drew real boxes. See `docs/TEST_RESULTS.md` |
| Real obstacle detection | PASS_WITH_WARNINGS | EfficientDet-Lite0 on the Redmi. Boxes use the viewport crop. Score floor 0.35. A counted accuracy pass is still open |
| Floor segmentation | NOT_STARTED | Disabled in 0.7.0. DeepLab-v3 has no floor class and is not bundled. No floor mask is drawn |
| Camera boxes to virtual steering | PASS_WITH_WARNINGS | Unit tests 65/65 app + 9/9 kinematics. Emulator synthetic wheel moved. Emulator camera held because it saw zero objects. Redmi open |
| Voice guidance | PASS_WITH_WARNINGS | Gate tests plus emulator TTS on `Pixel_8a`. Five short cues, cooldown, quiet mode, English and Arabic when an offline voice exists. Redmi open |

## Phase 0 — Bootstrap

- [x] Environment audit
- [x] `AGENTS.md` + `.cursor/rules`
- [x] Progress / decisions / research docs
- [x] JDK 17 + Android SDK + Android Studio (Studio was already installed)
- [x] `adb devices` sees Redmi (`55aqpfqkvw9xbafe`, Android 14)
- [x] Git init (local). Private GitHub remote still optional

**Exit criteria:** `sdkmanager`/`adb` usable; Android app project can assemble. Assemble result is in `docs/TEST_RESULTS.md`.

## Phase 1 — Android skeleton APK

- [x] Kotlin Android app (CameraX preview, accessibility-minded shell).
- [x] Gradle Wrapper and debug APK (`outputs/apk/TriGuideAI-0.1.0-debug.apk`).
- [x] Install and launch on emulator `Pixel_8a` (activity visible). Interactive camera/TalkBack not tapped.
- [x] Install on the Redmi via cable ADB (0.4.1, after one `INSTALL_FAILED_USER_RESTRICTED`).

**Exit criteria:** APK installs and launches on the phone (verified, not assumed). 0.4.1 install and Home launch are recorded in `docs/TEST_RESULTS.md`. A Live Vision pass on 0.4.1 is still open.

## Phase 2 — Real-time camera pipeline

- [x] CameraX preview + frame analysis pipeline (`STRATEGY_KEEP_ONLY_LATEST`).
- [x] Timed analysis callbacks on the emulator (preview about 9–20 FPS in the Phase 3 smoke test).
- [ ] Same measurement on a Redmi. A mid-range phone budget is still open.

**Exit criteria:** Live preview + timed frame callback measured on device. Emulator only so far.

## Phase 3 — On-device obstacle / scene cues

- [x] MediaPipe Object Detector on CameraX (`STRATEGY_KEEP_ONLY_LATEST`), real boxes only when the model returns them.
- [x] Missing-model and load-failure states with no invented boxes.
- [x] Measured inference latency / FPS on the emulator (not on a phone).
- [x] Map detections into guidance that drives the virtual robot. Screen zone is image position, not a distance. 0.3.0 steers the virtual robot from that position. Redmi and accuracy remain open.
- [x] Audio cues for obstacle, turn, uncertain, camera unavailable, and simulation paused. Haptic feedback is still open.
- [ ] Controlled indoor test with recorded true positives and false alarms.

**Exit criteria:** Controlled indoor test with recorded true positives/false alarms. Not met. The detector runs; the evaluation does not.

## Phase 4 — Virtual steering + three-wheel simulator

- [x] Software model of a three-wheel tricycle (bicycle kinematics) with JVM tests. Manual control screen is in the APK.
- [x] Obstacle response and vision-driven commands for the virtual robot. Camera commands use image position only. The synthetic map uses authored coordinates.
- Draw from OpenBot control concepts without requiring a USB robot body.
- Floor segmentation is a separate item and is NOT_STARTED. It is disabled in 0.3.0.

**Exit criteria:** Simulator responds correctly to synthetic + camera-derived commands. Synthetic response was seen on the emulator. Camera-derived Hold was seen because the emulator returned zero objects. A camera-derived turn on a real scene is still open.

## Phase 5 — Integrated navigation mode

- [x] Combine vision → guidance → virtual steering while Live Vision is open, and synthetic steering on the simulator.
- [x] No camera upload. The manifest still has no internet permission.
- [ ] End-to-end demo on a Redmi.
- [ ] TalkBack pass. Controls have labels; TalkBack itself was not run.

**Exit criteria:** End-to-end demo script on Redmi, documented limitations. The virtual path is in 0.3.0. The Redmi script has not been run.

## Phase 6 — Voice guidance

- [x] Android text-to-speech for obstacle detected, turn suggestion, navigation uncertain, camera unavailable, and simulation paused.
- [x] Priority, dedupe, cooldown, interrupt for a critical cue, speech rate, and quiet mode. The control tick does not speak every frame.
- [x] English and Arabic phrases, using an offline device voice when that language is installed.
- [x] Settings for speech on/off, rate, quiet mode, and language preference. Research-mode disclaimer stays. Voice is not a safety proof.
- [ ] Same check on a Redmi, including whichever voices that phone actually has.

**Exit criteria:** Cues are spoken from the navigation decision without a sentence per frame, with unit tests for the gate. Met on the emulator. The phone check is still open.

## Later — Hardening

- Battery, thermal, offline reliability.
- Optional evaluation against open datasets / scripted routes.
- Only then consider physical OpenBot-style body if user requests.

## Research inputs (before locking stack)

| Source | Reuse posture |
| --- | --- |
| **OpenBot** | Phone-as-brain, control/camera patterns; MIT; adapt ideas, avoid dragging unused hardware firmware |
| **GuideLens / WorldLens** | Hazard zones, voice-first UX ideas; often cloud/Gemini — prefer on-device equivalents |
| **MediaPipe** | Primary on-device vision building blocks |
| **Project Guideline** | Accessibility ML pipeline inspiration; heavy C++/ARCore/Pixel focus — borrow concepts carefully |
| **VisionAId / similar** | Scan for licenses and on-device approaches before copying code |

Detailed notes: `docs/RESEARCH_NOTES.md`.
