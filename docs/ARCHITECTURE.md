# Architecture

## 0.9.36 render attribution / isolated synthetic test

Existing semanticworker result adds localcompletiontime, not sensorage. Pure
publication decision preserves250ms/stop/tracking/revision eligibility; bounded
SemanticDisplayTrace separates UI/View/onDraw entries/rejections from actual
paint commands and freshCanvas stats. SharedSemanticGridPainter consumes only
paintcells with column/row/label; depth→paint stripsworldgeometry. Independent
DUMP-protected debug SemanticRenderDiagnosticActivity uses syntheticpaintcells,
no camera/model/ground/navigation, bounded3..15s session. Samepalettestyle, no
stale mask extension. Sourcebounds/callback/clock fixes and tests described in
RENDER_RECOVERY_2026-10-20.md. Deviceacceptance pending; no production nav change.

## 0.9.34 diagnostic observability follow-up

Pure SemanticOverlayStats separates accepted inference from main-thread publish
and distinct fresh coloured Canvas submissions; terminalstop/receiptage/identity
gates and bounded first-draw percentiles. It does not prove compositor/human
visibility. SemanticGroundReading.reason names the existing rejection branch,
with unchanged statuses/thresholds/admission/unknown64. Same existing camera/
model/runtime/nav isolation. Actual0.9.33 case and acceptance limits:
PHONE_SEMANTIC_0933_2026-10-20.md; SEMANTIC_DISPLAY_DIAGNOSTICS_2026-10-20.md.

## 0.9.32 opt-in semantic/raw-depth diagnostic —2026-10-20

0.9.33 follow-up derives CPU→view quarter-turns from SDK axes before worker
rotation and uses the identical integer rotation for semantic lookup. Raw depth
physical axes remain unchanged. Converter has equivalent heap fast path with
stride/offset validation, no colour/filter algorithm change. New orientation
RED then100focusedPASS; runtime result below refers to0.9.32, not0.9.33.

Existing ARCore session/GL owner supplies a copied closed-before-inference CPU
YUV image and64 SDK-mapped view-grid samples from an admitted raw pair. Existing
hash-pinned DiagnosticSegmenter runs on one bounded worker, not GL/UI; no SDK
objects escape. Receipt250ms expiry/revision invalidation on stop/tracking or
rotation/size prevents stale publication. Stateless SemanticGroundDiagnostic
joins floor4 bottom-centre component with finite horizontal geometry, single-frame
candidate/relative height counts only. No production/nav/voice/default consumer.
Semantic extra defaultsfalse.0.9.32 actual20s6freshfusion/1stale,0groundcandidate;
modelidentity/runtime integration observed, physical recognition unverified.
Details/evidence: SEMANTIC_DEPTH_FUSION_2026-10-20.md.

## Host RGB-D fixture/oracle —2026-10-20 (no Android consumer)

Explicit --fetch uses official boundedHTTPRange+zipfile with strongidentity,
path/size/CRC/nooverwrite and complete-only hashedmanifest. Data outsideGit/APK.
Offline evaluator verifies allhashes/PIN/publicval/SPEC, safeHDF5datasets, exact
frame→keyframe mapping, unit/shape/rotation; fullM_cam_from_uv pixelcenter rays
and Euclideanrange, not Androidcamera-z. GTsemantic world-Z is descriptiveonly.
No sourceimport/pipeline authority in Android/mainnav. Sourcefingerprint now
covers allresearchPython/requirementfiles. Reuse/legal/results: PUBLIC_RGBD_ORACLE_2026-10-20.md.

## 0.9.31 debug coordinate and surface-height contracts —2026-10-20

