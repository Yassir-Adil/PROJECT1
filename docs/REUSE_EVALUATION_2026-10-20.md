# Reuse + failure-focused evidence — 0.9.29/42

## Implemented this round

Baseline5b4986a,0.9.28/41. Bounded Cursor P/E wrote tests before source;
Codex independently recorded RED, released implementation, reviewed source,
integrated the SDK/evaluator and ran focused tests. Cursor R independently reviews
the landed source. Unrelated dirty owner voice-UX rule is preserved, never staged.

1. **Known planes versus four ray hits:** `SurfaceInventoryPolicy` summarizes at
   most256 primitive SDK plane candidates. Camera not tracking means no enumeration
   and no positive counts. Subsumed planes are excluded before tracking/orientation
   counts; PAUSED/STOPPED are not positive planes. Overflow is explicit `bounded`,
   not a complete inventory claim. SDK adapter maps lazily from
   `Session.getAllTrackables(Plane.class)` on the existing ARCore GL thread,1Hz
   alongside polygon-checked rays. Native enumeration itself remains SDK-owned.
   `SURFACE_INVENTORY` and final inventory-sample counters distinguish session-known
   orientations from current intersections. Session-known does NOT mean visible,
   newly detected, a semantic wall/floor, or a clear corridor.
2. **Failure-focused public evaluation:** existing LiteRT replay now also retains
   a151×32 ground-truth/prediction matrix, including ADE150 classes omitted by
   Top31 mIoU. Reports include per-class floor confusion, labelled floor precision/
   recall, unlabeled counts and helper SHA. TSV adds wall/rug/non-floor counts.
   GT0/padding is NOT negative evidence. Rugs are not automatically hazards or safe.
   Strict shape/integer/range/nonnegative/overflow validation; no input mutation.
   Synthetic export regression proves JSON/TSV preserve stair/rug rows and refuse
   overwriting a report. Existing Top31 metrics remain separately reported.

No primary camera/navigation/voice, model weights, dependency, Internet permission,
cloud, recording, paid service or confidence threshold changed. Diagnostics are
debug-only; release remains isolated by the final APK gate.

## Actual tests before implementation

- `risk-red.log`: unittest import failed with missing `semantic_risk_metrics.py`,
  exit1 BEFORE helper source existed. P/E helpers were not silently prewritten.
- `inventory-red.log`: Gradle compile failed with unresolved
  `SurfacePlaneTracking` / `SurfacePlaneCandidate` / policy/snapshot, exit1.
- Focused GREEN:16 inventory JVM tests; Python27 tests (10 existing+16 risk+1
  evaluator-export integration), zero failures/errors. SDK adapter compiled.
- Ignored evidence root: `outputs/reports/reuse-20261020/`. Worker reports:
  CURSOR_INVENTORY_2026-10-20.md, CURSOR_RISK_METRICS_2026-10-20.md,
  CURSOR_REUSE_REVIEW_2026-10-20.md. Full gate/phone disposition appended below.

## Actual pretrained inference on public images

Reused existing educational ADE20K validation dataset, no redownload/training.
Three sequential host-CPU runs, each256 evenly spaced same image/mask pairs out
of2000; reference-derived512 resize/pad and pinned model hashes unchanged.
One model evaluated at a time,4 threads, two warmup invocations, no concurrent
Gradle during inference. Per-image invoke-only timing excludes preprocessing and
scoring. This is **not Redmi/live-camera FPS or a certified MLPerf result**.

Selection digest (all3 equal):
`0157d45f938aeeefbf3334f5c36f7277cff018de93d909bdc22b66a34531a5a2`.
Evaluator SHA:
`84d377f6d6e6a2eab7742152e615d50ca0feb9c818220cb59614657276c3c3a3`.
Each summary pins model/helper/script and all image/mask hashes in TSV; raw counts
are retained. Selection includes23 stair-containing scenes/238245 resized stair
pixels,8039717 wall pixels and189248 rug pixels. This is diagnostic validation,
not a scene-balanced, untouched safety holdout or a new2000-image evaluation.

| Model | Top31 mIoU | Floor precision, labelled ADE150 | Floor recall | Wall→floor, wall pixels | Stair-related→floor, stair pixels | Rug→floor, rug pixels | Host invoke p50/p90 ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| MOSAIC uint8 |57.10%|72.31%|89.04%|0.681%|49.18%|28.80%|38.80 /42.31|
| DeepLab uint8 |55.08%|69.72%|84.82%|1.556%|42.14%|31.91%|68.54 /74.56|
| DeepLab float |54.85%|74.85%|85.17%|1.003%|24.64%|27.41%|122.81 /133.12|

