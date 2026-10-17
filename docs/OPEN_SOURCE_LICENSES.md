# Open-source licenses

## TriGuide original code

License not chosen by the project owner yet. Do not publish the APK as if a license were already assigned.

## Studied, not copied into `android-app/`

| Project | License | Use |
| --- | --- | --- |
| GuideLens-Android-App (`nrai18`) | GPL-3.0 | Local audit only. Kotlin and ONNX assets stay in `research/upstream/` and are gitignored. |
| MediaPipe samples (`google-ai-edge`) | Apache-2.0 | Reference for a later on-device detector. Not linked in this APK. |
| OpenBot (`ob-f`) | MIT | Architecture notes only (phone as brain, separate controller). Android sources were not checked out. Physical body is out of scope. |

## App dependencies (Maven, not vendored)

AndroidX, Jetpack Compose BOM `2024.10.01`, CameraX `1.4.0`, Kotlin `2.0.21`, JUnit `4.13.2`. Their own licenses apply (typically Apache-2.0).

YOLOv8-family weights found inside GuideLens were not imported. Treat them as encumbered (GPL-3.0 repo, and Ultralytics YOLO weights are commonly AGPL-3.0) until a lawyer or the user clears a specific file.