Existing singleARCoreGLowner unchanged. Pure TextureDepthProjection adapts Google
texture intrinsic scaling (not CPU); current physical pose transforms at most9
raw confident grid samples at1Hz AFTER admission inside same liveFrame/pair.
Aggregate counts only, no retained points/SDKobjects, no dense/mainnavconsumer.
Stride/UINT16 grid sampler reuses existing decoder/validation. Separate lazy256
SurfaceHeightProfile computes signed cameraY-upwardPlaneY range, no selected
ground or floor/table identity. Session-known!=visibility; no world/screen/metric
physical calibration claim. RAWdefault/confidence128/navigation unchanged,
debug/release APK isolation verified. GEOMETRY_COORDINATES_2026-10-20.md.

## 0.9.30 debug pose/config and opt-in dense comparison —2026-10-20

Single ARCore camera/GLowner remains. Distinct tracked frames feed bounded pure
CameraMotionStats; reset origin on trackingloss, SDKdelta only, no rawcoordinates
logged. Appliedconfig readback enforced depth/planes/cloud/geospatial invariants.
RAW_DEPTH_ONLY default. OptionalAUTOMATIC captures same-frame smoothedD_16 at
existing≤10Hz slot, closes image, reusesdepth-only validator/count decoder;600
scalar samples and boundedlatency. Dense never goes through rawproof/confidence
gate/acceptedcount/main guidance. MainCameraX unchanged, no concurrentcameraowner,
new model/dependency/permission. PHONE_INVENTORY_2026-10-20.md records real results.

## 0.9.29 diagnostic inventory and offline ADE150 evidence —2026-10-20

ARCore GL/Frame owner unchanged.1Hz inventory maps up to256 session-known Plane
objects lazily to primitive tracking/subsumption/orientation counts, separate
from current polygon-filtered4ray observations. No SDK objects retained, no main
guidance consumer; native enumeration not capped by primitive helper. Host-only
LiteRT replay adds151×32 confusion and floor mismatch denominators without
discarding GT>31. GT0/pad excluded from labelled precision. No height/hazard
inference, model training, new runtime/network or camera owner. Detail/evidence:
REUSE_EVALUATION_2026-10-20.md. Release excludes SDK diagnostic classes.

## 0.9.28 debug surface probes / camera preview —2026-10-20

One existing ARCore Session owns all camera work. Optional horizontal+vertical
planes feed four bounded VIEW-pixel hit queries, filtered by tracking, subsumption,
polygon and finite positive distance. Only primitive orientation/ray-distance
observations leave the GL callback; not ADE20K semantics or navigation evidence.
Same-frame NDC-to-texture SDK transform drives a licensed OES background helper
with reused buffers, strict shader checks, private program lifecycle and queued
GL cleanup before pause. Context destruction backs up terminal cleanup. Preview
and surfaces have independent debug intent switches; no RGB Image/store/upload.
Raw-depth proof/gates and all main guidance remain unchanged. Evidence:
SURFACE_PREVIEW_2026-10-20.md. SDK upward hits observed; wall accuracy not proven.

## 0.9.27 debug same-Frame provenance / observed coverage —2026-10-20

RawDepthPairScope brackets both SDK image acquires on one unchanged Frame;
private proof lives only inside the callback, then invalidates before dual close.
Gate compares source stamps only against previous stamps of that source, never
Frame-versus-image clocks. Local processing-age bound250ms remains; physical
sensor age is unknown. RawDepthCoverageStats independently records all decoded
pairs (including rejected/reprojected), capped10000, weighted scalar coverage,
null before observations. Neither component enters navigation or speech.
Design/execution evidence: RAW_DEPTH_PAIRING_AND_COVERAGE_2026-10-20.md.
This supersedes0.9.26's diagnostic equality/lag policy below, not main safety.

## 0.9.26 debug Raw Depth contract correction —2026-10-20

The acquired raw16-bit image is HardwareBuffer.D_16, not packed DEPTH16.
RawDepthImageFormat provides a strict tested boundary before plane use. Decoder,
confidence Y8 validation and timestamp/age/tracking gates are unchanged.
Nontracking distinct frames now contribute SDK state/failure-reason counters,
with1Hz log/UI telemetry and stop guards; they still never acquire depth. No
navigation/speech coupling. Rejected pairs expose primitive timestamps/counts
at most1Hz, without changing admission. Exact image-timestamp equality is an
application policy, not a guarantee found in SDK docs; cross-source clock
comparability and typed same-Frame provenance need review before gate revision.
Runtime evidence and unresolved geometry:
`docs/RAW_DEPTH_D16_FIX_2026-10-20.md`.

