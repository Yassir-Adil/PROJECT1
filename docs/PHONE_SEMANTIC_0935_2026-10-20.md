# Actual Redmi semantic/depth diagnostic 0.9.35 — 2026-10-20

## Outcome

Owner-confirmed stationary scene; the originally labelled 0.9.34 request was
explicitly updated to installed **0.9.35/48** before launch. One 20-second
AUTOMATIC + semantic_fusion=true diagnostic completed (20.18s), all three
resource-closure markers were observed, and scoped force-stop left no app PID.
No navigation, audio, image/coordinate recording, network permission or upload.

**Owner clarification after explaining the grid: the intended coloured cells
did NOT appear.** The earlier ambiguous answer said colours appeared before the
owner understood the question; retain that history, not a positive visual pass.
Current visual feedback agrees with one publication and zero Canvas submissions.
Why the View did not submit coloured drawing is still unresolved; there is no
screenshot/frame recording. No claim the old issue is fixed. Updated diagnosis
and staged plan: DISPLAY_RECOVERY_PLAN_2026-10-20.md.

## Reproducible evidence

- Source commit at launch: `6112d186799e177d10e9de49efa26ffaaff01cf7`.
- Installed version and base APK were independently rechecked after the run.
  Installed/staged debug SHA256:
  `3f451b6d21b823bcf4a4b8f636f7d5cbf96911382ad5ad89a5c63dad1667ea04`.
- Private, ignored aggregate-only log:
  `outputs/reports/semantic-0935-phone-20261020-210033/phone.log`.
  SHA256 `d4c843badcc3343372a8eedfa494b9997724829450e5bb1d9f40d3052b74c1cd`.
- PID-scoped, tag-filtered logs include RAW_DEPTH_RESULT COMPLETE,
  SEMANTIC_FUSION_CLOSED, CAMERA_BACKGROUND_RELEASED and RAW_DEPTH_CLOSED.
  No matching fatal/processing/capture failure marker in this scoped log; this
  is not a full-device crash audit.
- Last full engineering gate, NOT rerun during this read-only phone test:
  `outputs/reports/quality-gate/20261020T102847617148Z/summary.json` PASS811:
  694 app + 10 sim + 21 policy + 86 eval, zero failures/errors/skips,
  both APK builds/policies pass. This turn changes records, not executable code.

## Measurements

| Measurement | Actual observation | Limit |
| --- | --- | --- |
| Distinct camera frames / tracking / nontracking | 563 / 534 / 29 | Not model FPS or an accuracy measure |
| Decoded raw depth/confidence pairs | 143 | Includes reprojection; not 143 independent measurements |
| Raw admission | 1 | 96 depth_not_new, 46 no_valid_confident_pixels; tracking rejects counted separately |
| Pairs containing confident pixels | 97 | Does not establish distinct sensor evidence; only one admitted |
| Semantic results / stale / ground candidates | 1 / 0 / 0 | Extremely small sample, no throughput/accuracy conclusion |
| Exact ground rejection | too_few_geometry_samples, support=1 | Existing minimum is 6; thresholds unchanged |
| Model labels, coarse 8x8 | wall 33, floor 24, rug 0 | Argmax labels, not verified identities or confidence |
| Semantic grid raw world points / unmapped | 5 / 0 | Only 1 floor-component support; not ground coverage |
| Height unknown | 64 of 64 | No floor reference; no rug-height/obstacle proof |
| UI publication / fresh Canvas / actual paint submissions | 1 / 0 / 0 | Owner clarified the intended grid was not visible; exact failure cause unresolved |
| Cleared without fresh Canvas | 1 | No post-paint rejection recorded |
| Geometry invalidations | 3 | Sampled viewport 1173→1175→1178px during startup; not evidence of repeated semantic-driven reflow |
| Semantic receipt→result | 239.720308ms | p50=p90 because n=1; not a latency distribution |
| GL capture / worker prepare including queue / model invoke | 3.727616 / 146.619769 / 34.588846ms | Each n=1; timings do not account for every decode/evaluation step |
| SDK session.update p50 / p90 | 1.53 / 2.99ms | Diagnostic sampled timing |
| Raw summary p50 / p90 | 2.35 / 2.35ms | n=1 admitted sample |
| SDK plane inventory | 0 vertical; 12/19 upward-height samples | Inventory is not visibility or wall/floor identity |
| SDK pose max displacement | 0.060929571m | Uncalibrated SDK estimate, not verified physical movement |
| End-process PSS / RSS | 141075 / 276924KiB | Endpoint, not peak, leak or comparative memory proof |
| Battery / temperature | 87% charging; 28.9→30.1°C | Short charging run, not energy/thermal benchmark |

The separate legacy 3x3 projection probe recorded 1 sample with 0 finite world
points; it has a different grid from the semantic 8x8 probe. Do not merge these
counts or call them inconsistent evidence.

## Interpretation and next two priorities

1. **Rendering attribution / cold-start budget:** 239.72ms processing leaves
   only ~10.28ms of the original 250ms receipt-age allowance even before UI
   delivery. This is a plausible missed-draw mechanism, not demonstrated by an
   exact first-draw rejection timestamp. Preserve freshness; measure separately
   worker→UI→first-draw rejection, and distinguish semantic colours from other
   probes before changing appearance or declaring visibility fixed.
2. **Sparse raw support:** do not force a ground reference from semantic floor
   labels, repeated reprojected depth or a single point. Before another owner
   camera request, replay low-support/stale/public labelled fixtures and assess
   whether diagnostic-only 2D labels can be observed independently of sparse
   depth, while geometric ground fusion still requires original same-frame raw
   admission. This is a proposed next step, NOT implemented in this turn.

No causal speed, recognition or memory comparison with 0.9.33 is warranted:
scene, motion, startup and available distinct raw evidence were not controlled.
No model replacement, threshold reduction, stale overlay extension, CLEAR,
turn instruction or eyes-closed walking claim. The app was stopped; no further
camera run is scheduled or needs the owner to hold the phone now.
