# Upstream perception integration ledger (Phase 0)

**Date:** 2026-10-19  
**Scope:** Phase 0–3 (audit, diagnostic seam, candidate ledger, fusion gate).  
**APK assets:** still no floor-segmenter weights. Hash-only copies may exist under `research/model-candidates/` (gitignored).

---

## Files expected to change (Phase 0–1)

| Path | Role |
| --- | --- |
| `docs/UPSTREAM_PERCEPTION_INTEGRATION.md` | This ledger |
| `docs/OPEN_SOURCE_LICENSES.md` | Upstream license rows |
| `docs/BRIDGE_TO_GPT.md` | Phase report |
| `docs/PROGRESS.md` | Short status |
| `android-app/app/src/main/java/com/triguide/ai/domain/FloorSegmenterModelContract.kt` | Model contract (no weights) |
| `android-app/app/src/main/java/com/triguide/ai/domain/FloorSegmenterResult.kt` | Typed diagnostic results |
| `android-app/app/src/main/java/com/triguide/ai/domain/FloorSegmenterDiagnostic.kt` | Pure adapter → FloorMaskEvidence / replay |
| `android-app/app/src/main/java/com/triguide/ai/vision/FloorSegmenterCameraAdapter.kt` | Shared CameraX analysis hook (drop frames; no second camera) |
| `android-app/app/src/test/kotlin/com/triguide/ai/domain/FloorSegmenterDiagnosticTest.kt` | JVM fixtures |
| `android-app/app/src/main/java/com/triguide/ai/ui/TriGuideRoot.kt` | AppServices + analysis fan-out only |
| `android-app/app/src/main/java/com/triguide/ai/domain/NavigationModels.kt` | FloorSegmentationStatus text update |
| `android-app/app/build.gradle.kts` | versionName for assembleDebug tracking |

## Untouched safety-critical files (Phase 1)

- `NavigationDecisionEngine.kt`
- `PathRiskAnalyzer.kt` (policy)
- `GuidanceSession.kt`
- `VoiceGuidance.kt` / `VoiceGuidanceGate`
- `EmergencyStopBar.kt` / emergency-stop hold path
- Bundled EfficientDet asset and detector scoring thresholds
- `AndroidManifest.xml` network removals

---

## Upstream sources

### 1. Sailens Android

| Field | Value |
| --- | --- |
| URL | https://github.com/wnbotoo/sailens-android |
| Commit inspected | `7d6be56036663c6621f4c50585fe1fa1e2ae9bcc` (main tip at audit) |
| License | Apache-2.0 |
| Use in TriGuide | **Architecture ideas only** (separate semantic vs detection, model contracts, SHA-256 provenance, per-model scheduling, freshness/TTL, replayable traces, cross-check before movement). |
| Code copied? | **No** — not vendored |
| Model/dataset weights? | Not imported; any Sailens-bundled weights require separate license/SHA review before use |
| Redistribution of ideas | Allowed as original TriGuide code |
| Android impact | Design influence on diagnostic contracts only |

### 2. Google MediaPipe Samples

| Field | Value |
| --- | --- |
| URL | https://github.com/google-ai-edge/mediapipe-samples |
| Commit inspected | `cb208b3c5e6ef1586a23dee940f2413c6c97a510` (main tip at audit) |
| License | Apache-2.0 |
| Selected design | Android **Image Segmenter** LIVE_STREAM pattern under `examples/image_segmentation/android/` (`confidence_mask` / `category_mask`): async result listener, confidence/category masks, CPU/GPU delegate, init/error handling, frame timestamps. Official guide: https://developers.google.com/edge/mediapipe/solutions/vision/image_segmenter/android |
| Code copied? | **No** — pattern adapted into TriGuide-owned diagnostic types; MediaPipe Tasks Vision already a Maven dependency |
| Model weights? | **Not downloaded**. Sample DeepLab/selfie models are **not** assumed indoor-floor-safe and are not bundled |
| Redistribution | Sample code not vendored; Tasks library remains Maven Apache-2.0 |
| Android impact | Future LIVE_STREAM segmenter may reuse Tasks API; Phase 1 stays no-model |

### 3. PaddleSeg / PP-LiteSeg

| Field | Value |
| --- | --- |
| URL | https://github.com/PaddlePaddle/PaddleSeg |
| Branch/commit | `release/2.10` tip `3c4db66de1d9d59d0628ed87590b6308a2f4aa2a` |
| License (code) | Apache-2.0 |
| Use | **Candidate model/training/export source only** (Phase 2+) |
| Code copied? | **No** |
| Weight / dataset license | **Unclear for redistribution in APK** until a specific checkpoint URL, training-data terms, conversion recipe, and SHA-256 are recorded. Cityscapes/ADE20K-style labels are **not** indoor walkable clearance. |
| Reported FPS | Desktop/Snapdragon marketing numbers must **never** be presented as Redmi FPS |
| Phase 1 action | Document only; **do not download** |

### 4. BlindAssist (research only)

| Field | Value |
| --- | --- |
| URL | https://github.com/violetljj/blind-assist |
| License | **AGPL-3.0** |
| Use | Evaluation ideas only |
| Code copied? | **Forbidden — none** |

### 5. Explicitly excluded

| Source | Reason |
| --- | --- |
| GuideLens | GPL-3.0 — research tree only |
| Ultralytics / YOLO weights | Commonly AGPL; not imported |
| DeepLab ADE20K zoo checkpoint | Weight redistribution vs ADE20K image terms still uncleared (`OPEN_SOURCE_LICENSES.md`) |

---

## Class mapping status (Phase 1)

**No segmentation class is mapped to a walkable or indoor-floor region.**

Generic Cityscapes `road`/`sidewalk` or ADE20K `floor` labels must not be treated as safe indoor walking evidence. Until an explicit class-mapping document is approved with measured Redmi clips, any confidence channel in fixtures remains a **diagnostic float field** only.

---

## Phase 1 runtime posture

- Single CameraX analysis stream (existing `CameraXSource`).
- Async dropped-frame diagnostic offer path when a future model exists.
- **No model asset** → status `NO_MODEL` / FloorMaskEvidence `UNAVAILABLE`.
- Results feed `FloorMaskEvidenceEvaluator` + `EvidenceReplayHarness` diagnostics only.
- **No** NavigationDecisionEngine / VoiceGuidance / TTS / movement coupling.

---

## License audit gate

Phase 0 **passes** for **code-pattern references** (Apache-2.0 Sailens ideas, MediaPipe samples pattern, PaddleSeg code license noted).  
Phase 0 **blocks weight download**: PaddleSeg/MediaPipe sample/DeepLab checkpoint redistribution for TriGuide APK is **not cleared**. Stop before Phase 2 weight fetch until a specific file has URL + SHA-256 + weight license + dataset license recorded here and in `OPEN_SOURCE_LICENSES.md`.