## 0.9.23 storage / isolated Raw Depth boundary — 2026-10-20

Local label/session/name stores reuse one bounded strict-UTF8 atomic-replace
helper (same-directory staging + file fsync + atomic move, API26+). No unsafe
truncate fallback; malformed input is rejected, load caps are enforced, counts
saturate and chronology is file/insertion order rather than reboot-unsafe
elapsed timestamps. Person save/delete failures do not claim durable success.
Disabling consent takes effect in memory even when storage fails; a failed disk
disable is not proof of durable revocation after restart. No faces are stored.

RAM label/summary mutations run immediately under a short lock. Only best-effort
disk persistence uses the existing coroutines runtime:
one serial worker, one latest pending write, nonblocking offers, terminal close
that drops pending work without joining synchronous in-flight disk work.
Each job reads the latest immutable combined objects/session snapshot, so dropping
pending disk jobs cannot drop RAM mutations or an earlier description. A separate
disk lock prevents fsync from blocking ordinary RAM readers. `clearPersisted()`
reports durable-clear failure and serializes with pending disk jobs; the legacy
`clear(): Unit` API remains, and must not be used to announce durable success.
Guidance and speech never enter that queue. Store loading, explicit durable clear
and explicit person actions are not claimed entirely off the main thread.

The debug-only DUMP-protected RawDepthDiagnosticActivity is an explicitly
launched, bounded ARCore camera owner, never concurrent with CameraX. It acquires
raw depth/confidence, closes every Image, samples at most10Hz, and feeds pure
RawDepthFrameStats plus a terminal timestamp/age/tracking admission gate.
Repeated/reprojected/out-of-order/misaligned/late data is rejected; missing
depth is unknown. Stop/background drains renderer, pauses and closes Session.
Only bounded aggregate metrics are logged; no RGB/depth files, new dependency,
network permission, model or navigation authority. It is not exposed as a live
guidance option. Full phone lifecycle/accuracy acceptance is separate from JVM.

## Engineering audit / local evidence boundary — 2026-10-19

Current logical architecture, five ranked weaknesses, storage/evaluation/test
assessments and staged plan: `docs/ENGINEERING_AUDIT_2026-10-19.md`.
`scripts/test/verify-local.ps1` builds/checks without installing or controlling
the phone. `apk-policy.json` pins packaging, not navigational authority/licensing.
Static debug probes now require separately supplied app-private
`files/diagnostic-inputs/scene.jpg`; no room image belongs in APK assets.
Personal reports/test data are ignored, with explicit tracked-template exceptions.

## 0.9.22 surface lab and lifecycle boundary — 2026-10-19

More exposes a bounded debug-only lab through variant-specific SceneLabLauncher;
release has no intent/control. The same CameraX fan-out reuses EfficientDet and
MOSAIC. DiagnosticSurfaces removes model letterboxing into a 64x64 source grid;
DiagnosticSurfaceOverlay reuses preview center-crop/mirror geometry. Region
display is capped at 4/s, expires since completion, and never creates navigation
evidence. Explicit descriptions can mention predicted wall/floor/rug with height/
step uncertainty; no periodic surface voice. Default benchmark remains silent.

LiveVisionStatus receives actual path risk instead of inferring blockage from
HOLD alone. Navigation thresholds/actions are unchanged. LiveNavState has a
saveable, always-paused restore; Main starts paused on restoration. Actual lab
entry uses ActivityResult and returns to paused More. Back does not restart.

Debug ARCore 1.57.0 activity checks support without resume/frame acquisition;
phone confirms AUTOMATIC/raw. ARCore and CameraX are not concurrent camera owners.
No live depth, geometry, step detection or positive walkability is implemented.
These current boundaries supersede older no-semantic-description experiment
notes below. Verification: `docs/SURFACE_LAB_AND_DEPTH_2026-10-19.md`.

