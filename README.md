# TriGuide-AI

Android AI navigation and **virtual** three-wheel robotic steering simulation for visually impaired users. The phone is the primary computer — camera, on-device AI, and guidance — with **no physical robot hardware** in the current phase.

## Status

The version string is `versionName` in `android-app/app/build.gradle.kts`. What was actually built and installed is in `docs/PROGRESS.md` and `docs/TEST_RESULTS.md`.

Debug APKs from the script below land in `outputs/apk/TriGuideAI-<versionName>-debug.apk`.

## Scripts / الأوامر

From the repo root in PowerShell:

```powershell
.\scripts\setup\ensure-android-path.ps1
.\scripts\build\assemble-debug.ps1
.\scripts\test\redmi-install.ps1
```

| Command | English | عربي |
| --- | --- | --- |
| `.\scripts\setup\ensure-android-path.ps1` | Android SDK on PATH for this session | مسار أندرويد لهذه الجلسة |
| `.\scripts\setup\ensure-android-path.ps1 -Persist` | Save that SDK path for new terminals | ثبّت المسار للمستخدم، ثم افتح طرفية جديدة |
| `.\scripts\build\assemble-debug.ps1` | JDK 17 debug APK into `outputs/apk` | بناء نسخة التصحيح ونسخها إلى outputs/apk |
| `.\scripts\test\redmi-install.ps1` | Install the newest APK on serial `55aqpfqkvw9xbafe` only | تثبيت آخر APK على الريدمي فقط |

The install script stops if that phone is missing or not in the `device` state. It does not install on an emulator or any other serial.

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

1. Install **JDK 17** and the Android SDK (Android Studio or command-line packages) at `%LOCALAPPDATA%\Android\Sdk`.
2. Run `.\scripts\setup\ensure-android-path.ps1` in the session you will build from. Add `-Persist` if new terminals should see `ANDROID_HOME` and `platform-tools`.
3. Enable USB debugging on the Redmi (`55aqpfqkvw9xbafe`) and run `.\scripts\build\assemble-debug.ps1`, then `.\scripts\test\redmi-install.ps1`.

## License

To be decided when application code is added. Respect licenses of any vendored open-source components.
