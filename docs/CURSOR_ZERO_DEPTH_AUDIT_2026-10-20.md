# Cursor Z — zero-depth source audit (2026-10-20)

**Role:** independent bounded source reviewer (Cursor Z).
**Scope:** read `AGENTS.md`, `docs/WORK_QUEUE.md` Active verification; review listed debug Raw Depth / surface sources only.
**Allowed write:** this file only.
**Not done by this reviewer:** no tests, builds, ADB, Git, network, private outputs, shell, product edits, or device probes.

**Codex-reported 0.9.29 / 30s (consumed as external report, not re-run here):**
859 distinct frames / 828 TRACKING; 222 decoded D_16/Y8 pairs; `observed_valid_pixels=0` / `observed_confident_pixels=0`; no summarize rejects; 29 session inventories with `inspected=0`; no ray hits; `NO_DEPTH` / `duration` then `CLOSED`.
**Contrast (Codex, prior 0.9.28 warm):** 41 admitted depth pairs; 11/19 samples with upward surface hits.

**Contract name:** there is no `RawDepthImageContract.kt`. Format gate is `RawDepthImageFormat.kt`.

---

## Verdict

**No reproduced source defect** that would force all-zero depth maps, empty plane inventories, or empty ray hits while still successfully acquiring and summarizing 222 same-Frame D_16 + Y8 pairs.
The reported 0.9.29 outcome is **consistent with SDK-returned all-zero depth planes plus no known planes**, which is a **scene / motion-stereo / tracking-content** class of cause and **requires a controlled phone test** to confirm. It is **not** explained by confidence-threshold gating alone, a hardcoded zero in the decoder, or a format/stride validation failure (those paths would not yield `summarize_rejects=0` with `observed_decoded_pairs=222` and `observed_valid_pixels=0`).

Prior 0.9.28 warm admits + upward hits on the same diagnostic activity path contradict a permanent “always zero / always empty inventory” wiring bug in the current sources reviewed here.

---

## Flow map (actual code)

| Stage | Location | Behavior relevant to zero-depth |
| --- | --- | --- |
| Intent / extras | `RawDepthDiagnosticActivity.kt` 152–154, 999–1001 | `duration_seconds` (default 20); `camera_preview` default **true**; `surface_probe` default **true**. No depth-mode override via Intent. |
| Session create | 178–186 | `Session(this)` once; fail-closed if `RAW_DEPTH_ONLY` unsupported. |
| Config | `configureSession` 301–315 | `depthMode=RAW_DEPTH_ONLY`; `updateMode=LATEST_CAMERA_IMAGE`; plane finding `HORIZONTAL_AND_VERTICAL` iff `surfaceProbingEnabled` else `DISABLED`; light / instant placement / cloud / geospatial **DISABLED**; `focusMode=AUTO`. Single `configure`; no later reconfigure. |
| Resume / GL | 317–347, 372–456 | Session `resume` before `GLSurfaceView.onResume`; texture gen + `setCameraTextureNames` every draw; `update()` then optional background draw then `processFrame`. Texture name set **even if** preview draw disabled. |
| Distinct frames | `processFrame` 510–530 | New camera timestamp only; tracking counters on distinct frames. |
| Surfaces | 531, `maybeSampleSurfaces` 458–497 | Rate-limited ~1 Hz; inventory then four VIEW-pixel rays. Runs for distinct frames **before** non-TRACKING early return. |
| Depth acquire | 532–574 | TRACKING only; 10 Hz cadence **before** acquire; `RawDepthPairScope.withFrame` → `acquireRawDepthImage16Bits` then `acquireRawDepthConfidenceImage`. |
| Summarize / admit | `summarizeAndGate` 577–704 | Format D_16 + Y8 + size match; `RawDepthFrameStats.summarize`; `coverageStats.record`; if `validDepthPixels<=0` **or** `confidentPixels<=0` → reject `no_valid_confident_pixels` (no gate admit); else `RawDepthSampleGate.accept`. |
| Stop / result | 788–937 | Duration → `NO_DEPTH` if `accepted==0`; `RAW_DEPTH_CLOSED` after `session.close` in `onDestroy`. |

