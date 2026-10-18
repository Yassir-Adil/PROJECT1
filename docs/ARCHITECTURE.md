# Architecture

Android project: `android-app/`.

| Module | Role |
| --- | --- |
| `:app` | Compose UI, CameraX, wiring |
| `:sim-core` | Pure Kotlin tricycle kinematics and JVM unit tests |

Package `com.triguide.ai`.

## Interfaces

| Type | Phase 1–2 behavior |
| --- | --- |
| `CameraSource` | `CameraXSource` binds preview and analysis. Analysis is RGBA, 4:3, about 640×480, `STRATEGY_KEEP_ONLY_LATEST`, same `FILL_CENTER` viewport as the preview |
| `ObjectDetectionEngine` | `MediaPipeObjectDetectorEngine` — EfficientDet-Lite0 int8, CPU, `LIVE_STREAM`. The analysis crop is applied, then the frame is rotated upright. States: `LOADING`, `READY`, `MODEL_MISSING`, `LOAD_FAILED`. Boxes come only from the result listener. Scores under 0.35 are not drawn |
| `EnvironmentObservation` | Real labels only after a successful frame. Otherwise `realDetector = false` and the list is empty |
| `SceneObservation` | Camera samples carry preview-space box centers and area fractions. `metricDepthAvailable` stays false. Synthetic samples carry authored robot-frame obstacles |
| `NavigationDecisionEngine` | Maps one sample to `GuidanceAction`: Straight, Slight left, Left, Slight right, Right, Hold, Unknown. Each decision has a reason and a confidence. Straight is not emitted for camera samples |
| `TemporalDecisionSmoother` | Hold and Unknown apply immediately. A new direction needs 3 matching frames. A left/right reversal needs 5 |
| `GuidanceSession` | Separate smoothers for the camera and the synthetic map. Publishes the decision that is currently applied |
| `SteeringCommand` | Speed (m/s) and front-wheel angle (radians). Positive angle is left |
| `SyntheticObstacleWorld` | Authored obstacles: left barrel (1.0 m, 0.20 m), right crate (2.6 m, −0.40 m) |
| `Floor segmentation` | `NOT_STARTED`. Disabled. DeepLab-v3 was inspected and has no floor class, so it is not loaded. No mask is drawn |
| `RobotState` | Pose of the rear-axle midpoint plus the applied command |
| `VirtualRobotController` | `SimulatedTricycleController` steps `TricycleKinematics` |
| `FutureHardwareController` | `MockHardwareController` — `isMock = true`, `dispatch` returns `accepted = false` |
| `PerformanceMonitor` | Preview FPS from analysis callbacks, plus inference latency, rolling average, and inference FPS from completed results only |
| `VoiceGuidanceGate` | Pure policy: enable, quiet mode, dedupe key, cooldown, minimum gap, and interrupt. No synthesizer |
| `VoiceCues` | Maps a committed decision to one of five short phrases, in English and Arabic. The smoother’s warmup hold is silent |
| `AndroidVoiceSpeaker` | Android `TextToSpeech`. Offline voices only. `QUEUE_FLUSH` when a critical cue interrupts |
| `VoiceSettingsStore` | `SharedPreferences` `triguide_voice`: speech on/off, quiet mode, rate, language preference |

## Virtual chassis

Bicycle model of a tricycle: two fixed rear wheels (track width ignored) and one steered front wheel. Wheelbase default 0.35 m. Explicit Euler integration. Positive steering increases heading while moving forward (left, counter-clockwise from +x). Screen drawing flips the heading so the arrow matches that math frame.

## Screens

Live Vision is the start screen. More, Robot Simulation, Test Scenarios (three synthetic runs), Diagnostics (including a text share), and Settings are reached from More.

Opening the app enters Live Vision, binds the rear camera (`STRATEGY_KEEP_ONLY_LATEST`), and speaks the research disclaimer once per process, then “Camera started. Guidance on.” A missing camera permission is spoken and the system dialog is requested immediately. If Android will not show that dialog again, the app opens TriGuide’s settings page and says so.

The fingerprint reader on the project Redmi is the power button. This app does not receive those presses. Live Vision counts three quick taps (750 ms between taps) without consuming the pointer. That gesture restarts the camera and speaks “Camera started. Guidance on.” A single tap on More still opens the other screens.

Test Scenarios run `GuidanceScenarios` through `NavigationDecisionEngine` on three authored maps. Diagnostics builds a plain-text `DiagnosticsReport` and shares it. Floor segmentation stays `NOT_STARTED`. No segmenter is loaded. Describe this frame speaks the latest real boxes once and does not steer. Camera-informed simulation binds the rear camera on that screen.

Live Vision applies camera observations to the virtual robot while the preview is open. It shows the decision, the reason, the front wheel, the camera source, and that Hold and Unknown stop the virtual robot. The simulator can run manual buttons, the same camera path (it holds when the preview is closed), or the synthetic map. The simulator labels itself as simulated, shows confidence, and shows the non-verified path label. Those two screens also ask the voice gate once per control tick. Settings stores speech, rate, quiet mode, and language.

`minSdk` 26, `compileSdk` / `targetSdk` 35. JDK 17. Chosen because platforms 34 and 35 are installed on this PC and MediaPipe Tasks samples support API 24+, so 26 stays inside that range.
