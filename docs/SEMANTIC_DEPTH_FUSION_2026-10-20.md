# Single-camera semantic/raw-depth diagnostic — 2026-10-20

## Live checkpoint after owner-ready0.9.33 test

0.9.33/46 nowinstalled/hashverified and actual20.16sCOMPLETE+CLOSED:13fresh/0stale,
61.14/84.95ms localreceipt p50/p90,0groundreference. Owner confirms upright image
but did NOT see coloured cells; canvas/humanvisibility unverified. Detailed run:
PHONE_SEMANTIC_0933_2026-10-20.md. Following not-installed text is earlier history.
Phone stopped.0.9.34 reason/canvas instrumentation is a tests-first follow-up,
not a threshold/lifetime/navigation change.

## Follow-up0.9.33/46 — orientation and measured-path preparation

0.9.32 installed/physical evidence below is retained separately. Its worker
prepare cost dominated inference. Added tests-first640x480 heap/direct byte
equivalence and sliced-array offsets before optimizing the unchanged BT.601
conversion: wrapped heap fast path respects arrayOffset+position, validated Int
offsets and shared chroma; direct/read-only fallback stays identical. HostJVM
20iterations/10warmups p50 **3.8062→1.7216ms**,p90 **6.5006→2.0169ms**. These
are host microbenchmarks, NOT a measured Redmi speedup or full-pipeline gain.

Self-review additionally found raw ARCore CPU images were not made view-upright,
unlike existing LiveSegmenter.uprightCrop. A sideways model input is a plausible
recognition issue, not a proven cause of the phone's missing floor labels. New
pure SemanticImageOrientation derives quarter turns from current SDK
IMAGE_NORMALIZED→VIEW_NORMALIZED basis, rejects mirror/skew/invalid mappings;
worker rotates bitmap and CPU semantic lookup by the same exact integer rotation.
SDK physical pose/depth projection stay unrotated. Crop/rotation handled together,
not a sensor-orientation guess. Real missing-helper RED, four focused orientation
tests incl every-pixel bijection/edges/all turns and malformed transforms.

0.9.33 **100 focused PASS**, stable gate **20261020T092130274140Z PASS**:
681app+10sim+20policy+86eval = **797 tests**,0fail/error/skip; both builds/model/
permission/debug isolation/private-root checks PASS. Lint0error/fatal,45debug/
14release warnings; not a vulnerability, accuracy or safety audit. Independent
R final follow-up found no actionable scoped High/Medium correctness findings.
Source216files SHA256 `d90d818586ab135185fe2383098d3e826ec92eae62341790eaf06d43387faa1e`,
independently rechecked after gate. Debug APK SHA256
`4eeeac171d0587988b2cb093ca896cdb2c8dd47ea25cd9f21420107ccb27dfcb`;
releaseunsigned `15f484d620304e2cdc42283958ed7aa2e61fcf8bc72e4ae7221488b28b1cb364`.
Versioned ignored debug artifact staged/hashmatched. **Not installed or camera-
tested on Redmi**;0.9.32 is deliberately still the installed/tested build.
No changed recognition or phone-speed claim. All Cursor scopes now finished;
next camera test only after fresh owner setup, with floor visible in lower half.

## Scope and implementation

Candidate **0.9.32/45**, baseline8a72aca /0.9.31/44. Two bounded improvements:

1. Existing ARCore diagnostic camera now optionally supplies both CPU RGB for
   the existing hash-pinned MOSAIC NNAPI interpreter and admitted raw depth.
   No second CameraX owner, new model/dependency/permission or production change.
2. Fixed8x8 view-grid joins class labels and raw-confident world points for a
   single-frame horizontal floor-like reference candidate and relative-height
   diagnostics. This is NOT confirmed ground, an obstacle detector or clearance.

Default remains RAW_DEPTH_ONLY, semantic fusion OFF. Debug Activity remains
android.permission.DUMP-protected and absent from release. Existing normal
camera, object boxes, voice, STOP and HOLD/UNKNOWN remain unchanged.

## Ownership, geometry and failure handling

- Only current SDK Frame is accessed, on its existing GL/session owner. CPU
  Image is copied and closed before queuing a worker. Depth/confidence belong
  to the already-admitted same-live-Frame scope; reprojected/non-new pairs do
  not start segmentation. Image/Frame/Session/Pose never escape to inference.
- SDK VIEW_NORMALIZED→TEXTURE_NORMALIZED maps64 view-cell centres. Selected
  integer depth pixels then transform TEXTURE_NORMALIZED→IMAGE_NORMALIZED,
  undoing the existing512px centred gray letterbox for semantic lookup. No
  CPU4:3/depth16:9 crop guess, independent rotation or sensor-clock equality.
