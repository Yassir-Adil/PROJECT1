# Software comparison

Date: 2026-10-18. Sources are shallow or sparse clones under `research/upstream/` (not vendored into the app).

| | GuideLens Android | MediaPipe samples | OpenBot |
| --- | --- | --- | --- |
| Remote | `nrai18/GuideLens-Android-App` @ `6436424` | `google-ai-edge/mediapipe-samples` @ `cb208b3` (sparse: object detection + image segmentation Android) | `ob-f/OpenBot` @ `216ecb0` (sparse: docs only; `android/` tree not checked out) |
| License | GPL-3.0 (`LICENSE`) | Apache-2.0 | MIT |
| Stack | Kotlin, Compose, CameraX, ONNX Runtime 1.23.1 | Android object-detection sample, CameraX, TFLite Task models downloaded at build | Phone-as-brain Android apps, Arduino body, separate controller app |
| Vision | On-device YOLO World v2 INT8 + floor segmentation INT8. Medicine path calls a local server (Gemini) or falls back to Google search, and the Gradle file injects `OPENAI_API_KEY`. | EfficientDet / MobileNet SSD models from Google storage. Detection is real when the sample is built. | TFLite person following and driving policies aimed at a robot body |
| Offline core | Navigation models are packaged in assets. Medicine identification is not offline. | Sample builds download models; inference is on-device after that. | Policies can run on-device; the chassis is out of scope |
| SDK in repo | `compileSdk` / `targetSdk` 36, `minSdk` 29, AGP plugin `8.13.2`, Gradle 8.13 | Sample README: min SDK 24 | README: smartphone brain plus a physical body |
| Fit for TriGuide Phase 1 | Real camera and real detectors, but GPL-3.0, cloud medicine path, and a different UI. This PC has platforms 34 and 35 only, so their `compileSdk 36` project was not built here. | Best later detection building block (Apache-2.0). Not the TriGuide shell. | Useful control ideas. Physical hardware is out of scope. Android sources were not downloaded in this pass. |

## Decision

Ship a new TriGuide app (Option B). Do not copy GuideLens Kotlin or ONNX files into it. Details: `docs/DECISIONS.md` and `research/model-audit.md`.
