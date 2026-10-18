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

## 2026-10-18 — Phase 6 voice is a short cue, not a frame narration

**Decision:** Speak five short cues through Android `TextToSpeech`, gated by `VoiceGuidanceGate`. Do not speak the long on-screen reason on every camera frame. Do not add a floor segmenter.

**Why**

- The navigation loop already runs about every 50 ms. Reading the full reason each time would talk over itself and would change whenever a timestamp or a detector score changes.
- Each cue has a stable key: obstacle, one turn phrase, uncertain, camera, or paused. The same key waits out a cooldown (6 s critical, 10 s uncertain, 12 s turn, 4 s paused). A different higher-priority cue can interrupt. Obstacle detected and camera unavailable are critical. Quiet mode allows only those two.
- The first frames of a new direction are the smoother’s vote (“Holding until the direction stays”). That vote is not a new situation, so it is silent. The committed action is what gets spoken.
- Arabic is a second phrase on the same cue, not a translation service. It is used only when the device has an offline Arabic voice. This APK still removes `INTERNET`. A voice that needs a download is ignored.
- Preferences are four fields in `SharedPreferences` (`triguide_voice`). They are read on the control tick. DataStore was not added for this set.
- The research-mode disclaimer stays on screen. Voice copy says the cue is not a safety proof. Version bumped to 0.4.0 (`versionCode` 4).

**Consequences**

- Speech rate is clamped to 0.6–1.6 and passed to `setSpeechRate`.
- A missing or still-starting engine does not retry every frame. The gate waits 5 s and leaves the text on screen.
- Simulation paused is spoken once when an assist mode stops, and it can be queued behind the current sentence. It does not cut off a turn.
- Floor segmentation remains `NOT_STARTED` and disabled.

## 2026-10-18 — Phone demo layout 0.4.1, cable ADB stays the install path

**Decision:** Keep inference, the model, and the voice gate as they are. Change only the phone screens so a demo can see the decision, the reason, and the wheel without scrolling past them, and keep cable ADB as the install path.

**Why**

- On the Redmi, the 0.4.0 Live Vision dump placed the camera preview near the bottom of a 2400 px screen, under a long readout. The chair log happened while that preview was mostly off screen.
- HyperOS can cancel `adb install` with `INSTALL_FAILED_USER_RESTRICTED` even after an earlier install succeeded. A cable retry worked. Wireless ADB was not used. It does not make detection or speech faster, and it is a worse first-install path.
- `adb shell input` is denied until USB debugging (Security settings) is on. The app cannot turn that on. The user taps the phone, or enables that developer switch.

**Consequences**

- Version 0.4.1 (`versionCode` 5). System Back returns Home. A camera denial that will not show the dialog again opens app settings. The detector line says READY only when the engine is ready.
- No new model, no depth, no network permission.

## 2026-10-18 — Screen triple-tap is the guidance shortcut

**Decision:** Keep the fingerprint button out of the app. Restore a screen triple-tap on Live Vision as the way to restart guidance. Do not add a volume-key shortcut. Do not load a floor model in this build.

**Why**

- The Redmi reader is still the power button. That limitation is unchanged and is spoken once on a cold start.
- A blind user needs a control that does not depend on finding More. Three quick taps anywhere on Live Vision are available without a new permission.
- The detector counts taps the UI already classified as taps. It does not consume the pointer, so a scroll and a single button press still go to the existing controls.
- MediaPipe Image Segmenter was not added. A floor mask that is not actually running would look like a free floor. The label stays `NOT_STARTED`.

**Consequences**

- Version 0.6.0 (`versionCode` 8). The gap between taps is 750 ms, inside the requested 600–800 ms window. Three taps restart the camera and speak “Camera started. Guidance on.” in the current voice language.
- Test Scenarios run the navigation engine on three authored maps. Diagnostics can share a plain-text status dump. Neither one is an indoor evaluation.

