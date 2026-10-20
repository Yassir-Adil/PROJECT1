# Cursor R — reuse pair source review — 2026-10-20

**Role:** Independent Cursor R (source review only).
**Queue:** `docs/WORK_QUEUE.md` — Active pair 2026-10-20, reuse research and failure-focused evaluation (P: bounded SDK plane inventory; E: ADE150×Top31 semantic risk metrics).
**Sole write target:** this file. No other edits. No shell / Gradle / ADB / Git / network. No `outputs/` or private data.
**Verification not claimed:** build, full gate, phone, ADB, or “PASS”. Codex owns RED/GREEN execution records (helper RED; Python27 GREEN; focused Kotlin running — acknowledged, not re-run here).

## Evidence read

| Item | Status |
| --- | --- |
| `AGENTS.md` | Read (mission / honesty / debug isolation) |
| `docs/WORK_QUEUE.md` active reuse pair | Read |
| `docs/CURSOR_INVENTORY_2026-10-20.md` | Read (P contract + implementation notes) |
| `docs/CURSOR_RISK_METRICS_2026-10-20.md` | Read (E contract) |
| `SurfaceInventoryPolicy.kt` | Read (landed) |
| `SurfaceInventoryPolicyTest.kt` | Read (16 contract tests) |
| `ArCoreSurfaceProbe.kt` | Read (`inventory` + existing `sample`) |
| `RawDepthDiagnosticActivity.kt` | Read (plane config; `maybeSampleSurfaces`; result counters/logs) |
| `research/semantic_risk_metrics.py` | Read (landed) |
| `research/tests/test_semantic_risk_metrics.py` | Read (16 tests) |
| `research/evaluate_ade20k_segmenter.py` | Read (integration + export) |
| `research/tests/test_evaluate_ade20k_segmenter.py` | Read (export / Top31 / stairs / rug coverage) |

## Verdict

**ACCEPT WITH LIMITATIONS — no P1 source defect** in the reviewed landed inventory policy/adapter wiring or ADE150×Top31 metrics module relative to the reuse-pair contracts.

Cap-256 / `hasNext` peek, subsumed→paused/stopped→orientation precedence, session-known inventory vs ray visibility, no retained SDK objects in the snapshot, orientation≠floor/wall wording, GT0 excluded from labeled floor precision, stairs rows outside Top-31 preserved, rug not auto hazard/safe, and non-mutation of label maps / confusion matrices all match the intended diagnostic scope.

**P2** items below are exact remedies or explicit accepts. This review does **not** assert JVM/Python GREEN, APK build, Lint, install, or Redmi behaviour.

---

## P — Surface inventory

### Accept (contract matches source)

| Check | Source | Finding |
| --- | --- | --- |
| Cap 256; peek without consuming 257 | `SurfaceInventoryPolicy.kt` 45, 80–98 | `while (inspected < MAX_PLANES && iterator.hasNext()) { next(); inspected++ }` then `truncated = inspected == MAX_PLANES && iterator.hasNext()`. Extra element detected only via `hasNext`, never `next`. Locked by `lazyUnboundedIterable…` and `truncatedDoesNotConsumeTheExtraCandidateViaNext`. |
| Not-tracking skips iteration | Policy 55–67; adapter 19–20; tests `cameraNotTracking*` | Early return before `iterator()`; adapter uses `emptyList()` + `cameraTracking=false` when camera not `TRACKING`. |
| Subsumed / tracking filters | Policy 83–95; tests mixed/subsumed/paused | Precedence: `subsumed` → `PAUSED` → `STOPPED` → TRACKING orientation / `unknownKind`. Subsumed never increments orientation or paused/stopped. |
| Session inventory ≠ ray / visibility | Adapter 13–17, 18–34 vs 44–72; Activity 465–495, 895–900 | `inventory` = `session.getAllTrackables(Plane)` primitive counts; `sample` = per-ray `hitTest` + polygon gate. Logs: `scope=session_known_not_visibility`; UI: “not visibility or floor proof”; separate `inventory_*` vs `surface_samples_with_*` counters; result line `inventory_scope=session_known_not_visibility`. |
| SDK ownership / lifecycle / no retain | Adapter 22–34; snapshot is ints only | Maps to `SurfacePlaneCandidate` scalars; returns `SurfaceInventorySnapshot` only. No `Session`/`Plane`/`Frame` fields on policy types. Locals end with the call. Called on existing GL `update()` path inside `frameInUseLock` (`RawDepthDiagnosticActivity.kt` 419–445, 531). |
| Orientation ≠ floor / wall | Policy KDoc 21–25; Activity logs `semantics=orientation_only`; banned-word test | `HORIZONTAL_UPWARD` / `VERTICAL_SURFACE` remain geometry labels. Plane finding only when `surfaceProbingEnabled` (`configureSession` 305–307). No navigation / speech / CLEAR coupling in this path. |

