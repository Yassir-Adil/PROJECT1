# Test results

Statuses: NOT_STARTED | IN_PROGRESS | BLOCKED | PASS | PASS_WITH_WARNINGS | FAIL

## Phone-only policy (user, 2026-10-18)

Installs and smoke tests for this user run on the personal Redmi only. Do not start, install to, or smoke-test an emulator (`emulator-5554` included). If the phone is missing, unauthorized, or offline, stop and record the blocker. Do not fall back to an emulator.

Emulator sections below are historical. They are **non-authoritative** for this user from this note forward. A phone result replaces them when the same check is run on the Redmi.

## 2026-10-18 — 0.8.2 dominant side turn (phone not attached)

**Status: BLOCKED**

The unit tests and the debug APK succeeded. The install and phone log did not run. `adb devices -l` listed no devices. A USB device scan did not show the Redmi (`VID_2717` was absent). No emulator was started.

### Build

- `:app:testDebugUnitTest` 113/113 PASS, 0 failures, 0 skipped. New cases: a dominant left chair with a weaker right bottle (60 percent of the chair’s score times area) is Slight right and speaks “Chair on the left. Turn slightly right. Not a measured gap.”; a left chair and a right table at 80 percent hold with “Objects on both sides. Hold.”; a center chair holds with “Chair in the center. Hold.”; three weaker right boxes do not add up into a both-sides hold; a clearly larger chair is the spoken subject when a smaller cup has a higher score.
- `:sim-core:test` was not run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.8.2'`, `versionCode='12'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.8.2-debug.apk` (81,441,801 bytes).

### Phone

- Not run. `adb install` was not attempted because `55aqpfqkvw9xbafe` was not in `device` state. No logcat. No speaker check.

## 2026-10-18 — Redmi 0.8.1 side-turn speech

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (`22101316G`, `ruby`). No emulator was attached or installed to.

### Build

- `:app:testDebugUnitTest` 108/108 PASS, 0 failures, 0 skipped. New cases: a person in the left third still turns when a weak bottle is in the center; a strong chair in the center holds and the phrase does not say “left”; a turn queued behind Hold is spoken after the gap, in Arabic; Settings `requestSave("أحمد")` stores a name only after confirm and the pending line does not say a photo waits.
- `:sim-core:test` was UP-TO-DATE. It was not re-run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.8.1'`, `versionCode='11'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.8.1-debug.apk` (81,441,801 bytes).

### Phone

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.8.1-debug.apk` → `Success`. `dumpsys package` showed `versionName=0.8.1` `versionCode=11`.
- `am force-stop` then `am start -n com.triguide.ai/.MainActivity`. No FATAL line in the filtered log.
- Opening lines, all `speak result=0`: the research disclaimer, “Camera started. Guidance on.”, and the fingerprint limitation.
- `TriGuideNav` then committed `SLIGHT_RIGHT`: “toilet is on the left of the image (detector confidence 0.45, 5 percent of the frame). Steering slightly right.” A later line used the model label `sink` on the left, same action. The app did not invent those labels.
- At 10:08:07.931 `TriGuideVoice` `speak result=0` `queue=1` `kind=TURN_SUGGESTION` `lang=ENGLISH` `rate=0.9` `pitch=1.0` `text=Toilet on the left. Turn slightly right. Not a measured gap.` The phrase does not contain “center”. Result 0 means the engine accepted it. Sound from the speaker was not confirmed by ear.
- The same turn was not repeated on the following frames in that window. Floor segmentation was not exercised.

## 2026-10-18 — Redmi 0.7.0 detection smoke

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (`22101316G`, `ruby`). No emulator was attached or installed to.

### Build

- `:app:testDebugUnitTest` 95/95 PASS, 0 failures, 0 skipped. Includes the score floor, the viewport crop clamp, and the on-demand scene sentence.
- `:sim-core:test` was UP-TO-DATE. It was not re-run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.7.0'`, `versionCode='9'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.7.0-debug.apk` (81,408,299 bytes).

