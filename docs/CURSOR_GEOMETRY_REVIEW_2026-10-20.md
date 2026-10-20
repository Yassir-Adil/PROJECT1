# Cursor — geometry P/H + Codex integration review — 2026-10-20

**Role:** Independent source-only review (read tools only).
**Write target:** this file only.
**Not done:** shell, Gradle, unit-test execution, ADB, Git, network, private outputs, product/source/test/shared-doc edits, approval bypass.
**Device / build / GREEN:** not claimed. Helpers and Activity were not compiled or run by this reviewer.

## Queue / evidence read

| Item | Status |
| --- | --- |
| `AGENTS.md` | Read (phone-first, honest testing, diagnostic vs navigation separation) |
| `docs/WORK_QUEUE.md` Active pair — texture-depth projection and surface-height diagnostics | Read (Cursor P / Cursor H / Codex contracts; baseline 28fd899 / 0.9.30/43 cited in queue text) |
| `TextureDepthProjection.kt` | Read |
| `TextureDepthProjectionTest.kt` | Read (21 cases) |
| `SurfaceHeightProfile.kt` | Read |
| `SurfaceHeightProfileTest.kt` | Read (18 cases) |
| `RawDepthGridTest.kt` | Read |
| `RawDepthFrameStats.kt` (incl. `sampleConfidentGrid`) | Read |
| `ArCoreSurfaceProbe.kt` | Read |
| `RawDepthDiagnosticActivity.kt` (surface + projection wiring) | Read |
| `docs/CURSOR_PROJECTION_RESULT_2026-10-20.md` / `CURSOR_HEIGHT_RESULT_2026-10-20.md` | Read (worker claims; GREEN not re-verified here) |
| Official ARCore Pose / texture-intrinsic docs | **Not re-fetched** this pass (network forbidden) |

## Verdict

**No blocking source defects** found against the Active-pair contracts and the checklist below. Cursor P/H pure helpers match their test contracts; Codex wires texture intrinsics + physical camera pose, runs projection only after existing same-Frame admission, keeps height/projection diagnostic (no floor / CLEAR / main-nav coupling), and bounds logging.

**One optional efficiency note** (double `getAllTrackables`) — see F1. Not a correctness or safety defect; not worth a dedicated scope expansion solely to fix.

---

## Checklist (source observation only)

| Concern | Result |
| --- | --- |
| Integer pixel math (no +0.5) | OK — `TextureDepthProjection.kt` 125–128 uses integer `x`/`y`; test `integerPixelConventionHasNoHalfPixelOffsetVersusPrincipal` locks origin |
| GPU texture vs CPU image intrinsics | OK — Activity uses `frame.camera.textureIntrinsics` only (`RawDepthDiagnosticActivity.kt` 822–828); no `imageIntrinsics` / display-intrinsic fallback in reviewed files |
| Physical vs display-oriented pose | OK — projection and height use `frame.camera.pose` (`Activity` 837–838; `ArCoreSurfaceProbe.kt` 17); comment at 837–838; no `displayOrientedPose` in Activity |
| Invalid input / Long overflow / aspect | OK — Long depth area + Long cross-product aspect (`TextureDepthProjection.kt` 80–90); height overflow → `invalidHeight` (`SurfaceHeightProfile.kt` 85–88); grid coordinate uses Long (`RawDepthFrameStats.kt` 52–53) |
| Stride / buffer preservation | OK — absolute `get(index)` paths; `RawDepthGridTest.stridesPositionsLimitOrderAndBytesArePreserved` + direct-buffer position test; Activity reuses same live planes after `summarize` without rewind |
| Fake confidence / invented grid coverage / floor | OK — grid uses real confidence ≥128 and `mm > 0` (`RawDepthFrameStats.kt` 62); log scopes `raw_confident_grid_only` / `ground_reference=unknown` / `floor_table_identity=unknown`; height helper does not pick min-plane-as-floor |
| Same-live-Frame raw pair scope **after** existing admission | OK — `sampleTextureProjection` called only after `sampleGate.accept` returns `"accepted"` (`Activity` 762–811), still inside `RawDepthPairScope.withFrame` block (615–628) with open depth/confidence Images |
| Bounded counts / throttle | OK — grid ≤64 (`sampleConfidentGrid`); height/inventory ≤256; projection + surface paths gated by `UI_LOG_MIN_INTERVAL_NS` (1 s); depth admit still 10 Hz |
| Lifecycle / log privacy / main navigation isolation | OK — debug Manifest activity only; logs count/status scalars + relative height range (Codex: “ambiguity visible”); `no_coordinates_logged=true` on projection; `navigation=none` throughout; no `src/main` references to these symbols |

