# Progress Log

## 2026-10-20 — 0.9.23/36: storage hardening + isolated Raw Depth

Installed0.9.23/36 over USB and verified installed APK SHA against the gate;
original0.9.22 APK preserved, no app-data clear. App left force-stopped.
Final offline gate PASS:424 app +10 sim +19 policy +10 evaluation =463 tests,
zero failures/errors/skips; debug/release builds and APK policy pass. Lint has
0 errors,54/34 warnings. Atomic bounded stores, honest person persistence
failure results and RAM/disk-isolated coalescing note writes are implemented.
Cursor review caught integration issues; seven added RED-first tests and an
existing oversized-file regression drove corrections before final acceptance.
Debug Raw Depth decoder/admission/lifecycle instrument is implemented, not
navigation fusion. On Redmi, invalid-duration refusal closes with zero frames;
valid depth and0.9.23 UI/voice remain untested because the phone screen locked.
Full evidence/limits/next pair: `docs/STORAGE_AND_RAW_DEPTH_2026-10-20.md`.
Final source999bb71 was committed/pushed by Codex and exact origin ref verified.
Owner allows public sanitized source, not private images/logs/weights/APKs;
unrelated owner-rule edit was preserved unstaged. No APK publicly uploaded.

## 2026-10-19 — Engineering audit + local verification / private-evidence boundary

Implemented two foundational improvements, not another perception feature:

- Source-grounded architecture/storage/testing/evaluation/performance audit and
  five ranked weaknesses; staged options in `docs/ENGINEERING_AUDIT_2026-10-19.md`.
- `scripts/test/verify-local.ps1`: offline-default tests, Lint, debug/release
  builds, actual APK asset/hash/permission/experimental-isolation checks, source
  fingerprint and independent new report directory. No ADB/install/staging.
- Private reports/test-data/debug weights ignored; tracked-capture regression
  check. Removed room fixture from APK assets by preserving it outside assets;
  static probe reads only an explicitly supplied app-private fixture and hashes
  its input. Existing reports/APKs/photos were not deleted or overwritten.
- Lint detected one Compose producer-assignment error: made existing increment
  explicit without suppressing the check or changing the timer's semantics.
- Final gate PASS: **339 app + 10 sim + 18 policy + 10 evaluation = 377** tests,
  no failures/errors/skips, both assemblies/Lint pass. Lint retains 40 debug / 30
  release warnings. See `outputs/reports/quality-gate/20261019T083518346232Z/`.
- USB static probe passed missing-input refusal and local-image inference on a
  temporary candidate. Restored original installed 0.9.22/35 hash-exact, data
  preserved, app force-stopped; no camera/audio/depth/walking run. Report:
  `outputs/reports/quality-gate/20261019T082859291386Z/phone-static/summary.json`.
- Release remains byte-identical. No permanent phone update, Cursor completion
  claim, commit/push, paid service, private upload or sensitive permission added.

Next pair: measured isolated official Raw Depth/confidence and resilient bounded
local storage. Neither is implemented by this audit. Earlier milestones below
are history; 0.9.22 original remains on the phone, not this working debug APK.

## 2026-10-19 — 0.9.22 surface lab and free depth capability

**Installed and retained: 0.9.22/35 on Redmi over USB.** Installed APK hash
matches the sealed candidate; data preserved and guidance left paused.

- More exposes a bounded debug wall/floor/rug lab with original object boxes,
  correct letterbox projection/expiry and explicit cautious surface narration.
  Possible/unknown is not presented as confirmed blockage; risk policy unchanged.
- Saveable restore always pauses; actual lab result returns to paused More.
  Real More → lab → Describe → Stop → More → Back and silent regression pass.
- ARCore AUTOMATIC/raw support confirmed on phone, **zero depth frames** acquired.
  Measure Raw Depth/confidence next before model replacement or paid hardware.
- **339 app + 10 sim tests**, final debug/release build pass. Earlier failed
  mixed-context/harness assertions retained and documented; heard audio unverified.
- Evidence: `docs/SURFACE_LAB_AND_DEPTH_2026-10-19.md`. Free-first roadmap:
  `docs/WALL_RUG_DEPTH_ROADMAP_2026-10-19.md`. No CLEAR/turn or walking claim.

## 2026-10-19 — Cable ADB verified and preferred

- Known Redmi is now authorized on USB; Windows ADB interface status OK.
  Read-only comparison: 12/12 commands each; USB shell p50 **35.99 ms** versus
  Wi-Fi **41.99 ms**. Pulling the same 88.7-MB installed APK took **2.118 s**
  versus **3.646 s**. Both hashes match the verified original 0.9.21/34.
- Prefer explicit USB serial for subsequent tests; paired Wi-Fi remains a
  fallback. No app installation/launch, code, data or phone settings changed.
  This does not change inference speed or prove long-run USB reliability.
- Evidence: `docs/REDMI_USB_TRANSPORT_CHECK_2026-10-19.md`.

## 2026-10-19 — Combined object boxes and explicit description preview

**Status:** Debug-only implementation and final Redmi checks pass; original restored.

- The missing boxes/audio belonged to the intentionally isolated MOSAIC probe,
  not a deletion from the main app. Added opt-in combined observation mode using
  the existing detector, yellow named boxes, theme, offline narrator and settings.
- Explicit Describe only, no automatic narration or navigation. Fixed a render-
  clock race that intermittently hid fresh results/disabled the description button.
- **320 app + 10 unchanged sim tests** and debug/release build pass. Final Redmi
  20-s run: 214 detector completions, no speech; Describe/Stop handler checks
  and default-silent regression pass. Heard audio/TalkBack remain unverified.
- Original **0.9.21/34** restored with exact installed APK hash match; release
  bytes unchanged. Candidate is a temporary debug preview, not the installed default.
- Evidence and outstanding load/accuracy/safety gates:
  `docs/COMBINED_VISION_PREVIEW_2026-10-19.md`.

## 2026-10-19 — Live camera diagnostic and stale/session guard

**Status:** Debug-only implementation/build/tests and two Redmi live runs pass.

- Reused existing CameraX and pretrained MOSAIC in an isolated, timed camera
  activity. Shared static/live preprocessing and bulk raw RGB packing reduced
  observed preparation p50 **56.81 -> 8.00 ms** and callback-to-map p50
  **81.65 -> 30.15 ms**. Different scenes/camera rates: no FPS gain is claimed.
- Added one-in-flight/session/receipt-age gate with 16 tests, plus 4 byte-packing
  parity tests. **311 app tests / 10 unchanged sim tests** pass; debug/release
  build succeeds and release APK bytes remain unchanged.
- Two 20-second live runs and static-image class-proportion regression passed.
  Both lifecycle checks stopped on Home/pause with no later sampled frames.
  No model-to-navigation, speech, image recording, or network permission added.
- Original 0.9.21/34 restored with exact installed APK hash match after cycles.
  Camera capture age, combined detector load, stairs and walking remain open.
- Evidence and limitations: `docs/LIVE_SEGMENTER_DIAGNOSTIC_2026-10-19.md`.

## 2026-10-19 — Wireless Redmi recovery and post-fix replay complete

**Status:** Paired Wi-Fi connection verified; original installed APK restored.

- Recovered from missing USB by modern wireless ADB pairing. When its connect
  port changed, a fresh isolated mDNS discovery found the known Redmi endpoint;
  no new pairing, legacy port 5555, or global ADB restart was needed.
- Installed the sealed input/decoder diagnostic over Wi-Fi and ran MOSAIC raw
  RGB NNAPI **1000** times: **17.06 ms p50 / 21.14 ms p90 / 56.35 sequential
  inferences/s**. A legacy-input control and DeepLab UINT8 raw-input CPU batch
  also passed. All are identical-still replay, not live-camera FPS or accuracy.
- All three batches logged no voice events. Restored original **0.9.21/34**;
  installed APK pulls before/after match SHA-256 exactly. No app data cleared.
- USB root cause, full camera latency, stair protection, and walking safety
  remain unverified. No production guidance or model integration was changed.
- Evidence: `docs/REDMI_WIRELESS_AND_POSTFIX_PROBE_2026-10-19.md`.

## 2026-10-19 — Public labeled evaluation and diagnostic contract repair

**Status at this checkpoint:** Offline evaluation complete; phone run was
pending. The newer wireless recovery entry above completes static validation.

- Downloaded the official ADE20K archive, verified all ZIP CRCs, and extracted
  **2,000 validation image/mask pairs** outside the repository. No new owner
  photographs were needed and no model was trained.
- Added a reproducible local LiteRT evaluator with pinned hashes, frame hashes,
  same-stem masks, reference-derived resizing, 31-class confusion/IoU, and
  separate stair-as-floor counts. All three full runs used identical data.
- MOSAIC: **58.81% mIoU / 73.86% wall IoU / 77.43% floor IoU**. DeepLab float:
  **55.25% mIoU**; DeepLab UINT8: **54.84%**. These are internal host-CPU semantic
  evaluations, not official MLPerf submissions or Redmi accuracy measurements.
- On **1,799,391** stair pixels in 180 images, MOSAIC predicted floor for
  **19.45%**; float DeepLab **15.21%**; UINT8 DeepLab **20.00%**. Top31 has no
  stair class. MOSAIC leads average semantics but cannot authorize movement.
