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

