# Model audit — GuideLens assets

Date: 2026-10-18. Checked the local clone, not a release APK.

## Files that are actually in the clone

| File | Bytes | Header |
| --- | --- | --- |
| `app/src/main/assets/yolov8s-worldv2_int8.onnx` | 13,522,706 | protobuf field text `onnx.quantiz` (`08 08 12 0D 6F 6E 6E 78...`) |
| `app/src/main/assets/floor_segmentation_int8.onnx` | 12,454,891 | protobuf field text `onnx.quantiz` (`08 07 12 0D 6F 6E 6E 78...`) |

These are not Git LFS pointer files. `.gitattributes` is absent. Both are large binary ONNX protobufs.

## Loaders

- `ObjectDetector.kt` reads `yolov8s-worldv2_int8.onnx` from assets and calls `OrtSession` (`ai.onnxruntime`).
- `ONNXFloorSegmenter.kt` reads `floor_segmentation_int8.onnx` the same way.
- `FloorSegmenter.kt` can fall back to `createMockFloorMask` if the session is not ready. That path is labeled mock inside GuideLens. TriGuide does not use it.

## What was not done

- GuideLens `assembleDebug` was **not** run.
- The ONNX sessions were **not** executed, so load success on a phone is **not** claimed.
- No separate hunt was required to find the two filenames: they are already in the upstream git tree. Copying them into TriGuide is still blocked (below).

## Why TriGuide does not vendor these weights

1. The GuideLens repository is GPL-3.0. TriGuide will not copy that code or those assets into the shipping app.
2. The detector name is YOLOv8-family. Ultralytics YOLO weights are typically AGPL-3.0. That was not re-cleared, so the files stay in the local research clone only.
3. Medicine identification in the same app is online (Gemini server / OpenAI key / Google fallback), which conflicts with a default offline camera path.
4. This machine does not have Android platform `android-36`. GuideLens sets `compileSdk = 36`, so a build was not attempted.

## Later detection plan

Use MediaPipe Tasks (Apache-2.0 samples). Their object-detection Gradle script downloads EfficientDet / MobileNet SSD files from Google storage at build time. That integration is not in the Phase 1 APK. The Phase 1 engine status is `NOT_IMPLEMENTED` and it returns no detections.