### P2 issues (inventory)

#### P2-I1 — SDK enumerates the full trackable set; policy only bounds *mapping/counts*

- **Where:** `ArCoreSurfaceProbe.kt` 22–34; comment 15–16.
- **What:** `session.getAllTrackables(Plane::class.java)` returns the full SDK `Collection` before `asSequence().map {…}.asIterable()` is summarized. `MAX_PLANES` limits how many planes are mapped/counted and whether `truncated` is set; it does **not** stop native enumeration of plane N>256.
- **Risk:** Diagnostic honesty only — `status=bounded` / `truncated=true` means “inspected hit the policy cap”, not “SDK stopped listing”. Thermal/GC cost of a huge trackable set is uncapped by the helper.
- **Remedy or accept:** **Accept with limitation** for this slice (queue allows SDK-owned enumeration). If a later slice needs a hard cap, document that ARCore provides no partial `getAllTrackables`, and keep treating `bounded` as inspect-cap — never as completeness of the physical scene. Optional: log `sdk_collection_size` when accessible without retaining planes (only if Collection.size is O(1) and still copies no Plane into fields).

#### P2-I2 — On-screen inventory line omits non-TRACKING buckets

- **Where:** `RawDepthDiagnosticActivity.kt` 494–495 (UI); contrast log 482–487.
- **What:** UI shows only `V=` / `U=` tracking counts + status. Paused / stopped / subsumed / downward / unknown / inspected / truncated detail is logcat-only.
- **Risk:** Operator may read “V=0 U=0 (observed)” as “no planes” when many are paused/subsumed (still visible in `SURFACE_INVENTORY` log).
- **Remedy:** Extend the UI string with `paused/stopped/subsumed` (and `truncated` when true), **or** **accept** and require logcat for full inventory. Do not rename upward→floor.

#### P2-I3 — No JVM test for adapter mapping (expected)

- **Where:** `ArCoreSurfaceProbe.inventory` has no `testDebug` twin (SDK types).
- **Accept:** Pure policy tests cover counting. Adapter is a thin enum/`subsumedBy` map. Residual risk = SDK enum drift; fail-closed `else -> STOPPED` / `else -> UNKNOWN` is appropriate.

---

## E — ADE150×Top31 semantic risk metrics + evaluator

### Accept (contract matches source)

| Check | Source | Finding |
| --- | --- | --- |
| GT0 counted, not labeled precision error | `semantic_risk_metrics.py` 113–118, 125–128; test `test_unlabeled_gt0_is_counted_but_not_precision_error` | `unlabeled_as_floor_pixels = floor_col[0]`; labeled denom = `floor_col[1:]`; precision/error fractions ignore GT0. |
| Stairs outside Top-31 preserved | `full_confusion` shape (151,32); test stair rows 54/60/97/122; evaluator `STAIR_IDS` + `full_total` | Top-31 `confusion()` still drops GT∉1..31 for mIoU (`evaluate_ade20k_segmenter.py` 103–105); ADE150 matrix keeps stair GT rows for false-floor diagnostics. |
| Rug not hazard / not safe | metrics keys + `test_no_implicit_hazard_or_safe_categories`; evaluator limitations 228 | Rug is `per_gt[29]` + TSV `rug_*` columns only. No `rug_hazard` / `rug_safe` / walkable/clear categories. |
| Integer overflow / mutation | `full_confusion` 82–88 int64 copies; `metrics` 105–108 cell+total guards; tests uint8 index wrap, int64 max cell, aggregate sum, no input mutation | Flat index `gt*32+pred` cannot uint8-wrap; metrics rejects negative / >INT64_MAX cells and sum; matrices/label maps not mutated. |
| Evaluator integration | `evaluate_ade20k_segmenter.py` 28–30, 159, 186–187, 212–215, 230–231 | Imports module; accumulates `full_total`; exports `semantic_risk`, `full_confusion_ade150_…`, `semantic_risk_script_sha256`, schema v1; `allow_nan=False`. Frame TSV labeled floor cols use `full_current[1:, 4]` (GT0 excluded). |
| Export test coverage (partial) | `test_evaluate_ade20k_segmenter.py` 62–121 | Stairs+rug floor confusion preserved; 151-row matrix; script hash length 64; overwrite refused; precision+error fraction sum 1; Top31 `scored_pixels` excludes stairs/GT0. |

