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
| ENV-010 | Medium | No authorized phone on `adb devices` | Open — needs USB debugging |
| BUILD-001 | Low | `stripDebugDebugSymbols` left native libs unstripped (`libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, and now `libmediapipe_tasks_jni.so`); debug APK still built | Open |
| RES-001 | Info | GuideLens `assembleDebug` not run (`compileSdk` 36 platform missing; GPL, not shipping) | Accepted for Option B |
| TEST-001 | Low | Phase 3 left simulator buttons and TalkBack untested. The 0.3.0 session read Home, manual simulation, synthetic simulation, and Live Vision through `uiautomator`. TalkBack is still open (see TEST-002) | Open |
| DET-001 | Medium | No Redmi measurement. Phase 3 emulator samples were about 3–4 FPS. The 0.3.0 emulator sample was preview 28 FPS, inference 8 FPS, last 51 ms, average 71 ms, first call 182 ms. That is still not a phone result | Open |
| DET-002 | Medium | Emulator camera returned zero objects again in 0.3.0, so bounding-box alignment and a camera-triggered turn were not seen | Open |
| DET-003 | Medium | No labeled true-positive / false-alarm count | Open |
| DET-004 | Info | Screen zone (left/center/right) is the box center in the frame. It is not a distance | Accepted |
| NAV-001 | Medium | Camera steering has no metric depth. The 0.3.0 emulator held at wheel 0 because the frame was empty. A real room has not been run | Open |
| NAV-002 | Info | Floor segmentation is NOT_STARTED and disabled. No floor model is loaded | Accepted for 0.3.0 |
| NAV-003 | Info | The drawn path is a kinematic sketch of the current command. It is not a verified free path | Accepted |
| TEST-002 | Low | TalkBack was not used. Manual Forward / Steer buttons were not tapped. System Back left the activity instead of the in-app Back control, which was below the fold | Open |
