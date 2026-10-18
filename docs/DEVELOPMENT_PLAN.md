# Phased Development Plan — TriGuide-AI

**Principle:** Software + APK first. No physical robot chassis until the user explicitly asks.

## Execution status — 2026-10-18

The prompt’s Phase 1 is the installable shell. Its Phase 2 is the virtual tricycle. The older headings below keep the longer roadmap.

| Work | Status | Evidence |
| --- | --- | --- |
| Environment audit | PASS_WITH_WARNINGS | `docs/ENVIRONMENT_AUDIT.md` |
| Foundation choice | Option B | `docs/DECISIONS.md` |
| Installable debug APK + CameraX shell | PASS (build), launch-only on emulator | `docs/TEST_RESULTS.md` |
| Tricycle kinematics + JVM tests | PASS | 9/9 in `:sim-core:test` |
| Redmi install | PASS_WITH_WARNINGS | `55aqpfqkvw9xbafe` is `device`. 0.4.1 is installed and Home launched. 0.4.0 showed a streaming camera and a chair line. 0.4.1 Live Vision was not opened. See `docs/TEST_RESULTS.md` |
| Real obstacle detection | PASS_WITH_WARNINGS | MediaPipe EfficientDet-Lite0 loads and runs on the emulator. Accuracy and Redmi are open. See `docs/TEST_RESULTS.md` |
| Floor segmentation | NOT_STARTED | Disabled in 0.4.0. No floor model is loaded |
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
