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
| `NavigationDecision` | `HoldNavigationPolicy` — speed 0. A detection does not steer |
| `SteeringCommand` | Speed (m/s) and front-wheel angle (radians) |
| `RobotState` | Pose of the rear-axle midpoint plus the applied command |
| `VirtualRobotController` | `SimulatedTricycleController` steps `TricycleKinematics` |
| `FutureHardwareController` | `MockHardwareController` — `isMock = true`, `dispatch` returns `accepted = false` |
| `PerformanceMonitor` | Preview FPS from analysis callbacks, plus inference latency, rolling average, and inference FPS from completed results only |

## Virtual chassis

Bicycle model of a tricycle: two fixed rear wheels (track width ignored) and one steered front wheel. Wheelbase default 0.35 m. Explicit Euler integration. Positive steering increases heading while moving forward (left, counter-clockwise from +x). Screen drawing flips the heading so the arrow matches that math frame.

## Screens

Home, Live Vision, Robot Simulation, Test Scenarios (labels only, status `NOT_STARTED`), Diagnostics, Settings.

`minSdk` 26, `compileSdk` / `targetSdk` 35. JDK 17. Chosen because platforms 34 and 35 are installed on this PC and MediaPipe Tasks samples support API 24+, so 26 stays inside that range.
