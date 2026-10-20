# Public RGB-D oracle and owner-ready Redmi verification —2026-10-20

## Completed scope

Two improvements on e145115 /0.9.31/44: bounded public validation fixtures, and
an offline coordinate oracle using the official per-scene matrices/pose/units.
No Android source, APK bytes, model, default, navigation or permission changed.
The same installed0.9.31 was verified by version+SHA before the owner-ready probe.

Full final gate713 tests PASS; source-only independent Cursor R findings fixed
or explicitly accepted with limits. Actual phone20s COMPLETE+CLOSED:20 raw
diagnostic admits,3 finite projected world points,4 upward height samples;
0vertical planes. Runtime projection is now observed, metric accuracy/identity
are not. Owner action complete; phone app stopped, no walking requested.

## Reuse, provenance and local tooling

Primary pin: Hypersim3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254. Official
[selective downloader](https://github.com/apple/ml-hypersim/blob/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254/contrib/99991/download.py)
MIT Range/zipfile approach adapted to strict206-before-body, exact range/length,
ETag/If-Match/identity/no-redirect, byte/request budgets and fake opener tests.
No whole4GB scene or1.9TB dataset fetch. Prior suffix-range requests reset;
explicit absolute Range+current IPv4HTTP1.1 probe returned206/22bytes. Multiple
variables changed, so no claim of the single prior network failure cause.

Data CC-BY-SA3.0: Hypersim, Roberts et al., ICCV2021, Apple. Separate Apple
toolkit terms retained in third_party/hypersim-toolkit-LICENSE.txt; MIT text in
transport module. Adapt only the official
[pixel-centred ray/pose equations](https://github.com/apple/ml-hypersim/blob/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254/contrib/mikeroberts3000/jupyter/01_casting_rays_that_match_hypersim_images.ipynb),
not meshes/pandas/Embree/rendering toolkit. Ground-truth NYU40 names checked in
[official statistics source](https://github.com/apple/ml-hypersim/blob/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254/code/python/analysis/dataset_generate_image_statistics.py):
wall1/floor2/table7/floormat20, not ADE IDs.

Added host-only h5py3.16.0 from public PyPI, BSD-3-Clause; wheel includes separately
licensed HDF5. Python3.12 Windows wheel SHA verified before local install:
`96b422019a1c8975c2d5dadcf61d4ba6f01c31f92bbde6e4649607885fe502d6`.
NumPy2.3.4 unchanged; no pip/runtime/Android upgrade. Requirements pinned in
research/requirements-eval.txt. No wheel/data/model binaries published.

## Actual public data, not model training

Predetermined first validation scenes, cam_00/frame0: ai_003_010,ai_004_003,
ai_004_004. Public-val membership checked before fetching and before evaluation.
Final v2 fixture:23 hash-pinned files,7324034 Range body bytes plus3275228
metadata bytes;7424075 local extracted/metadata bytes. Files are depth, world
positions, semantic labels, keyframe indices/poses, scale and per-scene camera
matrix/split CSV; **no RGB images or model inference/training** in this round.
Original v1 fixture preserved; v2 adds explicit keyframe-index binding, not a
changed scene selection. Frame0 maps to pose row0 in all three actual scenes.

Local root: `%LOCALAPPDATA%/TriGuide-AI/datasets/hypersim-fixture-20261020-v2`.
Complete manifest SHA:
`36b036d0f3bb09f69e2eb628534a7e4b9ce20588103d720141fe33ca5710ceda`.
Raw bytes/manifest stay outside Git/APK. Partial/CRC-failed roots are untrusted,
remain incomplete and are never silently resumed; use a fresh output root.

Final report: outputs/reports/rgbd-20261020/evaluation-v2/summary.json, SHA:
`4ee4a95e61c622188772b775113591111819390cd1775e1e5413aa00b8b78876`.
Host evaluation1.0209s; not Redmi latency. Correct Euclidean ray range vs positive
camera-z, tilt-shift matrix, proper pose, asset-unit scale, invalid masks and
shared finite geometry:2192998 compared pixels, not independent observations.

| Predetermined scene | Compared pixels | Coordinate residual p50/p90/p99 mm | Range minus camera-z p90 m |
| --- | ---: | ---: | ---: |
| ai_003_010 |786432|0.916 /2.413 /5.640|0.6032|
| ai_004_003 |786432|0.807 /1.915 /3.186|0.4804|
| ai_004_004 |620134|0.830 /1.868 /2.742|0.7103|

These residuals compare reconstructed **ground-truth** range to quantized
float16 world positions, NOT learned depth/detection accuracy or phone accuracy.
Three synthetic frames are a calibration smoke set, not a statistical benchmark.
The range/z discrepancy illustrates why raw datasets cannot share depth units
blindly; do NOT feed Hypersim range into the Android camera-z contract.

Ground-truth-labelled world-Z medians in ai_004_004: floor~0, table0.150426m,
floor_mat0.004892m; ai_003_010 table0.770531m/floor_mat0.006993m, no floor-labelled
pixels. These describe **all sampled labelled object points**, including sides/
legs, not measured tabletop height, ground clearance or rug-edge height. They
cannot identify a plane, justify a rug STOP bypass, CLEAR or movement. Labels
were provided by the dataset; the evaluator did not recognize those objects.

## Executed RED → fixes → GREEN and independent review

All logs private under outputs/reports/, not worker claims of execution:

- Missing modules RED: rgbd-tests-red-20261020.log; HDF5-runner RED separately.
- Fingerprint RED: new helper/requirements not included (expected2files,got0).
  Fixed selection to cover all research Python/requirements, including earlier
  semantic helper, plus current dirty/untracked source.
- Initial Cursor G source:10 broadcast errors from np.stack without broadcasting.
  Four numeric RED regressions caught scale-dependent determinant gate, complex
  coercion, bool scale and unrepresentable residuals. Fixed; extra1e200 matrix
  scale RED led to max-coefficient normalization instead of overflowing norm.
- Codex corrected two wrong test fixtures (2x3 shape, off-axis range!=z), not the
  projection math to satisfy a wrong expectation. ZIP test writer normalizes
  backslash paths on Windows; raw-member mutation now tests extractor rejection.
- Transport two independent REDs: float-1 read size accepted, failed connection
  not counted. Both fixed; bounded fake ZIP/network tests GREEN.
- Actual first data eval failed CSV dimensions '768.0'; strict integral-float
  dimension parser added after RED, not a truncating int(float) workaround.
- R found keyframe-row assumption + evaluator test gap. Added explicit indices
  fetch/hash/exact lookup; six synthetic end-to-end tests after actual RED cover
  sparse/missing/duplicate indices, PIN/SPEC/hash/scale/shape/provenance gates.
  HDF5area reject-before-read and finite delta/source-requirement hashes fixed.
- R final re-review: no new Medium/High defect. Partial evidence preservation
  accepted; suggestion to return unnormalized ray matrix withdrawn with numeric
  proof. Worker R is source-only, not independent execution or physical QA.

Final gate outputs/reports/quality-gate/20261020T041447079371Z/summary.json
PASS21.937s:597 app+10sim+20policy+86evaluation=713,0failure/error/skip. Both
builds, APK assets/permission/debug isolation/privacy-root policies PASS. Lint
0errors56debug/34releasewarnings unchanged. Source201 files SHA:
`ff10c4683bddbffaed26cd7dad98be4f7b21e28b6746851af36d7e313d6b50d2`.
Debug SHA unchanged and matched phone:
`38c90117f342cb0f8620070f0994546f7e90aa63bf1cb6f3f64c4e5700675857`.

## Actual owner-ready Redmi20s, no walking

Owner explicitly confirmed USB/unlock/lit wall+floor+furniture, standing with
slow lateral20–30cm phone movement. Live precheck online transport6, unlocked,
version0.9.31/44 and installedSHA exact. No new install required. One opt-in
AUTOMATIC debug Session, default RAW_DEPTH_ONLY unchanged. PID12380,
START MYT12:16:39.049, RESULT12:16:59.269, CLOSED12:17:00.283,20.16s elapsed.

| Actual measurement | Observed |
| --- | --- |
| Distinct / tracking frames |565 /533 (32nontracking)|
| Raw acquired attempts / decoded pairs |146 /145|
| Raw admitted / empty / repeated-depth rejects |20 /104 /21|
| Observed raw valid / confident pixel fractions |26.4201% /4.63209% (include reprojections)|
| Projection samples / finite world points |5 /3 (3samples have no confident grid pixels)|
| Upward inventory / height / ray samples |4 /4 /4 of19samples|
| Vertical inventory / ray samples |0 /0|
| SDK max segment displacement |0.288370m, not calibrated physical distance|
| Dense nonzero / total pixels |1051200 /2088000, no confidence/admission|
| Session.update p50/p90 ms |1.44 /3.19|
| Raw receipt→summary p50/p90 ms |2.13 /3.75|
| Surface queries p50/p90 ms |1.30 /3.46|
| Dense processing p50/p90 ms |1.67 /4.42, excludes full SDK inference|

Two real projection log samples: one and two confident-grid pixels gave one and
two finite camera/world points. No coordinates retained/logged. SDK heights
across four observations range0.702970–0.897206m camera-above-upward-plane; no
floor/table identity or tape-measure accuracy. Actual CPU640x480/GPU1920x1080/
depth160x90; correct texture-intrinsic path exercised. No invalid projection,
malformed/decode/dense errors or crash marker observed in scoped PID/tag log.

Private final log phone-0931-auto20-final.log SHA:
`bf0cd1fa6e238fa93ba46ab4b90e3d9d5e9e86f653b3fa37b0bec4b79ab93450`.
App scoped force-stopped after CLOSED; pidof empty. No RGB/depth/audio recording,
private upload, global log clear, driver/security/server reset or walking cue.
Earlier insufficient-light0tracking run remains a real failure, not relabelled.

## Ranked next work and limits

1. One ARCore-owned camera adapter for existing indoor segmentation + geometry:
   reuse SDK texture↔CPU coordinate transforms and bounded CPU-image worker,
   not concurrent CameraX/ARCore. Initially diagnostics/HOLD-only. Verify mask
   alignment/clock domains/current provenance and wall examples before guidance.
2. Ground reference from semantic identity + supported connected extent + gravity/
   robust geometry + temporal consistency, tested against tables/steps/rugs.
   Largest/lowest plane and ground-truth class medians are not live floor evidence.
3. Only if these fail measured device gates, benchmark one ready pretrained depth
   candidate; no model shopping, training on held-out fixtures, purchase or cloud.

Wall detection, safe path, floor/table identity, stair/mat hazard accuracy, real
audio/TalkBack and sustained heat/energy remain unverified. Current improvements
are an evaluation foundation and runtime geometry proof, not solved navigation.
