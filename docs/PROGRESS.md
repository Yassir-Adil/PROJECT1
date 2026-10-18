# Progress Log

## 2026-10-18 — Real boxes on the Redmi, floor still not started (0.7.0)

**Status:** 0.7.0 install and phone smoke PASS_WITH_WARNINGS on the personal Redmi. The detector drew real boxes. Floor segmentation stays `NOT_STARTED`. No floor mask is drawn.

### Detection

- The analysis frame is cropped to the camera viewport before it is rotated upright. On this phone the log was `rot=90 buffer=640x480 crop=134,0,372x480 upright=480x372`.
- The model may return scores from 0.25. A box is drawn, and may steer, only at 0.35 or above. Weaker hits are logged and not drawn.
- With the rear camera on a person and a table, logcat `TriGuideDetect` reported real scores, including `person=0.72`, `bottle=0.52`, and `laptop=0.45`. A `couch` at 0.34 was logged as below the floor and was not drawn. The same open also logged false labels from the model (`tv`, and on the earlier frame `frisbee`). Those boxes came from the model. They were not invented in the app.
- A screencap during that open showed yellow boxes on the people and on a bottle. One box was labeled frisbee over the table. Decision was Hold because a reported object was in the center. Front wheel 0.0 degrees. Preview 30 FPS, inference 8 FPS, last 75 ms, average 92 ms, on that UI dump.
- An earlier stretch of the same launch, before the camera was aimed at the people, logged `drawn=0` at the 0.25 model floor. An empty frame is still Hold, not a clear path.

### Floor

- MediaPipe DeepLab-v3 float32 (2,780,176 bytes, `storage.googleapis.com` `deeplab_v3.tflite`) was downloaded and inspected. Its label list is the Pascal set (`background`, `person`, `chair`, `sofa`, and the rest). The file contains no `floor` label. There is no int8 build at the usual MediaPipe URL (HTTP 404). It was not bundled. No mask is drawn. Nothing is labeled safe from a floor.

### Also in this build

- Live Vision shows the detector sentence and asks to point the rear camera at a chair or a person.
- **Describe this frame** speaks the latest reported boxes once. It is not on the control tick. On the phone, one tap logged `TriGuideScene` and `speak result=0` for “Reported in the latest frame: person 0.68 center, bottle 0.41 right, bottle 0.36 right. This describes detector boxes only. It is not a clearance and not a floor reading.” Sound from the speaker was not confirmed by ear.
- Robot Simulation’s camera-informed mode now opens the rear camera on that screen. That mode was not opened on the phone in this session.

### Phone and tests

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- Final `adb install -r outputs/apk/TriGuideAI-0.7.0-debug.apk` → Success. `versionName=0.7.0` `versionCode=9`. APK size 81,408,299 bytes. No FATAL line. `INTERNET` is not in the badge.
- `:app:testDebugUnitTest` 95/95 PASS, 0 failures, after the final source change. `:sim-core:test` was UP-TO-DATE and was not re-run. Kinematics sources did not change.

### Still open

- A counted true-positive / false-alarm pass. This smoke is not that count.
- Floor segmentation, until a lite model with a real floor class is available under a compatible license.
- Camera-informed simulation was not opened on the phone.
- TalkBack was not walked. Speaker audio was not confirmed by ear.

## 2026-10-18 — Screen triple-tap restores guidance (0.6.0)

**Status:** 0.6.0 install and phone smoke PASS_WITH_WARNINGS on the personal Redmi. Screen triple-tap restarted guidance. Fingerprint control is still impossible and was not pursued.

### Shortcut

- Three taps on Live Vision, each within 750 ms of the previous one, restart the camera and speak “Camera started. Guidance on.”
- Cold open still starts Live Vision, the camera, and speech. The third spoken sentence says the fingerprint button cannot control the app and that a screen triple-tap restarts guidance.
- A drag is not a tap. One tap on More still opens More and speaks “Guidance paused.”

### Phone (no emulator)

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- `adb install -r outputs/apk/TriGuideAI-0.6.0-debug.apk` → Success. `versionName=0.6.0` `versionCode=8`. APK size 81,391,581 bytes.
- Cold start spoke, all `speak result=0`: the research disclaimer, “Camera started. Guidance on.”, and “The fingerprint button cannot control this app. Triple-tap the screen to restart guidance.”
- `adb shell input tap 540 1600` three times in one shell (188 ms). Logcat `TriGuideTap`: `tap count=1` at 08:54:10.352, `tap count=2` at 08:54:10.382, `triple-tap accepted; restarting guidance` at 08:54:10.408. `TriGuideVoice` then `speak result=0` for “Camera started. Guidance on.” Sound from the speaker was not confirmed by ear.
- A swipe did not log a tap. One tap on More logged `tap count=1` and opened More. Test Scenarios showed Straight, Right (left barrel), and Hold (post on axis), each labeled as a synthetic map. Robot Simulation showed “Simulated tricycle only.” A later synthetic-mode UI dump failed with `could not get idle state` while the screen kept updating.
- Live Vision on this open showed detector READY, Preview 15 FPS, Inference 13 FPS, last 42 ms, average 44 ms, Decision Hold, front wheel 0.0 degrees, source Camera 2D. No box was in that frame. Floor segmentation remained `NOT_STARTED`.

### Tests