---

## Finding

### F1 — Optional: double session plane inventory per throttled surface sample (Low / efficiency)

- **Where:** `RawDepthDiagnosticActivity.maybeSampleSurfaces` 503–504 calls `ArCoreSurfaceProbe.inventory` then `ArCoreSurfaceProbe.heights`; each calls `session.getAllTrackables(Plane::class.java)` (`ArCoreSurfaceProbe.kt` 19 and 42).
- **What:** Same GL-thread, same `Frame`, ~1 Hz throttle (`UI_LOG_MIN_INTERVAL_NS`). Native plane enumeration runs twice; Kotlin mapping is separately lazy/capped at 256, but the SDK query cost is paid twice.
- **Correctness:** Not a semantic bug. Both paths are diagnostic; eligibility rules differ (inventory counts tracking kinds; heights require upward + finite signed Y). Dual snapshots in one Frame are extremely unlikely to diverge.
- **Worth changing vs scope?** **Not as a standalone task.** Cost is bounded by 1 Hz and typically small plane sets. Prefer coalescing into one `getAllTrackables` → dual primitive maps **only if** this adapter is already open for another edit. Do not expand model/nav/permission scope for this alone.

---

## Non-blocking notes (not defects)

1. **Double → Float for `Pose.transformPoint`:** `Activity` 844–847 narrows finite `Double` camera points to `Float` before world transform. Pathological huge finite laterals (accepted by P’s “no arbitrary bound” rule) can become non-finite floats and yield `invalid_projection`. Acceptable for diagnostic OpenGL/SDK float pose; not a contract violation.
2. **`grid_max=9` log literal** (`Activity` 862) matches default `gridSize=3`, not a shared named constant. Cosmetic drift risk only.
3. **Worker GREEN:** P/H docs correctly state tests were not run by those workers. This review likewise did not execute `:app:testDebugUnitTest` or device probes.

---

## Helper / integration summary (accepted patterns)

### Cursor P — `TextureDepthProjection`

- Gate order matches contract: positive dims → Long area ≤ 1_048_576 → Long aspect equality → finite positive focals / finite principal → pixel in frame → depthMm 1..65535 → scale → finite scaled focals → unproject → finite result.
- OpenGL camera: `z = -m`, `x = m*(x-cxS)/fxS`, `y = m*(cyS-y)/fyS`.
- Apache notice + Google 1.57 `PointCloudHelper` attribution present. Pure JVM; no SDK/buffers/pose/nav.

### Cursor H — `SurfaceHeightProfile`

- `not_tracking` / `invalid_camera` skip iteration; else always `observed`.
- Eligible: `HORIZONTAL_UPWARD && tracking && !subsumed`; non-eligible with bad Y stay `ignored`.
- Lazy ≤256 with `hasNext` truncation peek; signed min/max; no floor selection or retained SDK types.

### Codex wiring

- **Projection:** texture intrinsics + physical pose; confident grid only; after gate accept; 1 Hz; counts only in logs/result line.
- **Heights:** adapter maps primitives without retaining `Plane`; UI/log state relative range + explicit UNKNOWN floor/table / no walking permission Arabic line; sample counter `height_profile_samples_with_upward`.
- **Grid helper:** no synthetic confidence; zero depth excluded; buffer state preserved per tests.

---

## Explicit limits of this review

- No compile, focused test, full gate, Lint, APK, or Redmi run by this reviewer.
- No live verification that Redmi texture aspect matches depth, that finite world counts are non-zero, or that height ranges match a tape measure.
- No re-fetch of ARCore 1.57 primary docs this pass; physical-pose + texture-intrinsic pairing judged from in-tree comments, Google-sample attribution on P, and absence of display-oriented / CPU-intrinsic APIs in the wiring.
- Did not review unrelated dirty `owner-request-voice-ux.mdc` or untracked `research/10-reuse-licence-check.md` beyond noting queue preserve instructions.

## Disposition

**Source review accepted for the geometry pair checklist.** Optional F1 coalesce only opportunistically. Stop.
