# TriGuide-AI

Android AI navigation and **virtual** three-wheel robotic steering simulation for visually impaired users. The phone is the primary computer — camera, on-device AI, and guidance — with **no physical robot hardware** in the current phase.

## Status

**Debug APK 0.1.0 is built.** Live camera preview is wired. Object detection is not implemented. The robot screen is a manual virtual tricycle. See `docs/TEST_RESULTS.md` and `docs/PROGRESS.md`.

APK: `outputs/apk/TriGuideAI-0.1.0-debug.apk`

The app was installed and launched on the local `Pixel_8a` emulator. It has not been installed on a Redmi yet.

## Quick links

| Doc | Purpose |
| --- | --- |
| `AGENTS.md` | Standing instructions for AI agents |
| `docs/DEVELOPMENT_PLAN.md` | Phased plan |
| `docs/DECISIONS.md` | Why Option B |
| `docs/TEST_RESULTS.md` | Commands that actually ran |
| `docs/SAFETY_LIMITATIONS.md` | What this build must not be used for |
| `research/model-audit.md` | GuideLens ONNX files |
| `docs/KNOWN_ISSUES.md` | Open blockers |

## Local setup (high level)

1. Install **JDK 17** and **Android Studio** (or complete Android SDK command-line packages).
2. Set `JAVA_HOME` (17) and `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk`.
3. Enable USB debugging on your phone; confirm with `adb devices`.
4. After the Android app exists: `./gradlew assembleDebug` and install the APK.

## License

To be decided when application code is added. Respect licenses of any vendored open-source components.
