# Adapted ARCore sample pattern

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