### P2 issues (metrics / evaluator)

#### P2-E1 — `np.bincount` unweighted dtype is platform `intp` (pathological maps)

- **Where:** `semantic_risk_metrics.py` 87–88.
- **What:** After safe int64 flat indices, unweighted `np.bincount` returns platform `intp`. On a hypothetical 32-bit/`intp` narrow host, a single densest bin with count >2³¹−1 could wrap before `astype(np.int64)`. Intended ADE 512×512 and unit fixtures cannot hit this; the module’s explicit INT64 guards apply to **metrics inputs**, not to bincount’s intermediate dtype.
- **Remedy:** Accumulate with an int64 destination, e.g. `out = np.zeros(GT_CLASS_COUNT * PRED_CLASS_COUNT, dtype=np.int64)` + `np.add.at(out, flat, 1)` (or equivalent), then reshape. **Or accept** with limitation: “host intp must hold max per-bin count for one map; research eval is 64-bit.”

#### P2-E2 — Evaluator multi-frame `full_total +=` has no int64 sum guard

- **Where:** `evaluate_ade20k_segmenter.py` 159, 186–187, 214.
- **What:** Per-frame `full_confusion` is safe for 512²; long runs add into `full_total` with NumPy `+=`. A pathological mega-run could wrap int64 without the Python-int checks used inside `metrics()` (which would then see negatives and raise — or, if wrap stays non-negative, silently wrong positives).
- **Remedy:** Before `full_total += full_current`, reject if any `full_total.astype(object) + full_current` would exceed INT64_MAX (or cast through Python int / checked add). **Or accept** for official ADE20K validation sizes (~2k×512² ≪ 2⁶³).

#### P2-E3 — Evaluator integration test gaps (coverage, not logic bug)

- **Where:** `research/tests/test_evaluate_ade20k_segmenter.py` `test_export_keeps_excluded_stairs_and_rug_counts_without_changing_top31_score` (≈107–121). Module-level GT0 / hazard bans are in `test_semantic_risk_metrics.py` only.
- **What:** Export test does **not** assert: `unlabeled_as_floor_pixels > 0` for the GT0 fixture cell; `wall_gt_pixels` / `wall_predicted_floor_pixels` TSV columns; `semantic_risk["diagnostic_only"] is True`; `semantic_risk_schema_version == 1`; banned hazard/safe tokens absent from `summary.json`.
- **Remedy:** Add assertions on the existing fixture (mask has GT0/4/54/29, pred all floor): e.g. `stored["semantic_risk"]["unlabeled_as_floor_pixels"] > 0`, `diagnostic_only is True`, schema version 1, and spot-check `wall_*` TSV fields. Keep rug/stairs as raw counts only.

#### P2-E4 — Resize/pad GT0 conflates “unlabeled” with pad (documented; keep honest)

- **Where:** `evaluate_ade20k_segmenter.py` 78–81, 227; metrics GT0 handling.
- **What:** Reference pad is label 0; GT0 in risk metrics therefore mixes ADE “other/unlabeled” and preprocessing pad. Limitations string already states this.
- **Accept with limitation:** Do not treat `unlabeled_as_floor_*` as a pure dataset-other rate without subtracting pad, or document pad pixel count separately in a later schema bump — **not required to accept this slice.**

---

## Cross-cutting limits (do not over-claim)

1. **Diagnostic only:** Inventory and ADE150 risk counts do not authorize movement, CLEAR, turn hints, or eyes-closed walking.
2. **Vertical ≠ wall; upward ≠ floor** — repeated in source and logs; preserve.
3. **R did not run tests, builds, or phone probes.** Codex RED/GREEN and any Redmi surface/depth numbers are outside this file’s evidence class.
4. **No navigation / permission / dependency changes** observed in the reviewed exclusive scopes; Activity remains debug DUMP diagnostics.

## Disposition

| Track | Disposition |
| --- | --- |
| P `SurfaceInventoryPolicy` + adapter/Activity wiring | **Accept with limitations** (P2-I1..I3) |
| E `semantic_risk_metrics` + evaluator export | **Accept with limitations** (P2-E1..E4) |
| P1 blockers | **None** in reviewed source |

**STOP.** Independent reuse-pair source review complete. No further writes from Cursor R in this turn.
