# Test results

Statuses: NOT_STARTED | IN_PROGRESS | BLOCKED | PASS | PASS_WITH_WARNINGS | FAIL

## 2026-10-20 — 0.9.23 final gate and Redmi refusal test

**PASS (engineering), physical depth/UI acceptance still pending.**
`outputs/reports/quality-gate/20261019T172320780644Z/summary.json`:
424 app +10 sim +19 policy +10 evaluation =463 tests, no failure/error/skip;
both builds/Lint/APK policy pass,54 debug/34 release warnings, no errors.
Source fingerprint, RED evidence, corrected integration failures, APK hashes
and exact boundaries: `docs/STORAGE_AND_RAW_DEPTH_2026-10-20.md`.
Redmi install reports0.9.23/36 and pulled APK hash matches the final gate.
Invalid duration0 emits one FAIL/invalid_duration result with0 frames then
CLOSED. Valid raw depth, main UI/voice/TalkBack, height accuracy and walking
are not tested here. Phone locked; no bypass; app force-stopped.

## 2026-10-19 — Local engineering gate and private-input regression

**Engineering gate: PASS, with documented Lint warnings.** Run:
`scripts/test/verify-local.ps1`, offline default, existing JDK17/SDK35/Python eval
environment. Final machine-readable result:
`outputs/reports/quality-gate/20261019T083518346232Z/summary.json`.

| Check | Actual result |
| --- | --- |
| App JVM (forced test execution) | 339, zero failures/errors/skips |
| sim-core JVM (forced test execution) | 10, zero failures/errors/skips |
| New policy unittest suite | 18, zero failures/errors/skips |
| Existing evaluation unittest suite | 10, zero failures/errors/skips |
| Scene-manifest example | Valid template, no real labeled media; not an accuracy pass |
| Debug / release assembly | PASS; no staging over existing outputs/apk artifact |
| Lint debug / release | 0 errors/fatal; 40 / 30 warnings, not suppressed |
| APK content | Four pinned models in debug, one in release; no private image |
| Permissions / debug isolation | Only camera/vibrate/internal receiver permission; debug activities DUMP-protected; release no ARCore/diagnostic activity/model |
| Local evidence policy | 7 ignore probes, no private tracked files in known roots; source backup exclusions present |

Initial Lint attempt offline failed on uncached collection-jvm:1.4.0, not a
source defect. One online public dependency-resolution run then revealed
ProduceStateDoesNotAssignValue at existing `value++`; explicit assignment fixed
the Lint issue without changing cadence or disabling the check. Subsequent full
gate ran offline. Version-update, TOML and localization warnings remain; no
dependency/toolchain upgrade is claimed. Scoped source hygiene checks are
separate from old bridge Markdown hard-break whitespace already in this tree.

New tests include private APK fixture/unreviewed weight, missing/changed model,
duplicate ZIP entry, added sensitive/network permission, ARCore leakage into
release, debug protection, missing backup exclusion, force-tracked private
capture, changed/untracked/deleted source fingerprints, and empty/failed/skipped
test-result rejection. These are not functional accuracy tests of camera scenes.

**Phone static-input regression: PASS**, separate from the local-only gate:
`outputs/reports/quality-gate/20261019T082859291386Z/phone-static/summary.json`.
Backup installed APK first; temporary candidate installed over USB with data
preserved. Absent `files/diagnostic-inputs/scene.jpg` refused explicitly. Supplied
consented existing fixture then decoded as 1440x2560, input SHA-256
`63a5308d3c3e0413ce761f575eccc057a6fd4f7053f2a89e20132bdeb90d3711`, and
MOSAIC completed 3 CPU invokes after two warmups. No camera/depth/voice event.
This CPU-only short input test is not the NNAPI live benchmark or accuracy QA.
Deleted only temporary phone input copies created for this run; local image is
preserved at `outputs/reports/local-inputs/wall-scene.jpg`.

Original 0.9.22/35 restored, pulled and verified identical before/after:
`05e3bcbbb8df465cd8b91cd1de3c89f19605ec5a3e5f300ddd48b16a3c5473ca`.
App left force-stopped; no auto-resume. Working debug SHA is
`76a7ea52e3de56d8eba8df8b46d2490659ea19b813dbb8d635020077e15e17e6`;
unchanged release SHA is
`34c42542c68f4e1df7d77eaa12c82c12c158a5452260f068009054fe1582e05f`.
Old APKs may contain the old private fixture; do not distribute them based on a
PASS of the newly built APK. No push/publication, upload or paid activation.

Not re-tested: real UI lifecycle/camera scenes, hearing/TalkBack, navigation
accuracy, walking, sustained thermal/battery or depth/edge-height accuracy.
No critical stopping or evidence gate was relaxed. Audit/plan:
`docs/ENGINEERING_AUDIT_2026-10-19.md`.

## 2026-10-19 — 0.9.22 surface lab, paused return and depth support

**Status: PASS_WITH_WARNINGS.** Final 339 app/10 sim tests and debug/release
build pass (53 s). Nineteen new surface/priority/restore tests. USB phone confirms
ARCore AUTOMATIC/raw support with **frames=0**, no resumed ARCore camera.

- Final real More-button entry, Describe, Stop, result-callback More return and
  Back to Guidance paused pass; no sampled results after Stop. Eight-second
  default-silent regression passes. TTS acceptance is not heard-audio proof.
- Control run callback-to-map p50/p90 50.11/70.40 ms. Earlier 60-s component
  run 42.33/52.46 ms used the pre-lifecycle candidate; no speedup claim.
- Kept installed **0.9.22/35**, exact SHA
  `05E3BCBBB8DF465CD8B91CD1DE3C89F19605EC5A3E5F300DDD48B16A3C5473CA`.
  Original backup preserved; no data clear. Release excludes ARCore/models;
  both manifests offline. Release bytes changed legitimately this round.
- Final raw evidence: `outputs/reports/redmi-evidence/20261019-surfaces-depth/final-v3/`.
  Aborted attempts, viewport assertion mistakes and open height/accuracy/audio/
  lifecycle limits: `docs/SURFACE_LAB_AND_DEPTH_2026-10-19.md`.

## 2026-10-19 — USB versus paired Wi-Fi transport

**Status: PASS_WITH_WARNINGS.** Cable ADB and Windows USB interfaces are healthy
in this sample. Known serial confirmed over both transports, 12/12 shell requests
each. USB p50/p90 **35.99/41.46 ms**, Wi-Fi **41.99/46.65 ms**. One identical
88,732,901-byte APK pull per transport took **2.118 s USB / 3.646 s Wi-Fi**;
both copies hash-match original 0.9.21/34. No install, app launch, source edit,
settings change or repeat JVM build/suite. Single-transfer timing is not a
controlled throughput study, an install-speed guarantee, or an on-device AI
performance result. See `docs/REDMI_USB_TRANSPORT_CHECK_2026-10-19.md`.

## 2026-10-19 — Combined observation UI, explicit voice, and silent regression

**Status: PASS_WITH_WARNINGS.** Final debug candidate tested on paired Redmi.

- **320/320 app**, **10/10 unchanged sim-core**, debug/release assemble pass.
  Nine new tests cover fresh/invalid/expired objects, cautious descriptions,
  empty-frame wording and quiet/off/stopped speech policy.
- Fixed cached-render-clock rejection of newly completed detector frames.
  After correction, a normal Describe tap reached the handler and offline TTS
  (`speech_accepted=true`); Stop produced `reason=user_stop`, with no sampled
  frames/starts in the three-second post-stop log. Audible quality unverified.
- 20-s combined run: 214 detector completions, 165 accepted segmentation results
  (163 measured), **zero description/speech requests**. Callback-to-map p50/p90
  **42.85/52.61 ms**; not capture latency or an accuracy/speedup claim.
- Eight-second default-mode regression remained silent with detector disabled.
  Actual screenshots confirm reused yellow named boxes and original theme.
- Final candidate SHA:
  `565B0145FAF6CC85894FF451C04045B4F54512E4BF03B2DF308E570ED0D38BED`.
  Original 0.9.21/34 restored, installed hash matches
  `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
- Release bytes unchanged; no production navigation or network changes.
  Method, aborted QA attempts, screenshots, raw paths and limits:
  `docs/COMBINED_VISION_PREVIEW_2026-10-19.md`.

## 2026-10-19 — Real camera MOSAIC diagnostic and frame admission

**Status: PASS_WITH_WARNINGS.** Two 20-second CameraX/MOSAIC runs and two
pause/late-output checks passed on Redmi over paired Wi-Fi. Debug-only.

- Final **311/311 app tests** (20 new) pass; unchanged sim-core remains **10/10**.
  Debug/release assemble pass; release hash unchanged.
- Scalar -> bulk RGB observed prepare p50/p90: **56.81/73.11 -> 8.00/8.62 ms**.
  Callback-to-decoded-map p50/p90: **81.65/99.87 -> 30.15/32.17 ms**.
  Not sensor capture-to-result, concurrent detector throughput, or accuracy.
- Measured accepted rates **7.95 -> 6.75/s** under the same 10/s cap but
  different live scenes/camera cadence; no result-FPS improvement claimed.
- Gate tests include invalid/order/cadence/busy/age/stop/restart/concurrency;
  raw-byte packing matches the scalar reference. Static wall/floor proportions
  remain identical after shared-runtime extraction and optimization.
- Each phone cycle restored original **0.9.21/34** with installed APK hash
  `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
