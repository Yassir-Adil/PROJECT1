# Path risk upgrade (0.9.0; evidence-contract correction R0)

## Problem
The live assistant said Hold on empty scenes, treated floor clutter as obstacles, and spoke too often.

## Change
- Added `PathRiskAnalyzer` with levels: `CLEAR_OBSERVED`, `POSSIBLE_OBSTACLE`, `BLOCKED`, `UNKNOWN`.
- Original 0.9.0 behavior: empty / off-corridor / ignored COCO clutter → `CLEAR_OBSERVED` + virtual `STRAIGHT`. Object detection alone cannot establish walkable space, so this interpretation was incorrect.
- R0 correction: empty, ignored-only, and off-corridor-only camera detections → `UNKNOWN` + virtual stop. Any detected corridor obstacle → `BLOCKED` + `HOLD`; a detector box on one side cannot prove a walkable gap on the other. Camera boxes alone no longer issue a steering direction. `CLEAR_OBSERVED` is reserved for a future positive walkable-area source. Unknown remains silent under the current voice gate unless the camera itself is unavailable.
- Strong corridor obstacles (person, chair, furniture) → `BLOCKED` with short Arabic/English alerts.
- Voice is silence-by-default; 5 s cooldown for non-critical repeats.
- Diagnostic strings logged as `TriGuideNav` / `decision.diagnostic`.
- Floor segmentation still `NOT_STARTED` (SegFormer not on device yet).

## Tests
`:app:testDebugUnitTest` after the change (see TEST_RESULTS / gradle output).

## SegFormer
Deferred on-device. DeepLab has no floor class. SegFormer-B0 ADE20K needs a proven mobile ONNX/LiteRT pipeline before bundling. Floor alone never means safe.

## R0 implementation status — 2026-10-18

The source and existing expected-result assertions were updated for the corrected contract, including removing camera-box-only side steering. Build, automated tests, and Redmi behavior have not been verified for this change yet.