---

## Checklist against suspected defect classes

### 1) Config override / wrong camera / wrong session

| Check | Finding |
| --- | --- |
| Depth mode overridden after configure? | **No.** Only `configureSession` at create (301–315). |
| Plane finding accidentally disabled by default? | **No.** Default `EXTRA_SURFACE_PROBE=true` (154, 1001) → `HORIZONTAL_AND_VERTICAL` (305–307). Disabled only if Intent extra false (would empty planes **without** explaining all-zero depth). |
| Explicit `setCameraConfig` / front camera? | **Absent.** Default ARCore camera selection; no second Session. |
| Preview off killing stereo? | **Unlikely from code.** `setCameraTextureNames` always (427); only `cameraBackground.draw` is gated (434–444). |
| Focus AUTO vs research FIXED note | Present (313). **Not a proven zero-map defect**; do not assert cause. |

**Defect?** Not reproduced.

### 2) Format / strides / decoder (hardcoded zero?)

| File | Finding |
| --- | --- |
| `RawDepthImageFormat.kt` 6–14 | Requires `HardwareBuffer.D_16` (48). Wrong format → `IllegalStateException` → stop fail path, not silent zeros. |
| Activity 586–595 | Confidence must be `ImageFormat.Y8`; size must match. |
| `RawDepthFrameStats.kt` 70–91 | Absolute LE UINT16 + UINT8; `validDepthPixels` increments iff `depthMm != 0`; confidence threshold only gates `confidentPixels`. **No hardcoded zero output.** |
| Stride validation 113–146 | Rejects truncated / overlapping planes via `IllegalArgumentException` → Activity `summarize_rejects++` (627–633). Codex: **no summary rejects**. |
| Coverage | `RawDepthCoverageStats.record` accepts `valid=0,confident=0` when `sampled>0` (78–91). Matches `decoded=222` with `observed_valid=0`. |

**Implication of Codex numbers:** 222 pairs passed format + summarize. Aggregate `observed_valid_pixels=0` means **every sampled depth sample decoded as 0 mm**, not “discarded by threshold 128”. Threshold-only failure would leave `observed_valid_pixels > 0` (see prior 0.9.27 pairing doc pattern: nonzero valid with sparse confident).

**Defect?** No decoder hard-zero or stride reject path matching this report.

### 3) Data consumption / order / lifecycle / provenance

| File | Finding |
| --- | --- |
| `RawDepthPairScope.kt` 32–57 | Depth then confidence on same `frame`; stamp must be unchanged and positive; proof invalidated before close. |
| Gate `RawDepthSampleGate.kt` 43–53 | No depth/confidence stamp equality; `depth_not_new` / `camera_not_new` / processing age. **Not reached** when Activity returns early at valid/confident ≤ 0 (642–648). |
| Cap order | Admission interval updated before acquire (542–547); NotYetAvailable still consumes slot — consistent with attempts ≈ decoded under success. |
| Lifecycle | `onPause` → terminal `paused` (350–353). Codex `reason=duration` ⇒ full window, not pause kill. Stop drains GL then `session.pause`; close in `onDestroy`. |
| Buffer mutation | Summarize uses absolute `get(index)`; does not flip/limit buffers. |

**Defect?** Provenance and lifecycle look sound for diagnostic use. No consumption-order bug found that zeros plane bytes.

### 4) Surface inventory / rays

| File | Finding |
| --- | --- |
| `ArCoreSurfaceProbe.inventory` 19–34 | Non-TRACKING → empty summarize (`inspected=0`). TRACKING → `session.getAllTrackables(Plane)`; maps type/state/subsumed. |
| `SurfaceInventoryPolicy.kt` 51–110 | Counts only non-subsumed TRACKING planes by orientation; empty iterable ⇒ `status=observed`, `inspected=0`. Does **not** invent planes; does **not** zero a non-empty list. |
| `ArCoreSurfaceProbe.sample` 44–72 | VIEW-pixel `hitTest`; empty / invalid → `no_valid_surface`. |
| Rate | Activity 462–463: surface sample ≤ ~1 Hz → ~30 samples / 30s fits “29 inventories”. |

