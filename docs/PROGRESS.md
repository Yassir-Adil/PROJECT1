# Progress Log

## 2026-10-18 — Redmi install and launch (phone only)

**Status:** install and launch PASS_WITH_WARNINGS on the personal Redmi. Live camera, accuracy, and spoken-cue checks NOT_STARTED.

### Policy

The user requires every install and smoke test on their personal Redmi. The emulator is not used for those checks. Earlier emulator installs and smokes stay in `docs/TEST_RESULTS.md` as history and are non-authoritative for this user from this session forward.

### Done this session

- Restarted ADB. `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (`22101316G`, Redmi Note 12 Pro 5G, Android 14 / SDK 34). No emulator was started or installed to.
- Installed `outputs/apk/TriGuideAI-0.4.0-debug.apk` with `adb -s 55aqpfqkvw9xbafe install -r`. Result: Success. Package `com.triguide.ai` `versionName=0.4.0` `versionCode=4`.
- Launched `com.triguide.ai/.MainActivity`. It was the resumed visible activity (pid 30249). Logcat showed no FATAL crash.
- Home on the phone: detector READY, floor `NOT_STARTED`, voice line says offline English and Arabic voices are installed, camera permission denied. Start Camera was not tapped.

### Still open (on the phone)

- User taps Start Camera and allows the camera permission.
- A real-room object should produce a box and a navigation line. Timing is still unmeasured.
- Robot Simulation, Test Scenarios, Diagnostics, Settings (speech sample, quiet mode), and TalkBack were not opened.

## 2026-10-18 — Phase 6 voice guidance

**Status:** voice guidance PASS_WITH_WARNINGS on the emulator (non-authoritative under the phone-only policy above). Floor segmentation still NOT_STARTED. The later Redmi install of this same 0.4.0 APK is recorded in the section above. Accuracy evaluation NOT_STARTED.

### Done this session

- Added Android text-to-speech for five cues: obstacle detected, turn suggestion, navigation uncertain, camera unavailable, and simulation paused.
- A gate decides whether a cue may be spoken. The same cue has a cooldown. A critical cue can interrupt a lower one. Quiet mode keeps only obstacle detected and camera unavailable. Speech can be turned off. Rate is limited to 0.6–1.6. The control loop still runs about every 50 ms and does not speak on every tick.
- English and Arabic phrases. The speaker uses an offline voice for the chosen language. Arabic is used when that voice is installed. Otherwise the cue stays in English. This build still has no internet permission.
- Settings has speech on/off, quiet mode, speech rate, language (device, English, Arabic), the engine status, and a sample button. The research-mode line stays on every screen. The voice line says the cue is not a safety proof.
- The three-frame direction vote is not spoken. The committed turn is.
- `versionName` 0.4.0 (`versionCode` 4). Debug APK copied to `outputs/apk/TriGuideAI-0.4.0-debug.apk` (81,438,490 bytes).
- Unit tests: `:app:testDebugUnitTest` 65/65 PASS, `:sim-core:test` 9/9 PASS.
- Emulator `Pixel_8a`: install and launch PASS. Google TTS reported offline English and Arabic voices. Samples were synthesized in both languages on the first 0.4.0 install. After the warmup sentence was removed, the reinstalled APK spoke three turn cues on the synthetic map across about five seconds, and did not speak “Navigation uncertain” during the vote. The emulator was then stopped.

### Still open

- Install and a real-room check on the Redmi. See the blocked USB note below. Later the same morning `adb devices` also listed `55aqpfqkvw9xbafe` as `device` (Xiaomi `22101316G`). `pm path com.triguide.ai` on that serial was “Unable to find package.” This session did not install on it and did not open the app there.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started.
- TalkBack was not run. Quiet mode and the speech switch were visible and were not tapped on the emulator. The gate unit tests cover those switches.

## 2026-10-18 — Redmi install blocked before ADB

**Status:** superseded the same morning. This poll was BLOCKED. The later authorized install is in the section at the top of this file.

Windows sees a connected **Redmi Note 12 Pro 5G** as a file-transfer device (`USB\VID_2717&PID_FF40\55AQPFQKVW9XBAFE`). `adb devices -l` listed only emulator `emulator-5554`. The phone was not `unauthorized` or `offline`; the ADB interface was absent. `outputs/apk/TriGuideAI-0.4.0-debug.apk` (`com.triguide.ai` 0.4.0, `MainActivity`) was identified and not installed. Android version was not read from the phone. Detail: `docs/TEST_RESULTS.md`.

### Still open

- Unlock the phone, turn on USB debugging, and accept this PC on the “Allow USB debugging?” prompt. Then retry install.

## 2026-10-18 — Phase 5 virtual steering from detection

**Status:** vision-to-virtual-robot link PASS_WITH_WARNINGS. Floor segmentation NOT_STARTED (disabled). Redmi NOT_STARTED. Accuracy evaluation NOT_STARTED.

### Done this session

- Replaced the hold-only policy with a navigation engine. Camera decisions use the horizontal position of real boxes. They do not invent meters. Empty, center, contradictory, and stale samples hold or stay unknown.
- Added temporal smoothing: 3 repeated frames before a new direction, 5 before a left/right reversal.
- Synthetic map mode keeps an authored obstacle list for deterministic runs. Straight is allowed only there, when that list shows an empty forward corridor.
- Live Vision and Robot Simulation show the decision, reason, confidence, front-wheel response, the research-mode line, and the label “Illustrative path, not verified.”
- Floor segmentation was not added. The app labels it `NOT_STARTED` and disabled.
- `versionName` 0.3.0 (`versionCode` 3). Debug APK copied to `outputs/apk/TriGuideAI-0.3.0-debug.apk` (81,293,198 bytes).
- Unit tests: `:app:testDebugUnitTest` 48/48 PASS, `:sim-core:test` 9/9 PASS.
- Emulator `Pixel_8a`: install and launch PASS. Home showed detector READY and floor `NOT_STARTED`. Synthetic world showed a wheel response. Live Vision streamed, found zero objects, and held. Emulator was then stopped.

### Still open

- USB debugging on the Redmi, then install and a real-room check. A known object should produce a box and a matching decision. This emulator camera produced none.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started.

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
