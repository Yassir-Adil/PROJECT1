# Decision Log

## 2026-10-18 — Bootstrap without overwriting

- Repo was empty; created docs + agent rules only (no Android app code yet).
- **Why:** User asked for environment audit and persistent rules before the main project prompt / first build.

## 2026-10-18 — Local (This PC) over Cloud Agents for builds

- Android SDK, ADB, and Redmi USB testing require the local machine.
- Cloud agents remain optional for research-only tasks later.

## 2026-10-18 — No physical hardware in current scope

- Focus: camera AI, obstacle cues, virtual steering, three-wheel **simulator**, installable APK.
- Hardware purchase/build deferred until explicit user request.

## 2026-10-18 — Prefer on-device MediaPipe over cloud GuideLens stack

- GuideLens/WorldLens patterns inform UX; default runtime should work offline on the phone when possible.

## 2026-10-18 — JDK 17 path

- System MSI install of JDK 17 failed (exit 1603, likely needs admin).
- Installed Temurin 17 ZIP to `C:\Users\yassi\AppData\Local\Java\jdk-17.0.20.1+1` and set User `JAVA_HOME` there.
- Keep JDK 25 installed; Android Gradle must use 17.

## 2026-10-18 — CLI SDK before Android Studio

- Completed platforms/build-tools via `sdkmanager` so Phase 1 can proceed without Studio.
- Android Studio remains recommended later for Logcat / device UI convenience.

## 2026-10-18 — ADR: foundation Option B (new MediaPipe-ready app)

**Decision:** Option B. Ship `android-app/` as a new Kotlin + Compose + CameraX project. Do not fork GuideLens (A) and do not base the product on OpenBot (C).

**Evidence**

- GuideLens models are present and look like real ONNX protobufs (`yolov8s-worldv2_int8.onnx` 13,522,706 bytes, `floor_segmentation_int8.onnx` 12,454,891 bytes, header text `onnx.quantiz`). Loaders call ONNX Runtime. See `research/model-audit.md`.
- GuideLens is GPL-3.0. Medicine flow uses a network server / API key. `compileSdk` is 36; this PC only has platforms 34 and 35, so GuideLens was not built. Its UI is not the TriGuide screen set.
- MediaPipe samples are Apache-2.0 and already show an on-device object-detection Android path. They are the planned detector later, not linked in 0.1.0.
- OpenBot is MIT and is a phone-plus-chassis stack. The sparse clone used for notes did not include the Android tree. A physical body is out of scope.
- Option B was the shortest path to an installable APK with a real CameraX preview, an explicit non-detector, and a tested virtual tricycle model.

**Consequences**

- `minSdk` 26, `compileSdk`/`targetSdk` 35, JDK 17, Gradle wrapper 8.11.1, AGP 8.7.3, Kotlin 2.0.21.
- No GuideLens source or ONNX file is copied into `android-app/`.
- Detection status stays `NOT_IMPLEMENTED` until a licensed on-device model is integrated and measured.

## 2026-10-18 — Phase 3 MediaPipe Object Detector

**Decision:** Use MediaPipe Tasks Object Detector (`com.google.mediapipe:tasks-vision:1.1.1`, Apache-2.0) with the official EfficientDet-Lite0 int8 model, CPU delegate, live-stream mode.

**Why**

- 1.1.1 was the newest stable `tasks-vision` on Maven (14 Oct 2026) when this was pinned. `latest.release` was not used, so the build stays reproducible.
- Google’s object-detector guide recommends EfficientDet-Lite0 as the latency/accuracy default. The int8 file is the mobile size. Input is 320×320, trained on COCO (80 labels).
- Downloaded from `https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/latest/efficientdet_lite0.tflite`.
- File size 4,602,795 bytes. SHA-256 `0720BF247BD76E6594EA28FA9C6F7C5242BE774818997DBBEFFC4DA460C723BB`. Header bytes 4–7 are `TFL3`.
- License: Apache-2.0 for the MediaPipe Tasks library and this EfficientDet-Lite model. COCO 2017 annotations are CC BY 4.0. The `.tflite` is stored at `android-app/app/src/main/assets/models/efficientdet_lite0.tflite` and marked `noCompress` so it can be mapped from assets.
- CPU delegate only. The official sample’s GPU path is a known crash when the delegate is missing. CPU loaded on the 16 KB-page emulator.
- Score floor 0.50 and max 5 results, matching the sample’s default threshold and a slightly higher cap so a few obstacles can show.
- Camera path follows the official Android sample: `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST`, `OUTPUT_IMAGE_FORMAT_RGBA_8888`, `detectAsync` with a monotonic timestamp, results only from the listener. The RGBA buffer is rotated upright before inference so boxes are in the same space as the preview. Front-camera preview mirroring is applied only when drawing.
- Analysis is requested around 640×480, 4:3, with a `ViewPort` of `FILL_CENTER` shared with the preview. Phone alignment is still unchecked because the emulator returned no boxes.
- `tasks-vision` pulls in Google data transport, which merges `INTERNET` and `ACCESS_NETWORK_STATE`. Those two permissions are removed in the app manifest. Detection does not download a model or upload frames. Confirmed with `aapt dump permissions` on the 0.2.0 APK.
- Version bumped to 0.2.0 (`versionCode` 2) so the new APK is distinct from 0.1.0.
- A successful detection still does not steer the tricycle. `HoldNavigationPolicy` keeps speed at 0.

## 2026-10-18 — Phase 5 camera steering is directional only

**Decision:** Drive the virtual tricycle from `NavigationDecisionEngine` → `SteeringCommand` → `SimulatedTricycleController`. Remove `HoldNavigationPolicy`. Do not add a floor segmenter in this build.

**Why**

- A 2D box has a position in the image. It does not have a range. Turning that box into meters would be an invented clearance.
- An empty detector result is not open space. A box in the center of the image does not prove either side is free. Those cases return Hold. A missing frame, a contradictory sample, or a camera sample that claims metric depth returns Unknown.
- Straight is reserved for the synthetic map, and only when the authored list has no obstacle in the forward corridor. The map is `left barrel` at (1.0 m, 0.20 m) and `right crate` at (2.6 m, −0.40 m) in the start frame. Those coordinates are written in code. They are not measured by the camera.
- Decisions flicker if every frame can reverse the wheel. The smoother keeps Hold and Unknown immediate, waits 3 matching frames before a new direction, and waits 5 frames before swapping left and right.
- MediaPipe’s image segmenter would be a second model, a second camera path, and another accuracy question. It is not required to steer from boxes. Floor segmentation stays `NOT_STARTED` and disabled. No floor mask is treated as open floor.
- Version bumped to 0.3.0 (`versionCode` 3) so the APK is distinct from 0.2.0.

**Consequences**

- Positive front-wheel angle is still a left turn, matching the kinematic model. Full steer is 0.35 rad and slight steer is 0.15 rad, both inside the existing 30-degree clamp.
- Camera decision confidence is capped at 0.75. The spoken reason quotes the detector score separately and states that depth is not measured.
- The on-screen path is a short kinematic sketch of the current command. It is labeled “Illustrative path, not verified.”
- The physical controller remains a mock and still refuses commands.

