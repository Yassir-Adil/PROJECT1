# Positive path evidence: next phone-first spike (2026-10-18)

The 0.9.11 screen and voice remain fail-closed. This is a research/prototype
plan, **not** permission to say walk, continue, turn, or several paths.

## 0.9.13 checkpoint

A pure `FloorMaskEvidenceEvaluator` and 15 JVM tests now cover the typed
diagnostic input, freshness/identity validation, and a four-connected
floor-like component from the bottom-center mask cell. It is intentionally
unwired: no mask model is bundled, no live camera mask is produced, no replay of
labeled Redmi clips exists yet, and no navigation/TTS/virtual-motion consumer
uses its result. The reported connected fraction is a software diagnostic,
**not** a walkable-area or safe-passage confidence. Cursor found no High/Medium
source defect; a missing even-width seed test was added. Actual Redmi
measurements and mask-producer mapping checks remain open.

## Why this is the next slice

Object boxes can identify some hazards but cannot show free floor, wall edges,
door passages, drops, or branches. A relative-depth map alone is not a semantic
floor mask or a measured safe route. No currently reviewed model has been
validated as an indoor navigation solution on the owner's Redmi.

## Smallest implementation

1. Add a diagnostic-only typed `WalkableAreaObservation` with source model/hash,
   distinct monotonic frame ID/timestamp, image-to-mask transform, mask size,
   floor confidence, near-field connected-region result, excluded obstacle
   regions, and explicit `UNAVAILABLE`, `STALE`, `INVALID` states. It must not
   feed movement decisions or TTS yet.
2. Replay labeled, held-out phone clips through a pure offline evaluator before
   adding a live model. Include open corridor, closed door/wall, rugs/shadows,
   stairs/drop-offs, occlusion, low light, motion blur, and apparent versus real
   branches. Count false-positive walkable regions and false branches, not just
   aggregate segmentation accuracy.
3. For a live prototype, reuse the single CameraX stream with an asynchronous
   dropped-frame segmentation path; do not add another camera owner. MediaPipe
   Image Segmenter supports Android live-stream results and confidence masks,
   but it requires a compatible trained model. Its sample is an API reference,
   not an indoor-floor accuracy result.
4. Evaluate legal model weights and mobile format **before** bundling. A
   MobileNetV2/DeepLabV3 ADE20K checkpoint is only a historical indoor-class
   baseline; PP-LiteSeg published lightweight examples are primarily outdoor
   datasets. Conversion, redistribution rights, class precision, Redmi latency,
   memory, thermal behavior, and phone audio effect remain unverified.
5. After 30 s warm-up, benchmark at least 300 distinct inferences on the Redmi:
   median/p90 inference and end-to-end latency, preview/detector FPS, dropped
   frames, accelerator/fallback, memory, and ten-minute thermal/battery. No
   movement phrases until positive corridor/branch evidence and risk fusion
   pass a separate review; any missing/stale/contradictory evidence remains
   `UNKNOWN`/`HOLD`.

## Primary references

- Google MediaPipe Android Image Segmenter guide and official sample:
  https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter/android
- OpenMMLab MobileNetV2 model metadata (candidate baseline only):
  https://github.com/open-mmlab/mmsegmentation/blob/main/configs/mobilenet_v2/metafile.yaml
- PaddleSeg PP-LiteSeg architecture/examples (candidate baseline only):
  https://github.com/PaddlePaddle/PaddleSeg/blob/release/2.10/configs/pp_liteseg/README.md
- Google ARCore Raw Depth limitations/optional later experiment:
  https://developers.google.com/ar/develop/java/depth/raw-depth
