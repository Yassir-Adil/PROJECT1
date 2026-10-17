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
| TEST-001 | Medium | Emulator Live Vision was opened and showed a real empty result. Simulator buttons and TalkBack were not exercised this session | Open |
| DET-001 | Medium | No Redmi measurement. Emulator inference (about 3–4 FPS, last sample 83–260 ms, average about 195–246 ms) is not a phone result | Open |
| DET-002 | Medium | Emulator camera returned zero objects, so bounding-box alignment was not seen | Open |
| DET-003 | Medium | No labeled true-positive / false-alarm count | Open |
| DET-004 | Info | Screen zone (left/center/right) is the box center in the frame. It is not a distance | Accepted |