### Phone

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.7.0-debug.apk` → `Success`. `dumpsys package` showed `versionName=0.7.0` `versionCode=9`.
- `MainActivity` was the resumed activity. No FATAL line.
- `TriGuideDetect`: `detector ready model=MediaPipe EfficientDet-Lite0 int8 delegate=CPU`.
- Geometry on the captured lines: `rot=90 buffer=640x480 crop=134,0,372x480 upright=480x372`. Latency on the final process was about 88–158 ms on the lines that were kept.
- Aimed at people and a table, drawn examples included `person=0.72@36,1,459,369`, `bottle=0.52@381,98,404,152`, and `laptop=0.45@0,272,128,371`. A `couch=0.34` was in `below` and was not drawn. The model also emitted false labels (`tv`, and on an earlier frame of this version `frisbee`). The app did not add boxes of its own.
- Before the camera was on that scene, lines were `drawn=0:none below=0:none`. `TriGuideNav` then logged Hold: an empty detection is not open space.
- UI dump on the first 0.7.0 process: `READY. MediaPipe EfficientDet-Lite0 int8`, `Camera: Streaming. Preview 30 FPS. Inference 8 FPS, last 75 ms, average 92 ms.`, `Decision: Hold`, reason that a bottle was in the center, `Front wheel: 0.0 degrees`, and the sentence that nothing is assumed from an empty frame. The same dump included “found no objects at or above 0.35 confidence” only while the frame was empty; a later screencap showed yellow boxes, labels, and scores on people and a bottle, plus a frisbee label on the table. `uiautomator` later returned `could not get idle state` while the preview kept updating, so More and Settings were not dumped.
- One tap logged `TriGuideScene` and `TriGuideVoice` `speak result=0` for the on-demand sentence naming `person 0.68 center` and two bottles. Result 0 means the engine accepted the utterance. Sound from the speaker was not confirmed by ear. Cold start also logged `speak result=0` for the existing research disclaimer and “Camera started. Guidance on.”
- Floor segmentation was not exercised. No floor model is in the APK. No floor mask was drawn.
- Camera-informed simulation, Test Scenarios, Diagnostics, Settings, and TalkBack were not opened on this build.

## 2026-10-18 — Redmi 0.6.0 screen triple-tap and core smoke

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only:

```
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

No emulator was attached. `adb` binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`. Gradle used JDK 17 at `C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`.

### Build

- `:app:testDebugUnitTest` 87/87 PASS, 0 failures. New coverage: `TripleTapDetectorTest` (8), `GuidanceScenariosTest` (1), `DiagnosticsReportTest` (1), plus non-finite and zero-area navigation cases.
- `:sim-core:test` was UP-TO-DATE and was not re-executed. Kinematics files were not changed.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.6.0'`, `versionCode='8'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Permissions in the badge: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.6.0-debug.apk` (81,391,581 bytes).

### Install and cold start

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.6.0-debug.apk` → `Success`. `dumpsys package` showed `versionName=0.6.0` `versionCode=8`.
- `am force-stop` then `am start -n com.triguide.ai/.MainActivity`. Resumed activity was `com.triguide.ai/.MainActivity` (pid 4069). No `AndroidRuntime` FATAL line was in the filtered log.
- `TriGuideVoice` `speak result=0` (engine accepted; the speaker was not confirmed by ear), English:
  - Research and Simulation Mode disclaimer
  - `Camera started. Guidance on.`
  - `The fingerprint button cannot control this app. Triple-tap the screen to restart guidance.`
- UI dump text included Live Vision, that triple-tap sentence, `READY. MediaPipe EfficientDet-Lite0 int8`, `Camera: Streaming. Preview 15 FPS. Inference 13 FPS, last 42 ms, average 44 ms.`, `Decision: Hold`, `Front wheel: 0.0 degrees`, `Source: Camera 2D`, and More. No detection box was claimed in that frame.

### Triple-tap

Screen 1080×2400. One shell ran `input tap 540 1600` three times (shell elapsed 188 ms). That point is on the preview area, above More.

- `08:54:10.352` `TriGuideTap` `tap count=1`
- `08:54:10.382` `TriGuideTap` `tap count=2`
- `08:54:10.408` `TriGuideTap` `triple-tap accepted; restarting guidance`
- `08:54:10.472` `TriGuideVoice` `speak result=0` `text=Camera started. Guidance on.`

The disclaimer was not repeated. A vertical swipe did not log a tap. One tap on More logged `tap count=1`, spoke `Guidance paused.`, and opened the More screen.

### Other screens

- More showed floor `NOT_STARTED. Disabled. No floor model is loaded. Open floor is not inferred, and no floor mask is drawn.` Hardware line: `MOCK — no physical robot connected`.
- Test Scenarios showed Straight (empty corridor), Right (left barrel), and Hold (post on the forward axis), each marked as not the camera.
- Robot Simulation showed `Simulated tricycle only. Nothing on this screen is a physical robot.` and Manual drive selected, source Manual. Selecting Synthetic world made the next UI dump fail with `could not get idle state`. That mode was not read back.

### Not checked on the phone

- Whether the speaker was audible.
- A real object producing a box on this build.
- Settings, Arabic speech, quiet mode, the diagnostics share sheet, and TalkBack.
- True-positive / false-alarm counts.

## 2026-10-18 — Redmi 0.5.1 open-into-guidance, fingerprint API check

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only:

```
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

No emulator was attached. `adb` binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### Fingerprint hardware (read, not a gesture test)

`adb shell dumpsys fingerprint` included `isUdfps: false` and `isPowerbuttonFps: true`. `getprop` included `ro.hardware.fp.sideCap=true` and `persist.vendor.sys.fp.vendor=goodix`. `getevent -pl` shows `uinput-goodix` with `KEY_POWER` and other ordinary keys. `settings get` equivalent via `settings list secure` showed `fingerprint_double_tap=none`. No fingerprint tap was injected. The power button was not pressed from the PC, because a press can turn the screen off.

