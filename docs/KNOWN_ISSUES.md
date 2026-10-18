# Known Issues

| ID | Severity | Issue | Status |
| --- | --- | --- | --- |
| ENV-001 | — | Android Studio not installed | **Resolved** — present at `C:\Program Files\Android\Android Studio`, build `AI-243.22562.218.2431.13114758` |
| ENV-002 | — | SDK platforms / build-tools | **Resolved** (34/35 + build-tools) |
| ENV-003 | Low | Restart Cursor/terminals so User PATH picks up `adb` / JDK17 | Open until restart |
| ENV-004 | — | JDK 17 for Android | **Resolved** (user-local Temurin 17) |
| ENV-005 | Info | winget/choco/scoop not on PATH; used direct downloads | Accepted |
| ENV-006 | Low | GitHub not connected; no remote backups | Open |
| ENV-007 | — | No agents before bootstrap | Closed |
| ENV-008 | Info | JDK 25 MSI still default `java` on some shells until PATH order settles | Monitor |
| ENV-009 | Info | cmdline-tools XML version warning (v3 vs v4); packages still installed | Monitor |
| ENV-010 | — | Redmi Note 12 Pro 5G was MTP-only earlier on 2026-10-18 (`VID_2717` `PID_FF40`) | **Resolved** — `55aqpfqkvw9xbafe` is `device` (model `22101316G`, Android 14, HyperOS OS2.0.16.0). `com.triguide.ai` 0.4.1 is installed and Home launched |
| POL-001 | Info | User policy: installs and smoke tests run only on the personal Redmi. Do not use the emulator for those checks. Emulator results already written in `docs/TEST_RESULTS.md` are non-authoritative for this user going forward | Accepted |
| BUILD-001 | Low | `stripDebugDebugSymbols` left native libs unstripped (`libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, and now `libmediapipe_tasks_jni.so`); debug APK still built | Open |
| RES-001 | Info | GuideLens `assembleDebug` not run (`compileSdk` 36 platform missing; GPL, not shipping) | Accepted for Option B |
| TEST-001 | Low | Phase 3 left simulator buttons and TalkBack untested. The 0.3.0 session read Home, manual simulation, synthetic simulation, and Live Vision through `uiautomator`. TalkBack is still open (see TEST-002) | Open |
| DET-001 | Medium | 0.4.0 on the Redmi reported Preview 14 FPS and Inference 9 FPS (last 53 ms, average 59 ms) and logged a chair in the center. 0.4.1 Home shows READY. Live Vision on 0.4.1 was not opened, and PC taps are blocked (ENV-011) | Open |
| DET-002 | Medium | Emulator camera returned zero objects again in 0.3.0, so bounding-box alignment and a camera-triggered turn were not seen | Open |
| DET-003 | Medium | No labeled true-positive / false-alarm count | Open |
| DET-004 | Info | Screen zone (left/center/right) is the box center in the frame. It is not a distance | Accepted |
| NAV-001 | Medium | Camera steering has no metric depth. The 0.3.0 emulator held at wheel 0 because the frame was empty. A real room has not been run | Open |
| NAV-003 | Info | The drawn path is a kinematic sketch of the current command. It is not a verified free path | Accepted |
| TEST-002 | Low | TalkBack was not walked. Manual Forward / Steer were not tapped. 0.4.1 moves Back to the top and handles the system Back key in code. That path was not tapped on the phone because input injection is denied (ENV-011) | Open |
| VOICE-001 | Info | 0.4.0 logcat on the Redmi: `speak result=0` for `Obstacle detected. Hold.` (engine accepted it). Speaker audio was not confirmed by ear. 0.4.1 Home still says offline English and Arabic voices are installed. No sample was played. This APK has no internet permission | Open |
| VOICE-002 | Info | Quiet mode and the speech on/off switch were not tapped on the Redmi. Gate unit tests cover both | Open |
| ENV-011 | Medium | HyperOS blocked `adb shell input` (`SecurityException` / `INJECT_EVENTS`) during the 0.4.1 check. One `adb install` of 0.4.1 returned `INSTALL_FAILED_USER_RESTRICTED`; the retry succeeded. On 0.6.0, `input tap`, `input swipe`, and `input keyevent` were accepted. The block can return. Steps: `docs/DEVICE_COMPATIBILITY.md` | Open |
| FP-001 | High | Fingerprint triple-tap cannot be implemented. On this Redmi the reader is the power button (`isPowerbuttonFps: true`, `isUdfps: false`). Android does not deliver those presses to a normal app. `BiometricPrompt` authenticates a finger; it does not count taps. Xiaomi’s `fingerprint_double_tap` setting is a system action, currently `none`, and is not an app API | Accepted — 0.6.0 does not read the power button. Screen triple-tap restarts guidance. The limitation is spoken once on a cold start |
| NAV-002 | Info | Floor segmentation is NOT_STARTED and disabled. No floor model is loaded. No floor mask is drawn | Accepted for 0.6.0 |