- Fixed the diagnostic's speculative 31-channel decoder by accepting only the
  actual pinned INT32 maps (ARG_MAX depth 32). Preserved byte order and tested
  truncated/out-of-range masks. Corrected default UINT8 input to raw RGB per
  the upstream evaluator, retaining the earlier encoding as an explicit option.
- Cursor completed two direct CLI read-only audits; Codex verified its claims
  against raw upstream code and actual LiteRT kernels before accepting changes.
- **291 app + 10 sim-core JVM tests**, **10 Python tests**, and debug/release
  builds pass. Release hash/assets/permissions are unchanged.
- Redmi disconnected before the new backup/install step. No new APK was
  installed; the last verified original phone package remains 0.9.21/34.
- Full results, hashes, limitations, commands, and the next device gate:
  `docs/ADE20K_OFFLINE_EVALUATION_2026-10-19.md`.

## 2026-10-19 — Static segmenter candidate benchmark on Redmi

**Status:** Local/debug-only model evaluation complete; no navigation behavior changed.

- Compared the official ADE20K DeepLab float/uint8 pair and MLCommons MOSAIC R4
  on one owner-provided still. DeepLab uint8 was smaller but slower than float;
  MOSAIC CPU median p50 was **359.80 ms**. See
  `docs/DEEPLAB_UINT8_REDMI_AB_SETUP_2026-10-19.md` for full methods and caveats.
- MOSAIC R4 NNAPI delegated **100/100 graph nodes**. Three static replay batches,
  including 1,000 inferences, measured sustained sequential inference at **54.82
  FPS** (p50 **17.39 ms**, p90 **22.36 ms**). This is not live-camera FPS,
  end-to-end latency, or an accuracy/safety result.
- ADE20K Top-31 IDs are now mapped to source labels in debug diagnostics; the
  class-31 boundary is fixed and tested. Predictions still have no labeled Redmi
  accuracy score, and a `floor` label is not proof of traversable space.
- All output remains diagnostic-only. No `CLEAR`, turn, speech, model-to-camera,
  network, or release integration was added.
- Redmi original **0.9.21 / versionCode 34** restored after temporary diagnostics;
  pulled installed APK SHA matches the pre-test backup exactly.
- Fixed an off-by-one in the debug class-map validator: MLPerf's ADE20K evaluator
  iterates semantic IDs **1..31**, with 0 ignored/background. Added boundary and
  label-table tests accepting 31 and rejecting 32.
- Final build: debug + release assembled; **287 app** and **10 sim-core** JVM
  tests pass. Debug APK carries local candidates; release APK does not.
- Next gate: verify preprocessing against the reference, collect consented
  stationary labeled scenes, then measure full live-camera pipeline and
  thermal/failure behavior before considering any guidance integration.

## 2026-10-19 — Relative-depth synthetic risk-gate benchmark (0.9.21)

**Status:** Desktop tests + Redmi install. No LiteRT, no MiDaS bytes, no nav wiring.

- Extended `RelativeDepthBenchmarkHarness` so each synthetic case also asserts
  `RelativeDepthRiskGate` HOLD-only merge (normal depth cannot clear detector HOLD;
  wall/drop raise HOLD without detector hazard).
- Cases: **11** deterministic scenes; TSV export includes hold/reason columns.
- `:app:testDebugUnitTest` **285** PASS; `:sim-core:test` **10** PASS.
- APK `outputs/apk/TriGuideAI-0.9.21-debug.apk` installed on Redmi `55aqpfqkvw9xbafe`
  (versionName **0.9.21** / versionCode **34**). Static only — no walking.
- Next: optional Interpreter-only model I/O on a **separate branch**; still HOLD-only.

## 2026-10-19 — Relative-depth model preflight gate — 0.9.20

**Status:** Implemented and unit-tested; no model bytes or runtime were added.

- Added `RelativeDepthModelContract` for the first benchmark candidate: MiDaS small
  LiteRT fp16, pinned asset path, source, MIT provenance, tensor shape, ImageNet
  preprocessing, output semantics, and recorded SHA-256.
- Added fail-closed preflight tests for missing asset, malformed hash, wrong hash, and
  the exact pinned hash. A pass means benchmark eligibility only; it cannot produce
  navigation evidence, `CLEAR`, `STRAIGHT`, `TURN`, or speech.
- The candidate remains unbundled and the app remains offline by default.
- Added `RelativeDepthRiskGate` and `RelativeDepthInferenceCadenceGate`: future
  depth output can only add a HOLD contribution, never lower an existing hazard;
  depth work is budgeted at a separate 4 Hz starting cadence with no queue.
- The new policy is intentionally not connected to live camera/navigation yet.
- The first official LiteRT CPU runtime trial was reverted after Kotlin metadata
  incompatibility; a future runtime migration must be isolated and golden-output gated.

## 2026-10-19 — Relative-depth policy slice — 0.9.20

**Status:** Implemented and unit-tested; deliberately not wired to live navigation.

- Added model-agnostic `RelativeDepthEvidence` for future normalized inverse-depth maps.
- Wall/large-obstacle and possible-drop states can only raise diagnostic risk; normal
  depth evidence never creates clearance.
- Added fail-closed tests for stale, out-of-order, malformed, empty, and non-finite maps.
- No model asset, camera path, TTS, `CLEAR`, or `TURN` behavior was added.

## 2026-10-19 — Foundation milestone (evidence / diagnostic UX / replay) — 0.9.20

**Status:** Implemented in worktree for Codex review. **Do not treat as committed.**

- Safety: green open-lane gated on fused CLEAR only (live fused=null → no green); detector-empty never beeps as “open”.
- Diagnostic label: `observed candidate — not a safety signal`; neutral UNKNOWN band.
- One soft camera-active tone on Streaming+running; STOP/pause suppresses (`CameraActiveCue`).
- `PerceptionEvidenceStats` on `GuidanceSession` reject paths; replay samples gain failureReason + optional device counters.
- ImageProxy: documented sole close via `CameraXSource` `use{}` (no second analysis stream).
- Separation note: `docs/RESEARCH_SIM_BRIDGE.md`.
- Version: **0.9.20** / versionCode **33**. Tests/APK: see TEST_RESULTS after quality gates.
- Next smallest: Redmi static no-walking install + Codex diff review before depth/model work.

## 2026-10-19 — Open-lane green strip + soft beep (0.9.19)

**Status:** Built and unit-tested. Redmi install **blocked** (ADB list empty at install time).

- Green lower corridor strip when PathRisk is **UNKNOWN** (no detector obstacle in
  band 0.25–0.75). Caption: not proven safe. AI Vision overlay defaults **on**.
- Quiet double soft-beep (`SoftOpenLaneBeeper`, ToneGenerator) while guidance runs
  and camera streams, cooldown ~2.8 s — not CLEAR speech.
- Domain: `OpenLaneCue`; UI: `AiVisionRiskOverlay` walk lane; wire in `TriGuideRoot`.
- Tests: `:app:testDebugUnitTest` **248** PASS (0 fail), `:sim-core:test` **10** PASS
  (earlier same session).
- `assembleDebug` → `outputs/apk/TriGuideAI-0.9.19-debug.apk` (88,700,133 bytes),
  SHA-256 `CF26CDE44F6DFB8678347BD62638699B59797626C8C15BE96AE7DAFDB82B5731`.
- Install: `redmi-install.ps1` failed — `55aqpfqkvw9xbafe` not listed. No emulator.
- Version: **0.9.19** / versionCode **32**.
- Next: owner reconnect USB → install; ear check soft beep + green lane vs chair HOLD.

## 2026-10-19 — Milestone B: AI Vision diagnostic overlay (0.9.18)

**Status:** Built, unit-tested, installed on Redmi. No SegFormer / ADE20K weights in APK.
CLEAR is not wired from overlay or floor seam to navigation/TTS.

- PHASE2 ledger has no rights-cleared indoor floor model for bundling; shipped
  **AI Vision Mode** research overlay instead: Live Vision toggle draws corridor
  band **0.25–0.75** with PathRisk colors (green=UNKNOWN/off-path diagnostic,
  yellow=POSSIBLE, red=BLOCKED; blue only for synthetic CLEAR marker).
- Domain: `AiVisionDiagnostic` + UI `AiVisionRiskOverlay`; Diagnostics shows
  `FloorSegmenterCameraAdapter.diagnosticSummaryLine()` + AI Vision legend.
- Floor segmenter remains NO_MODEL / class map **UNMAPPED**.
- Tests: `:app:testDebugUnitTest` **246** PASS, `:sim-core:test` **10** PASS.
- `assembleDebug` → `outputs/apk/TriGuideAI-0.9.18-debug.apk` (88,867,433 bytes),
  SHA-256 `26127F192BE77CA4FEB8AEB300808088420D41174F6F7EB69870009D1AF373AE`.
- Redmi `55aqpfqkvw9xbafe` install Success; `dumpsys` **versionName=0.9.18** /
  versionCode **31**. No emulator.
- Codex checklist: `docs/CODEX_TEST_CHECKLIST.md` (owner ear/scene separate).
- Version: **0.9.18** / versionCode **31**.
- Next: Milestone C depth spike (HOLD-only) after Codex/owner smoke — not started.

## 2026-10-19 — Milestone A: corridor relevance + Evidence Recorder (0.9.17)

