# Texture-depth coordinates and surface heights —2026-10-20

## Scope / status

Two diagnostic improvements on baseline28fd899 /0.9.30/43. New0.9.31/44 installed
and exact APK hash verified. Focused83/full653 tests and builds/policy PASS;
independent source review accepted. Phone15s smoke ended NO_DEPTH then CLOSED,
with SDK INSUFFICIENT_LIGHT,0tracking: positive geometry remains unverified.
Not a floor/wall detector or navigation release.

1. Adapt Google ARCore1.57 texture-intrinsic scalar unprojection; reject aspect/
   units/pixel/calibration/nonfinite errors. Sample at most9 raw confident grid
   pixels at1Hz, after existing raw admission, inside the same live Frame/pair
   scope. Use physical camera pose, not display pose. Only aggregate counts
   logged, no points/images stored. Dense depth has no projection authority.
2. Reuse Plane.centerPose for signed cameraY-planeY range over at most256
   tracking/nonsubsumed/upward planes. Negative heights permitted. Empty, invalid,
   truncated and not-tracking remain explicit. Lowest/largest is NOT floor;
   ground/table identity remains UNKNOWN. Display/log range, not a walking cue.

## Tests first, with real failures recorded

Cursor P/H had exclusive source/test/report files and no shell/build/ADB/Git/
network/private-output authority. Codex added7 grid/stride tests independently.
Actual pre-implementation compile REDs (both exit1):

- outputs/reports/projection-grid-red-20261020.log — missing grid sampler/pixel.
- outputs/reports/projection-height-red-20261020.log — missing projection and
  surface-height types. Source released only after RED.

Codex reviewed tests and found a finite huge-number fixture wrongly expecting
rejection; corrected it, and a boundary principal now genuinely off-image.
First GREEN attempt:83 tests,1 failure because mathematically equivalent
multiply/divide orders near MAX_VALUE were asserted bit-exact. Allowed2ULP for
that numerical comparison only, retaining finite-result/real-overflow checks.
No confidence/timing/navigation acceptance threshold changed.

Second focused run projection-height-green2-20261020.log PASS:
21 projection +18 height +7 grid +37 existing decoder=83,0fail/error/skip.
Coverage includes4:3-vs16:9 mismatch, signs/units/UINT16, invalid values/overflow,
lazy256 cap/no257th consumption, no iteration while untracked, direct/strided/
positioned buffers preserved, zero/low-confidence pixels and grid cap64.

## Source research / measured limits

research/11-coordinate-and-ground-reference-reuse.md has pinned primary sources,
verified Google model-card grant/indoor and safety limitations, and actual failed
bounded public Hypersim Range requests. No dataset/model download, package
installation or private upload. Three official validation entries selected from
split CSV only; not three downloaded/evaluated frames.

No floor/table identity, rug/edge height, wall coverage, world-coordinate physical
accuracy or screen alignment ground truth verified yet. Session-known planes
can be off-screen; counts include repeated/reprojected evidence, not independent
coverage. Scalar tests do not validate ARCore pose/metric accuracy. Production
CameraX/navigation/STOP/HOLD/UNKNOWN, confidence128 and raw default unchanged.
Release isolation must pass the final gate; no Internet/cloud/new permission.

## Executed final engineering gate

outputs/reports/quality-gate/20261020T034155749325Z/summary.json PASS103.75s:
597 app +10 sim +19 policy +27 evaluation=653,0fail/error/skip; debug/release
builds, assets/permission/privacy-root/debug isolation checks PASS. Lint0errors,
56 debug/34 release warnings (prior55/34); no blanket clean-Lint claim. Findings
on reviewed changed paths are existing diagnostic string-localization warnings;
dependency checks remain backlog, no unrelated automatic SDK/dependency upgrade.
No new model/dependency/Internet/sensitive permission. Source188 files SHA:
`6e48349aa980e2601cabe67d74612c2f05a46977fa8f37030eeec3f449335ad6`.

Staged/installed debug SHA:
`38c90117f342cb0f8620070f0994546f7e90aa63bf1cb6f3f64c4e5700675857`.
Unsigned release SHA:
`6a48ee217cf86e31c32814fa09cc4c55f02a75065a74498fea818a4595d3e8b2`.
Install-r preserved app data,0.9.31/44 readback. Cursor R source-only review:
CURSOR_GEOMETRY_REVIEW_2026-10-20.md, no blocking defects; optional coalescing
double SDK plane query deferred (1Hz, no demonstrated bottleneck). All workers
finished. Codex independently ran tests and primary-source verification.

## Actual Redmi15s launch smoke (not owner-prepared accuracy test)

Phone online/unlocked/awake, launcher foreground, no app process before install.
Default RAW_DEPTH_ONLY launched COLD/Status ok, PID25403, resumed Activity
confirmed. RESULT local MYT11:45:06.537, CLOSED11:45:07.526,15.17s elapsed.
407 distinct frames,0tracking/407nontracking;349 paused INSUFFICIENT_LIGHT,
58 paused NONE. No depth acquisition/admission/projection (all0);14 height
samples correctly not_tracking/null,0planes. Background832 draws are not camera
FPS. Session.update p50/p90=1.51/3.19ms; surfaces0.09/0.29ms, not full inference.
No crash marker in this scoped tag/PID capture. No lighting/geometry/voice/heat
claim, no walking requested. Projection positive SDK wiring not exercised;
do not substitute prior0.9.30 admits or synthetic tests as0.9.31 physical success.

Private filtered log outputs/reports/geometry-research-20261020/0931-RAW-15s.log,
SHA `e3bfbfbb294a820177d4f31f22fd673a1c424208848e27ba3b5c0a2bbfce0c1f`.
No RGB/depth/point capture, account, network permission or image upload.

## Remaining order

Tiny scene-disjoint RGB-D fixture with correct ray-vs-z/tilt-shift/asset-unit
conversion, then independently labelled floor/table/wall/mat/step checks, then
one-camera fusion. Do not buy a sensor or add another runtime to hide a missing
coordinate/ground-reference contract. No eyes-closed walking acceptance.