### Install and cold start

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.5.1-debug.apk` → `Success`. Package `versionName=0.5.1` `versionCode=7`. File size 81,495,956 bytes.
- `am force-stop` then `am start -n com.triguide.ai/.MainActivity`. Resumed activity was `com.triguide.ai/.MainActivity` (pid 29066). Logcat had no FATAL for the app.
- `TriGuideVoice` lines, all `speak result=0` (engine accepted; the speaker was not confirmed by ear):
  - Research and Simulation Mode disclaimer
  - `Camera started. Guidance on.`
  - `Guidance starts when you open TriGuide. This phone's fingerprint control is the power button, and Android does not give those presses to apps.`
- After a second `am start` brought the existing task forward, `uiautomator dump` text included `Live Vision`, the fingerprint limitation line, `READY. MediaPipe EfficientDet-Lite0 int8`, `Camera: Streaming. Preview 30 FPS. Inference 10 FPS, last 142 ms, average 95 ms`, `Decision: Hold`, `Front wheel: 0.0 degrees`, and `More`.

### Unit tests on the PC

`:app:testDebugUnitTest` 75/75 PASS (0 failures). A TalkBack sentence was edited after that run, and `:app:assembleDebug` then succeeded. `:sim-core:test` was not re-run.

## 2026-10-18 — Redmi 0.4.1 install, plus 0.4.0 camera lines

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` after `adb kill-server` and `adb start-server`:

```
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

No emulator was attached. `adb` was not on PATH. Binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe` (Android Debug Bridge 1.0.41, version 37.0.1-15733141).

Properties read with `adb -s 55aqpfqkvw9xbafe shell getprop`:

| Property | Value |
| --- | --- |
| ro.product.manufacturer | Xiaomi |
| ro.product.marketname | Redmi Note 12 Pro 5G |
| ro.product.model | 22101316G |
| ro.product.device | ruby |
| ro.build.version.release | 14 |
| ro.build.version.sdk | 34 |
| ro.mi.os.version.name | OS2.0 |
| ro.build.version.incremental | OS2.0.16.0.UMOMIXM |
| ro.miui.ui.version.name | V816 |

### 0.4.0 camera and speech engine (observed before the 0.4.1 rebuild)

- `adb -s 55aqpfqkvw9xbafe install -r outputs\apk\TriGuideAI-0.4.0-debug.apk` → `Performing Streamed Install` / `Success`.
- Launch `com.triguide.ai/.MainActivity` → resumed. Package `versionName=0.4.0` `versionCode=4`.
- A UI dump while that process was in front showed Live Vision, not Home. This session did not send taps. Screen text included `Camera: Streaming. Preview 14 FPS. Inference 9 FPS, last 53 ms, average 59 ms` and `MediaPipe EfficientDet-Lite0 int8 found no objects at or above 0.50 confidence.` Decision text at that dump: Hold, reason “No confirmed passage…”, front wheel 0.0 degrees. The preview view started near the bottom of the 2400 px screen.
- Later logcat from pid 31381: `TriGuideNav` `Camera 2D HOLD chair is in the center of the image. No confirmed passage to either side. Depth is not measured.` and `TriGuideVoice` `speak result=0 queue=0 kind=OBSTACLE_DETECTED lang=ENGLISH rate=1.0 text=Obstacle detected. Hold.`
- `speak result=0` is `TextToSpeech.SUCCESS` (the engine accepted the utterance). Whether the speaker was audible was not checked by ear. Quiet mode was not toggled. TalkBack was not walked.

### 0.4.1 build and install

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Command: `gradlew.bat :app:testDebugUnitTest :sim-core:test :app:assembleDebug --no-daemon`

- BUILD SUCCESSFUL in 57s.
- `:app:testDebugUnitTest` executed: 71 tests, 0 failures, 0 errors, 0 skipped (includes 6 new `PhoneDemoStatusTest` cases).
- `:sim-core:test` was UP-TO-DATE and was not re-executed. The existing XML `TEST-com.triguide.ai.sim.TricycleKinematicsTest.xml` timestamp `2026-10-17T23:45:27` records 9 tests, 0 failures.

APK:

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,342,405 | `ECD2D12F731458A803AB5E179C91682AB993A320DF217750B06D70FA8FAE1AF8` |
| `outputs/apk/TriGuideAI-0.4.1-debug.apk` | 81,342,405 | same hash |

- First `adb install -r` of that APK: `Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]`. The phone stayed `device`. The installed package was still 0.4.0.
- Retry `adb install -r -t` about one minute later: `Performing Streamed Install` / `Success` (about 8 seconds). No install dialog was visible in a UI dump taken during that retry; the launcher was in front.
- After install: `versionCode=5` `versionName=0.4.1`. `am start` → `topResumedActivity` `com.triguide.ai/.MainActivity`, pid 9367. Logcat had no FATAL for the app.
- Home UI dump text: disclaimer present; title `TriGuide`; `Detection: READY. MediaPipe EfficientDet-Lite0 int8`; `Decision: Unknown`; `Reason: Navigation has not received an observation.`; `Front wheel: 0.0 degrees`; floor `NOT_STARTED`; `Voice: On. Rate 1.0. Quiet mode off. Device language. Offline English and Arabic voices are installed.`; primary button `Live Vision`.
- Live Vision, a new box, and a new spoken cue were not opened on 0.4.1. `adb shell input keyevent` failed: `SecurityException` … `INJECT_EVENTS`. That matches HyperOS with USB debugging (Security settings) off.

## 2026-10-18 — Redmi install and launch (0.4.0)

**Status: PASS_WITH_WARNINGS**

Physical device was present and authorized. No emulator was started. No install or launch was sent to an emulator.

`adb` was not on this shell’s PATH. Binary used: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

Commands:

```text
adb kill-server
adb start-server
adb devices -l
adb -s 55aqpfqkvw9xbafe shell getprop ro.product.model
adb -s 55aqpfqkvw9xbafe shell getprop ro.build.version.release
adb -s 55aqpfqkvw9xbafe install -r outputs\apk\TriGuideAI-0.4.0-debug.apk
adb -s 55aqpfqkvw9xbafe shell am start -n com.triguide.ai/.MainActivity
```

### Device

`adb devices -l` after the server restart listed only:

```text
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