**Status:** Built and installed on Redmi. Desktop tests earlier: `:app:testDebugUnitTest`
PASS, `:sim-core:test` PASS, `assembleDebug` → `outputs/apk/TriGuideAI-0.9.17-debug.apk`.

**Install (owner «الان؟» retry):** `ensure-android-path.ps1` + `adb devices -l` showed
`55aqpfqkvw9xbafe` as `device` (22101316G / ruby). `scripts/test/redmi-install.ps1`
installed `TriGuideAI-0.9.17-debug.apk` — streamed install Success.
`dumpsys package` confirmed **versionName=0.9.17** / versionCode **30**.
Launched `com.triguide.ai/.MainActivity` via `am start` (Starting: Intent reported).
No emulator. Milestone B not started. Owner ear/scene test still needed.

**Prior attempt (owner «تم»):** ADB list empty; install skipped.

- Walking corridor narrowed to **x 0.25–0.75** so far-edge chairs no longer force HOLD.
- `DecisionEvidenceRecorder` (text-only) wired through `GuidanceSession`; shown/shared from Diagnostics.
- Owner roadmap map: `docs/OWNER_ROADMAP_EXECUTION.md` (success = fewer critical direction errors, not label count).
- Version: **0.9.17** / versionCode **30**.

## 2026-10-19 — Phase 2–3 continued (0.9.16)

**Status:** Desktop. `:app:testDebugUnitTest` **235** PASS, `:sim-core:test` **10** PASS,
`assembleDebug` SUCCESS → `outputs/apk/TriGuideAI-0.9.16-debug.apk` (**not** installed).

- Phase 2 candidate ledger + MediaPipe TFLite SHA-256 hash copies (not in APK).
- Phase 3 `EvidenceFusionGate` fail-closed; production mapping UNMAPPED so CLEAR cannot fire live.
- Phase 4–5 deferred (no Redmi segmenter measurements; no TTS clearance language).
- Version: **0.9.16** / versionCode **29**.

## 2026-10-19 — Phase 0–1 diagnostic floor segmenter seam (0.9.15)

**Status:** Desktop only. `:app:testDebugUnitTest` 218 PASS, `:sim-core:test` 10 PASS,
`:app:assembleDebug` SUCCESS. APK **not** installed. Redmi **not** used.

- Phase 0 ledger: `docs/UPSTREAM_PERCEPTION_INTEGRATION.md` (Sailens / MediaPipe samples /
  PaddleSeg / BlindAssist). No model weights downloaded.
- Phase 1: `FloorSegmenterDiagnostic` + camera analysis fan-out; results →
  `FloorMaskEvidence` / `EvidenceReplayHarness` only. Class mapping UNMAPPED.
- NavigationDecisionEngine / VoiceGuidance / emergency STOP untouched.
- Stop before Phase 2 (candidate weight benchmarking).

## 2026-10-19 — `closed-door-wall` Redmi capture

**Status:** Captured and visually reviewed as a stationary indoor scene. The
frame shows closed doors/wall and tile floor with no visible person. This is a
negative/uncertain perception result, not a route or safety claim.

- Command: `.\scripts\test\redmi-evidence-baseline.ps1 -SceneId closed-door-wall -DurationSeconds 20`.
- Artifact directory:
  `outputs/reports/redmi-evidence/closed-door-wall-20261019-050957/`.
- The app remained fail-closed: `HOLD=0`, `UNKNOWN=322` filtered navigation
  lines, and `CLEAR/turn=0`. Early logs show `no_camera_frame`; once frames
  arrived, the detector produced mostly `drawn=0:none` and one low-confidence
  below-threshold refrigerator result. The screenshot itself shows the scene,
  but a screenshot is not a walkable-area mask.
- Detector log samples: 17; approximate FPS `0.61`; latency p50 `77 ms`, p90
  `91 ms`. Voice logs contain one `Guidance on.` request and no obstacle
  request; audible output remains unverified.
- Snapshot: total PSS `162194 KB`, native heap `57204 KB`, Dalvik heap
  `4360 KB`, battery level `100`, USB charging, temperature raw `335`.

This capture is eligible for later replay/label review, but it must not be
used to compare segmentation models or authorize movement. The app was stopped
after collection.

## 2026-10-19 — Repeatable Redmi evidence collector and camera-stream baseline

**Status:** Collector verified on the connected physical Redmi. This is a
camera/runtime baseline only; the captured view was the owner's clothing/body
close to the lens, not one of the required walkability scenes.

- Added the phone-only local collector
  `scripts/test/redmi-evidence-baseline.ps1`. It refuses emulator serials,
  does not install or clear app data, captures start/end screenshots and UI
  XML, filters navigation/detector/voice logs, and records memory/battery
  snapshots. It writes only under local `outputs/reports`.
- Replayed scene id `owner-occlusion` on installed 0.9.14/27 without walking.
  The latest 6-second run recorded 14 detector samples, approximate FPS 2.33,
  detector latency p50 64 ms / p90 86 ms, 98 HOLD log lines, 12 UNKNOWN log
  lines, and zero clear/turn log lines.
- Voice logs contained one skipped obstacle request during startup, one
  `Guidance on.` session notice, and one `Obstacle ahead.` request. This is
  request/log evidence only; it is not proof that any cue was heard.
- Snapshot: total PSS `165396 KB`, native heap `60636 KB`, Dalvik heap
  `5432 KB`, battery level `100`, USB charging, temperature raw `339`.
- Artifacts are under
  `outputs/reports/redmi-evidence/owner-occlusion-20261019-045848/`.
  No image/video was uploaded or added to the manifest as labeled scene data.

The next valid capture requires the owner to hold the Redmi stationary toward
one consented scene from the manifest (for example `closed-door-wall` or
`open-corridor`). Until then, these measurements must not be used to compare
segmentation models or authorize a path.

## 2026-10-18 — Local Redmi evidence replay harness

**Status:** Implemented and desktop-verified as a diagnostic-only seam. No
navigation behavior, model, TTS policy, network permission, or AI companion
was added or changed.

- Added `EvidenceReplayHarness` around the existing `FloorMaskEvidence`
  contract. It replays deterministic per-scene candidates and summarizes
  evidence states/issues, accepted-frame timing, latency p50/p90, effective
  FPS, recorded decision keys, and recorded voice-event keys. It cannot emit
  `CLEAR`, `STRAIGHT`, a turn, navigation, or speech.
- Added JVM coverage for empty, stale, malformed, out-of-order, duplicate,
  independent-scene, invalid-latency, and blank-scene inputs. The app suite is
  now 204/204 and the simulation suite remains 10/10.
- Added the consented-scene template at
  `test-data/scene-manifest.example.tsv`; no captured phone media was added.
  Required scenes remain unlabeled until a controlled, stationary Redmi
  capture is actually reviewed.
- `:app:testDebugUnitTest :sim-core:test` and `:app:assembleDebug` passed.
  The diagnostic APK is 0.9.14/27, 88,770,219 bytes, SHA-256
  `00207876D9A8649AEA42580021DAD8CF490B56185FFCA31EFEF6D0866D913240`.
  It was not installed because the harness is not runtime-wired and installing
  it would not add evidence.
- A stationary smoke on the already-installed Redmi build remained fail-closed:
  the log showed `no_camera_frame` and `UNKNOWN`, with no safe-path or turn
  result. Snapshot readings were total PSS `176546 KB`, native `45037 KB`,
  Dalvik `3743 KB`, battery level `97`, temperature raw `274`, charging.
  This is a camera-start/failure baseline, not a scene-accuracy result.

Next evidence gate: obtain consented, stationary scene captures on the Redmi,
then replay and label them. No free-walk or positive-clear claim is allowed
until the independent mask, temporal, obstacle, and unknown-region criteria
are proven.

## 2026-10-18 — Redmi 0.9.14 safety navigation fix

**Status:** Built, installed, and reproduced fix on Redmi. This is a navigation
state repair, not a new vision model or assistive-safety validation.

- Found a real regression in 0.9.13: after STOP, three down swipes opened More;
  Android Back called `LiveNavState.start()` and silently re-enabled running and
  the camera. The 23:19 phone screenshot `redmi-0913-after-more.png` shows
  `Path not confirmed`, black preview, and STOP instead of paused state.
- `LiveNavState.returnToLive()` now changes only the screen. System Back and
  back callbacks from Simulator, Scenarios, Diagnostics, and Settings use it;
  an explicit Restart gesture or the clearly labeled `Live guidance` button
  remains the only intentional start. Three focused tests cover stopped and
  active More returns plus other tools.
- `:app:testDebugUnitTest` 200/200, `:sim-core:test` 10/10, and
  `:app:assembleDebug` passed. APK 0.9.14/27 is 88,650,981 bytes, SHA-256
  `25D20786100EAE71D33159ADED7CAF1807B5BC3F3D30BEF8D9AFE556CDCDA11B`.
  Installed with `adb install -r -t` Success; no app data was cleared.
- Repeated STOP → More → Android Back on Redmi: screenshot
  `redmi-0914-paused-more-back.png` shows `Guidance paused`, no camera preview,
  and no STOP. After the one in-flight navigation callback at 23:23:04.664,
  no further nav/detector log appeared during the return check. Triple-left
  explicitly restarted at 23:23:36.696; STOP was then activated at 23:23:43.
  App was left paused. Human touch timing and speaker quality remain open.