## 2026-10-18 — Boxes follow the preview crop; floor stays off

**Decision:** Crop the analysis buffer to the CameraX crop, then rotate it upright, before EfficientDet runs. Draw and steer from scores at or above 0.35. Let the model return scores from 0.25 so weaker hits can be logged and then dropped. Do not bundle DeepLab-v3. Floor stays `NOT_STARTED`.

**Why**

- On the Redmi the analysis buffer is 640×480 and the viewport crop is not the full buffer (`134,0,372x480` in the 0.7.0 log). Detecting on the uncropped buffer and then center-cropping the overlay does not match that crop. Cropping first puts boxes in the same image the preview shows.
- A 0.50 floor hid real scores just under it. In this smoke a bottle at 0.48 and a laptop at 0.45 were drawn. Scores under 0.35, such as a couch at 0.34, were logged and not drawn. The model still mislabels (frisbee, tv). Those are model outputs, not boxes the app invents. Empty frames stay Hold.
- DeepLab-v3 float32 from `https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite` is 2,780,176 bytes, Apache-2.0, and its embedded labels are Pascal classes. There is no floor class. The int8 URL returns HTTP 404. Shipping that model and painting background as floor would be a false floor. It was not copied into assets.
- Describe this frame reads the latest real boxes and speaks once, outside the 50 ms loop. It does not say the path is clear and it does not read a floor.
- Camera-informed simulation opens the rear camera on that screen, because leaving Live Vision used to stop the only camera and the mode then had no frame.

**Consequences**

- Version 0.7.0 (`versionCode` 9). Guidance uses the same 0.35 floor as the overlay.
- No floor mask. A missing mask is not open floor and is not safe.

## 2026-10-18 — Fingerprint taps are not available to this app

**Decision:** Do not ship a fingerprint triple-tap, a screen-wide triple-tap, or a volume-key shortcut. Opening TriGuide enters Live Vision and starts the camera and voice. Say once, in speech and on screen, that the fingerprint control cannot be used.

**Why this phone cannot do it**

- The connected phone is `55aqpfqkvw9xbafe`, model `22101316G`, market name Redmi Note 12 Pro 5G, HyperOS `OS2.0.16.0.UMOMIXM`. `adb shell dumpsys fingerprint` reports `isUdfps: false` and `isPowerbuttonFps: true`. The reader is in the power button. It is not under the display. `ro.hardware.fp.sideCap` is `true`. The vendor node is Goodix (`uinput-goodix`, `persist.vendor.sys.fp.vendor=goodix`).
- `uinput-goodix` can emit ordinary keys (`KEY_POWER`, volume, directions, `KEY_CAMERA`, and others). Those are vendor gesture injections, not a tap counter. With the screen on, Android’s window policy consumes `KEY_POWER` before a normal app sees it. A short press sleeps the phone. An app cannot count three of those presses.
- `settings` value `fingerprint_double_tap` is `none`. That is a Xiaomi system action (camera, notifications, and similar). It is not a callback an app can register.
- `BiometricPrompt` and the deprecated `FingerprintManager` authenticate an enrolled finger. They do not report “tapped three times,” and a failed match can lock the sensor. `FingerprintGestureController` (an accessibility API) reports swipes only, and only if a service is enabled and the HAL says gestures exist. This power-button reader does not expose that to a third-party app.
- No public HyperOS intent was found that delivers fingerprint-area touches to a normal app while it is in front.

**What ships instead**

- Version 0.5.1 (`versionCode` 7). Cold start opens Live Vision. The research disclaimer is spoken once per process, then “Camera started. Guidance on.”, then one sentence that the fingerprint button is not given to apps.
- If the camera permission dialog cannot be shown again, the app speaks and opens TriGuide’s system settings. More still opens the simulator, settings, and diagnostics.
- Screen triple-tap and the volume-key double-press from the earlier 0.5.0 experiment are not in this APK. 0.5.0 was installed earlier the same morning and is superseded.

