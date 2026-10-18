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

## Parallel builder, reviewer, and the phone

A builder chat and a reviewer chat may run at the same time. The builder owns product code, builds with `scripts/build/assemble-debug.ps1`, and installs with `scripts/test/redmi-install.ps1` only when `adb devices` shows serial `55aqpfqkvw9xbafe` as `device`. The reviewer appends `docs/REVIEW_NOTES.md` and stays off the builder's files while `.cursor/agent-lock.json` is missing or that builder lock is under 45 minutes old. Do not start an emulator. Put the SDK on PATH for the session with `scripts/setup/ensure-android-path.ps1`.

## Modern stack (standing rule)

Always use the latest stable versions (2025-2026) of all tools, libraries, and frameworks, with modern UI (Jetpack Compose + Material 3) and current algorithms. Prefer the newest maintained option. Never downgrade unless there is a concrete, reproduced compatibility problem, and document it in `docs/DECISIONS.md`. Full rule: `.cursor/rules/modern-stack.mdc`.

## Human-quality design and clean architecture (standing rule)

The UI must not look AI-generated: no excessive AI labels or markers, a restrained color palette, and clean, uncluttered layouts that look like a skilled human designer made them, while keeping full accessibility for blind users (TalkBack, large touch targets, contrast). Code and algorithms must follow a deliberate, layered, testable architecture, with nothing ad-hoc or random. Full rule: `.cursor/rules/modern-stack.mdc`.
