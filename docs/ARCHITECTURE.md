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
| `ObjectDetectionEngine` | `MediaPipeObjectDetectorEngine` — EfficientDet-Lite0 int8, CPU, `LIVE_STREAM`. States: `LOADING`, `READY`, `MODEL_MISSING`, `LOAD_FAILED`. Boxes come only from the result listener |
| `EnvironmentObservation` | Real labels only after a successful frame. Otherwise `realDetector = false` and the list is empty |
| `SceneObservation` | Camera samples carry preview-space box centers and area fractions. `metricDepthAvailable` stays false. Synthetic samples carry authored robot-frame obstacles |
| `NavigationDecisionEngine` | Maps one sample to `GuidanceAction`: Straight, Slight left, Left, Slight right, Right, Hold, Unknown. Each decision has a reason and a confidence. Straight is not emitted for camera samples |
| `TemporalDecisionSmoother` | Hold and Unknown apply immediately. A new direction needs 3 matching frames. A left/right reversal needs 5 |
| `GuidanceSession` | Separate smoothers for the camera and the synthetic map. Publishes the decision that is currently applied |
| `SteeringCommand` | Speed (m/s) and front-wheel angle (radians). Positive angle is left |
| `SyntheticObstacleWorld` | Authored obstacles: left barrel (1.0 m, 0.20 m), right crate (2.6 m, −0.40 m) |
| `Floor segmentation` | `NOT_STARTED`. Disabled. No segmenter is loaded |
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

Home, Live Vision, Robot Simulation, Test Scenarios (labels only, status `NOT_STARTED`), Diagnostics, Settings.

Live Vision applies camera observations to the virtual robot while the preview is open. The simulator can run manual buttons, the same camera path (it holds when the preview is closed), or the synthetic map. Both screens show the decision, the reason, the confidence, the front-wheel angle, the research-mode line, and the non-verified path label. Those two screens also ask the voice gate once per control tick. Settings stores speech, rate, quiet mode, and language.

`minSdk` 26, `compileSdk` / `targetSdk` 35. JDK 17. Chosen because platforms 34 and 35 are installed on this PC and MediaPipe Tasks samples support API 24+, so 26 stays inside that range.
