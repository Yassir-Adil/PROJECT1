# Progress Log

## 2026-10-18 — Phase 3 on-device object detection

**Status:** detection integration PASS_WITH_WARNINGS. Accuracy evaluation NOT_STARTED. Redmi NOT_STARTED.

### Done this session

- Bundled the official MediaPipe EfficientDet-Lite0 int8 model and wired `tasks-vision` 1.1.1 in LIVE_STREAM mode. CameraX analysis uses `STRATEGY_KEEP_ONLY_LATEST` and RGBA.
- Live Vision draws boxes, labels, and confidence only after a real result. Missing model and load failure stay empty.
- `versionName` 0.2.0. Debug APK copied to `outputs/apk/TriGuideAI-0.2.0-debug.apk` (81,322,624 bytes).
- Unit tests: `:app:testDebugUnitTest` 16/16 PASS, `:sim-core:test` 9/9 PASS.
- Emulator `Pixel_8a` (x86_64, 16 KB page-size image): install and launch PASS. Log: `detector ready`. Live Vision streamed and reported measured inference times. The virtual camera produced **zero** objects, so box alignment and accuracy were not checked. Emulator was then stopped.

### Still open

- USB debugging on the Redmi, then install and a real-room check (a known object should produce a box).
- True-positive / false-alarm counts. Not measured.
- Vision still does not steer the virtual robot.

## 2026-10-18 — Phase 1 APK and Phase 2 kinematics

**Statuses:** environment PASS_WITH_WARNINGS, foundation Option B, Phase 1 build PASS, emulator launch PASS_WITH_WARNINGS, kinematics tests PASS, Redmi NOT_STARTED.

### Done this session

- Re-checked the toolchain (JDK 17, SDK 34/35, Studio already installed, adb, two AVDs, no phone).
- Cloned GuideLens (full shallow), MediaPipe samples (sparse Android detection/segmentation), and OpenBot docs (sparse). Upstream trees stay gitignored.
- Confirmed GuideLens ONNX files are real protobufs, GPL-3.0, and `compileSdk` 36. Did not build GuideLens. Did not copy its code or weights.
- Chose Option B and created `android-app/` (Compose, CameraX, explicit non-detector, manual tricycle simulator).
- `gradlew.bat :app:assembleDebug :sim-core:test` → BUILD SUCCESSFUL, 9/9 tests, APK at `outputs/apk/TriGuideAI-0.1.0-debug.apk`.
- Installed that APK on emulator `Pixel_8a` and confirmed `MainActivity` was the resumed visible activity. Did not tap camera or simulator controls. Stopped the emulator afterward.

### Still open

- USB debugging on the Redmi, then install and a real camera check.
- MediaPipe detection (later). No fake detector is in the APK.
- Private git remote, if you want one.

## 2026-10-18 — Phase 0 mostly complete

- Docs, agent rules, JDK 17 user install, SDK packages 34/35, and research notes were already in the workspace before this session.
- Android Studio is installed (the earlier note that it was missing was wrong and is corrected in `docs/ENVIRONMENT_AUDIT.md`).