| Property | Value |
| --- | --- |
| Serial | `55aqpfqkvw9xbafe` |
| `ro.product.model` | `22101316G` |
| Market name | Redmi Note 12 Pro 5G |
| Brand / device | Redmi / `ruby` |
| Android | 14 (`ro.build.version.sdk` 34) |
| State | `device` (authorized) |

### Install

- APK: `outputs/apk/TriGuideAI-0.4.0-debug.apk` (81,438,490 bytes).
- `adb -s 55aqpfqkvw9xbafe install -r` → `Performing Streamed Install` / `Success`.
- Installed package `com.triguide.ai`: `versionName=0.4.0`, `versionCode=4`, `minSdk=26`, `targetSdk=35`.
- `lastUpdateTime=2026-10-18 08:01:39` (phone clock).

### Launch and logcat

- `am start -n com.triguide.ai/.MainActivity` → `Starting: Intent { cmp=com.triguide.ai/.MainActivity }`.
- Process stayed up: `pidof com.triguide.ai` → `30249`.
- `topResumedActivity` was `com.triguide.ai/.MainActivity`, task visible.
- Filtered logcat (`*:E`, about 400 lines, plus package lines) had no `AndroidRuntime` FATAL and no crash line for `com.triguide.ai`. Package-manager lines only: first-use pinning for `com.triguide.ai`.

### Home screen (uiautomator, not a camera test)

- Research and Simulation Mode banner was visible.
- `Object detection: READY. MediaPipe EfficientDet-Lite0 int8`
- `Navigation: Unknown. None`
- `Floor segmentation: NOT_STARTED`
- `Voice guidance: On. Rate 1.0. Quiet mode off. Device language. Offline English and Arabic voices are installed.` (on-screen status only; no sample was played)
- `Virtual robot: Kinematic simulator ready`
- `Physical hardware: MOCK — no physical robot connected`
- Buttons on screen: Start Camera, Robot Simulation, Test Scenarios, Diagnostics. Settings was partly below the fold.

`android.permission.CAMERA` was `granted=false`. Start Camera was not tapped. Camera preview, a real-room detection, inference timing, spoken cues, and TalkBack were not run.

## 2026-10-18 — Phase 6 voice guidance, APK, emulator smoke

**Status: PASS_WITH_WARNINGS**

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Commands from `android-app/`:

```text
.\gradlew.bat :app:testDebugUnitTest :sim-core:test --no-daemon
.\gradlew.bat :app:assembleDebug :sim-core:test --rerun-tasks --no-daemon
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

- First unit-test command: **BUILD SUCCESSFUL in 46s**. `:sim-core:test` was UP-TO-DATE in that invocation.
- Assemble plus forced kinematics rerun: **BUILD SUCCESSFUL in 1m 16s**. `stripDebugDebugSymbols` printed BUILD-001 again: `libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libmediapipe_tasks_jni.so`.
- Retest after the warmup-silence change: **BUILD SUCCESSFUL in 55s**.

### Unit tests

| Suite | tests | failures | errors | time |
| --- | --- | --- | --- | --- |
| `com.triguide.ai.domain.VoiceGuidanceGateTest` | 17 | 0 | 0 | 0.014 s |
| `com.triguide.ai.domain.NavigationDecisionEngineTest` | 32 | 0 | 0 | 0.092 s |
| `com.triguide.ai.domain.DetectionReportsTest` | 12 | 0 | 0 | 0.105 s |
| `com.triguide.ai.domain.PerformanceMonitorTest` | 4 | 0 | 0 | 0.012 s |
| `com.triguide.ai.sim.TricycleKinematicsTest` | 9 | 0 | 0 | 0.010 s |

App total 65/65 at 2026-10-17T23:51:14Z. Sim-core 9/9 at 2026-10-17T23:45:27Z. No instrumentation tests were run.

`VoiceGuidanceGateTest` covers repeated frames, critical cooldown, interrupt, equal-priority critical interrupt, a lower cue waiting, the minimum gap, quiet mode, speech off, rate clamp, Arabic phrase selection, a one-shot pause, a rejected utterance, decision mapping, and a silent smoother warmup.

### APK

| Path | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,438,490 | `1AC24BF62423E646E57446984330B7D1B9E17184F958BC4140091D25AA796794` |
| `outputs/apk/TriGuideAI-0.4.0-debug.apk` | 81,438,490 | same hash |

`aapt dump badging` (build-tools 36.0.0): `versionName='0.4.0'`, `versionCode='4'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Native ABIs: arm64-v8a, armeabi-v7a, x86, x86_64. Asset `assets/models/efficientdet_lite0.tflite` is in the APK. `aapt dump permissions`: `CAMERA` plus the AndroidX non-exported receiver permission. `INTERNET` is not in the APK.

