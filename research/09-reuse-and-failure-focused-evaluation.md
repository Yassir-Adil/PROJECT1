# Reuse decisions and failure-focused evaluation — 2026-10-20

## Decision, not a new-model shopping list

Baseline: 0.9.28/41, source 5b4986a. An actual warm Redmi probe produced
upward-plane ray hits but no vertical hits. Four ray misses cannot tell whether
ARCore knows a vertical plane elsewhere. Existing Top31 semantic mIoU omits
ground-truth classes above31, including stairs. Neither result licenses motion.

This round implements two missing measurements using existing infrastructure:

1. Reuse ARCore's session plane inventory alongside existing polygon-checked
   rays. Separate known orientation, current intersection, and physical identity.
2. Reuse the installed LiteRT evaluator and already downloaded public ADE20K
   validation images; preserve all150 ground-truth classes against32 predicted
   labels. Measure wall/rug/stair-to-floor confusion, not only average mIoU.

No model training, new runtime, purchased sensor, cloud, permissions or production
guidance change. Results and engineering gate are in
[REUSE_EVALUATION_2026-10-20.md](../docs/REUSE_EVALUATION_2026-10-20.md).

## Ranked reusable components

| Priority / ready source | What to reuse | Terms / actual suitability | Disposition and next gate |
| --- | --- | --- | --- |
| 1. [Google ARCore1.57 HelloAR](https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/hello_ar_java/app/src/main/java/com/google/ar/core/examples/java/helloar/HelloArActivity.java) | `Session.getAllTrackables(Plane.class)` inventory alongside hit tests | Sample Apache-2.0, existing SDK binary terms separate; already runs on this Redmi | Implemented diagnostic inventory; bounded primitive mapping256,1Hz. Native enumeration remains SDK-owned. No known plane is assumed currently visible. |
| 2. [Google Project Guideline](https://github.com/google-research/project-guideline/tree/bb3628df8df97f41b526927b273e2d8fa3b71fba) | Single camera/pose source, robust depth alignment, occupancy aggregation, tracking-loss stop, simulator testing | Apache-2.0 source; Android/NDK/Bazel, Pixel6–8 TPU and marked-line requirements in README; not a supported Google product | Architecture reference, NOT a drop-in room navigator. Next: same-frame intrinsics/pose and ground-reference calibration, then a separately tested adapter. Do not import its whole app or assume its weights/data are cleared. |
| 3. [Depth Anything V2 Small](https://github.com/DepthAnything/Depth-Anything-V2/tree/a561b849ebae10a6f5ef49e26c83cbbcd36c71bf) + [Android ONNX demo](https://github.com/shubham0204/Depth-Anything-Android/tree/f4adf9918b13db754fcf83fd7d8553f8f6d55b58) | Pretrained depth and existing fused preprocessing/export rather than writing a model | Small24.8M/Apache-2.0; larger weights CC-BY-NC4.0. Android sample Apache-2.0, ONNX runtime. Weight artifact, data terms, dimensions and preprocessing still independently verified at import | Best prepared alternative benchmark track, not installed or bundled this round. Demo256 input has252 output (512→504), so current512 masks cannot consume it unchanged. Benchmark heat/latency/edge errors on Redmi before adoption. Relative depth is not metres. |
| 4. [Open3D](https://github.com/isl-org/Open3D/tree/b6c5e196384ad71e75b6e6f9c5da22d046221f1d) | Offline reference RANSAC `segment_plane`, outlier handling and point-cloud geometry tests | [MIT license](https://github.com/isl-org/Open3D/blob/b6c5e196384ad71e75b6e6f9c5da22d046221f1d/LICENSE); no model weights | Host-side oracle candidate after calibrated RGB-D fixtures; not another Android dependency. Do not select the largest plane and call it floor: tables/walls can dominate. |
| 5. [Depth Anything3](https://github.com/ByteDance-Seed/Depth-Anything-3/tree/3d835ec1a5802d64a8b8b15f817a1ab54809bfe4) | Newer depth/pose research and released evaluation tools | Small~80M/Base120M Apache-2.0, larger any-view variants NC; metric-large350M has different contract. Small is not listed as metric | Reviewed rather than ignored for age. Deferred Android integration: no Redmi benchmark, larger thanV2 Small, runtime/export/memory risk. A newer name is not device evidence. |
| 6. [OpenBot](https://github.com/ob-f/OpenBot) | Phone compute/control separation and data workflow ideas | MIT code; physical robot and driving policies have a different purpose | Retain existing architecture reference; no chassis, motor policy or dependency import for a phone-only wall/mat problem. |

GitHub default-branch SHA/committer dates were checked via public API on
2026-10-20: Guideline bb3628d (Oct15), V2 a561b84 (Mar24), Android demo f4adf99
(Sep7), Hypersim3463c5c (Sep11), DA3 3d835ec (Jul27), Open3D b6c5e19 (Sep16).
These are reproducibility pins, not claims of release quality or endorsement.

### Concrete algorithms worth adapting, after their prerequisites

Project Guideline's [depth_align_ransac.cc](https://github.com/google-research/project-guideline/blob/bb3628df8df97f41b526927b273e2d8fa3b71fba/project_guideline/depth/depth_align_ransac.cc)
fits monocular depth against tracked geometry. Its
[occupancy_map.cc](https://github.com/google-research/project-guideline/blob/bb3628df8df97f41b526927b273e2d8fa3b71fba/project_guideline/environment/occupancy_map.cc)
is a concrete reference for accumulating obstacles rather than repeatedly
announcing unrelated boxes. Our inference: a useful future design, only after
one camera owner, coordinate/clock checks, ground-reference and held-out errors.
No part is copied or reported as integrated here. Its README explicitly requires
a sighted spotter and warns that not all obstacles/scenarios are detected.

The [V2 metric branch](https://github.com/DepthAnything/Depth-Anything-V2/blob/a561b849ebae10a6f5ef49e26c83cbbcd36c71bf/metric_depth/README.md)
provides indoor Hypersim-trained checkpoints. That is a distinct artifact from
the Android relative-depth demo; naming an output metres does not establish
camera-specific distance accuracy. Do not combine these contracts silently.

## Ready data: use appropriate labels, not random internet photos

| Data / primary source | Evidence available | Terms and next use |
| --- | --- | --- |
| [ADE20K](https://ade20k.csail.mit.edu/terms/) | Existing local2000 validation RGB+151-ID semantic masks; no depth/IMU or trip-height truth | Images noncommercial research/education; software/annotations separately BSD-3 terms. Reused now without redownloading or publishing images. [Official taxonomy](https://github.com/CSAILVision/sceneparsing/blob/master/objectInfo150.txt): wall1/floor4/rug29/stairs54/stairway60/escalator97/step122. |
| [Hypersim](https://github.com/apple-aiml-research/ml-hypersim/tree/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254) | Synthetic RGB, depth, normals, poses, semantic labels and scene-level train/val/test split; public release74619 images | Data CC-BY-SA3.0; toolkit has its own Apple terms. Full~1.9TB is not justified; selective-download tooling is linked upstream. Next select tiny held-out scenes, provenance/hashes, required HDF5/calibration only. No paid meshes required for image evaluation, no asset purchase authorized. |
| [NYU DepthV2](https://cs.nyu.edu/~fergus/datasets/nyu_depth_v2.html) | Real indoor Kinect RGB-D reference, unlike synthetic scenes | Retained candidate; explicit redistribution terms not established by the landing page reviewed. No dataset fetched or license inferred from a mirror/library. Domain/sensor and invalid-depth handling need checks. |
| [ScanNet](https://github.com/ScanNet/ScanNet) | Posed RGB-D indoor sequences and surface/instance annotations | Authors require terms agreement and institutional-email request. Deferred; no account, email or agreement submitted on owner's behalf. Code license does not waive dataset terms. |

Hypersim `depth_meters` is Euclidean camera-ray distance, NOT camera-z depth;
world positions also require `meters_per_asset_unit`. Semantic IDs use NYU40,
not ADE150. Those conversions and scene-disjoint selection are mandatory before
comparing it with phone geometry. No Hypersim results claimed this round.

Rugs in images are not labelled by raised edge/height/slip risk. A flat rug and
a folded mat cannot be resolved reliably by an object name. Geometry must first
measure height relative to an identified ground reference; missing evidence is
UNKNOWN, not an automatic STOP bypass or movement permission.

## Measured adoption gates

1. Complete this round's full engineering gate, source review and real public
   validation inference; preserve production behavior.
2. Plane inventory/ray evidence with actual vertical/horizontal examples and
   controlled ground truth; record failure categories rather than count empty
   rays as success. No request for another owner photo just for research.
3. Tiny public RGB-D held-out scene adapter, explicit coordinate/unit tests,
   robust ground reference; offline Open3D reference before Android port.
4. Only if current geometry is inadequate, isolated V2 Small Android benchmark
   against the same labelled scenes, with artifact hash, tensors, p50/p90,
   memory/thermal, metric scale errors, and regressions. DA3 remains a comparison
   option, not an automatically preferred replacement.
5. Main camera ownership/fusion, wall/mat/step guidance only after independent
   positive evidence gates. Never turn or CLEAR from semantic pixels alone.

Papers reviewed: [Depth AnythingV2, NeurIPS2024](https://arxiv.org/abs/2406.09414),
[Depth Anything3,2025](https://arxiv.org/abs/2511.10647),
[Hypersim,ICCV2021](https://arxiv.org/abs/2011.02523).
Published quality is not our Redmi accuracy; no paper's headline metric is used
as a navigation acceptance threshold.
