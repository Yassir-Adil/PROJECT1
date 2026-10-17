# Test results

Statuses: NOT_STARTED | IN_PROGRESS | BLOCKED | PASS | PASS_WITH_WARNINGS | FAIL

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

## Not run

| Check | Status | Why |
| --- | --- | --- |
| Redmi / physical phone install | NOT_STARTED | No authorized device on `adb devices` |
| GuideLens `assembleDebug` | NOT_STARTED | `compileSdk` 36 platform is not installed; app is not the shipping target |
| On-device detection accuracy | NOT_STARTED | No detector is in the APK |
| Interactive UI pass on emulator | NOT_STARTED | Launch only |