### Emulator smoke (not a phone, not an accuracy test)

**Status: PASS_WITH_WARNINGS**

- AVD `Pixel_8a`, emulator 36.1.9.0. System image `system-images\android-36\google_apis_playstore_ps16k\x86_64`. `ro.product.model` `sdk_gphone16k_x86_64`. ABI `x86_64`. `wm size` `1080x2400`.
- First `adb install -r` of an earlier 0.4.0 binary in this session returned `Success`. Home text included `Voice guidance: On. Rate 1.0. Quiet mode off. Device language. Offline English and Arabic voices are installed.` and `Floor segmentation: NOT_STARTED`.
- Log `TriGuideVoice`: `ready en=true ar=true offline=283 installed=472 engines=Speech Recognition and Synthesis from Google`.
- Settings showed speech, quiet mode, rate 1.0, Device language / English / Arabic, and “Spoken cues repeat the research decision. They are not a safety proof and do not mean the path is clear.” Version line `0.4.0 (4)`.
- Play voice sample, English: `speak result=0` `lang=ENGLISH` `text=Research and simulation. Obstacle detected. This voice is not a safety proof.` Google TTS log: `Synthesis request for locale eng-USA and name en-US-language`.
- After selecting Arabic, the sample was `lang=ARABIC` `text=بحث ومحاكاة. عقبة مكتشفة. هذا الصوت ليس دليل أمان.` Google TTS log: `Synthesis request for locale ara- and name ar-language`.
- That first binary also spoke a warmup “navigation uncertain” before the synthetic turn. The gate now drops the smoother’s “Holding until the direction stays” vote. The final APK was installed on a restarted `Pixel_8a` (`adb install` `Success`, `versionName=0.4.0`).
- Final APK, about 3 s after Synthetic world, log showed three speaks, not a speak per frame: `Turn right.` at 01:35:56, `Slight right.` at 01:35:58, `Straight ahead.` at 01:36:01. All `result=0`, `lang=ENGLISH`, `kind=TURN_SUGGESTION`. On-screen decision at the dump was Straight, 80 percent, front wheel 0.0 degrees, virtual speed 0.40 m/s, source Synthetic map, with “Illustrative path, not verified.” The last-cue line did not double the period. `AndroidRuntime:E` had no FATAL line. `pidof` was `3820`.
- Quiet mode and the speech switch were not tapped. `adb -s emulator-5554 emu kill` stopped the emulator.
- A Xiaomi `22101316G` (`55aqpfqkvw9xbafe`) appeared as an adb `device` while the emulator was up. `pm path com.triguide.ai` on that serial was “Unable to find package.” Nothing was installed or opened on it.

## 2026-10-18 — Redmi USB install attempt

**Status: BLOCKED**

The phone is plugged in. ADB does not see it. `adb install` was not run. The app was not launched. No logcat, UI dump, camera check, synthetic-world check, or spoken-guidance check was done on the phone.

This shell started with `ANDROID_HOME` unset. The user environment variable is `C:\Users\yassi\AppData\Local\Android\Sdk` (same value for `ANDROID_SDK_ROOT`). `adb` was not on PATH until `platform-tools` was prepended. Binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`, Android Debug Bridge 1.0.41, version 37.0.1-15733141.

### What ADB listed

First `adb devices -l`:

```text
emulator-5554          device product:sdk_gphone16k_x86_64 model:sdk_gphone16k_x86_64 device:emu64xa16k transport_id:6
```

No Redmi line: not `device`, not `unauthorized`, not `offline`.

Windows saw the phone as a portable device, not as an ADB interface:

- Friendly name: `Redmi Note 12 Pro 5G`
- Class: WPD (MTP / file transfer)
- Status: OK
- Instance: `USB\VID_2717&PID_FF40\55AQPFQKVW9XBAFE`
- Vendor `2717` is Xiaomi. Product `FF40` is the MTP-only id. No second USB interface and no “Android ADB Interface” were present.

`adb kill-server` then `adb start-server`, then four polls about 2 seconds apart, still showed no phone. Those polls also dropped the emulator briefly. A later `adb devices -l` showed only `emulator-5554` again (`transport_id:2`). The Redmi never appeared.

`adb shell getprop ro.product.model` and the Android version were not read. There is no ADB transport to the phone. The model above is the Windows device name only.

### APK selected, not installed

Newest named debug APK present:

| Path | Bytes | Last write |
| --- | --- | --- |
| `outputs/apk/TriGuideAI-0.4.0-debug.apk` | 81,438,490 | 2026-10-18 07:51:11 |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,438,490 | same timestamp |

`aapt dump badging` (build-tools 36.0.0) on `TriGuideAI-0.4.0-debug.apk`: `package name='com.triguide.ai'`, `versionCode='4'`, `versionName='0.4.0'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Launchable activity: `com.triguide.ai.MainActivity`. Permissions: `android.permission.CAMERA` and the AndroidX non-exported receiver permission.