- Transforms start with NaN output sentinels; outside-image UV is unknown,
  never clamped. Invalid input slots are not sent to SDK as NaN. Raw selected
  samples reuse the existing unsigned16/Y8 stride decoder; no dense-confidence
  substitution. Physical pose/texture intrinsics reuse0.9.31 projection.
- CPU YUV420888 Y/U/V with independent row/pixel strides; bounded pure JVM
  limited-range BT.601 conversion. Colour space is an explicit assumption,
  NOT a calibrated Redmi colour/recognition claim. Non-full image crop is
  rejected instead of ignored. No images, masks, points or audio stored.
- At most one copied/in-flight image,1Hz offer cap,250ms LOCAL receipt→result
  maximum. Slow results are rejected, not queued. No capture→result latency
  claim, since sensor clock ages remain unknown. Model loading/inference is
  off GL/UI. Stop invalidates immediately; interpreter close is worker-owned.
- Tracking loss/recovery or viewport geometry change invalidates pending
  publication by revision. Main-thread publication rechecks revision and receipt
  age; overlays/text expire by receipt age, not by completion/publication age.
  Coarse purple/blue/amber grid cells are model suggestions, not object boxes
  or safe-green paths. No TTS/turn/walk/clear output.

## Height contract and limitations

Only ADE Top31 floor4, four-connected to bottom centre (columns3/4,row7), may
anchor a candidate. Rug29, table16, wall1 and unknown0 cannot anchor it. Need
>=6 finite world supports, X/Z spread>=0.20m, triangle area>=0.01m², and ALL
support Y within0.04m of their median. No outlier-dropping to manufacture ground.
Relative samples>0.08m above/below are counted; rug samples within0.04m are
reported as approximately coplanar only. Other/missing heights remain unknown.
Without an eligible reference ALL64 height samples are unknown, not zero.

These are diagnostic fixture thresholds, NOT measured sensor precision or
validated trip-hazard limits. SDK world-Y is gravity aligned but not a physical
floor datum. The reference is single-frame, no temporal confirmation, floor/
wall identity proof or navigation authority. Sparse depth, low texture/light,
semantic floor-on-stairs confusion, coarse sampling and driver latency can all
prevent a candidate; they must remain visible failures/unknowns. A flat label
or coplanar rug sample does NOT authorize walking or bypass current STOP.

## Tests-first evidence

- CursorY/H had disjoint source/test/doc scopes and wrote tests first. Codex
  independently executed compile RED before each source release. Initial RED
  also found Y test Byte-literal syntax and H Random-import defects; corrected.
- `semantic-fusion-red-20261020.log`: missing mapping/selected-depth/YUV helpers.
- `semantic-publication-ground-red-20261020.log`: missing publication and ground
  helpers, before implementation.
- First focused run91 tests/2 failures: two minimal-floor fixtures translated
  `.60..80` coordinates and subtraction rounded below the strict`.20` bound.
  Changed fixture origin to exact`0..20`, not production acceptance tolerances.
- `semantic-unknown-red-20261020.log`: real1-test assertion failure, unknown
  heights incorrectly0 without reference. Fixed source to64; updated old shared
  rejection assertion to the corrected contract (intermediate16 failures kept).
- `semantic-fusion-green3-20261020.log`: **92 focused tests,0fail/error**, actual
  Codex run. Includes76 new tests plus16 existing frame-gate regressions.

Post-review rotation regression added2 cases: **94 focused PASS**,78 new plus16
existing frame-gate tests. Independent Cursor re-review found no remaining High/
Medium concrete scoped source defect; dispositions in
CURSOR_SEMANTIC_FUSION_REVIEW_2026-10-20.md. Live onDraw validation, rotation-only
invalidation, required-span copies, unmapped counters and GL/worker/inference/
end-to-end timing/session-throughput diagnostics are included.

Stable final gate **20261020T053311702676Z PASS**:675app+10sim+20policy+86eval =
**791 tests**,0fail/error/skip; both builds and APK model/permission/debug-isolation/
known private-root policy pass. Lint0error/fatal,45debug/34release warnings.
Source213files SHA256 `c5c6ea7e09ac8d6581d2244c9a96a8af3f0f0cc4f38ec6eacb2a3bdbfd7bf838`.
First full gate correctly FAILED due to source edits during verification; not a
reported success. Its successor's wall time includes a host suspension/clock gap
and is not an execution-speed measurement.