**Defect?** Empty `inspected=0` with no hits is **correct** for empty trackables / no valid hits. Distinguishing `status=not_tracking` vs `status=observed` (empty) needs log fields; not asserted here without device logs.

### 5) Result status semantics

`logResultOnce` 901–907: `duration` + `accepted==0` → `NO_DEPTH` (not COMPLETE). Matches report. Empty maps correctly refuse admit (641–647) and refuse COMPLETE.

---

## Separated findings

### A. Reproduced / source-backed facts (no device run by Z)

1. All-zero **valid** depth across decoded pairs cannot be caused by confidence threshold 128 alone (`RawDepthFrameStats.kt` 84–89; Activity early reject 642–648 still records coverage first at 639).
2. Successful D_16/Y8 summarize of 222 pairs rules out format mismatch, size mismatch, and stride truncation as the explanation for this RESULT shape.
3. No hardcoded zero depth/confidence aggregates in FrameStats / CoverageStats / Gate / PairScope.
4. Config does not disable raw depth; plane finding defaults on; no Intent key turns depth off.
5. Same Activity path previously admitted depth and saw upward hits (0.9.28 Codex) ⇒ current wiring is capable of non-zero output under other conditions.
6. Empty inventory + no ray hits are consistent with **no Plane trackables / no valid hits**, not with a policy that drops TRACKING planes into `inspected=0` without counting.

### B. Plausible scene / parallax / runtime causes (phone test required — not asserted)

- Motion-stereo raw depth often stays empty or low-confidence on still / low-texture / poorly lit holds (project notes elsewhere; **not re-proven here**).
- TRACKING without established geometry can yield empty `getAllTrackables(Plane)` and miss rays while camera timestamps still advance.
- Launch with `surface_probe=false` would empty planes only; would **not** by itself explain all-zero depth.
- USB / process / version identity of the Codex 0.9.29 log was **not** verified by this reviewer.

### C. Not claimed

- No floor / wall / clearance / safe / walkable interpretation.
- No automatic model or threshold change.
- No undocumented root cause asserted for the 0.9.29 zero maps.

---

## Minimal diagnostics (only where a real observability gap exists)

Do **not** weaken gates or lower confidence threshold.

1. **Rate-limited log on `no_valid_confident_pixels`** (Activity 642–647 currently only `countRejection` + UI; unlike gate rejects at 664–674). Include: camera/depth/confidence timestamps, width/height, row/pixel strides, `validDepthPixels`, `confidentPixels`, `sampledPixels`. Purpose: confirm live “all-zero map” vs “nonzero depth / zero confident” without changing admission.
2. **Optional RESULT rollup:** count of `SURFACE_INVENTORY` with `status=not_tracking` vs `status=observed` and `inspected=0`, so empty-plane TRACKING is distinguishable from inventory-during-non-TRACKING. No semantic elevation of planes.

No other product changes proposed from this audit.

---

## Acceptance

**Accept current sources for this symptom class:** the Codex 0.9.29 zero-depth + empty inventory report does **not** demonstrate a reproduced acquisition/decoder/config/provenance defect in the files reviewed. Next step remains Codex/owner **physical probe** with known textured parallax (no walking), not a speculative gate or model change.

**Verification statement:** **No tests and no device run were executed by Cursor Z.** Findings are from static review of:

- `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthDiagnosticActivity.kt`
- `RawDepthPairScope.kt`
- `RawDepthFrameStats.kt`
- `RawDepthImageFormat.kt` (actual format contract name)
- `RawDepthSampleGate.kt`
- `RawDepthCoverageStats.kt` (coverage path used by RESULT)
- `ArCoreSurfaceProbe.kt`
- `SurfaceInventoryPolicy.kt`
- (supporting read) `SurfaceProbePolicy.kt`, `DiagnosticCameraBackground.kt` (preview vs texture-name), debug manifest activity entry