- Candidate SHA `1390D008DE44E1F2A48DE202F7AE63F5A385FE77CADBAED5473BFA52BFE517A4`.
- Method/raw paths/review limits: `docs/LIVE_SEGMENTER_DIAGNOSTIC_2026-10-19.md`.
  No navigation, speech, recording, walking, or heard-audio validation added.

## 2026-10-19 — Post-fix Redmi static replay over paired Wi-Fi

**Status: PASS_WITH_WARNINGS.** Wireless pairing/recovery, diagnostic install,
three static batches, and exact original APK restoration verified.

- MOSAIC reference RGB / NNAPI / 1000 runs: p50 **17.06 ms**, p90 **21.14 ms**,
  mean 17.75 ms, **56.35 sequential inferences/s**; 100/100 nodes delegated.
- MOSAIC legacy input / NNAPI / 20 runs: p50 17.56 ms, p90 19.48 ms.
- DeepLab UINT8 reference RGB / CPU / 20 runs: p50 736.88 ms, p90 744.79 ms.
- All three batches completed actual INT32 decoding and logged
  `VOICE_EVENTS=none`. No live camera, walking, guidance, or accuracy test.
- Candidate SHA: `DC0BE8CED37F89C1771D3EFC58B09870252E01E962CB354DFA1F8C833BD9E567`.
- Before/after installed original SHA:
  `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`;
  **0.9.21/34** restored, app data not cleared, app left stopped.
- An earlier install attempt failed offline before any installation. Recovery
  rediscovered the changed TLS connect port; all actual test/restore work used
  Wi-Fi with USB absent. No OS privilege or USB root-cause fix is claimed.
- Raw logs/APKs/summary: `outputs/reports/redmi-evidence/20261019-postfix-1792392212884/`.
  Method/limits: `docs/REDMI_WIRELESS_AND_POSTFIX_PROBE_2026-10-19.md`.
- No code/build changes or repeat JVM/Python suites in this phone-only session;
  the sealed candidate's prior **291 + 10 JVM / 10 Python** results stand.

## 2026-10-19 — ADE20K 2,000-pair semantic evaluation and debug contract fixes

**Status: PASS_WITH_WARNINGS.** Offline runs complete. The phone run was pending
at this checkpoint and is now completed in the newer entry above.

- Official archive: 967,382,037 bytes, SHA-256
  `7FF1BE44964418441F542A7CC1E1A650E7DC0FC275F5D23252BC9BBDBC977B29`;
  all 44,429 ZIP entries passed CRC. Extracted 2,000 image/mask validation pairs.
- Full Top31 mIoU: MOSAIC **58.8138%**, float DeepLab **55.2504%**, UINT8 DeepLab
  **54.8354%**. All runs scored the same 321,270,074 valid pixels and 31 classes.
- Stair-to-floor diagnostic: **19.4504% / 15.2106% / 20.0002%**, respectively,
  over 1,799,391 resized stair pixels in 180 images. These are pixel fractions,
  not scene-level hazard recall or walking safety.
- Five deterministic 64-image subset runs isolate input/geometry choices.
  All frame/model/script hashes and raw counters are in
  `outputs/reports/ade20k-eval/`; full interpretation is in
  `docs/ADE20K_OFFLINE_EVALUATION_2026-10-19.md`.
- **10/10 Python tests** pass, including scalar-reference scoring and actual
  LiteRT bilinear/nearest kernel comparison for a 2048x1536 fixture.
- **291/291 app JVM tests**, zero failures/errors. Final debug build/test:
  **BUILD SUCCESSFUL in 42s**. Sim-core's unchanged **10/10** suite remains passing.
  Release assembled in the prior combined build and its hash remains unchanged.
- Corrected the debug-only decoder to pinned INT32 maps with 32 category IDs;
  default UINT8 uses reference RGB codes; legacy requantization is explicit.
- Debug APK SHA-256:
  `DC0BE8CED37F89C1771D3EFC58B09870252E01E962CB354DFA1F8C833BD9E567`.
  Release SHA-256:
  `5B2D6B483624277B032F1F3FC3C10DF6DA928C6D820C31BDE0E62EC505C40540`.
  Release has zero diagnostic assets and no diagnostic activity or INTERNET.
- Two Cursor CLI Ask-mode reviews completed without edits; Codex independently
  verified findings and rejected two unsupported proposed fixes with tests.
- Redmi became unavailable before backup/install. The failed read-only backup
  preflight performed no device mutation; the new debug APK was not installed.
  Last verified installed original is 0.9.21/34 from the earlier static probe.

## 2026-10-19 — Segmenter static model benchmark and restore (0.9.21)

**Status: PASS_WITH_WARNINGS.** Local static-image diagnostic only; not a walking,
accuracy, or navigation test.

- Redmi `55aqpfqkvw9xbafe`: DeepLab float, DeepLab uint8, and MOSAIC R4 were
  replayed on the same 1440×2560 still at 512×512. DeepLab uint8 CPU p50 was
  738.67–744.44 ms; float p50 was 395.53–414.31 ms. MOSAIC CPU median p50 was
  359.80 ms.
- MOSAIC R4 NNAPI: TFLite logged **100/100 nodes delegated** into one NNAPI
  partition. Two 20-run batches measured p50 17.62/17.72 ms and p90
  17.89/18.68 ms. A 1,000-run batch measured p50 **17.39 ms**, p90 **22.36 ms**,
  mean 18.24 ms (**54.82 sequential static-image inferences/s**).