- 0.9.13 More screen Diagnostics text was also visually checked on Redmi:
  `Floor segmentation: NOT_STARTED`, disabled, no bundled/validated floor
  model, and boxes do not prove an open path (`redmi-0913-more.png`).

## 2026-10-18 — Redmi 0.9.13 install and first live smoke

**Status:** Partial phone smoke, not an assistive-safety validation. Redmi
`55aqpfqkvw9xbafe` changed from installed 0.9.10/23 to 0.9.13/26 with
`adb install -r -t` Success; `dumpsys package` confirmed the version. App data
was not cleared. ADB briefly dropped after launch, then the owner reauthorized
USB debugging and testing resumed.

- Real Live Vision screenshot `outputs/reports/redmi-0913-live-2.png` shows the
  logo, short warning/status, camera preview, and sole STOP control. Camera
  detector streamed; its box on the left was `person` over the owner's arm,
  so it is not scored as a false positive. Navigation log stayed BLOCKED/HOLD;
  no live walk/turn/clear decision was observed in this sample.
- ADB tap on STOP produced `emergency stop activated` at 23:11:05.277 and one
  `Guidance paused.` TTS request at 23:11:05.393. Paused screenshot
  `outputs/reports/redmi-0913-stopped.png` shows camera preview/STOP gone;
  no detector/nav output persisted after the in-flight 23:11:05.394 callback.
  This is not a human touch-latency or audible-speaker measurement.
- Three upward ADB swipes were accepted as DESCRIBE and submitted one spoken
  description request. The scene was moving and owner/body occlusion was in
  view, so this was a shortcut smoke test, not label accuracy. A later three-UP
  test on the paused screen requested `Guidance paused. Restart to describe.`
  at 23:17:34; it did not narrate a stale frame.
- Controlled fixed **floor/mat**, not corridor: screenshots
  `redmi-0913-fixed.png` (23:15:17) and `redmi-0913-fixed-end.png` (23:16:15)
  show a tile/mat scene with no boxes and `Path not confirmed`. During the
  sampled 23:15 minute, 506 navigation entries were UNKNOWN and no
  `TriGuideVoice` requests occurred. This supports quiet, fail-closed behavior
  for this scene only; it does not prove a clear route or audible silence.
- After 23:16:35 detections changed rapidly (including full-frame, mutually
  inconsistent labels), and `Obstacle ahead.` was requested at 23:16:36 and
  23:16:47. The scene was no longer stable, so these are not evidence of a
  fixed-scene reminder regression. They do expose noisy model labels and a
  moving-scene speech cadence worth a separate measured review.
- Actual corridor, wide chair, TalkBack, large text, heard audio quality,
  camera failure/retry, and junction accuracy remain open.

## 2026-10-18 — Diagnostic floor-mask evidence seam (0.9.13)

**Status:** Desktop-verified candidate only. `:app:testDebugUnitTest` 197/197,
`:sim-core:test` 10/10, and `:app:assembleDebug` passed. ADB listed no device;
0.9.13 was not installed or phone-tested.

- Added a pure, unwired floor-mask diagnostic contract: model/source identity,
  monotonic frame identity and age, image-to-mask mapping, finite confidence grid,
  optional excluded cells, explicit rejection states, and a four-connected
  bottom-center floor-like cell count. Fifteen focused tests cover toy masks,
  exclusions, malformed data, stale/duplicate/out-of-order frames, and clock
  boundaries. This is **not** a live segmenter or a safe-route result.
- Diagnostics wording no longer implies SegFormer was selected. No mask/model
  is bundled or run; Live Vision speech, camera HOLD/UNKNOWN decisions, and
  virtual motion are unchanged. Cursor's independent source review found no
  High/Medium defect. Its even-width seed test gap was closed with a new test;
  mapping metadata remains intentionally unverified against a live producer.
- Exact APK: `outputs/apk/TriGuideAI-0.9.13-debug.apk` (versionCode 26,
  88,650,981 bytes), SHA-256
  `2CE55AFFD78B6CA62846415B53D8A4DDF37A698B2F772A70A789998E22DB0CEF`.
  Matching test copy is under `C:\Users\yassi\Videos\TriGuide-Tests`.

## 2026-10-18 — Box-extent corridor correction (0.9.12)

**Status:** Desktop-verified candidate only. `:app:testDebugUnitTest` 182/182, `:sim-core:test` 10/10, `:app:assembleDebug` successful. The Redmi is still absent from ADB, so this was not installed or phone-tested.

- Cursor's independent 0.9.11 review found that a detector box could cover the walking corridor while its center lay just outside the horizontal band. `DirectionalCue` now carries real normalized left/right/top/bottom bounds; the camera adapter mirrors the bounds consistently; risk uses box overlap and lower-edge reach, with safe legacy fallback. Invalid bounds cannot create CLEAR or movement.
- Focused tests cover wide furniture and ignored small objects crossing the corridor edge, wholly outside boxes, and mirrored coordinates. Live-camera movement speech remains disabled; this is better hazard screening, not positive path evidence.
- Exact APK: `outputs/apk/TriGuideAI-0.9.12-debug.apk` (versionCode 25, 88,650,981 bytes), SHA-256 `F950A37F9675772E667650CE72D062ADBE4FE94949D9F3CE62302DEA597A7666`. The 0.9.11 entry below is historical and its APK was kept for rollback.

## 2026-10-18 — Stop-only Live Vision + fail-closed voice/risk (0.9.11)

**Status:** Desktop-verified candidate only. `:app:testDebugUnitTest` 176/176, `:sim-core:test` 10/10, `:app:assembleDebug` successful. Redmi install and touch/TalkBack/audio checks are pending because `adb devices -l` is empty.

- Normal Live Vision now renders the logo, short research warning, one truthful status, two compact shortcut lines, camera preview, and one 64 dp red STOP. The four existing triple-swipe gestures and triple-tap remain; TalkBack custom actions remain. Camera permission recovery is an exceptional visible button.
- STOP triggers on first touch/TalkBack action, immediately zeroes the virtual command and stops the camera, then gives one paused cue. Removed the old 3-second hold. On-demand Describe rejects paused, failed, missing, or stale camera frames.
- Lower-corridor ignored labels such as a cup/knife remain possible hazards. Camera-only false CLEAR and false turn regressions fail closed; automatic dense-scene narration is disabled. Explicit Describe and urgent hazard speech remain.
- This does **not** provide verified walk/turn/junction guidance from live camera. Positive walkable-area and branch evidence is still absent; see `STOP_ONLY_MAIN_SCREEN_SPEC_2026-10-18.md`.
- The next smallest diagnostic-only positive-evidence experiment is specified in `WALKABLE_AREA_EVIDENCE_SPIKE_2026-10-18.md`; no model or movement cue was silently added to this APK.
- Exact APK: `outputs/apk/TriGuideAI-0.9.11-debug.apk` (versionCode 24), SHA-256 `58B18750401174BE92C48D5A194949E9FE7D2AE16D321D706967C85C0330D82C`.

## 2026-10-18 — Main screen: stop-only + logo cues (0.9.8)

**Status:** Installed on Redmi `55aqpfqkvw9xbafe` as **0.9.8** / 21.

- Live Vision: no pressable chrome except the red **3 s** emergency stop. Describe / More / status / restart are gesture shortcuts only (and TalkBack actions).
- Top card: logo + TriGuide + safety line + live guidance prompt (walk carefully / slight turn / stop / keep walking) + brief shortcut tips.
- Camera retry is text + gesture, not a button. Multi-path cue exists in code but stays off until a real junction detector exists.

## 2026-10-18 — Bottom chrome + emergency stop + trust plan (0.9.7)

**Status:** `assembleDebug` PASS → `outputs/apk/TriGuideAI-0.9.7-debug.apk` (versionCode 20). Installed on Redmi `55aqpfqkvw9xbafe` as **0.9.7**.

- Live Vision bottom: **Describe** and **More** as icon + developer subtitles (`boxes only · not clearance`, `tools · diagnostics`).
- Thin full-width **red** emergency bar: continuous **3 s** hold to pause guidance, stop camera, speak paused. TalkBack custom action “Emergency stop”.
- Camera lens frame: larger corner radius (32 dp), double border.
- Research plan for eyes-closed trust (walls/depth/voice): `docs/GUIDANCE_TRUST_IMPROVEMENT_PLAN.md` (Project Guideline, MiDaS LiteRT, phased A–F).

## 2026-10-18 — Full-screen camera + gesture control (0.9.6)

**Status:** Code + 163 unit tests PASS. APK built. Redmi USB dropped before install — pending reconnect.

- Live Vision is full-bleed camera in a curved lens frame. Status and gesture hint are text-only overlays (not buttons).
- Eyes-free: 3× swipe up = Describe, down = More, right = speak status, left = restart; triple-tap still restarts. TalkBack gets the same as custom actions.
- Permission / Retry camera remain real buttons only when needed.
- APK: `outputs/apk/TriGuideAI-0.9.6-debug.apk` (versionCode 19).

## 2026-10-18 — Quality 0.9.4 shipped to Redmi (BUILDER)

**Status:** Unit tests PASS (158). APK installed on `55aqpfqkvw9xbafe` as **0.9.4** / 17 at 14:42:41. Clean launch log shows offline TTS voice id + Obstacle ahead + HOLD/UNKNOWN (no side gap from boxes alone).