**Stop.** End of Cursor Z report.

---

# Cursor Z — append: landed motion + dense diagnostic pair (2026-10-20)

**Role:** independent bounded source review of the landed next diagnostic pair.
**Write scope:** append to this file only; previous 0.9.29 zero-depth audit above is unchanged.
**Not done:** no shell/build/ADB/Git/network/private outputs; **no tests or phone verification by Z.**
**Codex process note (not re-run):** helper compile RED before source; focused GREEN claimed running.
**SDK note (Codex-verified docs, not re-fetched here):** raw depth remains available under `AUTOMATIC`; dense (`acquireDepthImage16Bits`) is heavier and has no matching confidence image.

**External second 0.9.29 owner-ready lateral-motion run (consumed, not re-run):**
869 distinct / 836 TRACKING; 227 decoded pairs all-zero valid; 29 empty inventories; `NO_DEPTH` then `CLOSED`. Motion was owner-reported, not yet measured by these new helpers on that run (helpers landed after). That RESULT shape still does **not** by itself prove a new wiring defect; it reinforces need for pose + optional AUTOMATIC comparison on a future probe.

---

## Verdict (new pair)

**Accept with P2 fixes / accepted limitations.** No **P1** admission-coupling, retention, or gate-weakening defect found in the landed sources.
`RAW_DEPTH_ONLY` remains default; raw same-Frame proof + confidence 128 path unchanged; dense is opt-in `AUTOMATIC` observation only; motion is segment-relative SDK translation scalars only.
Prior audit gap (rate-limited empty-map log) is **landed** as `RAW_DEPTH_EMPTY` (`RawDepthDiagnosticActivity.kt` ~718–727).

---

## What landed (source map)

| Piece | Location | Contract check |
| --- | --- | --- |
| Mode select | Activity 104, 163–170, 1094 | Default `RAW_DEPTH_ONLY`; Intent `EXTRA_DEPTH_MODE` `null`/`RAW_DEPTH_ONLY`/`AUTOMATIC`; else fail-closed `invalid_depth_mode`. |
| Support | 198–205 | Always requires `RAW_DEPTH_ONLY` supported; also requires requested mode supported. |
| Config + readback | `configureSession` 322–344 | Sets `diagnosticDepthMode`; `check` applied depth + plane modes; logs facing/image/texture sizes — **no pose coordinates**. |
| Motion | `CameraMotionStats.kt`; Activity 566–571, 518–522, 877, 998, 1014–1016 | Distinct-frame `Camera.pose` tx/ty/tz → scalars; non-TRACKING breaks segment without using coords; snapshot scalars only; `stop()` on `requestStop`; RESULT + ~1 Hz `CAMERA_MOTION` without coordinates. |
| Dense | Activity `sampleDenseDepth` 621–647; call 616–618 | Only if `AUTOMATIC`; `acquireDepthImage16Bits().use`; `summarizeDepthOnly`; cap `denseSamples >= MAX_LATENCY_SAMPLES` (600); RESULT `dense_*` + `dense_confidence=unavailable` + `dense_navigation=none`. |
| Depth-only decoder | `RawDepthFrameStats.summarizeDepthOnly` 127–172; `DepthOnlySummary` 22–25 | Nonzero UINT16 LE counts; shared plane validation; **no** confidence/percentiles/admission. |
| UI copy | Activity 225–230 | Feet still + slow side-to-side toward lit texture; no walking; missing = unknown. |
| Raw path | PairScope + `summarize` + gate + threshold 128 | Unchanged authority for `accepted` / `NO_DEPTH` / `COMPLETE`. |

---

## Requirement checklist

