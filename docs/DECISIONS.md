# Decision Log

## 2026-10-18 — Bootstrap without overwriting

- Repo was empty; created docs + agent rules only (no Android app code yet).
- **Why:** User asked for environment audit and persistent rules before the main project prompt / first build.

## 2026-10-18 — Local (This PC) over Cloud Agents for builds

- Android SDK, ADB, and Redmi USB testing require the local machine.
- Cloud agents remain optional for research-only tasks later.

## 2026-10-18 — No physical hardware in current scope

- Focus: camera AI, obstacle cues, virtual steering, three-wheel **simulator**, installable APK.
- Hardware purchase/build deferred until explicit user request.

## 2026-10-18 — Prefer on-device MediaPipe over cloud GuideLens stack

- GuideLens/WorldLens patterns inform UX; default runtime should work offline on the phone when possible.

## 2026-10-18 — JDK 17 path

- System MSI install of JDK 17 failed (exit 1603, likely needs admin).
- Installed Temurin 17 ZIP to `C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1` and set User `JAVA_HOME` there.
- Keep JDK 25 installed; Android Gradle must use 17.

## 2026-10-18 — CLI SDK before Android Studio

- Completed platforms/build-tools via `sdkmanager` so Phase 1 can proceed without Studio.
- Android Studio remains recommended later for Logcat / device UI convenience.

## 2026-10-18 — ADR: foundation Option B (new MediaPipe-ready app)

**Decision:** Option B. Ship `android-app/` as a new Kotlin + Compose + CameraX project. Do not fork GuideLens (A) and do not base the product on OpenBot (C).

**Evidence**

- GuideLens models are present and look like real ONNX protobufs (`yolov8s-worldv2_int8.onnx` 13,522,706 bytes, `floor_segmentation_int8.onnx` 12,454,891 bytes, header text `onnx.quantiz`). Loaders call ONNX Runtime. See `research/model-audit.md`.
- GuideLens is GPL-3.0. Medicine flow uses a network server / API key. `compileSdk` is 36; this PC only has platforms 34 and 35, so GuideLens was not built. Its UI is not the TriGuide screen set.
- MediaPipe samples are Apache-2.0 and already show an on-device object-detection Android path. They are the planned detector later, not linked in 0.1.0.
- OpenBot is MIT and is a phone-plus-chassis stack. The sparse clone used for notes did not include the Android tree. A physical body is out of scope.
- Option B was the shortest path to an installable APK with a real CameraX preview, an explicit non-detector, and a tested virtual tricycle model.

**Consequences**

- `minSdk` 26, `compileSdk`/`targetSdk` 35, JDK 17, Gradle wrapper 8.11.1, AGP 8.7.3, Kotlin 2.0.21.
- No GuideLens source or ONNX file is copied into `android-app/`.
- Detection status stays `NOT_IMPLEMENTED` until a licensed on-device model is integrated and measured.