- Describe humility: never confidently name toilet-class misfires; person named only at ≥0.75 else hedged.
- Includes prior R0, voice episode fix, calm Live Vision, offline `setVoice`, R1a distinct samples.
- APK: `outputs/apk/TriGuideAI-0.9.4-debug.apk`. Ear check still owner.
- Evidence: `docs/BRIDGE_TO_GPT.md`.

## 2026-10-18 — Redmi voice regression and calm Live Vision UI

**Status:** Real-device voice fix verified on one static scene; new UI candidate desktop-tested but not installed. The exact stop/resume record is `docs/PHONE_TEST_CHECKPOINT_2026-10-18.md`.

- Codex recorded a 42-second Redmi screen+phone-audio sample after Cursor's voice episode fix. The five-second obstacle-warning loop did not recur: one immediate warning and one reminder about 30 seconds later. Camera-failure recovery and an actual empty-corridor scene remain untested on phone. Existing false labels (`toilet` on table, `person` on packaging) remain unresolved.
- Codex specified a calmer default Live Vision screen in `docs/LIVE_VISION_UI_SPEC.md`; Cursor implemented and then polished it after Codex source review. The default screen now favors a short research warning, a plain status, preview, on-demand Describe, and a secondary More action. Detailed metrics remain in Diagnostics. Dynamic detector labels are not auto-read by TalkBack from the preview/overlay.
- Current UI candidate: `:app:testDebugUnitTest` + `:app:assembleDebug` passed with 139 JVM tests / zero failures. The candidate was **not installed** while the phone charges; its real viewport, TalkBack order, and audio behavior are still pending.

## 2026-10-18 — R0 camera evidence correction CODEX

**Status:** Source and existing expected-result assertions updated. Build, tests, and Redmi verification not run.

- Empty, ignored-only, and off-corridor-only object detections now resolve to `UNKNOWN`, with zero virtual speed and steering.
- Any camera-box-only corridor obstacle now resolves to `BLOCKED` + `HOLD`; the app no longer infers a left/right gap from the absence of boxes. `CLEAR_OBSERVED` is reserved for future positive walkable-area evidence and the authored synthetic world.
- Research compared complementary architecture/interaction references and checked Redmi Note 12 Pro ARCore Depth API support. No segmentation model was selected or bundled; ARCore remains optional because its camera-session integration needs a separate device prototype.
- Cursor review task: `docs/BRIDGE_FROM_GPT.md`; architecture: `docs/ROOT_ARCHITECTURE_REDESIGN.md`.

## 2026-10-18 — Simulator comparison and kinematics correction CODEX

**Status:** Source and expected assertions updated; `:sim-core:test` not run.

- Reviewed published alternatives. Webots is stronger for desktop physics/world/sensor simulation, but is not an Android library; keep it separate for a possible later parity harness. ROS-Mobile is a ROS client, not a standalone simulator.
- Kept `:sim-core` for the app's small 2D virtual-command visualization, clarified that it has no rigid-body physics/collisions/sensors, and replaced explicit-Euler pose stepping with exact constant-curvature arc integration.
- Updated the focused kinematics assertions; no build, tests, or APK/device run was performed.

## 2026-10-18 — Short Describe snapshot (0.9.2) BUILDER

**Status:** Code + unit tests + APK PASS. Redmi install BLOCKED (`adb devices` empty).

- Codex `CURSOR_TASK`: short confidence-aware `Describe this frame` from detector boxes only.
- `SceneDescription`: allowlist + weak «جسم»/“something”; Arabic like «قدامك شخص في الوسط وكرسي على اليمين.»; never clearance/safety claims.
- Tests: `SceneDescriptionTest` + full `:app:testDebugUnitTest` PASS (123). `assembleDebug` SUCCESS.
- APK: `outputs/apk/TriGuideAI-0.9.2-debug.apk` (versionCode 15).
- Evidence: `docs/BRIDGE_TO_GPT.md`. Speaker ear-check not claimed.

## 2026-10-18 — Quiet open + camera phase gate (0.9.1) BUILDER

**Status:** Code + unit tests + APK PASS. Redmi install BLOCKED (ADB empty after build).

- One spoken open line only: «قدامك في شنو؟» / What's ahead?
- Disclaimer / triple-tap / fingerprint not spoken on open.
- Camera STARTING no longer feeds repeating camera-unavailable speech from the 50 ms loop.
- Codex P0 from `BRIDGE_FROM_GPT.md` executed; evidence in `BRIDGE_TO_GPT.md`.
- APK: `outputs/apk/TriGuideAI-0.9.1-debug.apk` (versionCode 14).

## 2026-10-18 — Path risk / silence-by-default (0.9.0)

**Status:** 0.9.0 built. Unit tests PASS. Redmi not on USB at install time — install pending.

### Guidance
- `PathRiskAnalyzer` classifies `CLEAR_OBSERVED` / `POSSIBLE_OBSTACLE` / `BLOCKED` / `UNKNOWN`.
- Empty corridor and ignored COCO clutter (cups, books, frisbee glints, etc.) → CLEAR + virtual STRAIGHT, **silent**. Not a safety certificate.
- Strong person/furniture in the walking corridor → BLOCKED; center says “Obstacle ahead.” / “في عائق قدامك.”
- Off-corridor people stay silent.
- Diagnostic field + `TriGuideNav` log line per decision.
- Floor still `NOT_STARTED` (SegFormer deferred; see `docs/PATH_RISK_UPGRADE.md`).

### Voice
- Silence by default on CLEAR and POSSIBLE.
- Non-critical cooldown ~5 s.
- Scene briefs need 3+ objects and longer gaps.

### APK
`outputs/apk/TriGuideAI-0.9.0-debug.apk`

## 2026-10-18 — A dominant side box still turns (0.8.2)

**Status:** 0.8.2 is built and unit-tested. The Redmi was not attached, so this build is not installed and there is no phone log. Floor stays `NOT_STARTED`. No face photo is stored. No paid voice was added.

### Guidance

- The dominant box is the highest score times area. A box at least 1.5 times the area of the next box is used instead when its score times area is still at least 70 percent of that leader.
- If that box is left of the center band (center under 0.42) the virtual robot turns right. If it is right of the band (center over 0.58) it turns left. A box in the outer third (under 0.33 or over 0.67) is a slight turn. A box nearer the center band is a fuller turn.
- One opposite box, or one center box, cancels the turn only when its score times area is at least 70 percent of the dominant box. Weaker boxes are not added together. That was the 0.8.1 hold: a chair on the left stayed Hold because other boxes were treated as both sides.
- A true center box, an empty frame, and two similarly strong sides still hold.

### Phrases

- Dominant chair on the left, weaker box on the right: “Chair on the left. Turn slightly right. Not a measured gap.”
- Chair on the right, no similar box on the left: “Chair on the right. Turn slightly left. Not a measured gap.”
- Strong chair on the left and a similarly strong table on the right: “Objects on both sides. Hold.”
- Chair in the center: “Chair in the center. Hold.”
- Empty frame: “No reported object. Hold. An empty picture is not a clear path.”

### Tests and APK

- `:app:testDebugUnitTest` 113/113 PASS, 0 failures, 0 skipped. New cases: dominant left plus a weaker right box turns slightly right; strong left plus strong right holds; center holds; several weaker right boxes do not cancel a left chair; a clearly larger chair is named instead of a smaller higher-score cup.
- `:sim-core:test` was not run. Kinematics sources did not change.
- `:app:assembleDebug` BUILD SUCCESSFUL. `aapt dump badging` (build-tools 36.0.0): `versionName='0.8.2'`, `versionCode='12'`, `minSdk` 26, `targetSdk` 35, `compileSdk` 35. Badge permissions: `CAMERA`, `VIBRATE`, and the AndroidX non-exported receiver permission. `INTERNET` was not listed.
- Copied to `outputs/apk/TriGuideAI-0.8.2-debug.apk` (81,441,801 bytes).

### Phone

- `adb devices -l` listed no devices. A USB scan did not show the Redmi (`VID_2717` was absent). `adb install` was not run. No logcat was captured. The installed app on the phone, if it is still 0.8.1, does not contain this change.

### المطلوب منك الآن

1. صِل ريدمي بالكمبيوتر ووافق على تصحيح USB. `adb devices` لازم يُظهر `55aqpfqkvw9xbafe` كـ `device`. بدون هذا لا يمكن تثبيت 0.8.2.
2. بعد التثبيت: ضع كرسياً في **النصف الأيسر فقط**، بعيداً عن الوسط، لمدة 10 ثوانٍ. يفترض أن تسمع: “Chair on the left. Turn slightly right. Not a measured gap.”
3. ثم انقله إلى **النصف الأيمن فقط** لمدة 10 ثوانٍ. يفترض أن تسمع: “Chair on the right. Turn slightly left. Not a measured gap.”
4. إذا بقي الكرسي في الوسط، أو كان جسم قوي على الجهتين معاً، فالتوقف صحيح: “Hold”. لا تفتح الإعدادات في هذه التجربة.

## 2026-10-18 — Side turns speak, and the side word matches the zone (0.8.1)

**Status:** 0.8.1 install and phone smoke PASS_WITH_WARNINGS on the personal Redmi. A box on the left committed Slight right and the voice log spoke that turn. Floor stays `NOT_STARTED`. No face photo is stored. No paid voice was added.