| Requirement | Status |
| --- | --- |
| Strict dense vs raw admission separation | **Pass.** Dense never touches `sampleGate`, `coverageStats`, `acceptedSamples`. Status still from raw `accepted` only (989–995). |
| Dense confidence unavailable | **Pass.** No confidence acquire; RESULT explicitly `dense_confidence=unavailable`. |
| Bounded 600 dense samples/stats | **Pass.** Cap and `denseProcessingMs` both tied to `MAX_LATENCY_SAMPLES` (600); no slide needed because count stops at 600. |
| Close image all paths | **Pass.** Kotlin `.use` on dense `Image`; raw still via `RawDepthPairScope` / nested `.use`. |
| No camera/frame/image/`Pose` retained | **Pass.** Local `pose` / `Image` only; `CameraMotionStats` keeps scalar origin + counters (not SDK types). |
| Pause terminal | **Pass.** `onPause` → `requestStop("paused")`; stops gate/coverage/**motion**. |
| No coordinates logged | **Pass for motion/pose.** Logs displacement metres + flags only. (Pre-existing surface ray distances in VIEW space unchanged; not camera pose dumps.) |
| Motion = segment-relative SDK estimate only | **Pass.** Euclidean distance from segment origin; aggregate max; comments + RESULT deny physical/parallax readiness. |
| RAW_DEPTH_ONLY default; AUTOMATIC opt-in | **Pass.** |
| Raw proof / confidence 128 unchanged | **Pass.** |

---

## Integration / lifecycle / errors / performance

**Lifecycle:** Motion recorded on every new distinct frame (before depth cadence). Dense runs only after the shared ~10 Hz admission slot (and only `AUTOMATIC`), after raw pair attempt — limits dense cost vs full camera FPS. `requestStop` stops motion; `failClosedStartup` still omits `motionStats.stop()` (P2; usually no samples yet).

**Errors:** Dense `NotYetAvailableException` counted, non-fatal. Other dense exceptions: `denseInvalid++` then **rethrow** outside `processFrame`’s try/catch → `onDrawFrame` → `gl_frame_*` FAIL. Fail-closed, but **reason taxonomy is wrong** for dense (P2). Soft `IllegalArgumentException` on raw summarize does not stop the probe; dense path is harsher (P2 asymmetry).

**Performance:** AUTOMATIC does raw pair decode + dense decode on the GL thread at ≤10 Hz, up to 600 dense maps. Heavier by design (SDK). Observable via `dense_processing_*` / `session_update_*` percentiles after a real AUTOMATIC run — **not measured by Z.**

---

## P1 / P2 vs accepted limitations

### P1
**None found** that couples dense into raw admission, retains SDK `Pose`/`Image`/`Frame`, logs raw coordinates, weakens confidence 128, or changes default mode.

### P2 (concrete, optional)
1. **Dense soft-fail asymmetry** (`sampleDenseDepth` 643–646): catch `IllegalArgumentException` (and optionally format) like raw summarize rejects — increment `denseInvalid`, do **not** rethrow — so one bad dense plane does not abort an otherwise useful raw+motion probe. Keep fail-closed for truly unexpected if desired, with a `dense_*` stop reason instead of `gl_frame_*`.
2. **`failClosedStartup` / restored-activity:** call `motionStats.stop()` (and coverage if missing) for stop symmetry with `requestStop`.
3. **AUTOMATIC UI:** surface mode + dense valid fraction / motion max displacement in status copy (still “not clear / no walk”), so opt-in comparison is visible without logcat only.
4. **Naming:** dense cap reuses `MAX_LATENCY_SAMPLES` — consider a dedicated `MAX_DENSE_SAMPLES = 600` alias for clarity (behavior already correct).

### Accepted limitations (not defects)
- Motion metres ≠ validated physical parallax or “owner moved enough.”
- Dense nonzero ≠ raw admit; dense cannot authorize navigation.
- Shared 10 Hz slot means dense is not full-rate.
- Second lateral-motion 0.9.29 all-zero raw RESULT remains a **scene/runtime** hypothesis until a post-land probe logs `motion_*` and (if used) `dense_*`.
- Codex SDK doc claim (raw under AUTOMATIC; dense heavier / no confidence) accepted as external; not re-verified here.

---

## Relation to second all-zero lateral run

Source review of the new helpers does **not** overturn the earlier accept: 227 all-zero decoded raw pairs with empty inventories still look like empty SDK depth/planes, not a new hard-zero in `summarizeDepthOnly` or motion. The new pair is the correct **measurement** layer (pose displacement + optional dense counts) without weakening gates.

---

## Verification statement (append)

**Cursor Z ran no unit tests and no device/ADB probe.** Codex RED-then-GREEN compile/test claims and any AUTOMATIC phone RESULT are outside this review. Static review covered:

- `CameraMotionStats.kt` + `CameraMotionStatsTest.kt` (contract read only)
- `RawDepthFrameStats.kt` (`summarize` + `summarizeDepthOnly`) + depth-only section of `RawDepthFrameStatsTest.kt` (read only)
- `RawDepthDiagnosticActivity.kt` mode/readback/motion/dense/UI/RESULT paths

**Stop.** End of Cursor Z append.

---

# Cursor Z — final polish recheck (2026-10-20)

**Scope:** own-report append only; prior sections preserved. No shell/build/ADB/Git/network/private outputs. **No final gate or phone verification by Z** (Codex reported focused 54 GREEN, `1e200` RED→`hypot` fix, then 607-gate PASS; a further gate is expected after this polish — not run here).

## Source facts (exact finals)

| Change | Evidence |
| --- | --- |
| Finite large displacement | `CameraMotionStats.kt` 89: `hypot(hypot(dx, dy), dz)` (replaces `sqrt` of summed squares). Test present: `largeFiniteDisplacementAvoidsIntermediateSquareOverflow` (`1e200`). Overflowing *differences* still `invalid_pose` (86–91) — correct. |
| Restored / failClosed stop | Restored: `motionStats.stop()` + `coverageStats.stop()` (154–155). `failClosedStartup`: both (1059–1060). |
| Dense caps | `MAX_DENSE_SAMPLES = 600` (1114) for sample count; `denseProcessingMs` separately slide-capped at `MAX_LATENCY_SAMPLES` (642–643). |
| Dense errors | No rethrow; `Log.e` + `requestStop("dense_process_${…}", failed=true)` on UI thread (648–653), parallel to raw `depth_process_*`. NotYetAvailable still soft-count only. |
| UI mode prefix | `updateStatus` → `"$diagnosticDepthMode — تشخيص فقط\n$text"` (1080–1082). |
| Cloud/geospatial readback | `check` applied both DISABLED (341–342). |

Admission/default/raw proof/confidence-128 separation unchanged by these edits.

## P2 disposition

| Prior P2 | Disposition |
| --- | --- |
| #2 failClosed/restored stop symmetry | **Closed** in source. |
| #4 dedicated dense sample cap | **Closed** (`MAX_DENSE_SAMPLES`); latency list independently bounded. |
| #1 misleading `gl_frame_*` / error taxonomy | **Closed** for unexpected dense failures (`dense_process_*`). **Remaining optional:** soft-handle dense `IllegalArgumentException` like raw summarize rejects (still hard-stops probe via `requestStop`, which is fail-closed and correctly labeled — not a P1). |
| #3 AUTOMATIC/motion/dense on-screen | **Partially closed** (requested mode prefix). Still no live dense-valid / motion-max in status body — optional only. |

## Remaining accepted limits (unchanged)

Motion = SDK segment-relative estimate only; dense ≠ raw admit / no confidence / no navigation; shared ≤10 Hz dense with raw slot; all-zero raw lateral runs remain scene/runtime until a measured `motion_*` / optional AUTOMATIC probe. No gate weakening.

**P1:** none in this polish.
**Z verification:** static read of final `CameraMotionStats.kt` + Activity paths above only. Stop.
