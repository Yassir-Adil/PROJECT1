# TriGuide-AI

Android AI navigation and **virtual** three-wheel robotic steering simulation for visually impaired users. The phone is the primary computer — camera, on-device AI, and guidance — with **no physical robot hardware** in the current phase.

## Status

**Phase 0 — Environment bootstrap.** Docs and agent rules are in place. Android toolchain setup is still required before the first APK build. See `docs/ENVIRONMENT_AUDIT.md` and `docs/PROGRESS.md`.

## Quick links

| Doc | Purpose |
| --- | --- |
| `AGENTS.md` | Standing instructions for AI agents |
| `docs/DEVELOPMENT_PLAN.md` | Phased plan |
| `docs/RESEARCH_NOTES.md` | OpenBot, GuideLens, MediaPipe, etc. |
| `docs/DECISIONS.md` | Why we chose X |
| `docs/KNOWN_ISSUES.md` | Open blockers |

## Local setup (high level)

1. Install **JDK 17** and **Android Studio** (or complete Android SDK command-line packages).
2. Set `JAVA_HOME` (17) and `ANDROID_HOME` to `%LOCALAPPDATA%\Android\Sdk`.
3. Enable USB debugging on your phone; confirm with `adb devices`.
4. After the Android app exists: `./gradlew assembleDebug` and install the APK.

## License

To be decided when application code is added. Respect licenses of any vendored open-source components.
