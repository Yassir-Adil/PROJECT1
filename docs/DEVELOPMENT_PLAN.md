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
| Redmi install | NOT_STARTED | No phone on ADB |
| Real obstacle detection | PASS_WITH_WARNINGS | MediaPipe EfficientDet-Lite0 loads and runs on the emulator. Accuracy and Redmi are open. See `docs/TEST_RESULTS.md` |

## Phase 0 — Bootstrap

- [x] Environment audit
- [x] `AGENTS.md` + `.cursor/rules`
- [x] Progress / decisions / research docs
- [x] JDK 17 + Android SDK + Android Studio (Studio was already installed)
- [ ] `adb devices` sees Redmi
- [x] Git init (local). Private GitHub remote still optional

**Exit criteria:** `sdkmanager`/`adb` usable; Android app project can assemble. Assemble result is in `docs/TEST_RESULTS.md`.

## Phase 1 — Android skeleton APK

- [x] Kotlin Android app (CameraX preview, accessibility-minded shell).
- [x] Gradle Wrapper and debug APK (`outputs/apk/TriGuideAI-0.1.0-debug.apk`).
- [x] Install and launch on emulator `Pixel_8a` (activity visible). Interactive camera/TalkBack not tapped.
- [ ] Install on a Redmi via ADB.

**Exit criteria:** APK installs and launches on the phone (verified, not assumed). Phone check is still open. Emulator launch is recorded in `docs/TEST_RESULTS.md`.

## Phase 2 — Real-time camera pipeline

- [x] CameraX preview + frame analysis pipeline (`STRATEGY_KEEP_ONLY_LATEST`).
- [x] Timed analysis callbacks on the emulator (preview about 9–20 FPS in the Phase 3 smoke test).
- [ ] Same measurement on a Redmi. A mid-range phone budget is still open.

**Exit criteria:** Live preview + timed frame callback measured on device. Emulator only so far.

## Phase 3 — On-device obstacle / scene cues

- [x] MediaPipe Object Detector on CameraX (`STRATEGY_KEEP_ONLY_LATEST`), real boxes only when the model returns them.
- [x] Missing-model and load-failure states with no invented boxes.
- [x] Measured inference latency / FPS on the emulator (not on a phone).
- [ ] Map detections into guidance that drives the virtual robot. Screen zone text is shown; it is not a distance and it does not steer.
- [ ] Audio / haptic feedback.
- [ ] Controlled indoor test with recorded true positives and false alarms.

**Exit criteria:** Controlled indoor test with recorded true positives/false alarms. Not met. The detector runs; the evaluation does not.

## Phase 4 — Virtual steering + three-wheel simulator

- [x] Software model of a three-wheel tricycle (bicycle kinematics) with JVM tests. Manual control screen is in the APK.
- [ ] Obstacle response and vision-driven commands (detection is not implemented).
- Draw from OpenBot control concepts without requiring a USB robot body.

**Exit criteria:** Simulator responds correctly to synthetic + camera-derived commands. Manual kinematics pass; vision coupling does not.

## Phase 5 — Integrated navigation mode

- Combine vision → guidance → virtual steering into one “assist” mode.
- Logging for sessions (privacy-aware; no accidental cloud upload of camera by default).
- Polish accessibility (TalkBack, large controls if any).

**Exit criteria:** End-to-end demo script on Redmi, documented limitations.

## Phase 6 — Hardening (later)

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
