# Coordinate and ground-reference reuse — 2026-10-20

## Decisions from actual evidence

Redmi0.9.30 readback: CPU640x480, GPU1920x1080, depth160x90. Rescaling CPU
intrinsics directly onto depth is not justified. Google's [raw-depth guide](https://developers.google.com/ar/develop/java/depth/raw-depth)
specifies GPU aspect/native orientation. Its pinned [DepthData.java](https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/DepthData.java)
uses texture intrinsics; [PointCloudHelper.java](https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/PointCloudHelper.java)
scales focal/principal values to depth dimensions, with integer pixels and
OpenGL x-right/y-up/-z-forward. Sample source Apache-2.0; existing SDK binary
terms remain separate. Adapted scalar formula, not its signed-short/tightly
packed depth shortcuts: our stride/UINT16/confidence validation is preserved.

The [Camera reference](https://developers.google.com/ar/reference/java/com/google/ar/core/Camera)
distinguishes physical pose/unrotated texture from display-oriented pose. The
new probe uses the physical pose of the same live Frame and texture intrinsics,
never a cached pose or CPU fallback. Exact aspect matching is a conservative
debug gate: future nonmatching layouts require an independently verified SDK
transform, not a guessed crop. No screen overlay alignment is claimed here.

Reused [Plane.centerPose](https://developers.google.com/ar/reference/java/com/google/ar/core/Plane)
for bounded signed cameraWorldY-planeCenterWorldY offsets on tracking,
nonsubsumed upward planes. Range can be negative and is not camera-to-floor
height. The lowest/largest plane could still be furniture; no plane is selected
as ground. Session-known is not currently visible or inside the walking path.
These are prerequisites for measuring the wall/mat problem, not its solution.

## Ready public RGB-D, transport actually checked

[Hypersim at3463c5c](https://github.com/apple/ml-hypersim/tree/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254)
provides public scene-disjoint v1 validation, depth, normals, poses and NYU40
labels. Data CC-BY-SA3.0; toolkit Apple terms separate. The first public validation
entries actually read from the official split CSV are ai_003_010/cam_00 frames0,
1,2. Do not use train ai_001_001 as a held-out accuracy claim.

Its linked [selective downloader](https://github.com/apple/ml-hypersim/blob/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254/contrib/99991/download.py)
is MIT (Thomas Germer2020) and reuses HTTP Range+zipfile rather than downloading
whole scenes. Reviewed source includes a URL table and seek/read wrapper; its
wrapper does not enforce206/Content-Range, timeouts or bounded response length.
Before reuse add those guards and path/member-size/CRC checks. Never allow a
server ignoring Range to silently fetch a multi-GB archive.

Actual local checks: Apple HEAD returned200, Accept-Ranges bytes, train scene
archive2249481604bytes. This is **not** successful Range transfer. Bounded GETs
with curl, requests and native .NET failed connection reset, including a22-byte
validation-scene footer request. No images/HDF5/whole archive downloaded; no TLS
verification disabled, alternate paid service, account or new package installed.
The fetched split CSV was parsed in memory. A temporary transport failure is
not evidence the dataset is unavailable generally. No Hypersim accuracy reported.

Next bounded fixture: scene-disjoint2-3 scenes, depth/position/normal/semantic,
camera matrices and asset-unit scale, per-file hashes/provenance outside Git/APK.
Hypersim depth is Euclidean ray range, whereas ARCore depth is camera-z; its
tilt-shift projection and world units must be respected. Never compare raw
numbers across these conventions or use ground truth to calibrate a test image
and then report that as deployable model accuracy. No1.9TB fetch/paid meshes.

## Guideline ready depth model — independently checked, not installed

The [pinned Google model card](https://github.com/google-research/project-guideline/blob/bb3628df8df97f41b526927b273e2d8fa3b71fba/project_guideline/vision/models/docs/guideline_depth_model_card.pdf)
was downloaded, all9 pages text-extracted, and pages1/9 rendered and visually
inspected. PDF SHA256:
`d367edfa2be46b30c7f2d290862bf76ac50951cd3d6cbae545d0bd95f209fc0e`.
This independently confirms an Apache-2.0 grant on the specific model card;
the earlier blanket 'weights not verified' deferral is now narrowed, not silently
repeated. No model bytes downloaded, hash/tensor/runtime gate still pending.

Card:10.8MB MobileNetV2 U-Net, uint8[1,192,192,3] input, float32[1,192,192,1]
inverse-depth output, center crop; metric conversion needs scale/shift calibrated
to camera/view. Outdoor training/evaluation; indoor scenes explicitly outside
known conditions. Card's safety-critical warning and failure examples (shadow
as obstacle, missing objects, fence errors) make it unsuitable as a direct
walking-decision substitute. Pixel7 TPU/GPU/CPU numbers are NOT Redmi benchmarks.
Research/diagnostic benchmark candidate only. Training-data rights are not
automatically cleared by the model grant; no dataset redistribution/training here.

## Ranked remaining work (not implemented by naming it)

1. Ground/table held-out geometry fixtures, correct source coordinates and
   relative-height residuals. Current two helpers only instrument these gates.
2. Fuse semantic identity, gravity, connected ground extent, relative height and
   temporal evidence with one camera owner, after fixture/phone validation.
   A rug class alone cannot distinguish flat textile from a raised trip edge.
3. If SDK geometry remains inadequate, benchmark ONE ready model (V2 Small
   indoor candidate or Guideline outdoor comparison) with actual artifact/tensors,
   Redmi p50/p90/memory/thermal and wall/edge failures. Do not add all runtimes.
4. Open3D host RANSAC oracle after data transport/calibration. No heavyweight
   Android dependency, guessed floor selection, new sensor or paid solution now.