## Debug-only combined observation preview — 2026-10-19

The live experiment can explicitly enable `triguide.diagnostic.objects=true`.
It reuses the same CameraXSource, passes a copied frame to the existing MediaPipe
detector, and runs MOSAIC separately. `CombinedDiagnosticScreen` reuses
`TriGuideTheme`/`DetectionOverlay`; `DiagnosticObjectPresentation` rejects stale,
invalid and stopped result display. A periodic expiry tick triggers evaluation,
but current monotonic time is read at render so fresh frames are not future-dated.
`DiagnosticObjectCompanion` reuses offline narration/settings/voice gate solely
for an explicit Describe request. Stop hides boxes/shuts voice down before native
cleanup. No path-evidence fusion, steering or network connection exists.
Current explicit surface-lab narration is described above.
Default diagnostics remain detector-free/silent; release model assets are
unchanged. This is not a synchronized multimodal navigation pipeline. See
`docs/COMBINED_VISION_PREVIEW_2026-10-19.md` for the bounded phone checks/limits.

## Debug-only live segmentation experiment — 2026-10-19

`LiveSegmenterDiagnosticActivity` reuses `CameraXSource` -> official
`ImageProxy.toBitmap` -> cropped/upright bitmap -> shared `DiagnosticSegmenter`
-> existing INT32 decoder. `DiagnosticFrameGate` admits one work item with no
application queue, caps cadence and invalidates old-session/receipt-aged output.
Static and live probes share hash pins, RGB packing, tensors and reusable buffers.
The camera source alone closes ImageProxy; the worker serializes Interpreter
teardown. Statistics omit unverified capture-to-callback age. These classes and
activity exist only in src/debug, protected by DUMP, and have no route to
production evidence fusion, guidance, recording, or network. Default mode is
silent; the optional object/surface Describe path is described above. See
`docs/LIVE_SEGMENTER_DIAGNOSTIC_2026-10-19.md` for measured boundaries.

Android project: `android-app/`.

| Module | Role |
| --- | --- |
| `:app` | Compose UI, CameraX, wiring |
| `:sim-core` | Pure Kotlin tricycle kinematics and JVM unit tests |

Package `com.triguide.ai`.

## Interfaces