Older copies also on disk, not chosen: `TriGuideAI-0.3.0-debug.apk` (07:28), `0.2.0` (07:09), `0.1.0` (06:47).

`adb install -r` was not executed in that check, including not against the emulator.

Later the same morning, after the emulator was restarted, `adb devices -l` listed both `emulator-5554` and `55aqpfqkvw9xbafe` (`device`, manufacturer Xiaomi, model `22101316G`). The final 0.4.0 APK was installed on `emulator-5554` only. The phone package was still absent. No phone UI was opened. The MTP-only result above stands for the earlier polls.

## 2026-10-18 — Phase 5 navigation, APK, emulator smoke

**Status: PASS_WITH_WARNINGS**

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Commands from `android-app/`:

```text
.\gradlew.bat :app:testDebugUnitTest :sim-core:test --stacktrace --no-daemon
.\gradlew.bat :sim-core:test --rerun-tasks --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

- Combined unit-test command: **BUILD SUCCESSFUL in 1m 29s**. `:sim-core:test` was UP-TO-DATE in that invocation, so it was not re-executed there.
- Forced kinematics rerun: **BUILD SUCCESSFUL in 21s**.
- `assembleDebug`: **BUILD SUCCESSFUL in 37s**. `stripDebugDebugSymbols` was UP-TO-DATE and printed no new strip warning in this run. BUILD-001 remains from earlier builds.

### Unit tests

| Suite | tests | failures | errors | time |
| --- | --- | --- | --- | --- |
| `com.triguide.ai.domain.NavigationDecisionEngineTest` | 32 | 0 | 0 | 0.092 s |
| `com.triguide.ai.domain.DetectionReportsTest` | 12 | 0 | 0 | 0.065 s |
| `com.triguide.ai.domain.PerformanceMonitorTest` | 4 | 0 | 0 | 0.014 s |
| `com.triguide.ai.sim.TricycleKinematicsTest` | 9 | 0 | 0 | 0.007 s |

App total 48/48. Sim-core 9/9. No instrumentation tests were run.

Navigation cases covered by `NavigationDecisionEngineTest`: obstacle on the left, obstacle on the right, slight versus full turn, center box, empty frame, both sides, a dominant side, a camera sample that claims metric depth, contradictory geometry, stale frame, missing timestamp, rapid direction changes, a delayed reversal, synthetic corridor clear, synthetic center obstacle, synthetic both sides, and the virtual wheel following a confirmed command.

### APK

| Path | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,293,198 | `26532FEC9A283CE4B34D4F57840E47659ED27BCF8866B8FC251C8A732DF2B6C0` |
| `outputs/apk/TriGuideAI-0.3.0-debug.apk` | 81,293,198 | same hash |

`aapt dump badging` (build-tools 36.0.0): `versionName='0.3.0'`, `versionCode='3'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Native ABIs: arm64-v8a, armeabi-v7a, x86, x86_64. Asset `assets/models/efficientdet_lite0.tflite` is in the APK. `aapt dump permissions`: `CAMERA` plus the AndroidX non-exported receiver permission. `INTERNET` is not in the APK.

### Emulator smoke (not a phone, not an accuracy test)

**Status: PASS_WITH_WARNINGS**