### Zoning and phrases

- A dominant box whose center is in the left third (under 0.33) or the right third (over 0.67) suggests a side turn. A weak box in the middle does not cancel that turn. A strong box in the center band (0.42 to 0.58), or a strong box on the opposite side, still holds.
- A turn line names one side. Example: “Toilet on the left. Turn slightly right. Not a measured gap.” It does not say left and center in the same line. A center hold says “in the center” and does not say left or right. The extra limit is one sentence on that cue. The same turn key still waits out its cooldown, so it is not spoken on every frame.
- A committed turn is kept if Hold is still talking, then spoken once the gap allows, including during the opening sentences. The 0.8.0 log dropped “Turn slightly left” after Slight left committed. This build enqueues it.

### Review items

- Settings **Prepare save** calls `requestSave` with the typed name. أحمد is accepted. The English “remember this person as …” parser is not used for that button.
- The pending line says no photo is stored and the save is a name only. It does not say a photo waits until confirm. `storeFace` still returns false.
- Drive-mode TalkBack sentences and moving object-note writes off the guidance tick were not changed in this build.

### Phone (no emulator)

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- `adb install -r outputs/apk/TriGuideAI-0.8.1-debug.apk` → Success. `versionName=0.8.1` `versionCode=11`. APK size 81,441,801 bytes.
- `aapt dump permissions` lists `CAMERA` and `VIBRATE`. `INTERNET` is not listed.
- Cold start of `MainActivity` had no FATAL line.
- `TriGuideNav` committed `SLIGHT_RIGHT` for a box the model called a toilet on the left, then a sink on the left. Those labels came from the model.
- `TriGuideVoice` `speak result=0` `queue=1` `kind=TURN_SUGGESTION` `text=Toilet on the left. Turn slightly right. Not a measured gap.` Result 0 means the engine accepted the utterance. Sound from the speaker was not confirmed by ear in this session.

### Tests

- `:app:testDebugUnitTest` 108/108 PASS, 0 failures. Includes the left-third turn against a weak center box, a strong center hold that does not say “left”, the queued turn after Hold, and an Arabic name saved only after confirm.
- `:sim-core:test` was UP-TO-DATE and was not re-run. Kinematics sources did not change.

### المطلوب منك الآن

ضع كرسياً في **النصف الأيسر** من المعاينة فقط، بعيداً عن الوسط، لمدة 10 ثوانٍ. ثم انقله إلى **النصف الأيمن** فقط لمدة 10 ثوانٍ. يفترض أن تسمع جملة واحدة عن الجهة، مثل “Chair on the left. Turn slightly right.” ثم “Chair on the right. Turn slightly left.” إذا بقي الكرسي في الوسط فالتوقف صحيح. لا حاجة لفتح الإعدادات في هذه التجربة.

## 2026-10-18 — Side cues and consent-only names (0.8.0)

**Status:** 0.8.0 install and phone smoke PASS_WITH_WARNINGS on the personal Redmi. Path guidance stays primary. A centered sink was spoken as Hold. A sink on the right committed Slight left in the navigation log. That turn sentence was not in the voice log. Floor stays `NOT_STARTED`. No face photo is stored.

### Guidance and speech

- The blocking band is the middle of the picture, horizontal centers 0.42 to 0.58. A box clearly to one side suggests a slight turn. A box near that band suggests a fuller turn. A middle box, both sides, an empty frame, or a bad sample still holds or stays unknown. Box area is not a distance. The 3-frame confirm and 5-frame reversal are unchanged.
- Camera turns name the detector label and the side. English example: “Person on the left. Turn slightly right.” Arabic example: “شخص على اليسار. انعطف يميناً قليلاً.” Hold in the middle says “Person in the center. Hold.” An empty frame says it is not a clear path.
- Describe this frame is still a button, not the control loop. It counts people and names left, center, and right, then says the line is boxes only. A saved name is added only when `recognize()` returns one. In this build it returns nobody, so Describe does not invent a name.
- A short scene brief can speak when several boxes are present and a navigation cue did not just speak. It does not choose a direction.
- Speech rate for a fresh or migrated install is 0.90. Hold and camera cues use pitch 0.96. Other cues use 1.0. The phone log showed `rate=0.9`.

### People

- Remember people is off until the user turns it on in Settings. A name is written only after Prepare save and Confirm save. The camera does not enroll strangers. No crop, embedding, or photo is written (`faceStored` stays 0). Delete is on the same screen. `guidance-memory/` is app-private and excluded from Android backup. Notes do not steer.
- There is no microphone listener for “remember this person as …”. The Settings buttons are the stub. Face recognition is deferred.

### Phone (no emulator)

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- `adb install -r outputs/apk/TriGuideAI-0.8.0-debug.apk` → Success. `versionName=0.8.0` `versionCode=10`. APK size 81,441,801 bytes.
- `aapt dump permissions` lists `CAMERA` and `VIBRATE`. `INTERNET` is not listed.
- `MainActivity` was the resumed activity. The filtered logcat had no FATAL line.
- Google TTS (`com.google.android.tts`) reported offline English and Arabic. Logged offline Arabic voices included `ar-xa-x-arz-local` and `ar-xa-x-ard-local` (quality 400, network false). The session spoke English because the resolved language was English.
- `speak result=0` for the research disclaimer, “Camera started. Guidance on.”, the fingerprint limitation, “Sink in the center. Hold.” (pitch 0.96), and “No reported object. Hold. An empty picture is not a clear path.” Result 0 means the engine accepted the utterance. Sound from the speaker was not confirmed by ear.
- `TriGuideNav` then committed `SLIGHT_LEFT`: “sink is on the right of the image (detector confidence 0.50, 7 percent of the frame). Steering slightly left.” The two frames before that were the smoother vote. The voice log through 09:57:04 did not contain “Turn slightly left” or “Sink on the right.” The scene was still switching between center hold and an empty frame.
- A screencap was pulled to `outputs/reports/redmi-080-live.png` (1,329,479 bytes). This note does not describe that image beyond the log lines above.

### Tests

- `:app:testDebugUnitTest` 104/104 PASS, 0 failures, after the brief-scheduler fix. Includes side turns, named phrases, scene counts, and consent (a name is stored only after the switch is on and Confirm; `storeFace` returns false; `recognize` is null).
- `:sim-core:test` was UP-TO-DATE and was not re-run. Kinematics sources did not change.

### Deferred

- A face model, a face crop, and an embedding. Not started. Consent stores a name only.
- Cloud or paid TTS. Not integrated. Listed under optional tools below.
- A counted true-positive / false-alarm pass. Not measured.
- Floor segmentation. Not started.
- TalkBack was not walked. The user has not yet confirmed they heard a left/right sentence.

### Optional tools (need your approval — not subscribed)

- Google Cloud Text-to-Speech, Neural2 or WaveNet Arabic
- Azure AI Speech, neural Arabic
- ElevenLabs

The phone already has offline Google Arabic voices. No voice-pack install is required for this smoke.

### المطلوب منك الآن

1. افتح التطبيق إذا الشاشة قفلت. النسخة المثبتة `0.8.0`.
2. وجّه الكاميرا الخلفية بحيث يكون شخص أو كرسي على **يسار** الصورة، وثبّت الهاتف حوالي 10 ثوانٍ. اسمع هل قال: “Person on the left. Turn slightly right.”
3. ثم وجّهه بحيث يكون نفس الشيء على **اليمين** حوالي 10 ثوانٍ. اسمع هل قال: “on the right. Turn slightly left.”
4. إذا تبي الكلام بالعربي: More → Settings → Arabic. أصوات العربية غير المتصلة موجودة على الهاتف. لا تحتاج تثبيت حزمة صوت.
5. لا تحفظ وجهاً الآن. تذكر الأشخاص يبقى مغلقاً إلى أن تفتحه أنت من الإعدادات وتضغط Confirm save لاسم واحد. الكاميرا لا تسجّل الغرباء.
6. قل لنا هل سمعت الجملتين، أو ماذا سمعت بدلاً عنهما.

## 2026-10-18 — Real boxes on the Redmi, floor still not started (0.7.0)

**Status:** 0.7.0 install and phone smoke PASS_WITH_WARNINGS on the personal Redmi. The detector drew real boxes. Floor segmentation stays `NOT_STARTED`. No floor mask is drawn.

### Detection

- The analysis frame is cropped to the camera viewport before it is rotated upright. On this phone the log was `rot=90 buffer=640x480 crop=134,0,372x480 upright=480x372`.
- The model may return scores from 0.25. A box is drawn, and may steer, only at 0.35 or above. Weaker hits are logged and not drawn.
- With the rear camera on a person and a table, logcat `TriGuideDetect` reported real scores, including `person=0.72`, `bottle=0.52`, and `laptop=0.45`. A `couch` at 0.34 was logged as below the floor and was not drawn. The same open also logged false labels from the model (`tv`, and on the earlier frame `frisbee`). Those boxes came from the model. They were not invented in the app.
- A screencap during that open showed yellow boxes on the people and on a bottle. One box was labeled frisbee over the table. Decision was Hold because a reported object was in the center. Front wheel 0.0 degrees. Preview 30 FPS, inference 8 FPS, last 75 ms, average 92 ms, on that UI dump.
- An earlier stretch of the same launch, before the camera was aimed at the people, logged `drawn=0` at the 0.25 model floor. An empty frame is still Hold, not a clear path.

