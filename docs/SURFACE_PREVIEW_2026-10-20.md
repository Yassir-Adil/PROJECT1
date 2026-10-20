# SDK surface probes and visible camera — 2026-10-20

## Why these two increments

Baseline0.9.27 accepted nine diagnostic depth samples but did not establish
metric accuracy or adequate coverage. Inspection found plane finding DISABLED
and a camera texture allocated without any draw, leaving a black test viewport.
Reuse installed ARCore1.57 and its source-licensed background pattern before
adding models, training, sensors, paid services or a second camera owner.

## Implemented, diagnostic-only

1. Enable horizontal+vertical SDK planes in the bounded debug probe. Four VIEW
   rays (center and three lower points) query the current Frame. Ignore non-plane,
   nontracking, subsumed, out-of-polygon, invalid-distance or over-bound hits.
   Select the closest valid hit per ray. Equal-distance ties keep first encounter
   order; no semantic priority inferred. Primitive observations only, at most1Hz.
2. Draw the same Frame's OES camera texture behind four decorative aiming marks.
   SDK UV transform handles crop/display rotation; no manual image scaling.
   Direct buffers and shader locations are reused. Shader/link/GL failures stop
   the diagnostic; invalid/partial UVs cannot reuse an older image mapping.
   Program/texture cleanup is queued on the GL thread before terminal pause,
   guarded by current EGL context. Context preservation is disabled as backstop.

Independent intent switches retain a diagnostic baseline:
triguide.diagnostic.camera_preview and triguide.diagnostic.surface_probe, both
default true in this debug instrument. Raw-depth mode, confidence128, pair proof,
processing-age gate and main guidance/voice remain unchanged. No new permission,
dependency, model, RGB Image acquisition, saved pixels, recording or cloud.

## Source contracts and reuse