- `:app:testDebugUnitTest` 87/87 PASS, 0 failures. Includes the tap detector, the three scenarios, the diagnostics text, and two navigation edge cases (non-finite cue, zero-area cue).
- `:sim-core:test` was UP-TO-DATE. It was not re-run. Kinematics sources did not change.

### Still open

- A person confirming they heard the sentences.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started. No floor mask is drawn.
- TalkBack was not walked. Settings and the share sheet were not opened on the phone.

## 2026-10-18 — Blind-first open, fingerprint shortcut is blocked by the OS

**Status:** 0.5.1 install and cold start PASS_WITH_WARNINGS on the personal Redmi. Fingerprint triple-tap is **not possible** for this app. Screen triple-tap and volume-key shortcuts are not the control.

### What the phone actually has

`dumpsys fingerprint` on `55aqpfqkvw9xbafe` (Redmi Note 12 Pro 5G, `22101316G`, HyperOS `OS2.0.16.0.UMOMIXM`) reports `isUdfps: false` and `isPowerbuttonFps: true`. The reader is the side power button (Goodix, `uinput-goodix`), not an under-display sensor. `fingerprint_double_tap` is `none`. Detail is in `docs/DECISIONS.md`.

### What 0.5.1 does

- Opening the app enters Live Vision, starts the rear camera, and speaks the research disclaimer once, then “Camera started. Guidance on.”, then one sentence that the fingerprint button is not available to apps.
- If camera permission is missing, the app speaks and shows the system dialog. If Android will not show it again, the app speaks and opens TriGuide’s settings screen.
- More still reaches the simulator, settings, and diagnostics. It is not the first screen.
- No volume-key gesture and no screen-wide triple-tap are shipped as the shortcut.

### Phone check (no emulator)

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.5.1-debug.apk` → Success. `versionName=0.5.1` `versionCode=7`. APK size 81,495,956 bytes.
- Cold start of `MainActivity` was the resumed activity (pid 29066). No FATAL line.
- Logcat `TriGuideVoice` `speak result=0` for the disclaimer, “Camera started. Guidance on.”, and the fingerprint limitation. Result 0 means the engine accepted the utterance. Sound from the speaker was not confirmed by ear.
- A later UI dump, after bringing the task forward, showed Live Vision (not a home menu): `Camera: Streaming. Preview 30 FPS. Inference 10 FPS, last 142 ms, average 95 ms`, detector READY, decision Hold, and the fingerprint limitation line. More was on the screen.

### Tests

- `:app:testDebugUnitTest` 75/75 PASS, then one TalkBack sentence was edited and `:app:assembleDebug` succeeded. `:sim-core:test` was not re-run.

### Still open

- A person at the phone confirming they heard the three sentences.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started.

## 2026-10-18 — Redmi 0.4.1 home, after a real 0.4.0 camera pass

**Status:** 0.4.1 install and Home launch PASS_WITH_WARNINGS on the personal Redmi. Live Vision on 0.4.1 was not opened. The earlier 0.4.0 camera and speech-engine lines are recorded below and are not repeated as a 0.4.1 proof.

### Phone

`adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (Xiaomi `22101316G`, market name Redmi Note 12 Pro 5G, device `ruby`, Android 14 / SDK 34, HyperOS `OS2.0.16.0.UMOMIXM`). No emulator was started or installed to. `adb` was not on PATH. Binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### 0.4.0 on the phone (before the UI change)

- `adb install -r outputs/apk/TriGuideAI-0.4.0-debug.apk` → Success. `versionName=0.4.0` `versionCode=4`.
- `MainActivity` launched and was the resumed activity.
- A later UI dump showed Live Vision already open. This session did not inject taps. The screen text was `Camera: Streaming. Preview 14 FPS. Inference 9 FPS, last 53 ms, average 59 ms` and, at that moment, no objects at or above 0.50.
- Logcat from that process: `TriGuideNav` `chair is in the center of the image` and `TriGuideVoice` `speak result=0` `kind=OBSTACLE_DETECTED` `lang=ENGLISH` `text=Obstacle detected. Hold.` Result 0 means the speech engine accepted the utterance. Sound from the speaker was not confirmed by ear.

### 0.4.1

- Live Vision keeps the decision, the reason, and the front wheel above a taller preview. Back sits at the top of each screen, and the system Back key returns Home. A permanent camera denial offers app settings. Home states the detector as READY or not ready. One accent color. The launcher name is TriGuide.
- `versionName` 0.4.1 (`versionCode` 5). APK: `outputs/apk/TriGuideAI-0.4.1-debug.apk` (81,342,405 bytes).
- Unit tests executed: `:app:testDebugUnitTest` 71/71 PASS. `:sim-core:test` was UP-TO-DATE (earlier XML the same day: 9/9, 0 failures). It was not re-run.
- First `adb install -r` of 0.4.1 returned `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. A retry about a minute later returned Success. Installed package is `versionName=0.4.1` `versionCode=5`. Home was the resumed activity (pid 9367). No FATAL line. Home text included `Detection: READY`, `Decision: Unknown`, `Front wheel: 0.0 degrees`, and offline English and Arabic voices installed. Live Vision was not opened on this build. `adb shell input` is denied (`INJECT_EVENTS`), so buttons were not tapped from the PC.

### Still open

- On 0.4.1, the user opens Live Vision and points at a real object. Confirm a box, the wheel, and whether the cue is heard.
- Turn on USB debugging (Security settings) if the next install is canceled again, or if PC taps are needed. Steps: `docs/DEVICE_COMPATIBILITY.md`.

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