| Type | Phase 1–2 behavior |
| --- | --- |
| `CameraSource` | `CameraXSource` binds preview and analysis. Analysis is RGBA, 4:3, about 640×480, `STRATEGY_KEEP_ONLY_LATEST`, same `FILL_CENTER` viewport as the preview |
| `ObjectDetectionEngine` | `MediaPipeObjectDetectorEngine` — EfficientDet-Lite0 int8, CPU, `LIVE_STREAM`. The analysis crop is applied, then the frame is rotated upright. States: `LOADING`, `READY`, `MODEL_MISSING`, `LOAD_FAILED`. Boxes come only from the result listener. Scores under 0.35 are not drawn |
| `EnvironmentObservation` | Real labels only after a successful frame. Otherwise `realDetector = false` and the list is empty |
| `SceneObservation` | Camera samples carry preview-space box centers and area fractions. `metricDepthAvailable` stays false. Synthetic samples carry authored robot-frame obstacles |
| `NavigationDecisionEngine` | Maps one sample to `GuidanceAction`: Straight, Slight left, Left, Slight right, Right, Hold, Unknown. Each decision has a reason and a confidence. Straight is not emitted for camera samples |
| `TemporalDecisionSmoother` | Hold and Unknown apply immediately. A new direction needs 3 matching frames. A left/right reversal needs 5 |
| `GuidanceSession` | Separate smoothers for the camera and the synthetic map. Publishes the decision that is currently applied |
| `SteeringCommand` | Speed (m/s) and front-wheel angle (radians). Positive angle is left |
| `SyntheticObstacleWorld` | Authored obstacles: left barrel (1.0 m, 0.20 m), right crate (2.6 m, −0.40 m) |
| `Floor segmentation` | Diagnostic seam only (`FloorSegmenterCameraAdapter` / `FloorMaskEvidence` / `EvidenceFusionGate`). Class mapping **UNMAPPED**; no weights in APK. Never authorizes live CLEAR or TTS movement. See `docs/RESEARCH_SIM_BRIDGE.md` |
| `AI Vision overlay` | Research PathRisk colors. Green strip only if fused positive evidence (disabled live). Caption: observed candidate — not a safety signal |
| `PerceptionEvidenceStats` | Local reason-code counters (stale/out-of-order/voice-suppressed). No pixels |
| `CameraActiveCue` | One soft non-speech tone when Streaming+running; STOP/pause suppresses |
| `RobotState` | Pose of the rear-axle midpoint plus the applied command |
| `VirtualRobotController` | `SimulatedTricycleController` steps `TricycleKinematics` |
| `FutureHardwareController` | `MockHardwareController` — `isMock = true`, `dispatch` returns `accepted = false` |
| `PerformanceMonitor` | Preview FPS from analysis callbacks, plus inference latency, rolling average, and inference FPS from completed results only |
| `VoiceGuidanceGate` | Pure policy: enable, quiet mode, dedupe key, cooldown, minimum gap, and interrupt. No synthesizer |
| `VoiceCues` | Maps a committed decision to one of five short phrases, in English and Arabic. The smoother’s warmup hold is silent |
| `AndroidVoiceSpeaker` | Android `TextToSpeech`. Offline voices only. `QUEUE_FLUSH` when a critical cue interrupts |
| `VoiceSettingsStore` | `SharedPreferences` `triguide_voice`: speech on/off, quiet mode, rate, language preference |

## Virtual chassis

Bicycle-model approximation of a tricycle: two fixed rear wheels (track width ignored) and one steered front wheel. Wheelbase default 0.35 m. Constant-speed/steering commands follow an exactly integrated 2D circular arc; this is still kinematics, not rigid-body physics. There is no slip, inertia, collision, or sensor model. Positive steering increases heading while moving forward (left, counter-clockwise from +x). Screen drawing flips the heading so the arrow matches that math frame.

## Screens

Live Vision is the start screen. More, Robot Simulation, Test Scenarios (three synthetic runs), Diagnostics (including a text share), and Settings are reached from More.

Opening the app enters Live Vision, binds the rear camera (`STRATEGY_KEEP_ONLY_LATEST`), and speaks the research disclaimer once per process, then “Camera started. Guidance on.” A missing camera permission is spoken and the system dialog is requested immediately. If Android will not show that dialog again, the app opens TriGuide’s settings page and says so.

The fingerprint reader on the project Redmi is the power button. This app does not receive those presses. Live Vision counts three quick taps (750 ms between taps) without consuming the pointer. That gesture restarts the camera and speaks “Camera started. Guidance on.” A single tap on More still opens the other screens.

Test Scenarios run `GuidanceScenarios` through `NavigationDecisionEngine` on three authored maps. Diagnostics builds a plain-text `DiagnosticsReport` and shares it. Floor segmentation stays `NOT_STARTED`. No segmenter is loaded. Describe this frame speaks the latest real boxes once and does not steer. Camera-informed simulation binds the rear camera on that screen.

Live Vision applies camera observations to the virtual robot while the preview is open. It shows the decision, the reason, the front wheel, the camera source, and that Hold and Unknown stop the virtual robot. The simulator can run manual buttons, the same camera path (it holds when the preview is closed), or the synthetic map. The simulator labels itself as simulated, shows confidence, and shows the non-verified path label. Those two screens also ask the voice gate once per control tick. Settings stores speech, rate, quiet mode, and language.

`minSdk` 26, `compileSdk` / `targetSdk` 35. JDK 17. Chosen because platforms 34 and 35 are installed on this PC and MediaPipe Tasks samples support API 24+, so 26 stays inside that range.
