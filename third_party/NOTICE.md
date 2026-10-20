# Adapted ARCore sample pattern

## Host-only Hypersim fixture oracle —2026-10-20

research/hypersim_transport.py adapts Thomas Germer2020 MIT WebFile Range/seek
approach; full MIT text retained in that module. Pinned source:
https://github.com/apple/ml-hypersim/blob/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254/contrib/99991/download.py
Changes: strict official HTTPS origin/206/range/length/ETag/encoding checks,
bounded transfer/request counts, injectable fake transport; no URL table copied.

research/hypersim_geometry.py adapts the pixel-centred ray/pose/unit equations
from Apple2020 Hypersim notebook; full toolkit terms retained separately in
hypersim-toolkit-LICENSE.txt. Source:
https://github.com/apple/ml-hypersim/blob/3463c5c4a75f3cbfc65ed31cfd6e87204b3a2254/contrib/mikeroberts3000/jupyter/01_casting_rays_that_match_hypersim_images.ipynb
Changes: pure numpy, validation/invalid-data masks/robust norms, bounded arrays,
residual/ground-truth semantic reporting. No pandas/Embree/meshes/toolkit binary
or Android runtime imported. No endorsement/patent clearance claimed.

Hypersim data is separately CC-BY-SA3.0: Roberts et al., ICCV2021, Apple.
Tiny public validation geometry/calibration bytes and per-file provenance stay
outside Git/APK; no images/weights distributed or training. Host h5py3.16.0 is
BSD-3-Clause; bundled HDF5/other wheel components retain their own terms. No wheel
or binary vendored. This notice does not relicense data as application code.

## Debug texture-depth scalar projection —2026-10-20

TextureDepthProjection.kt adapts only the texture-intrinsic scaling and OpenGL
camera unprojection formula from Google ARCore SDK v1.57.0 PointCloudHelper.java.
Copyright2021 Google LLC; modifications Copyright2026 TriGuide-AI contributors.
Source: https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/PointCloudHelper.java
Apache-2.0 header retained; full source licence arcore-sample-LICENSE.txt.
Changes: pure scalar contract, strict dimensions/aspect/units/numeric validation;
our existing stride/unsigned/confidence decoder retained, no packed/signed-short
shortcut, point-cloud renderer, RGB acquisition, anchors, storage or authority.
SDK binary terms remain separate. Model-card PDF was inspected, not vendored.

## Debug live camera background —2026-10-20

DiagnosticCameraBackground.kt adapts the OES screen-quad background/shader
pattern from Google ARCore SDK v1.57.0 shared_camera_java sample, Copyright2017
Google LLC; modifications Copyright2026 TriGuide-AI contributors.
Source: https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/shared_camera_java/app/src/main/java/com/google/ar/core/examples/java/common/rendering/BackgroundRenderer.java
Shaders: same sample assets/shaders/screenquad.vert and screenquad.frag.
Apache-2.0 source notice retained in Kotlin; full license arcore-sample-LICENSE.txt.
Changes: minimal Kotlin/fake-GL seam, checked shader/program lifecycle, SDK UV
mapping, no depth visualization/shared-camera framework/assets, ephemeral display
only. No RGB Image acquisition, saved pixels, recording or navigation authority.

## Earlier raw-depth lifecycle adaptation

`RawDepthDiagnosticActivity.kt` adapts the GLSurfaceView/session lifecycle pattern
from Google's ARCore SDK v1.57.0 `samples/raw_depth_java/.../RawDepthActivity.java`.
Copyright 2021 Google LLC. Modifications Copyright 2026 TriGuide-AI contributors.
Source: https://github.com/google-ar/arcore-android-sdk/blob/v1.57.0/samples/raw_depth_java/app/src/main/java/com/google/ar/core/examples/java/rawdepth/RawDepthActivity.java

The applicable source license (section 2 of the SDK LICENSE) is reproduced in
`arcore-sample-LICENSE.txt`. TriGuide changes are identified in the source header:
bounded debug-only metrics, no RGB/storage/cloud/navigation, fail-closed startup,
checked timestamp admission and lifecycle fixes. Other sample renderers, anchors,
image coloring and dependency tree are not imported.

This does not relicense the `com.google.ar:core` Maven SDK binary. That binary is
governed separately by ARCore Additional Terms:
https://developers.google.com/ar/develop/terms . Google Privacy Policy:
https://policies.google.com/privacy . No overall license is assigned here to
TriGuide original code or research model weights.
