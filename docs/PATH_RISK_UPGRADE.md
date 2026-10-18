# Path risk upgrade (0.9.0)

## Problem
The live assistant said Hold on empty scenes, treated floor clutter as obstacles, and spoke too often.

## Change
- Added `PathRiskAnalyzer` with levels: `CLEAR_OBSERVED`, `POSSIBLE_OBSTACLE`, `BLOCKED`, `UNKNOWN`.
- Empty / off-corridor / ignored COCO clutter → `CLEAR_OBSERVED` + virtual `STRAIGHT` (not a safety certificate). Silent TTS.
- Strong corridor obstacles (person, chair, furniture) → `BLOCKED` with short Arabic/English alerts.
- Voice is silence-by-default; 5 s cooldown for non-critical repeats.
- Diagnostic strings logged as `TriGuideNav` / `decision.diagnostic`.
- Floor segmentation still `NOT_STARTED` (SegFormer not on device yet).

## Tests
`:app:testDebugUnitTest` after the change (see TEST_RESULTS / gradle output).

## SegFormer
Deferred on-device. DeepLab has no floor class. SegFormer-B0 ADE20K needs a proven mobile ONNX/LiteRT pipeline before bundling. Floor alone never means safe.
