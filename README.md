# TriGuide-AI

Android AI navigation and **virtual** three-wheel robotic steering simulation for visually impaired users. The phone is the primary computer — camera, on-device AI, and guidance — with **no physical robot hardware** in the current phase.

## Status

Single-camera semantic/raw-depth **diagnostic** integration and actual device
results: [2026-10-20 report](docs/SEMANTIC_DEPTH_FUSION_2026-10-20.md).
It does not enable walking/turn/clear guidance; educational debug probe only.

The version string is `versionName` in `android-app/app/build.gradle.kts`. What was actually built and installed is in `docs/PROGRESS.md` and `docs/TEST_RESULTS.md`.

Debug APKs from the script below land in `outputs/apk/TriGuideAI-<versionName>-debug.apk`.

## Scripts / الأوامر

Small public RGB-D coordinate evaluation (host-only, **not** model training or
walking accuracy): see `docs/PUBLIC_RGBD_ORACLE_2026-10-20.md`. Install host
requirements from `research/requirements-eval.txt` in the evaluation environment.
`research/hypersim_fixture.py --fetch --output-root <new-directory-outside-repo>`
downloads only the pinned public validation members, refusing existing roots.
`research/evaluate_hypersim_fixture.py --fixture-root <that-directory> --output <new-report-directory>`
runs offline with hash/calibration checks. Data licences remain separate; do not
commit fixtures. No phone, network permission, model or Android-runtime change.

Local engineering verification (tests, Lint, debug/release builds, APK model and
privacy checks; **no phone install/control**):

```powershell
.\scripts\test\verify-local.ps1
```

Defaults to offline Gradle and existing tools. Prerequisites, explicit dependency
download option, current architecture, ranked gaps and evidence boundaries:
[`docs/ENGINEERING_AUDIT_2026-10-19.md`](docs/ENGINEERING_AUDIT_2026-10-19.md).
Personal captures and generated reports stay local/ignored; publish only reviewed,
redacted summaries. A passing gate is not walking-safety validation.

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
| `docs/ENGINEERING_AUDIT_2026-10-19.md` | Current audit, local quality gate, data policy and prioritized next work |
| `docs/DECISIONS.md` | Why Option B |
| `docs/TEST_RESULTS.md` | Commands that actually ran |
| `docs/SAFETY_LIMITATIONS.md` | What this build must not be used for |
| `research/model-audit.md` | GuideLens ONNX files |
| `docs/KNOWN_ISSUES.md` | Open blockers |
| `docs/STORAGE_AND_RAW_DEPTH_2026-10-20.md` | 0.9.23 actual gate/install evidence and raw-depth limits |
| `docs/FIELD_ISSUE_WORKFLOW.md` | Local user-reported scene failures and tests-first repair |
| `docs/WORK_QUEUE.md` | Shared Codex/Cursor ownership and next bounded tasks |

## Local setup (high level)

1. Install **JDK 17** and the Android SDK (Android Studio or command-line packages) at `%LOCALAPPDATA%\Android\Sdk`.
2. Run `.\scripts\setup\ensure-android-path.ps1` in the session you will build from. Add `-Persist` if new terminals should see `ANDROID_HOME` and `platform-tools`.
3. Enable USB debugging on the Redmi (`55aqpfqkvw9xbafe`) and run `.\scripts\build\assemble-debug.ps1`, then `.\scripts\test\redmi-install.ps1`.

## License

To be decided when application code is added. Respect licenses of any vendored open-source components.