### Floor

- MediaPipe DeepLab-v3 float32 (2,780,176 bytes, `storage.googleapis.com` `deeplab_v3.tflite`) was downloaded and inspected. Its label list is the Pascal set (`background`, `person`, `chair`, `sofa`, and the rest). The file contains no `floor` label. There is no int8 build at the usual MediaPipe URL (HTTP 404). It was not bundled. No mask is drawn. Nothing is labeled safe from a floor.

### Also in this build

- Live Vision shows the detector sentence and asks to point the rear camera at a chair or a person.
- **Describe this frame** speaks the latest reported boxes once. It is not on the control tick. On the phone, one tap logged `TriGuideScene` and `speak result=0` for “Reported in the latest frame: person 0.68 center, bottle 0.41 right, bottle 0.36 right. This describes detector boxes only. It is not a clearance and not a floor reading.” Sound from the speaker was not confirmed by ear.
- Robot Simulation’s camera-informed mode now opens the rear camera on that screen. That mode was not opened on the phone in this session.

### Phone and tests

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- Final `adb install -r outputs/apk/TriGuideAI-0.7.0-debug.apk` → Success. `versionName=0.7.0` `versionCode=9`. APK size 81,408,299 bytes. No FATAL line. `INTERNET` is not in the badge.
- `:app:testDebugUnitTest` 95/95 PASS, 0 failures, after the final source change. `:sim-core:test` was UP-TO-DATE and was not re-run. Kinematics sources did not change.

### Still open

- A counted true-positive / false-alarm pass. This smoke is not that count.
- Floor segmentation, until a lite model with a real floor class is available under a compatible license.
- Camera-informed simulation was not opened on the phone.
- TalkBack was not walked. Speaker audio was not confirmed by ear.

## 2026-10-18 — Screen triple-tap restores guidance (0.6.0)

**Status:** 0.6.0 install and phone smoke PASS_WITH_WARNINGS on the personal Redmi. Screen triple-tap restarted guidance. Fingerprint control is still impossible and was not pursued.

### Shortcut

- Three taps on Live Vision, each within 750 ms of the previous one, restart the camera and speak “Camera started. Guidance on.”
- Cold open still starts Live Vision, the camera, and speech. The third spoken sentence says the fingerprint button cannot control the app and that a screen triple-tap restarts guidance.
- A drag is not a tap. One tap on More still opens More and speaks “Guidance paused.”

### Phone (no emulator)

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- `adb install -r outputs/apk/TriGuideAI-0.6.0-debug.apk` → Success. `versionName=0.6.0` `versionCode=8`. APK size 81,391,581 bytes.
- Cold start spoke, all `speak result=0`: the research disclaimer, “Camera started. Guidance on.”, and “The fingerprint button cannot control this app. Triple-tap the screen to restart guidance.”
- `adb shell input tap 540 1600` three times in one shell (188 ms). Logcat `TriGuideTap`: `tap count=1` at 08:54:10.352, `tap count=2` at 08:54:10.382, `triple-tap accepted; restarting guidance` at 08:54:10.408. `TriGuideVoice` then `speak result=0` for “Camera started. Guidance on.” Sound from the speaker was not confirmed by ear.
- A swipe did not log a tap. One tap on More logged `tap count=1` and opened More. Test Scenarios showed Straight, Right (left barrel), and Hold (post on axis), each labeled as a synthetic map. Robot Simulation showed “Simulated tricycle only.” A later synthetic-mode UI dump failed with `could not get idle state` while the screen kept updating.
- Live Vision on this open showed detector READY, Preview 15 FPS, Inference 13 FPS, last 42 ms, average 44 ms, Decision Hold, front wheel 0.0 degrees, source Camera 2D. No box was in that frame. Floor segmentation remained `NOT_STARTED`.

### Tests

- `:app:testDebugUnitTest` 87/87 PASS, 0 failures. Includes the tap detector, the three scenarios, the diagnostics text, and two navigation edge cases (non-finite cue, zero-area cue).
- `:sim-core:test` was UP-TO-DATE. It was not re-run. Kinematics sources did not change.

### Still open

- A person confirming they heard the sentences.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started. No floor mask is drawn.
- TalkBack was not walked. Settings and the share sheet were not opened on the phone.

## 2026-10-18 — Blind-first open, fingerprint shortcut is blocked by the OS

**Status:** 0.5.1 install and cold start PASS_WITH_WARNINGS on the personal Redmi. Fingerprint triple-tap is **not possible** for this app. Screen triple-tap and volume-key shortcuts are not the control.

### What the phone actually has

`dumpsys fingerprint` on `55aqpfqkvw9xbafe` (Redmi Note 12 Pro 5G, `22101316G`, HyperOS `OS2.0.16.0.UMOMIXM`) reports `isUdfps: false` and `isPowerbuttonFps: true`. The reader is the side power button (Goodix, `uinput-goodix`), not an under-display sensor. `fingerprint_double_tap` is `none`. Detail is in `docs/DECISIONS.md`.

### What 0.5.1 does

- Opening the app enters Live Vision, starts the rear camera, and speaks the research disclaimer once, then “Camera started. Guidance on.”, then one sentence that the fingerprint button is not available to apps.
- If camera permission is missing, the app speaks and shows the system dialog. If Android will not show it again, the app speaks and opens TriGuide’s settings screen.
- More still reaches the simulator, settings, and diagnostics. It is not the first screen.
- No volume-key gesture and no screen-wide triple-tap are shipped as the shortcut.

### Phone check (no emulator)

- `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device`. No emulator was attached.
- `adb -s 55aqpfqkvw9xbafe install -r outputs/apk/TriGuideAI-0.5.1-debug.apk` → Success. `versionName=0.5.1` `versionCode=7`. APK size 81,495,956 bytes.
- Cold start of `MainActivity` was the resumed activity (pid 29066). No FATAL line.
- Logcat `TriGuideVoice` `speak result=0` for the disclaimer, “Camera started. Guidance on.”, and the fingerprint limitation. Result 0 means the engine accepted the utterance. Sound from the speaker was not confirmed by ear.
- A later UI dump, after bringing the task forward, showed Live Vision (not a home menu): `Camera: Streaming. Preview 30 FPS. Inference 10 FPS, last 142 ms, average 95 ms`, detector READY, decision Hold, and the fingerprint limitation line. More was on the screen.

### Tests

- `:app:testDebugUnitTest` 75/75 PASS, then one TalkBack sentence was edited and `:app:assembleDebug` succeeded. `:sim-core:test` was not re-run.

### Still open

- A person at the phone confirming they heard the three sentences.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started.

## 2026-10-18 — Redmi 0.4.1 home, after a real 0.4.0 camera pass

**Status:** 0.4.1 install and Home launch PASS_WITH_WARNINGS on the personal Redmi. Live Vision on 0.4.1 was not opened. The earlier 0.4.0 camera and speech-engine lines are recorded below and are not repeated as a 0.4.1 proof.

### Phone

`adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (Xiaomi `22101316G`, market name Redmi Note 12 Pro 5G, device `ruby`, Android 14 / SDK 34, HyperOS `OS2.0.16.0.UMOMIXM`). No emulator was started or installed to. `adb` was not on PATH. Binary: `C:\Users\yassi\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

### 0.4.0 on the phone (before the UI change)

- `adb install -r outputs/apk/TriGuideAI-0.4.0-debug.apk` → Success. `versionName=0.4.0` `versionCode=4`.
- `MainActivity` launched and was the resumed activity.
- A later UI dump showed Live Vision already open. This session did not inject taps. The screen text was `Camera: Streaming. Preview 14 FPS. Inference 9 FPS, last 53 ms, average 59 ms` and, at that moment, no objects at or above 0.50.
- Logcat from that process: `TriGuideNav` `chair is in the center of the image` and `TriGuideVoice` `speak result=0` `kind=OBSTACLE_DETECTED` `lang=ENGLISH` `text=Obstacle detected. Hold.` Result 0 means the speech engine accepted the utterance. Sound from the speaker was not confirmed by ear.

### 0.4.1

