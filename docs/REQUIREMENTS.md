# Requirements

## In scope now

1. Installable debug APK on Android 8.0+ (`minSdk` 26), built with Kotlin, Jetpack Compose, CameraX, and Material 3.
2. Home screen with TriGuide AI branding, status lines, and entries for camera, robot simulation, test scenarios, diagnostics, and settings.
3. Live camera preview after the user grants the camera permission, including loading, denied, and error states.
4. A manual three-wheel (tricycle / bicycle-model) simulator with unit tests for the motion equations.
5. Persistent on-screen notice: Research and Simulation Mode. Not a certified mobility aid.
6. TalkBack labels on primary controls and the camera preview.
7. No internet permission and no camera upload in this build.
8. On-device object detection when the bundled model loads. If the model is missing or fails to load, the observation stays empty. No fake boxes.

## Out of scope

- Buying or building a physical robot.
- Cloud vision as the default path.
- Copying GuideLens GPL-3.0 code or its ONNX weights into this app.
- Claiming a measured detection accuracy before a labeled indoor test.