DebugAPK SHA256 `63f76fdd63e268de6011823c9b5ed19841900e79b9922d9cc20effec5eb135ee`;
releaseunsigned `0e8887d043b5ff029920abb98c580a5ada3aefb4e779af5ae6ab7d7849fdd8af`.
Phone experiment is recorded separately below. Prior0.9.31 evidence is NOT
evidence for this candidate. Owner physically confirmed ready after the gate;
USB install0.9.32/45 succeeded and installedbaseAPK hash exactly matched.

## Physical test, only after owner setup

### Actual0.9.32 run, not0.9.33 validation

Owner physically confirmed USB/open/lit scene and stationary setup. Installed
0.9.32/45 and shell baseAPK SHA matched exactly. PID7734, actual duration20.18s,
17:14:46start→17:15:06result→17:15:07closed(MYT). Scoped stop afterwards, noPID
(pidof exit1 is expected for no process, not an app failure). No camera media
recorded or uploaded. Log SHA256
`e5a3f064791081cf8ec8744dd1717e883117fb04c860188dab759b2e519c8748`.

- Raw COMPLETE:542distinct frames/507tracking/35nontracking,34raw admits,144
  decoded pairs; rawconfident grid integration produced6 accepted semantic
  samples,1stale rejected. All6 unmapped_cells0;6–42 finite raw world samples
  per fusion grid. Existing separate3x3 projection7samples/8worldpoints.
- Accepted semantic receipt→result p50 **147.689461ms**,p90 **239.942461ms**,
 6sample nearest-rank. All7 attempts including rejected warmup p50 **165.955077**,
 p90 **607.160231ms**. Do not hide the slow rejected attempt.
- GLacquire/copy/map p50/p90 **5.478384/13.043307ms**; workerprepare incl queue/
 conversion/bitmap p50/p90 **118.641847/406.796385ms**; modelinvoke
 **31.818538/44.816615ms**. These aggregate stage times do not isolate converter
 causality. Accepted/session throughput **0.297396/s**, NOT cameraFPS or SDKfps.
- Floor reference **0candidates**. Five no_floor_component, one insufficient_support;
 floorcells0–3/64, wall-like labels47–59/64,rug0. Heightunknown64 each sample.
 No scene annotations/RGBrecording, so wall-like labels are NOT recognition
 accuracy.0vertical SDKplanes;11/19 upwardinventory/ray/height samples only.
- SDK maxsegmentdisplacement0.46277m is not calibrated motion. No malformed,
 semantic failure or AndroidRuntime crash marker in scoped logs. Resource
 closure markers include semantic engine,GLbackground and rawsession.
- One end-of-probe process sample PSS199864KiB (~195MiB),RSS338460KiB; notpeak/
 leak/longrun measurement. Battery11% both readings while charging,temperature
 37.8→37.5°C; no causal thermal/energy-efficiency claim from20s. Owner told
 phone can rest on charger; no further camera action without fresh setup.

### Next0.9.33 test (pending)

When built/reviewed, ask owner to connect/unlock Redmi and face a lit wall/floor/
furniture scene, feet stationary, slow20–30cm lateral phone motion, no Back.
Bounded20s AUTOMATIC comparison may opt in to semantic fusion while default is
unchanged. Capture filtered aggregate logs only, verify exact installed hash and
COMPLETE/CLOSED plus semantic outcomes; no RGB/depth/audio recording/upload.

ADB diagnostic extras (Codex executes after setup, not an owner prerequisite):

```powershell
adb -s SERIAL shell am start -W -n com.triguide.ai/.diagnostics.RawDepthDiagnosticActivity --ei triguide.diagnostic.duration_seconds 20 --es triguide.diagnostic.depth_mode AUTOMATIC --ez triguide.diagnostic.semantic_fusion true
```

Require actual admitted fusion samples, acceptable observed processing latency,
correct overlay orientation and resource shutdown before claiming runtime
integration. Metric/classification accuracy additionally needs measured labelled
scenes; build/JVM pass cannot establish it. Ground reference stability across
distinct observations is the next gate, not automatic guidance enablement.

## Primary reuse references

- Existing pinned interpreter/model: DiagnosticSegmenter, no new weights or
  runtime; existing separate code/weight/ADE20K terms still apply.
- [ARCore Frame current-image/transform/close contract](https://developers.google.com/ar/reference/java/com/google/ar/core/Frame).
- [Coordinates2d spaces](https://developers.google.com/ar/reference/java/com/google/ar/core/Coordinates2d).
- [Android YUV420888 plane contract](https://developer.android.com/reference/android/graphics/ImageFormat#YUV_420_888).
- Existing Apache2 Google1.57 texture-depth scalar projection is reused, see
  TextureDepthProjection.kt attribution and OPEN_SOURCE_LICENSES.md. Current
  SDK API contracts verified2026-10-20; docs are not real-device calibration.
