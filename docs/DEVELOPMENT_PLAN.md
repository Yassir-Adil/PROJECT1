# Phased Development Plan — TriGuide-AI

**Principle:** Software + APK first. No physical robot chassis until the user explicitly asks.

## Phase 0 — Bootstrap (current)

- [x] Environment audit
- [x] `AGENTS.md` + `.cursor/rules`
- [x] Progress / decisions / research docs
- [ ] JDK 17 + complete Android SDK / Studio
- [ ] `adb devices` sees Redmi
- [ ] Git init + optional Private GitHub remote

**Exit criteria:** `sdkmanager`/`adb` usable; empty Android app project can assemble.

## Phase 1 — Android skeleton APK

- Create Kotlin Android app (CameraX placeholder, accessibility-friendly shell).
- Gradle Wrapper, CI-friendly structure, debug APK.
- Install on device via ADB.

**Exit criteria:** APK installs and launches on the phone (verified, not assumed).

## Phase 2 — Real-time camera pipeline

- CameraX preview + frame analysis pipeline.
- Performance budget for mid-range phones (target FPS documented).

**Exit criteria:** Live preview + timed frame callback measured on device.

## Phase 3 — On-device obstacle / scene cues

- Integrate MediaPipe (and/or TFLite) for detection or depth/segmentation cues.
- Map detections → left / center / right + near / far style guidance signals.
- Audio / haptic feedback (TTS or tones); keep UI secondary.

**Exit criteria:** Controlled indoor test with recorded true positives/false alarms.

## Phase 4 — Virtual steering + three-wheel simulator

- Software model of three-wheel robot (kinematics + simple obstacle response).
- Overlay or debug view of commanded steering from vision signals.
- Draw from OpenBot control concepts without requiring USB robot body.

**Exit criteria:** Simulator responds correctly to synthetic + camera-derived commands.

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