- `adb devices` before boot: no devices. After boot: `emulator-5554` only. No phone.
- AVD `Pixel_8a`, emulator 36.1.9.0. System image path logged by the emulator: `system-images\android-36\google_apis_playstore_ps16k\x86_64`.
- `adb shell getprop sys.boot_completed` → `1`. `ro.product.model` → `sdk_gphone16k_x86_64`. ABI `x86_64`. `wm size` → `1080x2400`.
- `adb install -r outputs\apk\TriGuideAI-0.3.0-debug.apk` → `Success`.
- `adb shell am start -n com.triguide.ai/.MainActivity` → started. One launch `pidof` → `7403`, later relaunches `14022` and `16613`. `dumpsys activity activities` showed `com.triguide.ai/.MainActivity` as `topResumedActivity` on the first launch.
- Home text included `Object detection: READY. MediaPipe EfficientDet-Lite0 int8`, `Navigation: Unknown. None`, and `Floor segmentation: NOT_STARTED. Disabled.`
- Robot Simulation, manual mode, before any button press: `Decision: Manual`, `Confidence: not used`, `Front wheel response: 0.0 degrees`, `Illustrative path, not verified.`
- About 2 s after selecting Synthetic world on that first visit: `Decision: Straight`, confidence `80 percent`, front wheel `0.0 degrees`, virtual speed `0.40 m/s`, reason `Synthetic map: the authored list has no obstacle in the forward corridor.`
- Fresh launch, about 0.7 s after selecting Synthetic world: `Decision: Slight right`, reason `Synthetic map: left barrel is left of the virtual robot. Steering slight right. This is a simulator map.`, confidence `80 percent`, front wheel `-8.6 degrees`, virtual speed `0.25 m/s`, source `Synthetic map`.
- `adb shell pm grant com.triguide.ai android.permission.CAMERA`, then a tap on Start Camera.
- Live Vision text: `Decision: Hold`. Reason: `No confirmed passage. An empty detection is not open space. Depth is not measured.` Confidence `55 percent`. Front wheel `0.0 degrees`. Source `Camera 2D`. The research-mode line and `Illustrative path, not verified.` were on screen. Detector line: `found no objects at or above 0.50 confidence.` Performance line: `Preview 28 FPS. Inference 8 FPS, last 51 ms, average 71 ms.`
- Logcat `TriGuideDetect`: `detector ready model=MediaPipe EfficientDet-Lite0 int8 delegate=CPU`. `first inference latencyMs=182.0 objects=0`.
- Logcat `TriGuideNav`: `None UNKNOWN No real camera frame is available...` then `Camera 2D HOLD No confirmed passage...`
- Filtered `AndroidRuntime:E` had no FATAL line.
- Zero objects means no box was drawn and the camera path did not turn the wheel. This is not an accuracy score.
- The first `uiautomator` dump of the session failed with `null root node`. A later upward swipe from low on the screen landed on the launcher. The readings above are from the retries that showed TriGuide text.
- `adb emu kill` stopped the emulator. A follow-up `adb devices` listed no devices.

## 2026-10-18 — Phase 3 detector build, unit tests, emulator smoke

**Status: PASS_WITH_WARNINGS**

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Commands from `android-app/`:

```text
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :sim-core:test --rerun-tasks --stacktrace --no-daemon
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --stacktrace --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

- The first of those failed earlier in the session on a missing Compose `nativeCanvas` import. That was fixed before the green runs.
- `--rerun-tasks` run: **BUILD SUCCESSFUL**. `:sim-core:test` 9/9, time 0.016 s. App unit tests also passed in that run; the XML below is from the later `:app:testDebugUnitTest`.
- Overlay-fix run: **BUILD SUCCESSFUL in 28s**. `:app:testDebugUnitTest` executed.
- Manifest-only rebuild (network permissions removed): **BUILD SUCCESSFUL in 33s**. Unit tests were not executed again after that manifest edit. The Kotlin under test had not changed.

### Unit tests

| Suite | tests | failures | errors | time |
| --- | --- | --- | --- | --- |
| `com.triguide.ai.domain.DetectionReportsTest` | 12 | 0 | 0 | 0.050 s |
| `com.triguide.ai.domain.PerformanceMonitorTest` | 4 | 0 | 0 | 0.036 s |
| `com.triguide.ai.sim.TricycleKinematicsTest` | 9 | 0 | 0 | 0.016 s |

No instrumentation tests exist under `src/androidTest`. None were run.

Warning during `:app:stripDebugDebugSymbols`: unable to strip `libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, and `libmediapipe_tasks_jni.so`. They were packaged as-is. The build still succeeded.

### APK (final copy)

| Path | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,322,624 | `80CA809B9E4C3BA91181E62F1D6E01A06A0DD40ADDC3A5A0DEEAAA4E0CE0AC43` |
| `outputs/apk/TriGuideAI-0.2.0-debug.apk` | 81,322,624 | same hash |

`aapt dump badging`: `versionName='0.2.0'`, `versionCode='2'`, `minSdk` 26, `targetSdk` 35. Native ABIs: arm64-v8a, armeabi-v7a, x86, x86_64. Asset `assets/models/efficientdet_lite0.tflite` is 4,602,795 bytes inside the APK. Merged permissions after the strip: `CAMERA` only, plus the AndroidX non-exported receiver permission. `INTERNET` is not in the APK.

The size jump from the 0.1.0 APK is the MediaPipe JNI libraries plus this model.

### Emulator smoke (not a phone, not an accuracy test)

**Status: PASS_WITH_WARNINGS**

