# Open-source licenses

## TriGuide original code

License not chosen by the project owner yet. Do not publish the APK as if a license were already assigned.

## Studied, not copied into `android-app/`

| Project | License | Use |
| --- | --- | --- |
| GuideLens-Android-App (`nrai18`) | GPL-3.0 | Local audit only. Kotlin and ONNX assets stay in `research/upstream/` and are gitignored. |
| MediaPipe samples (`google-ai-edge`) | Apache-2.0 | Pattern for CameraX live-stream detection. The sample project is not compiled into this APK. |
| OpenBot (`ob-f`) | MIT | Architecture notes only (phone as brain, separate controller). Android sources were not checked out. Physical body is out of scope. |

## App dependencies (Maven, not vendored)

AndroidX, Jetpack Compose BOM `2024.10.01`, CameraX `1.4.0`, Kotlin `2.0.21`, JUnit `4.13.2`, MediaPipe Tasks Vision `1.1.1` (Apache-2.0). Their own licenses apply.

## Bundled model

| File | Source | License |
| --- | --- | --- |
| `android-app/app/src/main/assets/models/efficientdet_lite0.tflite` | `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite` | Apache-2.0 (Google / MediaPipe EfficientDet-Lite0 int8). Training data is COCO 2017; COCO annotations are CC BY 4.0 |

SHA-256 `0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB`, 4,602,795 bytes.

YOLOv8-family weights found inside GuideLens were not imported. Treat them as encumbered (GPL-3.0 repo, and Ultralytics YOLO weights are commonly AGPL-3.0) until a lawyer or the user clears a specific file.