- Live Vision keeps the decision, the reason, and the front wheel above a taller preview. Back sits at the top of each screen, and the system Back key returns Home. A permanent camera denial offers app settings. Home states the detector as READY or not ready. One accent color. The launcher name is TriGuide.
- `versionName` 0.4.1 (`versionCode` 5). APK: `outputs/apk/TriGuideAI-0.4.1-debug.apk` (81,342,405 bytes).
- Unit tests executed: `:app:testDebugUnitTest` 71/71 PASS. `:sim-core:test` was UP-TO-DATE (earlier XML the same day: 9/9, 0 failures). It was not re-run.
- First `adb install -r` of 0.4.1 returned `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. A retry about a minute later returned Success. Installed package is `versionName=0.4.1` `versionCode=5`. Home was the resumed activity (pid 9367). No FATAL line. Home text included `Detection: READY`, `Decision: Unknown`, `Front wheel: 0.0 degrees`, and offline English and Arabic voices installed. Live Vision was not opened on this build. `adb shell input` is denied (`INJECT_EVENTS`), so buttons were not tapped from the PC.

### Still open

- On 0.4.1, the user opens Live Vision and points at a real object. Confirm a box, the wheel, and whether the cue is heard.
- Turn on USB debugging (Security settings) if the next install is canceled again, or if PC taps are needed. Steps: `docs/DEVICE_COMPATIBILITY.md`.

## 2026-10-18 — Redmi install and launch (phone only)

**Status:** install and launch PASS_WITH_WARNINGS on the personal Redmi. Live camera, accuracy, and spoken-cue checks NOT_STARTED.

### Policy

The user requires every install and smoke test on their personal Redmi. The emulator is not used for those checks. Earlier emulator installs and smokes stay in `docs/TEST_RESULTS.md` as history and are non-authoritative for this user from this session forward.

### Done this session

- Restarted ADB. `adb devices -l` listed only `55aqpfqkvw9xbafe` as `device` (`22101316G`, Redmi Note 12 Pro 5G, Android 14 / SDK 34). No emulator was started or installed to.
- Installed `outputs/apk/TriGuideAI-0.4.0-debug.apk` with `adb -s 55aqpfqkvw9xbafe install -r`. Result: Success. Package `com.triguide.ai` `versionName=0.4.0` `versionCode=4`.
- Launched `com.triguide.ai/.MainActivity`. It was the resumed visible activity (pid 30249). Logcat showed no FATAL crash.
- Home on the phone: detector READY, floor `NOT_STARTED`, voice line says offline English and Arabic voices are installed, camera permission denied. Start Camera was not tapped.

### Still open (on the phone)

- User taps Start Camera and allows the camera permission.
- A real-room object should produce a box and a navigation line. Timing is still unmeasured.
- Robot Simulation, Test Scenarios, Diagnostics, Settings (speech sample, quiet mode), and TalkBack were not opened.

## 2026-10-18 — Phase 6 voice guidance

**Status:** voice guidance PASS_WITH_WARNINGS on the emulator (non-authoritative under the phone-only policy above). Floor segmentation still NOT_STARTED. The later Redmi install of this same 0.4.0 APK is recorded in the section above. Accuracy evaluation NOT_STARTED.

### Done this session

- Added Android text-to-speech for five cues: obstacle detected, turn suggestion, navigation uncertain, camera unavailable, and simulation paused.
- A gate decides whether a cue may be spoken. The same cue has a cooldown. A critical cue can interrupt a lower one. Quiet mode keeps only obstacle detected and camera unavailable. Speech can be turned off. Rate is limited to 0.6–1.6. The control loop still runs about every 50 ms and does not speak on every tick.
- English and Arabic phrases. The speaker uses an offline voice for the chosen language. Arabic is used when that voice is installed. Otherwise the cue stays in English. This build still has no internet permission.
- Settings has speech on/off, quiet mode, speech rate, language (device, English, Arabic), the engine status, and a sample button. The research-mode line stays on every screen. The voice line says the cue is not a safety proof.
- The three-frame direction vote is not spoken. The committed turn is.
- `versionName` 0.4.0 (`versionCode` 4). Debug APK copied to `outputs/apk/TriGuideAI-0.4.0-debug.apk` (81,438,490 bytes).
- Unit tests: `:app:testDebugUnitTest` 65/65 PASS, `:sim-core:test` 9/9 PASS.
- Emulator `Pixel_8a`: install and launch PASS. Google TTS reported offline English and Arabic voices. Samples were synthesized in both languages on the first 0.4.0 install. After the warmup sentence was removed, the reinstalled APK spoke three turn cues on the synthetic map across about five seconds, and did not speak “Navigation uncertain” during the vote. The emulator was then stopped.

### Still open

- Install and a real-room check on the Redmi. See the blocked USB note below. Later the same morning `adb devices` also listed `55aqpfqkvw9xbafe` as `device` (Xiaomi `22101316G`). `pm path com.triguide.ai` on that serial was “Unable to find package.” This session did not install on it and did not open the app there.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started.
- TalkBack was not run. Quiet mode and the speech switch were visible and were not tapped on the emulator. The gate unit tests cover those switches.

## 2026-10-18 — Redmi install blocked before ADB

**Status:** superseded the same morning. This poll was BLOCKED. The later authorized install is in the section at the top of this file.

Windows sees a connected **Redmi Note 12 Pro 5G** as a file-transfer device (`USB\VID_2717&PID_FF40\55AQPFQKVW9XBAFE`). `adb devices -l` listed only emulator `emulator-5554`. The phone was not `unauthorized` or `offline`; the ADB interface was absent. `outputs/apk/TriGuideAI-0.4.0-debug.apk` (`com.triguide.ai` 0.4.0, `MainActivity`) was identified and not installed. Android version was not read from the phone. Detail: `docs/TEST_RESULTS.md`.

### Still open

- Unlock the phone, turn on USB debugging, and accept this PC on the “Allow USB debugging?” prompt. Then retry install.

## 2026-10-18 — Phase 5 virtual steering from detection

**Status:** vision-to-virtual-robot link PASS_WITH_WARNINGS. Floor segmentation NOT_STARTED (disabled). Redmi NOT_STARTED. Accuracy evaluation NOT_STARTED.

### Done this session

- Replaced the hold-only policy with a navigation engine. Camera decisions use the horizontal position of real boxes. They do not invent meters. Empty, center, contradictory, and stale samples hold or stay unknown.
- Added temporal smoothing: 3 repeated frames before a new direction, 5 before a left/right reversal.
- Synthetic map mode keeps an authored obstacle list for deterministic runs. Straight is allowed only there, when that list shows an empty forward corridor.
- Live Vision and Robot Simulation show the decision, reason, confidence, front-wheel response, the research-mode line, and the label “Illustrative path, not verified.”
- Floor segmentation was not added. The app labels it `NOT_STARTED` and disabled.
- `versionName` 0.3.0 (`versionCode` 3). Debug APK copied to `outputs/apk/TriGuideAI-0.3.0-debug.apk` (81,293,198 bytes).
- Unit tests: `:app:testDebugUnitTest` 48/48 PASS, `:sim-core:test` 9/9 PASS.
- Emulator `Pixel_8a`: install and launch PASS. Home showed detector READY and floor `NOT_STARTED`. Synthetic world showed a wheel response. Live Vision streamed, found zero objects, and held. Emulator was then stopped.

### Still open

- USB debugging on the Redmi, then install and a real-room check. A known object should produce a box and a matching decision. This emulator camera produced none.
- True-positive / false-alarm counts. Not measured.
- Floor segmentation. Not started.

## 2026-10-18 — Phase 3 on-device object detection

**Status:** detection integration PASS_WITH_WARNINGS. Accuracy evaluation NOT_STARTED. Redmi NOT_STARTED.

### Done this session

- Bundled the official MediaPipe EfficientDet-Lite0 int8 model and wired `tasks-vision` 1.1.1 in LIVE_STREAM mode. CameraX analysis uses `STRATEGY_KEEP_ONLY_LATEST` and RGBA.
- Live Vision draws boxes, labels, and confidence only after a real result. Missing model and load failure stay empty.
- `versionName` 0.2.0. Debug APK copied to `outputs/apk/TriGuideAI-0.2.0-debug.apk` (81,322,624 bytes).
- Unit tests: `:app:testDebugUnitTest` 16/16 PASS, `:sim-core:test` 9/9 PASS.
- Emulator `Pixel_8a` (x86_64, 16 KB page-size image): install and launch PASS. Log: `detector ready`. Live Vision streamed and reported measured inference times. The virtual camera produced **zero** objects, so box alignment and accuracy were not checked. Emulator was then stopped.

### Still open

- USB debugging on the Redmi, then install and a real-room check (a known object should produce a box).
- True-positive / false-alarm counts. Not measured.
- Vision still does not steer the virtual robot.

## 2026-10-18 — Phase 1 APK and Phase 2 kinematics

**Statuses:** environment PASS_WITH_WARNINGS, foundation Option B, Phase 1 build PASS, emulator launch PASS_WITH_WARNINGS, kinematics tests PASS, Redmi NOT_STARTED.

### Done this session

- Re-checked the toolchain (JDK 17, SDK 34/35, Studio already installed, adb, two AVDs, no phone).
- Cloned GuideLens (full shallow), MediaPipe samples (sparse Android detection/segmentation), and OpenBot docs (sparse). Upstream trees stay gitignored.
- Confirmed GuideLens ONNX files are real protobufs, GPL-3.0, and `compileSdk` 36. Did not build GuideLens. Did not copy its code or weights.
- Chose Option B and created `android-app/` (Compose, CameraX, explicit non-detector, manual tricycle simulator).
- `gradlew.bat :app:assembleDebug :sim-core:test` → BUILD SUCCESSFUL, 9/9 tests, APK at `outputs/apk/TriGuideAI-0.1.0-debug.apk`.
- Installed that APK on emulator `Pixel_8a` and confirmed `MainActivity` was the resumed visible activity. Did not tap camera or simulator controls. Stopped the emulator afterward.

### Still open

- USB debugging on the Redmi, then install and a real camera check.
- MediaPipe detection (later). No fake detector is in the APK.
- Private git remote, if you want one.

## 2026-10-18 — Phase 0 mostly complete

- Docs, agent rules, JDK 17 user install, SDK packages 34/35, and research notes were already in the workspace before this session.
- Android Studio is installed (the earlier note that it was missing was wrong and is corrected in `docs/ENVIRONMENT_AUDIT.md`).
