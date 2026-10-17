# TriGuide-AI — Agent Instructions

## Mission

Build an **Android AI navigation + robotic steering simulation** that helps **visually impaired** users. The smartphone is the primary compute unit (camera, AI, guidance). Do **not** buy or assemble physical robot hardware unless the user explicitly requests it.

## Non-negotiables

1. **Phone-first:** Camera AI, obstacle detection, virtual steering, and a three-wheel robot simulator run on Android.
2. **No hardware purchases** in the current phase. Prefer software simulation and installable APKs.
3. **Reuse before reinvent:** Research and adapt ideas/components from GuideLens (concepts), OpenBot, MediaPipe, Project Guideline, VisionAId / related accessibility projects — with license respect.
4. **Honest testing:** Never claim a test, build, or device check succeeded unless it was actually executed. Record failures as failures.
5. **Fix before advance:** After each phase, run tests, diagnose failures, fix blockers, then continue.
6. **Document continuously:** Keep `docs/PROGRESS.md`, `docs/DECISIONS.md`, `docs/KNOWN_ISSUES.md`, and test results up to date.
7. **Do not overwrite** existing good work without a clear reason and a note in DECISIONS.

## Execution preferences

- Prefer **This PC** (local) for Android SDK, Gradle, ADB, and real-device testing (e.g. Redmi).
- Use **Auto-review** for tool approvals; ask the user only when permission or physical interaction is required (USB debugging, phone unlock, installer UI, GitHub auth).
- Prefer strongest available models for planning/hard bugs; Composer for routine implementation when suitable.

## Tech direction (default until revised)

- Language: **Kotlin** + modern Android (CameraX, Jetpack).
- On-device vision: **MediaPipe** / TFLite first; cloud-only vision is not the primary path.
- Steering: virtual / simulated three-wheel robot before any physical chassis.
- Deliverable: debug/release **APK** installable on a real phone.

## Workflow for agents

1. Read this file + `docs/DEVELOPMENT_PLAN.md` + latest `docs/PROGRESS.md`.
2. Environment audit if tools changed.
3. Implement the current phase only.
4. Build and test; update docs with real results.
5. Stop and report blockers that need the user (USB, accounts, hardware).