[Plane](https://developers.google.com/ar/reference/java/com/google/ar/core/Plane)
provides tracking, subsumption and polygon inclusion. [Plane.Type](https://developers.google.com/ar/reference/java/com/google/ar/core/Plane.Type)
describes orientation: upward may be a floor OR tabletop; vertical is not a
confirmed wall identity. [Frame.hitTest](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame#hitTest(float,%20float))
permits geometric leeway, so polygon inclusion is required here. SDK hit-ray
distance is not the raw-depth camera-axis Z or calibrated navigation clearance.

The minimal OES draw/shader pattern is adapted from
[Google's pinned v1.57.0 BackgroundRenderer](https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/shared_camera_java/app/src/main/java/com/google/ar/core/examples/java/common/rendering/BackgroundRenderer.java).
Apache-2.0 copyright/license/modification/source notices are retained in code,
third_party/NOTICE.md and arcore-sample-LICENSE.txt. No shared-camera framework,
depth visualization or asset/dependency tree imported; SDK binary terms remain
separate. Existing ARCore terms/privacy disclosure stays on the diagnostic UI.

## Tests-first coordination and review

Work list created before three disjoint Cursor tasks. S wrote19 policy/layout
tests, B wrote21 fake-GL lifecycle/UV tests; Codex independently executed missing-
helper compile RED before releasing either implementation. Codex owns SDK/UI
adapter, integration, version/policy/license files, builds, phone and backup.
Cursor R reviewed contracts then actual source; no remaining bounded-source
blocker reported. Its external fetch was approval-blocked; Codex fetched primary
pages. No approval bypass or worker shell/build/ADB/Git/private-output access.

Codex corrections: exact upstream source URL, reused GL depth-mask array, shader
cleanup on thrown creation calls, and GL attribute/texture cleanup in finally.
Focused offline run:40 tests PASS (19+21),7s,0 failure/error. Fake GL tests do not
prove real shader rendering, crop/orientation or physical surface accuracy.

## Acceptance evidence

Full offline0.9.28/41 gate PASS:
outputs/reports/quality-gate/20261020T020136609763Z/summary.json,105.078s.
500 app+10 simulator+19 policy+10 evaluation=539 tests,0 failure/error/skip.
Both APK builds, permission/model/debug-isolation policy and backup exclusions
pass. Lint0 fatal/errors,55 debug/34 release warnings (unchanged).
Source fingerprint (178 files):
b55cb2c1630c848bdf93e67ff4c0a9f3e42b64a64eadb38ec54a2ea31a3f2589.
Debug/staged/actual installed APK SHA256:
32c120b8d2d685a4ee10e2e23d480d3865f2c7430efde80c43a484a8bd5789b6.
Unsigned release APK SHA256:
76ad90539984324858c9d497b48928a62617d0fa7f0126ede000ed1fb5b022c9.
USB install-r succeeded without data clear, version0.9.28/41 and actual installed
hash verified. All prior staged APKs preserved. Source review accepted; worker
IMPLEMENTATION_READY alone was not counted as build/device evidence.

### Physical execution on Redmi (MYT,2026-10-20)

Owner confirmed a lit furnished wall/floor view, stationary body, then explicitly
reported that the live camera AND four white markers were visible. No screenshot
or RGB/depth recording was taken for this check. PID30527.

| Measurement | Coordinated30s run | Additional warm20s run |
| --- | --- | --- |
| Start / observed duration | 10:04:59.687 /30.19s | 10:07:17.742 /20.19s |
| Distinct camera / tracking frames | 858 /828 | 571 /540 |
| Decoded pairs / diagnostic admits | 230 /27 | 151 /41 |
| Empty-confidence / repeated-depth rejects | 54 /149 | 47 /63 |
| Surface samples / upward-hit samples | 29 /0 | 19 /11 |
| Samples with vertical hit | 0 | 0 |
| Background draws | 1751 | 1163 |
| session.update p50/p90 | 1.51 /3.23ms | 2.02 /6.98ms |
| Four-ray query p50/p90 | 1.01 /2.12ms | 2.37 /3.84ms |
| Depth receipt-to-summary p50/p90 | 2.32 /4.11ms | 7.17 /9.13ms |
| Observed confident fraction (all decoded/reprojected) | 0.478533% | 3.39464% |
| Result / resources | COMPLETE, RELEASED, CLOSED | COMPLETE, RELEASED, CLOSED |

Concrete warm-run SDK hit at10:07:26.672: lower_left HORIZONTAL_UPWARD at
1.7423042m and lower_center at1.6245512m, both observed_surface after polygon/
tracking/subsumption filters. These are uncalibrated ray distances, NOT proof
of floor, wall distance, obstacle height or sufficient clearance. The first run
returned no valid plane hit at the four selected rays; the later upward hits
exercise the real SDK adapter. No valid vertical/wall candidate was measured.

The warm20s run began as a pause regression, but the host foreground guard used
the older mResumedActivity property, absent in this Android output, and correctly
sent NO Back input. It completed the timer before a corrected follow-up check.
Treat its pose/scene as uncontrolled, not a planned calibration scene or a
controlled accuracy/performance A/B. Different samples are not an improvement
claim about unchanged source. Native logs did not establish a cause for the
initial no-hit result; global plane counts were not measured by this instrument.

### Explicit Back/background regression

New60s probe started10:08:06.392; host checked current focused window belongs to
the diagnostic, then injected Back after about4s. Actual result at10:08:11.083:
INTERRUPTED/paused,4.65s,113 distinct frames (all nontracking),234 background
draws,0 depth admits. GL RELEASED10:08:10.923; CLOSED10:08:11.962. Confirms
background cleanup with a real drawing program, NOT Stop/background under valid
depth. Initial guard refusals were host-test issues, not app failures.

Ignored evidence: outputs/reports/surface-preview-20261020/redmi-0.9.28-30s.log
and redmi-0.9.28-back.log (same process history; last start/result is Back run).
App force-stopped after logged close. One battery snapshot94%,34.3C is not a
sustained thermal/CPU/energy benchmark. Background draw count includes repeated
camera timestamps at display refresh; it is not camera FPS or fresh evidence.
No metric accuracy, sustained load, valid-depth Stop, physical rotation/crop or
TalkBack/voice pass inferred. Main STOP/UNKNOWN/HOLD remains unchanged.

## Remaining limits / next two priorities

- Preview visibility, SDK upward-surface hits, bounded query/update latency and
  timer/background GL-camera release have executed evidence above. Next measure
  global plane availability versus ray misses and actual vertical/wall errors;
  do not infer a missing plane from only four negative rays.
- Require held-out measured wall/ground/table/flat-mat/raised-edge/step geometry
  before any floor reference, clearance estimate or navigation fusion. Plane
  orientation alone must not suppress an obstacle or turn an unknown into CLEAR.
- No sustained thermal/energy, voice/TalkBack or eyes-closed-walking claim. Missing
  plane/depth is UNKNOWN, and horizontal surface may be furniture, not floor.