Counts behind labelled floor precision: MOSAIC1012863/3657942 predicted-floor
pixels have a different labelled semantic GT; uint81094386/3613999;
float849967/3380027. Those are **semantic mismatches, not hazard probabilities**:
e.g. rug/road may be physically traversable, but no height/clearance label exists.
Floor precision excludes GT0; floor recall denominator is actual labelled floor.
Wall→floor and stair→floor have different class-specific denominators. The
stair-related aggregate covers official IDs54/60/97/122, not all steps encountered
by a person or specifically descending stairs. Pixel averages can be dominated by
large regions. Do not generalize this sample's49.18% to all Redmi scenes; prior
full2000-image runs in ADE20K_OFFLINE_EVALUATION_2026-10-19.md are a distinct run.

Concrete decision: do NOT replace the production detector with MOSAIC or derive
CLEAR from floor categories. Higher average mIoU and lower host latency do not
establish better stair performance. Float is also not safe:24.64% stair-pixel
confusion persists here. Need geometric ground-reference/height and temporal
evidence, with positive alternative-path proof before turns.

## Researched reuse and ranked next work

Primary-source research,6 pinned repositories,3D/semantic data terms, direct
algorithm files and adoption gates:
[research report](../research/09-reuse-and-failure-focused-evaluation.md).

Next priority pair: (1) measured ground reference/current visibility versus known
planes, with vertical/table/floor rejection reasons; (2) tiny public RGB-D
held-out scene adapter and offline geometry oracle. Reuse ARCore and Guideline
alignment/occupancy patterns before custom models. If insufficient, benchmark
V2 Small using the existing Android ONNX example contract; DA3/Open3D/Hypersim
are researched options, **not integrated or measured**. No automatic large
downloads, hardware or new runtime tree. No additional owner photo required for
this research/evaluation work.

## Final verification and review disposition

Full offline gate ran twice. Final post-review gate:
`outputs/reports/quality-gate/20261020T022812604974Z/summary.json`, PASS22.391s;
516 app+10 simulator+19 policy+27 evaluation=**572 tests**,0 failure/error/skip.
Both builds, manifest/model/debug isolation/privacy-root checks PASS. Lint0
errors/fatal,55 debug/34 release warnings unchanged. Known-root privacy audit is
not a full secret/history scan. Final source fingerprint181 files:
`8f1c507264acc7c8a0c1c52ca25566275a609fbcff6b2fcd5781927217d918da`.

Cursor R accepted with limitations; Codex reviewed its exact findings:

- I1/I3 accepted: native inventory enumeration is SDK-owned, thin SDK mapping
  compiles but is not covered by fake-SDK JVM tests. Counts prove no geometry.
- I2 accepted: UI is a compact orientation summary; full paused/stopped/subsumed/
  truncated buckets require `SURFACE_INVENTORY` logs. V=0/U=0 alone is not absence.
- E1/E2 accepted for this bounded64-bit educational evaluator: at512² per frame
  and2000 official validation frames, counts≤524288000, far below signed64bit
  maximum. Arbitrarily huge maps/runs and32-bit hosts are not acceptance targets;
  metric inputs still validate nonnegative/int64 entries and aggregate totals.
- E3 repaired: synthetic export fixture now includes wall as well as GT0/floor/
  stair/rug; asserts wall TSV counts, GT0, diagnostic-only flag and schema. Final
  gate rerun after this test change. No blanket ban on words in limitations text.
- E4 retained: GT0 includes preprocessing pad, explicitly documented; no pure
  dataset-unlabeled or physical-hazard rate is inferred from it.

Debug APK/staged0.9.29/42 and actual installed Redmi base.apk SHA all matched:
`16494a12cb6cba89c0fc98eae6e8072be305aa06ce786f8464d92750a5782265`.
Release unsigned APK SHA:
`c438f51ff8ac4efd82d95522d2761b29e419fbcb027a6ea8820ee67e9d3a1b4d`.
Installed using `-r`, no app-data clear. Phone was awake/unlocked on launcher;
a bounded10s diagnostic launch returned `Status: ok`. **No runtime outcome is
claimed:** USB disappeared before logs could be collected. Empty PID capture was
rejected; a tag-scoped fallback logcat waiting for the device was cancelled.
`adb devices` then showed no device; scoped reconnect reported not found. We did
not reboot, alter drivers/security or bypass a lock. App close, inventory counts,
tracking/accuracy and physical scene quality are unverified for0.9.29. Timer is
implemented, not substituted for an observed close. No extra owner photo asked.

Private logs/reports and all images/weights/APKs remain outside the source backup.
Public source checkpoint and exact remote verification are recorded in the final
ignored receipt after commit; don't infer backup completion from this paragraph.