- `adb devices` before boot: no devices.
- AVD `Pixel_8a`, emulator 36.1.9.0. System image: `system-images\android-36\google_apis_playstore_ps16k\x86_64`.
- `adb shell getprop sys.boot_completed` → `1`. `ro.product.model` → `sdk_gphone16k_x86_64`. ABI `x86_64`.
- `adb install -r outputs\apk\TriGuideAI-0.2.0-debug.apk` → `Success`.
- `adb shell am start -n com.triguide.ai/.MainActivity` → started. `pidof com.triguide.ai` → `8406`. `dumpsys activity activities` showed `com.triguide.ai/.MainActivity` as `topResumedActivity`.
- Logcat `TriGuideDetect`: `detector ready model=MediaPipe EfficientDet-Lite0 int8 delegate=CPU`.
- UI hierarchy on Home included `Object detection: READY. MediaPipe EfficientDet-Lite0 int8`.
- `adb shell pm grant com.triguide.ai android.permission.CAMERA`, then a tap on Start Camera (bounds from `uiautomator dump`).
- Live Vision text then included `MediaPipe EfficientDet-Lite0 int8 found no objects at or above 0.50 confidence.`
- Two measured on-screen samples while streaming:
  - Preview 20 FPS. Inference 4 FPS, last 260 ms, average 195 ms.
  - Preview 9 FPS. Inference 3 FPS, last 83 ms, average 246 ms.
- Logcat first result: `first inference latencyMs=769.0 objects=0` (this first call is slower than the later samples).
- No `AndroidRuntime` FATAL line was present in the filtered logcat before the emulator was stopped.
- Zero objects means no box was drawn. Overlay alignment was **not** visually checked. This virtual camera is not a labeled scene, so the empty result is not an accuracy score.
- `adb emu kill` stopped the emulator.

## 2026-10-18 — Gradle debug APK and kinematics tests

**Status: PASS_WITH_WARNINGS**

Command (PowerShell, `android-app/`):

```text
$env:JAVA_HOME = C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1
$env:ANDROID_HOME = C:\Users\yassi\AppData\Local\Android\Sdk
.\gradlew.bat :app:assembleDebug :sim-core:test --stacktrace --no-daemon
```

- Gradle 8.11.1 (wrapper), JDK 17.0.20.1.
- Result: **BUILD SUCCESSFUL in 2m 49s**, 41 actionable tasks, process exit code 0.
- Warning during `:app:stripDebugDebugSymbols`: unable to strip `libandroidx.graphics.path.so` and `libimage_processing_util_jni.so`; they were packaged as-is. Build still succeeded.

### Unit tests

`:sim-core:test` report `android-app/sim-core/build/test-results/test/TEST-com.triguide.ai.sim.TricycleKinematicsTest.xml`

- tests = 9, skipped = 0, failures = 0, errors = 0, time = 0.013 s
- Cases: straight line, zero speed, zero dt, positive steer, reverse steer, clamp, input not mutated, wheelbase rejected, negative dt rejected

### APK

| Path | Bytes |
| --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 11,504,492 |
| `outputs/apk/TriGuideAI-0.1.0-debug.apk` (copy) | 11,504,492 |

`versionName` 0.1.0. No release APK was built.

## 2026-10-18 — Emulator install and launch

**Status: PASS_WITH_WARNINGS**

- `adb devices` before the emulator: no phones.
- AVD `Pixel_8a` started (`emulator` 36.1.9.0). System image path logged by the emulator: `system-images\android-36\google_apis_playstore_ps16k\x86_64`.
- `adb -s emulator-5554 shell getprop sys.boot_completed` returned `1`.
- `adb -s emulator-5554 install -r outputs\apk\TriGuideAI-0.1.0-debug.apk` → `Success`.
- `adb shell am start -n com.triguide.ai/.MainActivity` → `Starting: Intent { cmp=com.triguide.ai/.MainActivity }`.
- `pidof com.triguide.ai` → `1793`.
- `dumpsys activity activities` showed `com.triguide.ai/.MainActivity` as `topResumedActivity`, `visible=true`.
- Filtered logcat (`AndroidRuntime:E`) did not print a FATAL line for this launch.
- Camera permission, live preview, simulator buttons, and TalkBack were **not** exercised. A screenshot attempt was discarded because PowerShell redirection corrupted the PNG. The emulator was then stopped (`adb emu kill`).

## Still open

| Check | Status | Why |
| --- | --- | --- |
| Redmi / physical phone install and launch | PASS_WITH_WARNINGS | 2026-10-18 later: `55aqpfqkvw9xbafe` (Redmi Note 12 Pro 5G, Android 14) authorized. `TriGuideAI-0.4.0-debug.apk` installed and `MainActivity` resumed. No crash. Camera permission still denied. Earlier MTP-only polls in this file are superseded for install |
| Phone-only testing | Policy | User: installs and smoke tests on this Redmi only. Emulator results in this file are non-authoritative going forward |
| GuideLens `assembleDebug` | NOT_STARTED | `compileSdk` 36 platform is not installed; app is not the shipping target |
| On-device detection accuracy | NOT_STARTED | Camera was not started on the Redmi. No labeled scene was run |
| Floor segmentation | NOT_STARTED | Disabled. No floor model is loaded. Home on the Redmi still showed `NOT_STARTED` |
| Camera-derived turn on a real scene | NOT_STARTED | Start Camera was not tapped. `CAMERA` is denied until the user allows it |
| Redmi inference latency | NOT_STARTED | App is installed. Live Vision was not opened, so there is no phone FPS number |
| TalkBack and simulator buttons on the phone | NOT_STARTED | Home was read. Robot Simulation, Test Scenarios, Diagnostics, and Settings were not opened |
