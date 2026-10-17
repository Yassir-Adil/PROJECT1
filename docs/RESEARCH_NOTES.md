# Research Notes — Reusable Projects

Last updated: 2026-10-18

## OpenBot (`ob-f/OpenBot`)

- Smartphone as robot brain; Android robot + controller apps; TFLite person following / navigation policies.
- **Useful for TriGuide:** camera + control loop patterns, logging, simulated control before USB body.
- **Defer:** Arduino body, physical wheels — out of scope until user asks.
- License: MIT (verify at use time).

## GuideLens / WorldLens

- Hackathon-style assistants: YOLO detection, hazard approach estimation, OCR, voice navigation.
- Often **cloud-dependent** (Gemini, WebRTC). Good UX and hazard-priority ideas; **not** a drop-in offline APK core.
- Reuse: event priorities, cooldown announcements, left/center/right + near/far framing.

## MediaPipe

- Preferred first choice for on-device segmentation / detection / landmarks on Android.
- Aligns with “phone is compute” and offline-friendly goals.

## Project Guideline (Google Research)

- On-device guidance for blind/low-vision runners along a painted line; MediaPipe + ARCore; open-sourced.
- Strong reference for guidance systems and depth/occupancy thinking.
- Constraints: Pixel-oriented in original docs; complex C++ engine — adopt selectively.

## VisionAId / ObstacleAssist / DrishtiAI (and peers)

- Scan for on-device YOLO/ONNX/TFLite Android samples with accessibility feedback.
- Always check license, model weights terms, and whether code is maintainable before vendoring.

## Selection stance (pending Phase 1–3 experiments)

1. Build our own Kotlin app shell.
2. Vision: MediaPipe Tasks first; fall back to TFLite YOLO-style if needed.
3. Steering: internal three-wheel simulator inspired by OpenBot control semantics.
4. Avoid cloud-required vision as the default path.