- Source review of the [official MLPerf ADE20K evaluator](https://github.com/mlcommons/mobile_app_open/blob/master/flutter/cpp/datasets/ade20k.cc)
  showed category IDs **1..31** with zero treated as ignored/background. Combined
  with the [ADE20K category list](https://github.com/CSAILVision/sceneparsing/blob/master/objectInfo150.txt),
  this yields a source-backed Top-31 label table for diagnostic logs. The debug
  validator's former 0..30 range incorrectly rejected 31; it is now corrected
  and tested. This changes diagnostics only, not camera/navigation behavior.
- Class-ID outputs repeated in the measured samples. Names are now mapped using
  the ADE20K Top-31 taxonomy and MLPerf evaluator contract, but there is no
  ground-truth mask for the owner-provided still. This is not accuracy evidence
  or live camera FPS. No navigation, speech, turns, `CLEAR`, or user-facing
  safety cue used these models.
- Process/thermal/battery figures are single snapshots only, not controlled
  sustained resource measurements. Full raw measurements and limits:
  `docs/DEEPLAB_UINT8_REDMI_AB_SETUP_2026-10-19.md`.
- Final build command `:app:assembleDebug :app:testDebugUnitTest :sim-core:test
  :app:assembleRelease`: **BUILD SUCCESSFUL in 36s**. App JVM **287/287** and
  sim-core **10/10** pass. The app tests executed on the final debug diagnostic;
  sim-core had also been explicitly rerun successfully in the same validation
  sequence and was unchanged in this build.
- Debug APK SHA-256:
  `A8BD6CA1A72FDA333772F4468B5D9A6262CAC5A46A2570C92769CD98670A8076`.
  Release APK SHA-256:
  `5B2D6B483624277B032F1F3FC3C10DF6DA928C6D820C31BDE0E62EC505C40540`.
  Diagnostic candidates/activity are debug-only; release has no diagnostic
  assets/activity or `INTERNET` permission.
- Original Redmi app restored: versionName **0.9.21**, versionCode **34**;
  installed APK was pulled after restoration and its SHA-256 exactly matched
  the saved pre-test APK `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
- No walking or eyes-closed test. No claim of safe navigation.

## 2026-10-19 — Relative-depth risk-gate benchmark + Redmi install (0.9.21)

**Status: PASS_WITH_WARNINGS** (synthetic HOLD-only matrix + install). No model runtime.

- `:app:testDebugUnitTest` **287** PASS, 0 failures.
- `:sim-core:test` **10** PASS.
- `assembleDebug` → `outputs/apk/TriGuideAI-0.9.21-debug.apk` (88,732,901 bytes),
  SHA-256 `88828908DDE8C254A1AE0343B8073C80C0766518DEE706A7D8F1CEC3A07AF338`.
- Redmi `55aqpfqkvw9xbafe`: install Success; `dumpsys` **versionName=0.9.21** /
  versionCode **34**. No walking / eyes-closed test.
- Benchmark asserts: normal depth never clears detector HOLD; wall/drop raise HOLD;
  signature has no CLEAR/STRAIGHT.

## 2026-10-19 — Relative-depth policy slice + Redmi baseline (0.9.20)

**Status: PASS_WITH_WARNINGS** (policy-only depth analyzer; not wired to navigation).

- Added `RelativeDepthEvidence` as a model-agnostic, fail-closed contract for future
  normalized inverse-depth input.
- Unit coverage includes wall/large obstacle, possible drop, normal-floor diagnostic,
  stale, out-of-order, malformed, empty, and non-finite evidence.
- No model asset, camera wiring, TTS, `CLEAR`, `TURN`, or navigation behavior was added.
- `:app:testDebugUnitTest` **267** PASS, 0 failures; `:sim-core:test` **10** PASS;
  `assembleDebug` PASS.

## 2026-10-19 — Relative-depth model preflight gate (0.9.20)

**Status: PASS_WITH_WARNINGS** (metadata/preflight only; no model runtime).

- Added `RelativeDepthModelContract` for the MiDaS small LiteRT fp16 benchmark
  candidate. The contract pins the expected asset path, SHA-256, tensor shape,
  ImageNet normalization, relative-inverse-depth output, source, and MIT license
  metadata.
- Added tests for missing, malformed, mismatched, and matching hashes. A matching
  hash only permits a future local benchmark; it does not permit navigation, TTS,
  `CLEAR`, `STRAIGHT`, or `TURN`.
- Candidate file remains unbundled. No network permission, download path, camera
  wiring, or model interpreter was added.
- Added and tested `RelativeDepthRiskGate` plus a 4 Hz cadence gate. Depth can only
  raise a diagnostic HOLD; it cannot remove detector risk or create clearance, and
  it is not wired to live navigation.
- A trial of the official LiteRT `2.2.0` CPU runtime was rejected and removed after
  the build reported Kotlin metadata `2.3.0` versus this project’s `2.0.0`. Do not
  upgrade Kotlin/Lifecycle as part of the depth spike; create a separate compatibility
  branch and golden-output gate first.
- `:app:testDebugUnitTest` **267** PASS, 0 failures; `:sim-core:test` **10** PASS;
  `assembleDebug` PASS.

## 2026-10-19 — Redmi stationary baseline: closed-door-wall (0.9.20)

**Status: PASS_WITH_WARNINGS** (valid stationary phone capture; no walking).

- Redmi `55aqpfqkvw9xbafe` accepted the APK and ran the locked-scene harness.
- Start/end evidence shows the intended hallway, closed door, wall, and floor.
- 137 real inference samples in 20 seconds; FPS **6.85**; latency p50 **109 ms**;
  p90 **126 ms**.
- Navigation: `HOLD` 0 log lines, `UNKNOWN` 517 log lines, `CLEAR/TURN` **0**.
- Voice requests: 0; this is a log summary only and is **not** an audible-quality claim.
- Battery **51%**; temperature **39.0°C**; total PSS **257,824 kB** at capture end.
- Report: `outputs/reports/redmi-evidence/closed-door-wall-perf-validated-20261019-095853/`.
- The harness now rejects locked/non-foreground runs and measures FPS from latency-only
  inference events rather than throttled detection-description logs.
- No walking, eyes-closed, safety, or path-clear claim was made.

## 2026-10-19 — Foundation milestone (0.9.20)

**Status: PASS_WITH_WARNINGS** (desktop unit + assemble + Redmi install).

- `:app:testDebugUnitTest` **257** PASS, 0 failures (was 246 baseline; +foundation and depth-policy tests).
- `:sim-core:test` **10** PASS.
- `assembleDebug` → `android-app/app/build/outputs/apk/debug/app-debug.apk`
  (88,885,881 bytes), SHA-256
  `DD42542A82042F3AEF5A392F564E56E50002ED1B31CF356A7BF63BA29C30846C`.
- Audits: INTERNET `tools:node="remove"` present; single `setAnalyzer`; no live “path clear” authorization strings in nav; ImageProxy closed via sole `use{}` in `CameraXSource`.
- `git diff --check`: CRLF conversion warnings only (no whitespace-error lines).
- Redmi install: completed on 0.9.20. No walking tests.
- Owner ear (camera-active tone): **not confirmed**.

## 2026-10-19 — Open-lane green strip + soft beep (0.9.19)

**Status: PASS_WITH_WARNINGS** (desktop unit + assemble). Phone install **BLOCKED**
(no ADB device at install).

- `:app:testDebugUnitTest` **248** PASS, 0 failures (includes `OpenLaneCueTest`,
  updated `AiVisionDiagnosticTest` open-lane assertions).
- `:sim-core:test` **10** PASS (same session earlier).
- `assembleDebug` → `outputs/apk/TriGuideAI-0.9.19-debug.apk` (88,700,133 bytes),
  SHA-256 `CF26CDE44F6DFB8678347BD62638699B59797626C8C15BE96AE7DAFDB82B5731`.
- `scripts/test/redmi-install.ps1`: ERROR — Redmi `55aqpfqkvw9xbafe` not listed.
- Owner ear/scene (green lane visible; quiet beep-beep; chair → no green/beep):
  **not run**.

## 2026-10-19 — Milestone B AI Vision overlay (0.9.18)

**Status: PASS** (desktop unit + assemble + Redmi install). Owner ear/scene and
Codex checklist are separate; audible TTS not verified here.

- `.\scripts\build\assemble-debug.ps1` →
  `outputs/apk/TriGuideAI-0.9.18-debug.apk` (88,867,433 bytes), SHA-256
  `26127F192BE77CA4FEB8AEB300808088420D41174F6F7EB69870009D1AF373AE`.
- `:app:testDebugUnitTest` **246** PASS (0 failures), including new
  `AiVisionDiagnosticTest`.
- `:sim-core:test` **10** PASS.
- `adb devices` showed `55aqpfqkvw9xbafe` as `device`;
  `scripts/test/redmi-install.ps1` Success; `dumpsys`
  `versionName=0.9.18` `versionCode=31`.
- No emulator. No SegFormer/ADE20K assets. Overlay does not authorize CLEAR.

## 2026-10-19 — `closed-door-wall` stationary Redmi scene

**Status: PASS_WITH_WARNINGS.** The requested collector command completed on
the physical Redmi and produced a usable local screenshot of closed doors/wall
and tile floor. No walking occurred.

- `HOLD=0`, `UNKNOWN=322`, `CLEAR/turn=0` filtered navigation lines.
- Detector: 17 logged samples, approximate FPS `0.61`, latency p50 `77 ms`,
  p90 `91 ms`. Most frames had no usable detection; one low-confidence
  below-threshold refrigerator label appeared.
- Voice: one `Guidance on.` request, no obstacle request. This is log evidence,
  not proof of audible output.
- Memory/battery: total PSS `162194 KB`, native heap `57204 KB`, Dalvik heap
  `4360 KB`, battery `100`, USB charging, temperature raw `335`.
- Artifact: `outputs/reports/redmi-evidence/closed-door-wall-20261019-050957/`.

The result correctly stayed `UNKNOWN`; it does not validate floor
segmentation, a free walking area, or a safe path.

## 2026-10-19 — Redmi evidence collector smoke

**Status: PASS_WITH_WARNINGS.** The collector ran locally on physical Redmi
`55aqpfqkvw9xbafe`; the view was owner/body occlusion and is not a walkability
scene.

- Script: `scripts/test/redmi-evidence-baseline.ps1`.
- No APK install, data clear, video recording, walking, upload, or emulator
  use occurred.
- Latest `owner-occlusion` run: 14 detector log samples over the configured
  short capture, approximate FPS 2.33, latency p50 64 ms / p90 86 ms,
  `HOLD=98` and `UNKNOWN=12` filtered navigation lines, `CLEAR/turn=0`.
- Voice requests in the log were startup `Guidance on.` and one
  `Obstacle ahead.`; one obstacle request was skipped while TTS initialized.
  Log acceptance is not audible-output evidence.
- Memory/battery snapshot: total PSS `165396 KB`, native heap `60636 KB`,
  Dalvik heap `5432 KB`, battery `100`, USB charging, temperature raw `339`.

This confirms the camera stream and fail-closed reporting path can be observed
on the phone. It does not validate floor segmentation, free walking space,
turns, or a safe path.

## 2026-10-18 — Evidence replay harness and Redmi baseline

**Status: PASS_WITH_WARNINGS.** The local diagnostic seam is implemented; no
captured scene data or live navigation integration exists yet.

- `.\gradlew.bat :app:testDebugUnitTest :sim-core:test --no-daemon` passed:
  app JVM `204/204`, sim JVM `10/10`.
- `.\gradlew.bat :app:assembleDebug --no-daemon` passed. APK remains
  version `0.9.14/27`; SHA-256 is
  `00207876D9A8649AEA42580021DAD8CF490B56185FFCA31EFEF6D0866D913240`.
- The new `EvidenceReplayHarness` is pure local JVM code around
  `FloorMaskEvidenceEvaluator`; it records evidence states, issues, latency,
  FPS, decision keys, and voice-event keys without calling navigation, TTS,
  CameraX, network, or model loading.
- The scene manifest is a template only. `test-data` still has no captured
  frames/masks, and `ground_truth` is `pending` for every required scene.
- Stationary Redmi smoke used the already-installed build and produced
  `no_camera_frame` / `UNKNOWN` only in the sampled interval. No `CLEAR`, turn,
  safe-path wording, or upload was observed. The camera-stream start/retry
  check remains open and no walking was performed.

The next phone pass must first establish a visible/streaming camera on the
same stationary scene matrix; it must not be scored as segmentation accuracy
until consented labels and usable evidence files exist.

## Phone-only policy (user, 2026-10-18)

Installs and smoke tests for this user run on the personal Redmi only. Do not start, install to, or smoke-test an emulator (`emulator-5554` included). If the phone is missing, unauthorized, or offline, stop and record the blocker. Do not fall back to an emulator.

Emulator sections below are historical. They are **non-authoritative** for this user from this note forward. A phone result replaces them when the same check is run on the Redmi.

## 2026-10-18 — 0.9.14 Redmi STOP/Back regression

**Status: PASS_WITH_WARNINGS.** 0.9.14/27 installed on Redmi
`55aqpfqkvw9xbafe`; user-heard audio and TalkBack remain unverified.

- Reproduced 0.9.13 failure: after STOP → More → Android Back, the screen
  showed running Live Vision/STOP again without an explicit restart
  (`redmi-0913-after-more.png`). Source cause: Back and tool-page callbacks
  called `LiveNavState.start()`.
- Replaced automatic start on system/tool Back with screen-only return.
  Three new `LiveNavStateTest` cases pass. Total app JVM 200/200; sim JVM
  10/10; debug build passed. APK metadata 0.9.14/27; SHA-256
  `25D20786100EAE71D33159ADED7CAF1807B5BC3F3D30BEF8D9AFE556CDCDA11B`.
- `adb install -r -t` Success; `dumpsys package` confirmed 0.9.14/27.
  STOP at 23:23:04.535, More gesture at 23:23:12.531, then Android Back:
  `redmi-0914-paused-more-back.png` shows paused UI, no preview or STOP. One
  in-flight Nav callback occurred at 23:23:04.664; none later in the checked
  interval. Explicit triple-left restart registered at 23:23:36.696; STOP
  registered again at 23:23:43.208. App left paused.
- Diagnostics in 0.9.13 More screen visually stated floor segmentation
  NOT_STARTED, no bundled/validated model, and boxes are not an open path.
  0.9.14 changed no Diagnostics wording. No actual floor model was tested.

## 2026-10-18 — 0.9.13 Redmi partial smoke

**Status: PASS_WITH_WARNINGS** for install/version, preview, conservative
decision, STOP, fixed mat scene, and paused Describe; other real-device checks
remain **NOT_STARTED**.

- Exact 0.9.13/26 validated APK installed with `adb install -r -t` Success;
  prior installed version was 0.9.10/23. `dumpsys package` confirmed 0.9.13/26
  and camera permission granted. No clear-data command was run.
- `redmi-0913-live-2.png`: camera preview visible, a left `person` box over the
  owner's arm, header/status and one STOP. Logcat showed BLOCKED/HOLD and no
  detector-only movement decision in the sampled frames. First post-launch
  screenshot was black during startup; a later one rendered correctly.
- STOP ADB tap registered at 23:11:05.277, paused speech request at
  23:11:05.393; `redmi-0913-stopped.png` confirms the paused UI and stopped
  preview. One in-flight nav callback occurred at 23:11:05.394, with none later
  in the checked log. Three UP swipes later triggered DESCRIBE once.
- On a fixed tile/mat scene from 23:15:17 to 23:16:15, start/end screenshots
  show no boxes and `Path not confirmed`. In the 23:15 log minute, 506
  navigation entries were UNKNOWN and there were zero `TriGuideVoice`
  requests. The mat is not a corridor; no free-path claim follows.
- After 23:16:35, rapidly changing detector labels included whole-frame
  `tv`, `person`, `toilet`, `bed`, and `cat`; two obstacle TTS requests came at
  23:16:36 and 23:16:47. This was no longer a controlled static scene, so
  repeat-speech causality is not established. At 23:17:34, three UP swipes on
  the paused UI requested `Guidance paused. Restart to describe.`, not a stale
  scene description. The app remained paused after this check.
- ADB briefly fell offline and required owner USB-debugging reauthorization.
  An actual corridor, wide-chair edge, TalkBack, user-heard audio quality, and
  mask-model accuracy were not tested. `speak result=0` means TTS accepted the
  request, not that its sound was heard or liked.

## 2026-10-18 — 0.9.13 diagnostic floor-mask seam

**Status: PASS_WITH_WARNINGS** for desktop tests/build; phone test **BLOCKED**
because `adb devices -l` listed no device.

- `:app:testDebugUnitTest` 197/197 (including 15 new `FloorMaskEvidenceTest`
  cases) and `:sim-core:test` 10/10; zero failures, errors, or skipped cases.
  `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` confirmed
  `com.triguide.ai`, versionName 0.9.13, versionCode 26.
- Both `outputs/apk/TriGuideAI-0.9.13-debug.apk` and
  `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.13-validated-20261018.apk`
  are 88,650,981 bytes with matching SHA-256
  `2CE55AFFD78B6CA62846415B53D8A4DDF37A698B2F772A70A789998E22DB0CEF`.
- The new evaluator is pure and has no camera/navigation/TTS call site. Toy
  masks test software behavior only; no segmenter, labeled phone-clip accuracy,
  scene geometry, mobility safety, or audible output was validated. No emulator.
- After Cursor's independent source review, a focused even-width bottom-seed
  test was added. Full JVM suite and build were re-run successfully; APK code
  and SHA-256 are unchanged. Mapping metadata is not checked against a real
  camera/model producer and must remain disconnected from navigation.

## 2026-10-18 — 0.9.12 box-extent hazard screening

**Status: PASS_WITH_WARNINGS** for JVM tests/build; phone test **BLOCKED** because ADB lists no device.

- `:app:testDebugUnitTest` 182/182 and `:sim-core:test` 10/10, zero failures. `:app:assembleDebug` BUILD SUCCESSFUL. Output metadata: `versionName=0.9.12`, `versionCode=25`.
- Exact APK: `outputs/apk/TriGuideAI-0.9.12-debug.apk` (88,650,981 bytes), SHA-256 `F950A37F9675772E667650CE72D062ADBE4FE94949D9F3CE62302DEA597A7666`. Matching test copy: `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.12-validated-20261018.apk`.
- New tests cover box overlap with walking corridor, ignored knife/cup at the edge, wholly outside objects, mirrored bounds, and invalid bounds. No real-world detection accuracy or positive route evidence was established. `adb devices -l` returned only the header; no phone install/launch/logcat, no emulator.

## 2026-10-18 — 0.9.11 stop-only UI and safety hardening

**Status: PASS_WITH_WARNINGS** for JVM tests/build; phone test **BLOCKED** because ADB lists no device.

- `:app:testDebugUnitTest` 176/176, zero failures/errors; `:sim-core:test` 10/10, zero failures/errors.
- `:app:assembleDebug` BUILD SUCCESSFUL. Output metadata: `com.triguide.ai`, `versionName=0.9.11`, `versionCode=24`.
- Exact copied APK: `outputs/apk/TriGuideAI-0.9.11-debug.apk`, 88,650,981 bytes, SHA-256 `58B18750401174BE92C48D5A194949E9FE7D2AE16D321D706967C85C0330D82C`. Matching immutable test copy: `C:\Users\yassi\Videos\TriGuide-Tests\triguide-0.9.11-validated-20261018.apk`.
- Desktop coverage includes short fail-closed status speech, lower-corridor cup/knife risk, camera false-CLEAR/turn defense, and stale/paused Describe rejection. Compose touch timing, layout, TalkBack traversal, actual TTS audio, and live-camera behavior were **not** phone-tested.
- `adb devices -l` returned only the header; no install/launch/logcat attempt and no emulator.

## 2026-10-18 — 0.9.0 path risk (phone not attached)

Historical result superseded by the 0.9.12 camera evidence contract above. In particular, the old `CLEAR`/`STRAIGHT` behavior in the table below is **not** current phone-camera behavior.

**Status: PASS_WITH_WARNINGS** (unit + APK). Phone install **BLOCKED** (no ADB device).

- `:app:testDebugUnitTest` PASS after PathRiskAnalyzer + voice silence changes (includes `PathRiskAnalyzerTest`).
- `:app:assembleDebug` PASS → `outputs/apk/TriGuideAI-0.9.0-debug.apk` (versionCode 13).
- `adb devices` empty — not installed on Redmi. No emulator used.

### Before / after (logic, not yet re-measured on phone video)

| Case | Before (0.8.x speech) | After (0.9.0) |
| --- | --- | --- |
| Empty frame | Hold + “empty picture…” spoken | CLEAR, STRAIGHT, silent |
| Small cup / floor clutter | Could steer or hold | Ignored for path, silent |
| Person center corridor | “Person in the center. Hold.” | “Obstacle ahead.” / Arabic short |
| Clear corridor | Repeated uncertain | Silent |

## 2026-10-18 — 0.8.2 dominant side turn (phone not attached)

**Status: BLOCKED**

The unit tests and the debug APK succeeded. The install and phone log did not run. `adb devices -l` listed no devices. A USB device scan did not show the Redmi (`VID_2717` was absent). No emulator was started.

### Build

- `:app:testDebugUnitTest` 113/113 PASS, 0 failures, 0 skipped. New cases: a dominant left chair with a weaker right bottle (60 percent of the chair’s score times area) is Slight right and speaks “Chair on the left. Turn slightly right. Not a measured gap.”; a left chair and a right table at 80 percent hold with “Objects on both sides. Hold.”; a center chair holds with “Chair in the center. Hold.”; three weaker right boxes do not add up into a both-sides hold; a clearly larger chair is the spoken subject when a smaller cup has a higher score.
- `:sim-core:test` was not run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.8.2'`, `versionCode='12'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.8.2-debug.apk` (81,441,801 bytes).

### Phone

- Not run. `adb install` was not attempted because `55aqpfqkvw9xbafe` was not in `device` state. No logcat. No speaker check.

## 2026-10-18 — Redmi 0.8.1 side-turn speech

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (`22101316G`, `ruby`). No emulator was attached or installed to.

### Build

- `:app:testDebugUnitTest` 108/108 PASS, 0 failures, 0 skipped. New cases: a person in the left third still turns when a weak bottle is in the center; a strong chair in the center holds and the phrase does not say “left”; a turn queued behind Hold is spoken after the gap, in Arabic; Settings `requestSave("أحمد")` stores a name only after confirm and the pending line does not say a photo waits.
- `:sim-core:test` was UP-TO-DATE. It was not re-run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.8.1'`, `versionCode='11'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.8.1-debug.apk` (81,441,801 bytes).

### Phone

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.8.1-debug.apk` → `Success`. `dumpsys package` showed `versionName=0.8.1` `versionCode=11`.
- `am force-stop` then `am start -n com.triguide.ai/.MainActivity`. No FATAL line in the filtered log.
- Opening lines, all `speak result=0`: the research disclaimer, “Camera started. Guidance on.”, and the fingerprint limitation.
- `TriGuideNav` then committed `SLIGHT_RIGHT`: “toilet is on the left of the image (detector confidence 0.45, 5 percent of the frame). Steering slightly right.” A later line used the model label `sink` on the left, same action. The app did not invent those labels.
- At 10:08:07.931 `TriGuideVoice` `speak result=0` `queue=1` `kind=TURN_SUGGESTION` `lang=ENGLISH` `rate=0.9` `pitch=1.0` `text=Toilet on the left. Turn slightly right. Not a measured gap.` The phrase does not contain “center”. Result 0 means the engine accepted it. Sound from the speaker was not confirmed by ear.
- The same turn was not repeated on the following frames in that window. Floor segmentation was not exercised.

## 2026-10-18 — Redmi 0.7.0 detection smoke

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (`22101316G`, `ruby`). No emulator was attached or installed to.

### Build

- `:app:testDebugUnitTest` 95/95 PASS, 0 failures, 0 skipped. Includes the score floor, the viewport crop clamp, and the on-demand scene sentence.
- `:sim-core:test` was UP-TO-DATE. It was not re-run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.7.0'`, `versionCode='9'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.7.0-debug.apk` (81,408,299 bytes).

### Phone

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.7.0-debug.apk` → `Success`. `dumpsys package` showed `versionName=0.7.0` `versionCode=9`.
- `MainActivity` was the resumed activity. No FATAL line.
- `TriGuideDetect`: `detector ready model=MediaPipe EfficientDet-Lite0 int8 delegate=CPU`.
- Geometry on the captured lines: `rot=90 buffer=640x480 crop=134,0,372x480 upright=480x372`. Latency on the final process was about 88–158 ms on the lines that were kept.
- Aimed at people and a table, drawn examples included `person=0.72@36,1,459,369`, `bottle=0.52@381,98,404,152`, and `laptop=0.45@0,272,128,371`. A `couch=0.34` was in `below` and was not drawn. The model also emitted false labels (`tv`, and on an earlier frame of this version `frisbee`). The app did not add boxes of its own.
- Before the camera was on that scene, lines were `drawn=0:none below=0:none`. `TriGuideNav` then logged Hold: an empty detection is not open space.
- UI dump on the first 0.7.0 process: `READY. MediaPipe EfficientDet-Lite0 int8`, `Camera: Streaming. Preview 30 FPS. Inference 8 FPS, last 75 ms, average 92 ms.`, `Decision: Hold`, reason that a bottle was in the center, `Front wheel: 0.0 degrees`, and the sentence that nothing is assumed from an empty frame. The same dump included “found no objects at or above 0.35 confidence” only while the frame was empty; a later screencap showed yellow boxes, labels, and scores on people and a bottle, plus a frisbee label on the table. `uiautomator` later returned `could not get idle state` while the preview kept updating, so More and Settings were not dumped.
- One tap logged `TriGuideScene` and `TriGuideVoice` `speak result=0` for the on-demand sentence naming `person 0.68 center` and two bottles. Result 0 means the engine accepted the utterance. Sound from the speaker was not confirmed by ear. Cold start also logged `speak result=0` for the existing research disclaimer and “Camera started. Guidance on.”
- Floor segmentation was not exercised. No floor model is in the APK. No floor mask was drawn.
- Camera-informed simulation, Test Scenarios, Diagnostics, Settings, and TalkBack were not opened on this build.

## 2026-10-18 — Redmi 0.6.0 screen triple-tap and core smoke

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only:

```
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

No emulator was attached. `adb` binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`. Gradle used JDK 17 at `C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`.

### Build

- `:app:testDebugUnitTest` 87/87 PASS, 0 failures. New coverage: `TripleTapDetectorTest` (8), `GuidanceScenariosTest` (1), `DiagnosticsReportTest` (1), plus non-finite and zero-area navigation cases.
- `:sim-core:test` was UP-TO-DATE and was not re-executed. Kinematics files were not changed.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.6.0'`, `versionCode='8'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Permissions in the badge: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.6.0-debug.apk` (81,391,581 bytes).

### Install and cold start

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.6.0-debug.apk` → `Success`. `dumpsys package` showed `versionName=0.6.0` `versionCode=8`.
- `am force-stop` then `am start -n com.triguide.ai/.MainActivity`. Resumed activity was `com.triguide.ai/.MainActivity` (pid 4069). No `AndroidRuntime` FATAL line was in the filtered log.
- `TriGuideVoice` `speak result=0` (engine accepted; the speaker was not confirmed by ear), English:
  - Research and Simulation Mode disclaimer
  - `Camera started. Guidance on.`
  - `The fingerprint button cannot control this app. Triple-tap the screen to restart guidance.`
- UI dump text included Live Vision, that triple-tap sentence, `READY. MediaPipe EfficientDet-Lite0 int8`, `Camera: Streaming. Preview 15 FPS. Inference 13 FPS, last 42 ms, average 44 ms.`, `Decision: Hold`, `Front wheel: 0.0 degrees`, `Source: Camera 2D`, and More. No detection box was claimed in that frame.

### Triple-tap

Screen 1080×2400. One shell ran `input tap 540 1600` three times (shell elapsed 188 ms). That point is on the preview area, above More.

- `08:54:10.352` `TriGuideTap` `tap count=1`
- `08:54:10.382` `TriGuideTap` `tap count=2`
- `08:54:10.408` `TriGuideTap` `triple-tap accepted; restarting guidance`
- `08:54:10.472` `TriGuideVoice` `speak result=0` `text=Camera started. Guidance on.`

The disclaimer was not repeated. A vertical swipe did not log a tap. One tap on More logged `tap count=1`, spoke `Guidance paused.`, and opened the More screen.

### Other screens

- More showed floor `NOT_STARTED. Disabled. No floor model is loaded. Open floor is not inferred, and no floor mask is drawn.` Hardware line: `MOCK — no physical robot connected`.
- Test Scenarios showed Straight (empty corridor), Right (left barrel), and Hold (post on the forward axis), each marked as not the camera.
- Robot Simulation showed `Simulated tricycle only. Nothing on this screen is a physical robot.` and Manual drive selected, source Manual. Selecting Synthetic world made the next UI dump fail with `could not get idle state`. That mode was not read back.

### Not checked on the phone

- Whether the speaker was audible.
- A real object producing a box on this build.
- Settings, Arabic speech, quiet mode, the diagnostics share sheet, and TalkBack.
- True-positive / false-alarm counts.

## 2026-10-18 — Redmi 0.5.1 open-into-guidance, fingerprint API check

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` listed only:

```
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

No emulator was attached. `adb` binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### Fingerprint hardware (read, not a gesture test)

`adb shell dumpsys fingerprint` included `isUdfps: false` and `isPowerbuttonFps: true`. `getprop` included `ro.hardware.fp.sideCap=true` and `persist.vendor.sys.fp.vendor=goodix`. `getevent -pl` shows `uinput-goodix` with `KEY_POWER` and other ordinary keys. `settings get` equivalent via `settings list secure` showed `fingerprint_double_tap=none`. No fingerprint tap was injected. The power button was not pressed from the PC, because a press can turn the screen off.

### Install and cold start

- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.5.1-debug.apk` → `Success`. Package `versionName=0.5.1` `versionCode=7`. File size 81,495,956 bytes.
- `am force-stop` then `am start -n com.triguide.ai/.MainActivity`. Resumed activity was `com.triguide.ai/.MainActivity` (pid 29066). Logcat had no FATAL for the app.
- `TriGuideVoice` lines, all `speak result=0` (engine accepted; the speaker was not confirmed by ear):
  - Research and Simulation Mode disclaimer
  - `Camera started. Guidance on.`
  - `Guidance starts when you open TriGuide. This phone's fingerprint control is the power button, and Android does not give those presses to apps.`
- After a second `am start` brought the existing task forward, `uiautomator dump` text included `Live Vision`, the fingerprint limitation line, `READY. MediaPipe EfficientDet-Lite0 int8`, `Camera: Streaming. Preview 30 FPS. Inference 10 FPS, last 142 ms, average 95 ms`, `Decision: Hold`, `Front wheel: 0.0 degrees`, and `More`.

### Unit tests on the PC

`:app:testDebugUnitTest` 75/75 PASS (0 failures). A TalkBack sentence was edited after that run, and `:app:assembleDebug` then succeeded. `:sim-core:test` was not re-run.

## 2026-10-18 — Redmi 0.4.1 install, plus 0.4.0 camera lines

**Status: PASS_WITH_WARNINGS**

Physical device only. `adb devices -l` after `adb kill-server` and `adb start-server`:

```
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

No emulator was attached. `adb` was not on PATH. Binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe` (Android Debug Bridge 1.0.41, version 37.0.1-15733141).

Properties read with `adb -s 55aqpfqkvw9xbafe shell getprop`:

| Property | Value |
| --- | --- |
| ro.product.manufacturer | Xiaomi |
| ro.product.marketname | Redmi Note 12 Pro 5G |
| ro.product.model | 22101316G |
| ro.product.device | ruby |
| ro.build.version.release | 14 |
| ro.build.version.sdk | 34 |
| ro.mi.os.version.name | OS2.0 |
| ro.build.version.incremental | OS2.0.16.0.UMOMIXM |
| ro.miui.ui.version.name | V816 |

### 0.4.0 camera and speech engine (observed before the 0.4.1 rebuild)

- `adb -s 55aqpfqkvw9xbafe install -r outputs\apk\TriGuideAI-0.4.0-debug.apk` → `Performing Streamed Install` / `Success`.
- Launch `com.triguide.ai/.MainActivity` → resumed. Package `versionName=0.4.0` `versionCode=4`.
- A UI dump while that process was in front showed Live Vision, not Home. This session did not send taps. Screen text included `Camera: Streaming. Preview 14 FPS. Inference 9 FPS, last 53 ms, average 59 ms` and `MediaPipe EfficientDet-Lite0 int8 found no objects at or above 0.50 confidence.` Decision text at that dump: Hold, reason “No confirmed passage…”, front wheel 0.0 degrees. The preview view started near the bottom of the 2400 px screen.
- Later logcat from pid 31381: `TriGuideNav` `Camera 2D HOLD chair is in the center of the image. No confirmed passage to either side. Depth is not measured.` and `TriGuideVoice` `speak result=0 queue=0 kind=OBSTACLE_DETECTED lang=ENGLISH rate=1.0 text=Obstacle detected. Hold.`
- `speak result=0` is `TextToSpeech.SUCCESS` (the engine accepted the utterance). Whether the speaker was audible was not checked by ear. Quiet mode was not toggled. TalkBack was not walked.

### 0.4.1 build and install

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Command: `gradlew.bat :app:testDebugUnitTest :sim-core:test :app:assembleDebug --no-daemon`

- BUILD SUCCESSFUL in 57s.
- `:app:testDebugUnitTest` executed: 71 tests, 0 failures, 0 errors, 0 skipped (includes 6 new `PhoneDemoStatusTest` cases).
- `:sim-core:test` was UP-TO-DATE and was not re-executed. The existing XML `TEST-com.triguide.ai.sim.TricycleKinematicsTest.xml` timestamp `2026-10-17T23:45:27` records 9 tests, 0 failures.

APK:

| File | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,342,405 | `ECD2D12F731458A803AB5E179C91682AB993A320DF217750B06D70FA8FAE1AF8` |
| `outputs/apk/TriGuideAI-0.4.1-debug.apk` | 81,342,405 | same hash |

- First `adb install -r` of that APK: `Failure [INSTALL_FAILED_USER_RESTRICTED: Install canceled by user]`. The phone stayed `device`. The installed package was still 0.4.0.
- Retry `adb install -r -t` about one minute later: `Performing Streamed Install` / `Success` (about 8 seconds). No install dialog was visible in a UI dump taken during that retry; the launcher was in front.
- After install: `versionCode=5` `versionName=0.4.1`. `am start` → `topResumedActivity` `com.triguide.ai/.MainActivity`, pid 9367. Logcat had no FATAL for the app.
- Home UI dump text: disclaimer present; title `TriGuide`; `Detection: READY. MediaPipe EfficientDet-Lite0 int8`; `Decision: Unknown`; `Reason: Navigation has not received an observation.`; `Front wheel: 0.0 degrees`; floor `NOT_STARTED`; `Voice: On. Rate 1.0. Quiet mode off. Device language. Offline English and Arabic voices are installed.`; primary button `Live Vision`.
- Live Vision, a new box, and a new spoken cue were not opened on 0.4.1. `adb shell input keyevent` failed: `SecurityException` … `INJECT_EVENTS`. That matches HyperOS with USB debugging (Security settings) off.

## 2026-10-18 — Redmi install and launch (0.4.0)

**Status: PASS_WITH_WARNINGS**

Physical device was present and authorized. No emulator was started. No install or launch was sent to an emulator.

`adb` was not on this shell’s PATH. Binary used: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

Commands:

```text
adb kill-server
adb start-server
adb devices -l
adb -s 55aqpfqkvw9xbafe shell getprop ro.product.model
adb -s 55aqpfqkvw9xbafe shell getprop ro.build.version.release
adb -s 55aqpfqkvw9xbafe install -r outputs\apk\TriGuideAI-0.4.0-debug.apk
adb -s 55aqpfqkvw9xbafe shell am start -n com.triguide.ai/.MainActivity
```

### Device

`adb devices -l` after the server restart listed only:

```text
55aqpfqkvw9xbafe       device product:ruby_global model:22101316G device:ruby transport_id:1
```

| Property | Value |
| --- | --- |
| Serial | `55aqpfqkvw9xbafe` |
| `ro.product.model` | `22101316G` |
| Market name | Redmi Note 12 Pro 5G |
| Brand / device | Redmi / `ruby` |
| Android | 14 (`ro.build.version.sdk` 34) |
| State | `device` (authorized) |

### Install

- APK: `outputs/apk/TriGuideAI-0.4.0-debug.apk` (81,438,490 bytes).
- `adb -s 55aqpfqkvw9xbafe install -r` → `Performing Streamed Install` / `Success`.
- Installed package `com.triguide.ai`: `versionName=0.4.0`, `versionCode=4`, `minSdk=26`, `targetSdk=35`.
- `lastUpdateTime=2026-10-18 08:01:39` (phone clock).

### Launch and logcat

- `am start -n com.triguide.ai/.MainActivity` → `Starting: Intent { cmp=com.triguide.ai/.MainActivity }`.
- Process stayed up: `pidof com.triguide.ai` → `30249`.
- `topResumedActivity` was `com.triguide.ai/.MainActivity`, task visible.
- Filtered logcat (`*:E`, about 400 lines, plus package lines) had no `AndroidRuntime` FATAL and no crash line for `com.triguide.ai`. Package-manager lines only: first-use pinning for `com.triguide.ai`.

### Home screen (uiautomator, not a camera test)

- Research and Simulation Mode banner was visible.
- `Object detection: READY. MediaPipe EfficientDet-Lite0 int8`
- `Navigation: Unknown. None`
- `Floor segmentation: NOT_STARTED`
- `Voice guidance: On. Rate 1.0. Quiet mode off. Device language. Offline English and Arabic voices are installed.` (on-screen status only; no sample was played)
- `Virtual robot: Kinematic simulator ready`
- `Physical hardware: MOCK — no physical robot connected`
- Buttons on screen: Start Camera, Robot Simulation, Test Scenarios, Diagnostics. Settings was partly below the fold.

`android.permission.CAMERA` was `granted=false`. Start Camera was not tapped. Camera preview, a real-room detection, inference timing, spoken cues, and TalkBack were not run.

## 2026-10-18 — Phase 6 voice guidance, APK, emulator smoke

**Status: PASS_WITH_WARNINGS**

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Commands from `android-app/`:

```text
.\gradlew.bat :app:testDebugUnitTest :sim-core:test --no-daemon
.\gradlew.bat :app:assembleDebug :sim-core:test --rerun-tasks --no-daemon
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

- First unit-test command: **BUILD SUCCESSFUL in 46s**. `:sim-core:test` was UP-TO-DATE in that invocation.
- Assemble plus forced kinematics rerun: **BUILD SUCCESSFUL in 1m 16s**. `stripDebugDebugSymbols` printed BUILD-001 again: `libandroidx.graphics.path.so`, `libimage_processing_util_jni.so`, `libmediapipe_tasks_jni.so`.
- Retest after the warmup-silence change: **BUILD SUCCESSFUL in 55s**.

### Unit tests

| Suite | tests | failures | errors | time |
| --- | --- | --- | --- | --- |
| `com.triguide.ai.domain.VoiceGuidanceGateTest` | 17 | 0 | 0 | 0.014 s |
| `com.triguide.ai.domain.NavigationDecisionEngineTest` | 32 | 0 | 0 | 0.092 s |
| `com.triguide.ai.domain.DetectionReportsTest` | 12 | 0 | 0 | 0.105 s |
| `com.triguide.ai.domain.PerformanceMonitorTest` | 4 | 0 | 0 | 0.012 s |
| `com.triguide.ai.sim.TricycleKinematicsTest` | 9 | 0 | 0 | 0.010 s |

App total 65/65 at 2026-10-17T23:51:14Z. Sim-core 9/9 at 2026-10-17T23:45:27Z. No instrumentation tests were run.

`VoiceGuidanceGateTest` covers repeated frames, critical cooldown, interrupt, equal-priority critical interrupt, a lower cue waiting, the minimum gap, quiet mode, speech off, rate clamp, Arabic phrase selection, a one-shot pause, a rejected utterance, decision mapping, and a silent smoother warmup.

### APK

| Path | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,438,490 | `1AC24BF62423E646E57446984330B7D1B9E17184F958BC4140091D25AA796794` |
| `outputs/apk/TriGuideAI-0.4.0-debug.apk` | 81,438,490 | same hash |

`aapt dump badging` (build-tools 36.0.0): `versionName='0.4.0'`, `versionCode='4'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Native ABIs: arm64-v8a, armeabi-v7a, x86, x86_64. Asset `assets/models/efficientdet_lite0.tflite` is in the APK. `aapt dump permissions`: `CAMERA` plus the AndroidX non-exported receiver permission. `INTERNET` is not in the APK.

### Emulator smoke (not a phone, not an accuracy test)

**Status: PASS_WITH_WARNINGS**

- AVD `Pixel_8a`, emulator 36.1.9.0. System image `system-images\android-36\google_apis_playstore_ps16k\x86_64`. `ro.product.model` `sdk_gphone16k_x86_64`. ABI `x86_64`. `wm size` `1080x2400`.
- First `adb install -r` of an earlier 0.4.0 binary in this session returned `Success`. Home text included `Voice guidance: On. Rate 1.0. Quiet mode off. Device language. Offline English and Arabic voices are installed.` and `Floor segmentation: NOT_STARTED`.
- Log `TriGuideVoice`: `ready en=true ar=true offline=283 installed=472 engines=Speech Recognition and Synthesis from Google`.
- Settings showed speech, quiet mode, rate 1.0, Device language / English / Arabic, and “Spoken cues repeat the research decision. They are not a safety proof and do not mean the path is clear.” Version line `0.4.0 (4)`.
- Play voice sample, English: `speak result=0` `lang=ENGLISH` `text=Research and simulation. Obstacle detected. This voice is not a safety proof.` Google TTS log: `Synthesis request for locale eng-USA and name en-US-language`.
- After selecting Arabic, the sample was `lang=ARABIC` `text=بحث ومحاكاة. عقبة مكتشفة. هذا الصوت ليس دليل أمان.` Google TTS log: `Synthesis request for locale ara- and name ar-language`.
- That first binary also spoke a warmup “navigation uncertain” before the synthetic turn. The gate now drops the smoother’s “Holding until the direction stays” vote. The final APK was installed on a restarted `Pixel_8a` (`adb install` `Success`, `versionName=0.4.0`).
- Final APK, about 3 s after Synthetic world, log showed three speaks, not a speak per frame: `Turn right.` at 01:35:56, `Slight right.` at 01:35:58, `Straight ahead.` at 01:36:01. All `result=0`, `lang=ENGLISH`, `kind=TURN_SUGGESTION`. On-screen decision at the dump was Straight, 80 percent, front wheel 0.0 degrees, virtual speed 0.40 m/s, source Synthetic map, with “Illustrative path, not verified.” The last-cue line did not double the period. `AndroidRuntime:E` had no FATAL line. `pidof` was `3820`.
- Quiet mode and the speech switch were not tapped. `adb -s emulator-5554 emu kill` stopped the emulator.
- A Xiaomi `22101316G` (`55aqpfqkvw9xbafe`) appeared as an adb `device` while the emulator was up. `pm path com.triguide.ai` on that serial was “Unable to find package.” Nothing was installed or opened on it.

## 2026-10-18 — Redmi USB install attempt

**Status: BLOCKED**

The phone is plugged in. ADB does not see it. `adb install` was not run. The app was not launched. No logcat, UI dump, camera check, synthetic-world check, or spoken-guidance check was done on the phone.

This shell started with `ANDROID_HOME` unset. The user environment variable is `C:\Users\yassi\AppData\Local\Android\Sdk` (same value for `ANDROID_SDK_ROOT`). `adb` was not on PATH until `platform-tools` was prepended. Binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`, Android Debug Bridge 1.0.41, version 37.0.1-15733141.

### What ADB listed

First `adb devices -l`:

```text
emulator-5554          device product:sdk_gphone16k_x86_64 model:sdk_gphone16k_x86_64 device:emu64xa16k transport_id:6
```

No Redmi line: not `device`, not `unauthorized`, not `offline`.

Windows saw the phone as a portable device, not as an ADB interface:

- Friendly name: `Redmi Note 12 Pro 5G`
- Class: WPD (MTP / file transfer)
- Status: OK
- Instance: `USB\VID_2717&PID_FF40\55AQPFQKVW9XBAFE`
- Vendor `2717` is Xiaomi. Product `FF40` is the MTP-only id. No second USB interface and no “Android ADB Interface” were present.

`adb kill-server` then `adb start-server`, then four polls about 2 seconds apart, still showed no phone. Those polls also dropped the emulator briefly. A later `adb devices -l` showed only `emulator-5554` again (`transport_id:2`). The Redmi never appeared.

`adb shell getprop ro.product.model` and the Android version were not read. There is no ADB transport to the phone. The model above is the Windows device name only.

### APK selected, not installed

Newest named debug APK present:

| Path | Bytes | Last write |
| --- | --- | --- |
| `outputs/apk/TriGuideAI-0.4.0-debug.apk` | 81,438,490 | 2026-10-18 07:51:11 |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,438,490 | same timestamp |

`aapt dump badging` (build-tools 36.0.0) on `TriGuideAI-0.4.0-debug.apk`: `package name='com.triguide.ai'`, `versionCode='4'`, `versionName='0.4.0'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Launchable activity: `com.triguide.ai.MainActivity`. Permissions: `android.permission.CAMERA` and the AndroidX non-exported receiver permission.

Older copies also on disk, not chosen: `TriGuideAI-0.3.0-debug.apk` (07:28), `0.2.0` (07:09), `0.1.0` (06:47).

`adb install -r` was not executed in that check, including not against the emulator.

Later the same morning, after the emulator was restarted, `adb devices -l` listed both `emulator-5554` and `55aqpfqkvw9xbafe` (`device`, manufacturer Xiaomi, model `22101316G`). The final 0.4.0 APK was installed on `emulator-5554` only. The phone package was still absent. No phone UI was opened. The MTP-only result above stands for the earlier polls.

## 2026-10-18 — Phase 5 navigation, APK, emulator smoke

**Status: PASS_WITH_WARNINGS**

JDK 17.0.20.1 (`JAVA_HOME=C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1`), `ANDROID_HOME=C:\Users\yassi\AppData\Local\Android\Sdk`, Gradle wrapper 8.11.1, `--no-daemon`.

Commands from `android-app/`:

```text
.\gradlew.bat :app:testDebugUnitTest :sim-core:test --stacktrace --no-daemon
.\gradlew.bat :sim-core:test --rerun-tasks --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
```

- Combined unit-test command: **BUILD SUCCESSFUL in 1m 29s**. `:sim-core:test` was UP-TO-DATE in that invocation, so it was not re-executed there.
- Forced kinematics rerun: **BUILD SUCCESSFUL in 21s**.
- `assembleDebug`: **BUILD SUCCESSFUL in 37s**. `stripDebugDebugSymbols` was UP-TO-DATE and printed no new strip warning in this run. BUILD-001 remains from earlier builds.

### Unit tests

| Suite | tests | failures | errors | time |
| --- | --- | --- | --- | --- |
| `com.triguide.ai.domain.NavigationDecisionEngineTest` | 32 | 0 | 0 | 0.092 s |
| `com.triguide.ai.domain.DetectionReportsTest` | 12 | 0 | 0 | 0.065 s |
| `com.triguide.ai.domain.PerformanceMonitorTest` | 4 | 0 | 0 | 0.014 s |
| `com.triguide.ai.sim.TricycleKinematicsTest` | 9 | 0 | 0 | 0.007 s |

App total 48/48. Sim-core 9/9. No instrumentation tests were run.

Navigation cases covered by `NavigationDecisionEngineTest`: obstacle on the left, obstacle on the right, slight versus full turn, center box, empty frame, both sides, a dominant side, a camera sample that claims metric depth, contradictory geometry, stale frame, missing timestamp, rapid direction changes, a delayed reversal, synthetic corridor clear, synthetic center obstacle, synthetic both sides, and the virtual wheel following a confirmed command.

### APK

| Path | Bytes | SHA-256 |
| --- | --- | --- |
| `android-app/app/build/outputs/apk/debug/app-debug.apk` | 81,293,198 | `26532FEC9A283CE4B34D4F57840E47659ED27BCF8866B8FC251C8A732DF2B6C0` |
| `outputs/apk/TriGuideAI-0.3.0-debug.apk` | 81,293,198 | same hash |

`aapt dump badging` (build-tools 36.0.0): `versionName='0.3.0'`, `versionCode='3'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Native ABIs: arm64-v8a, armeabi-v7a, x86, x86_64. Asset `assets/models/efficientdet_lite0.tflite` is in the APK. `aapt dump permissions`: `CAMERA` plus the AndroidX non-exported receiver permission. `INTERNET` is not in the APK.

### Emulator smoke (not a phone, not an accuracy test)

**Status: PASS_WITH_WARNINGS**

- `adb devices` before boot: no devices. After boot: `emulator-5554` only. No phone.
- AVD `Pixel_8a`, emulator 36.1.9.0. System image path logged by the emulator: `system-images\android-36\google_apis_playstore_ps16k\x86_64`.
- `adb shell getprop sys.boot_completed` → `1`. `ro.product.model` → `sdk_gphone16k_x86_64`. ABI `x86_64`. `wm size` → `1080x2400`.
- `adb install -r outputs\apk\TriGuideAI-0.3.0-debug.apk` → `Success`.
- `adb shell am start -n com.triguide.ai/.MainActivity` → started. One launch `pidof` → `7403`, later relaunches `14022` and `16613`. `dumpsys activity activities` showed `com.triguide.ai/.MainActivity` as `topResumedActivity` on the first launch.
- Home text included `Object detection: READY. MediaPipe EfficientDet-Lite0 int8`, `Navigation: Unknown. None`, and `Floor segmentation: NOT_STARTED. Disabled.`
- Robot Simulation, manual mode, before any button press: `Decision: Manual`, `Confidence: not used`, `Front wheel response: 0.0 degrees`, `Illustrative path, not verified.`
- About 2 s after selecting Synthetic world on that first visit: `Decision: Straight`, confidence `80 percent`, front wheel `0.0 degrees`, virtual speed `0.40 m/s`, reason `Synthetic map: the authored list has no obstacle in the forward corridor.`
- Fresh launch, about 0.7 s after selecting Synthetic world: `Decision: Slight right`, reason `Synthetic map: left barrel is left of the virtual robot. Steering slight right. This is a simulator map.`, confidence `80 percent`, front wheel `-8.6 degrees`, virtual speed `0.25 m/s`, source `Synthetic map`.
- `adb shell pm grant com.triguide.ai android.permission.CAMERA`, then a tap on Start Camera.
- Live Vision text: `Decision: Hold`. Reason: `No confirmed passage. An empty detection is not open space. Depth is not measured.` Confidence `55 percent`. Front wheel `0.0 degrees`. Source `Camera 2D`. The research-mode line and `Illustrative path, not verified.` were on screen. Detector line: `found no objects at or above 0.50 confidence.` Performance line: `Preview 28 FPS. Inference 8 FPS, last 51 ms, average 71 ms.`
- Logcat `TriGuideDetect`: `detector ready model=MediaPipe EfficientDet-Lite0 int8 delegate=CPU`. `first inference latencyMs=182.0 objects=0`.
- Logcat `TriGuideNav`: `None UNKNOWN No real camera frame is available...` then `Camera 2D HOLD No confirmed passage...`
- Filtered `AndroidRuntime:E` had no FATAL line.
- Zero objects means no box was drawn and the camera path did not turn the wheel. This is not an accuracy score.
- The first `uiautomator` dump of the session failed with `null root node`. A later upward swipe from low on the screen landed on the launcher. The readings above are from the retries that showed TriGuide text.
- `adb emu kill` stopped the emulator. A follow-up `adb devices` listed no devices.

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
| Redmi / physical phone install and launch | PASS_WITH_WARNINGS | 2026-10-18 later: `55aqpfqkvw9xbafe` (Redmi Note 12 Pro 5G, Android 14) authorized. `TriGuideAI-0.4.0-debug.apk` installed and `MainActivity` resumed. No crash. Camera permission still denied. Earlier MTP-only polls in this file are superseded for install |
| Phone-only testing | Policy | User: installs and smoke tests on this Redmi only. Emulator results in this file are non-authoritative going forward |
| GuideLens `assembleDebug` | NOT_STARTED | `compileSdk` 36 platform is not installed; app is not the shipping target |
| On-device detection accuracy | NOT_STARTED | Camera was not started on the Redmi. No labeled scene was run |
| Floor segmentation | NOT_STARTED | Disabled. No floor model is loaded. Home on the Redmi still showed `NOT_STARTED` |
| Camera-derived turn on a real scene | NOT_STARTED | Start Camera was not tapped. `CAMERA` is denied until the user allows it |
| Redmi inference latency | NOT_STARTED | App is installed. Live Vision was not opened, so there is no phone FPS number |
| TalkBack and simulator buttons on the phone | NOT_STARTED | Home was read. Robot Simulation, Test Scenarios, Diagnostics, and Settings were not opened |
