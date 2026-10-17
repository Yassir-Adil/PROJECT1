# Test results

Statuses: NOT_STARTED | IN_PROGRESS | BLOCKED | PASS | PASS_WITH_WARNINGS | FAIL

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
| Redmi / physical phone install | NOT_STARTED | No authorized device on `adb devices` |
| GuideLens `assembleDebug` | NOT_STARTED | `compileSdk` 36 platform is not installed; app is not the shipping target |
| On-device detection accuracy | NOT_STARTED | Emulator live view returned zero objects. No labeled scene was run |
| Redmi inference latency | NOT_STARTED | No phone on ADB. Emulator numbers are recorded above and are not a phone result |
| Interactive UI pass on emulator | PASS_WITH_WARNINGS | Home and Live Vision text were read via `uiautomator`. TalkBack was not used. Simulator buttons were not tapped in this session |
