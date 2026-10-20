# Cursor R — SDK surfaces + visible camera design review — 2026-10-20

Independent bounded design review only. This file is the sole write target.
No shell, build, ADB, Git, product edits, private outputs, or external-fetch
bypass. Diagnostic-only scope: no models, cloud, Internet permission, navigation,
or voice. **Source for SurfaceProbePolicy / DiagnosticCameraBackground is not
reviewed yet** — this pass is contract + Activity baseline + integration risk.
Await explicit source follow-up after S/B land.

## Evidence read

| Item | Status |
| --- | --- |
| `AGENTS.md` | Read |
| `docs/WORK_QUEUE.md` Active pair — SDK surfaces and visible camera | Read (S + B contracts) |
| `RawDepthDiagnosticActivity.kt` (current) | Read — `PlaneFindingMode.DISABLED`; OES texture allocated + `setCameraTextureNames` + `update()`; **never drawn**; stop = GL pause then session pause; no `acquireCameraImage` / file / record |
| `docs/CURSOR_PAIR_DESIGN_REVIEW_2026-10-20.md` | Read (prior R method / disposition pattern) |
| `SurfaceProbePolicyTest.kt` (RED fixtures present) | Read — helper types not implemented yet |
| `DiagnosticCameraBackgroundTest.kt` | Not present in tree at review time |
| Official Plane / Plane.Type / Frame.hitTest | **R WebFetch approval-blocked** — not counted as independent live fetch |
| `google-ar/arcore-android-sdk` v1.57.0 `shared_camera/.../BackgroundRenderer.java` | **R WebFetch approval-blocked** |
| Codex-claimed primary fetches (queue text) | Acknowledged as Codex evidence ownership only |
| Local secondary notes | `research/08-arcore-depth-integration.md` Plane.Type / BackgroundRenderer mentions; `third_party/NOTICE.md` currently cites **raw_depth_java** lifecycle only, not shared_camera |

## Proposed contracts under review

### S — pure CPU surface evidence (`SurfaceProbePolicy` + layout)

- `SurfaceKind`: `UNKNOWN`, `VERTICAL_SURFACE`, `HORIZONTAL_UPWARD`, `HORIZONTAL_DOWNWARD`.
- Candidates carry `kind`, `distanceMeters`, `tracking`, `subsumed`, `insidePolygon`.
- `select`: session `tracking == false` → `UNKNOWN` / `not_tracking` (wins over hit count); else filter to valid (`kind != UNKNOWN`, plane tracking, `!subsumed`, `insidePolygon`, positive finite distance); min distance; empty → `no_valid_surface`; `>64` candidates → `too_many_hits`; success reason `observed_surface`.
- Layout: four VIEW-pixel rays — center `(0.5,0.5)`, lower_left `(0.25,0.8)`, lower_center `(0.5,0.8)`, lower_right `(0.75,0.8)` × viewport; invalid viewport → empty. Explicitly **not** raw-depth / CPU-image coordinates; aligned with current `setDisplayGeometry` viewport.
- Honesty: no floor / wall / clear / walkable semantics; no retained SDK objects.

### B — diagnostic OES camera background (`DiagnosticCameraBackground`)

- `CameraBackgroundGl` seam for CPU tests; real GLES only behind that seam.
- Lifecycle: `onSurfaceCreated` = **new GL context** (forget old program handle; do not delete across contexts); `draw` requires init, positive frame timestamp + texture id, finite complete UVs; **never reuse prior UVs**; `releaseOnGlThread` deletes current program once and disables draw; re-init allowed on a new context.
- UV path: NDC quad → caller `transformUv` → expected SDK `Frame.transformCoordinates2d` into `TEXTURE_NORMALIZED` (not manual rotation/crop). Reused direct buffers, rewind before transform/draw. Propagate transform/GL errors. No per-frame buffer alloc; no retained `Frame`/`Image`.
- Port target: pinned official shared_camera `BackgroundRenderer` OES pattern (Apache); no depth viz / shared-camera framework / new assets or dependencies.

---

## Blocking / high design findings

### H1 — Coordinate domains must stay separated (High)

Three spaces must not be conflated in Activity/adapter wiring (Codex later):

1. **VIEW pixels** — `SurfaceProbeLayout` + `Frame.hitTest(x,y)` after the same `setDisplayGeometry(rotation, viewportW, viewportH)` the Activity already uses (`onSurfaceChanged` → flags → GL `setDisplayGeometry`).
2. **TEXTURE_NORMALIZED** — background quad UVs via `transformCoordinates2d` (NDC → texture). Visual preview alignment with probe rays depends on both using the **current** display geometry and the **same** `Frame` after `session.update()`, not on copying depth-image (u,v).
3. **Raw depth / CPU image pixels** — existing depth decode path; **out of scope for hit rays**. Using depth width/height for hitTest would systematically mis-aim lower probes.

Contract text already states VIEW vs depth/CPU. Residual risk is integration: any helper that “scales by depth size” or uses `IMAGE_*` / untransformed texture coords for hitTest is a design violation. Tests for S layout already lock VIEW pixel math; Activity mapping tests are Codex-owned later.

### H2 — hitTest geometric leeway ⇒ polygon gate is mandatory (High; contract OK)

Queue correctly requires `insidePolygon` because official hitTest is documented (Codex fetch; R blocked) as allowing geometric leeway beyond exact plane extents. Design acceptance:

- Adapter must set `insidePolygon` from plane polygon inclusion on the hit pose (e.g. `Plane.isPoseInPolygon`), not from “hit list non-empty”.
- Non-plane trackables / hit types ignored before candidate construction.
- Subsumed planes filtered (`subsumed == true` never wins nearest).
- Per-plane tracking false filtered; session-level not_tracking overrides all hits.

Without polygon inclusion, nearest-distance selection would prefer optimistic near misses — unsafe if later misread as wall/floor confirmation. **Contract is sound; do not weaken in source.**

### H3 — No floor/wall/clear semantic guarantee (High; contract OK)

`HORIZONTAL_UPWARD` ≠ floor/walkable; `VERTICAL_SURFACE` ≠ confirmed wall; distances are observed plane-hit metres under ARCore tracking, **not** calibrated navigation clearance and **not** a substitute for raw-depth HOLD gates.

Required boundaries for later Activity/UI/logs (Codex):

- Reasons stay machine tokens: `observed_surface` / `not_tracking` / `no_valid_surface` / `too_many_hits`.
- No speech, no main-nav coupling, no CLEAR/HOLD change from surface readings in this slice.
- Existing STOP / UNKNOWN / HOLD, confidence 128, pair proof, and processing-age gates remain untouched.

RED tests already ban floor/wall/clear/walkable substrings in kind+reason for success paths — keep that in source.

### H4 — GL lifecycle: context loss vs program/texture handles (High)

Baseline Activity already regenerates the OES **texture** in `onSurfaceCreated` but never builds a draw program. B contract correctly treats `onSurfaceCreated` as a **new context**: forget old program id without `glDeleteProgram` on the dead context; create a new program or throw.

Integration must also:

- Call `DiagnosticCameraBackground.onSurfaceCreated()` from the same GL `onSurfaceCreated` that gens the camera texture (order: texture ready before any draw that samples it).
- After context loss, never draw with stale `cameraTextureId` or stale program; Activity already zeros draw when `cameraTextureId == 0` / stopping / `!sessionResumed`.
- `releaseOnGlThread` only while a current context exists (stop path: GL `onPause` drains `onDrawFrame` before `session.pause` — preserve that order from pair/raw-depth work).
- Draw **after** `session.update()` on that frame; pass that frame’s timestamp and a transform closure that does not escape the `Frame` beyond the draw call; **never** retain `Frame`/`Image` on the background helper.

Failure mode if violated: black/wrong UVs, EGL errors, or use-after-free of GL objects — diagnostic crash risk, not a safety relaxation, but must not break stop/session isolation.

### H5 — Stop / session isolation from new GL work (High)

Surface hitTest + background draw add GL-thread work inside the existing `synchronized(frameInUseLock)` update path. Design constraints:

- `stopping` / `sampleGate.stop()` / `coverageStats.stop()` behavior unchanged; surface/background must not open a second session or keep the camera after `requestStop`.
- On stop: no further hitTest/draw; release background program on GL thread; do not hold `Frame` across pause/close.
- Exceptions from transform/compile/draw/hitTest → fail-closed diagnostic stop or skip-frame logging only; **must not** mutate pair-gate state, confidence threshold, or invent depth admits.
- Pure CPU `SurfaceProbePolicy` retains no SDK objects — good for teardown. Adapter must copy scalars before any plane list iteration ends / frame returns.

### H6 — Image privacy with a *visible* camera (High / policy)

Drawing the OES camera stream to the DUMP-protected diagnostic `GLSurfaceView` is still **live preview**, not storage. Contract + baseline Activity align if and only if:

- No `acquireCameraImage` / YUV copy / `glReadPixels` / screenshot / `MediaRecorder` / disk or network export.
- No new permissions; no INTERNET; no cloud anchors / geospatial (already disabled in `configureSession`).
- Helper retains no `Image`/`Frame`; preview is ephemeral framebuffer content.
- NOTICE / OPEN_SOURCE attribution update for shared_camera shader port is **Codex** (workers must not expand privacy surface while “fixing” the sample).

Visible preview increases shoulder-surf / recording-by-another-app risk versus black GL clear; acceptable only while remaining debug/DUMP-isolated and non-recording. Do not treat preview as calibration proof.

---

## Medium design findings

### M1 — `too_many_hits` bound vs four-ray concatenation

`select` rejects when **candidate list size > 64**, independent of how many are valid (RED tests lock this). An Activity that concatenates hitTest results from four rays in a plane-rich scene can exceed 64 easily and stick on `UNKNOWN`/`too_many_hits` even when nearer valid hits exist.

Not a safety defect (fail closed), but a usefulness / flakiness risk. Prefer one of (Codex design choice, document in result):

- Cap/truncate **before** `select` with an explicit reason distinct from silent drop, or
- Run `select` **per ray** then expose four readings / pick nearest among ≤4 readings, or
- Keep global 64 but log raw hit counts so phone runs are interpretable.

Do not “fix” by raising the bound without a test and a reason code.

### M2 — Equal-distance tie-break unspecified

Nearest wins; equal finite distances across kinds/rays are undefined (order-dependent). For diagnostics, either document “first minimum in encounter order after stable ray order” or add a deterministic tie-break test. Low safety impact; high flake risk if tests assume a kind without locking order.

### M3 — Plane finding enablement cost and config timing

Baseline: `planeFindingMode = DISABLED`. This pair needs horizontal **and** vertical plane finding for the kind enum to be meaningful. Enabling planes can compete with VIO/raw-depth CPU (research/08 previously suggested HORIZONTAL-only for Scene Lab). Diagnostic OK, but:

- Configure while session rules allow (typically before resume / via `configure` when paused) — do not hot-toggle casually mid-`update`.
- Do not change `RAW_DEPTH_ONLY`, confidence 128, pair scope, or admission cap to “make planes work”.
- Vertical plane ≠ wall; upward ≠ floor — restate in RESULT logs.

### M4 — Background draw vs depth-test / clear order

Activity clears `COLOR | DEPTH` every frame and draws nothing today. Official background renderers typically draw the camera full-screen with depth test disabled (or drawn first). Integration must not leave a depth state that makes the preview invisible, and must not introduce depth visualization. Pure visual; not a sensor.

### M5 — Attribution pin gap for shared_camera

`third_party/NOTICE.md` today attributes **raw_depth_java** lifecycle only. Queue says Apache notice is already vendored and Codex updates pins when B lands. Design review: **do not ship a copied BackgroundRenderer shader without updating NOTICE + license pin to v1.57.0 shared_camera path**. Workers B/S must not edit that shared notice file.

### M6 — Primary-source independence limit (process)

R’s live fetches of Plane / Plane.Type / Frame.hitTest and BackgroundRenderer.java were **approval-blocked**. Same stance as pair review: Codex owns official-contract fetch evidence this round; R does not claim independent re-verification of SDK wording until a later allowed fetch or local pin. Design acceptance below is therefore **contract-relative + Activity-relative**, not a substitute for Codex’s cited docs.

---

## Precise tests required (design checklist)

### S — already demanded by RED `SurfaceProbePolicyTest` (keep)

| Case | Expect |
| --- | --- |
| Session not tracking | `UNKNOWN` / `not_tracking` even with hits; wins over `too_many_hits` |
| Candidate not tracking / subsumed / outside polygon / `UNKNOWN` kind | Filtered |
| Non-finite, ≤0 distance | Filtered |
| Empty / all invalid | `no_valid_surface` |
| Unsorted distances | Nearest valid; order-independent for distinct distances |
| Invalid closer + farther valid | Farther valid wins |
| 64 vs 65 candidates | accept nearest / `too_many_hits` |
| Semantic honesty | no floor/wall/clear/walkable in kind+reason |
| Enum exact members | four kinds only as contracted |
| Viewport ≤0 | empty rays |
| Four named VIEW rays | exact names + pixel math at 200×400 and 1920×1080 |

**Recommended add before/with source (still S-owned if within exclusive files):** equal-distance deterministic tie-break; confirm `select` does not retain/mutate input list references in a way tests can observe (optional).

### B — required CPU tests with fake GL (worker B; not yet in tree)

| Case | Expect |
| --- | --- |
| draw before `onSurfaceCreated` / after `releaseOnGlThread` | `false`, no GL draw |
| `frameTimestampNs <= 0` or `textureId <= 0` | `false`, no UV reuse |
| Transform writes NaN / incomplete (not 8 finite floats) | `false`, no draw with prior UVs |
| Successful transform then changed UV contents next frame | Second draw uses new UVs only |
| `onSurfaceCreated` after prior program | Old handle forgotten (no delete on new context); new positive program or throw |
| `releaseOnGlThread` twice | Idempotent disable; no double-delete crash in fake GL |
| Transform or link/compile error | Propagated; draw disabled or false per contract; no retained Frame |
| No Android GLES calls from unit test process | Fake `CameraBackgroundGl` only |

### Integration / Activity (Codex later — not this worker)

- Plane mode enabled only in debug diagnostic config; main guidance unchanged.
- hitTest inputs = layout VIEW pixels; ignore non-plane hits; map `Plane.Type` → `SurfaceKind` without semantic rename in UI.
- Background draw uses same-Frame `transformCoordinates2d` NDC→`TEXTURE_NORMALIZED`; no manual UV matrix.
- Stop path still: set stopping → gate/coverage stop → `surfaceView.onPause` → `session.pause` → result; background released on GL thread; no image files.
- Surface/background failures do not admit depth, lower confidence, or speak.

---

## Contract acceptance (design phase)

| Contract | Design verdict | Notes |
| --- | --- | --- |
| S surface policy + four VIEW rays | **Accept for tests-first implementation** | Honesty + polygon + tracking/subsumed filters are the right fail-closed shape; watch M1/M2 in adapter |
| B OES background + SDK UV transform | **Accept for tests-first implementation** | Lifecycle/UV non-reuse/privacy constraints are necessary; H4/H5/H6 bind Activity wiring |
| Enabling planes + visible camera in diagnostic Activity | **Accept as diagnostic-only intent** | Must not alter depth admission or main nav; NOTICE pin for shared_camera is Codex |

Neither contract authorizes navigation authority, metric safety, wall/floor labels, Internet, models, or voice.

---

## Severity-ranked result (design only)

| Sev | ID | Finding | Disposition now |
| --- | --- | --- | --- |
| High | H1 | Keep VIEW hit rays ≠ depth/CPU ≠ texture UV domains | Block bad adapter mapping in source review |
| High | H2 | Polygon inclusion mandatory (hitTest leeway) | Contract OK — enforce in adapter + tests |
| High | H3 | No floor/wall/clear/walkable guarantee | Contract OK — ban in logs/UI/speech |
| High | H4 | New GL context invalidates program + texture | B lifecycle OK — wire with texture regen |
| High | H5 | Stop/session isolation with extra GL work | Preserve GL-then-session pause; no Frame retain |
| High | H6 | Visible preview ≠ store/cloud; no RGB Image acquire | Keep DUMP/debug; no capture path |
| Med | M1 | Global >64 candidate cap vs 4-ray concat | Fail closed; document adapter strategy |
| Med | M2 | Equal-distance tie-break undefined | Add deterministic rule/test |
| Med | M3 | Plane finding cost / configure timing | Measure later; don’t touch depth gates |
| Med | M4 | Draw order / depth-test for preview | Integration detail |
| Med | M5 | NOTICE must pin shared_camera when port lands | Codex shared docs |
| Med | M6 | R primary-source fetch blocked | Codex fetch not double-counted as R’s |

**Design-phase verdict:** Both proposed contracts are acceptable to implement behind RED tests as diagnostic helpers. No product code review yet. **No source acceptance claimed.**

## Await

1. S/B finish tests-only → Codex RED → release source in exclusive files.
2. Cursor R source follow-up in this same document (or an appended section) after helpers land — re-check H1–H6 against actual Kotlin, still no shell/build/ADB/Git/product edits.
3. Codex integration/QA owns Activity plane mode, hitTest wiring, background draw call sites, NOTICE pins, builds, and phone evidence.

`DESIGN_REVIEW_READY` — awaiting source follow-up.

---

## Source follow-up — 2026-10-20 (helpers + Codex integration landed)

Read-only re-check of landed Kotlin + notices. No shell/build/ADB/Git; R did not
compile or run tests (Codex owns focused compile now). No primary-source re-fetch.

Files: `SurfaceProbePolicy.kt`, `ArCoreSurfaceProbe.kt`, `SurfaceProbeOverlayView.kt`,
`DiagnosticCameraBackground.kt`, `RawDepthDiagnosticActivity.kt`,
`SurfaceProbePolicyTest.kt`, `DiagnosticCameraBackgroundTest.kt`,
`third_party/NOTICE.md`, `OPEN_SOURCE_LICENSES.md` (new top camera-background section).
No main-source / navigation coupling found.

### H1–H6 disposition (actual source)

| ID | Verdict | Evidence |
| --- | --- | --- |
| H1 VIEW ≠ depth/CPU ≠ texture | **Satisfied** | `ArCoreSurfaceProbe` uses `SurfaceProbeLayout` VIEW px → `frame.hitTest(x,y)` with Activity `viewportWidth/Height` after `setDisplayGeometry`. Background UVs: same `Frame` as `update()`, `transformCoordinates2d(OPENGL_NORMALIZED_DEVICE_COORDINATES → TEXTURE_NORMALIZED)`. Overlay decorative markers call the same `SurfaceProbeLayout` on the overlay view size (MATCH_PARENT over GLSurfaceView). |
| H2 polygon / plane filters | **Satisfied** | Non-`Plane` hits dropped; kind from `Plane.Type` only; `subsumedBy != null`; `isPoseInPolygon(hit.hitPose)` gated with tracking/`!subsumed`; policy rejects invalid / non-finite / ≤0 distance. |
| H3 semantic limits | **Satisfied** | Kind names orientation-only; logs/UI state `semantics=orientation_only`, `no_floor_or_wall_confirmation`, “upward may be a table”, no speech/nav. Depth path still `CONFIDENCE_THRESHOLD_DIAGNOSTIC = 128`, `SAMPLE_CAP_HZ = 10`, unchanged gate/pair scope. |
| H4 GL setup / context | **Satisfied** | `onSurfaceCreated`: new OES texture then `cameraBackground.onSurfaceCreated()` (forget-old-program). `preserveEGLContextOnPause = false` documented as terminal-probe cleanup backstop. |
| H5 stop / session isolation | **Satisfied** | Stop still sets `stopping`, `sampleGate.stop()`, `coverageStats.stop()`, then `queueEvent` release **before** `surfaceView.onPause`, then `session.pause`. Release deletes program + texture only if `eglGetCurrentContext() != EGL_NO_CONTEXT`; always zeros `cameraTextureId`. Single existing `Session`; cloud/geospatial still DISABLED. No retained `Frame`/`Image` on helpers. |
| H6 image privacy | **Satisfied** | Preview draw only; no `acquireCameraImage` / file / record path in these files. NOTICE + OPEN_SOURCE_LICENSES pin shared_camera_java `BackgroundRenderer.java` v1.57.0 and state ephemeral debug preview. |

### M1 / M2 / GL hygiene

- **M1:** **Handled.** Per-ray bound: raw `hits.size > 64` → that ray `too_many_hits`; else `select` on that ray’s plane candidates only — no four-ray concatenation into one 64-list.
- **M2:** Equal nearest distances use strict `<`, so **first minimum in encounter order** (hitTest order after non-plane drop). **Enough for diagnostic-only** — not navigation. No required code fix; optional later unit test locking equal-distance first-wins if flake appears.
- **GL:** Shader compile/link checked; failed program deleted; vertex/fragment shaders deleted in `finally`. Reused `BooleanArray(1)` for depth mask; direct quad buffers allocated once; no per-frame buffer alloc inside `DiagnosticCameraBackground` / `GlesCameraBackgroundGl`. Source URL matches NOTICE (`shared_camera_java/.../BackgroundRenderer.java`).
- **UI:** Overlay decorative only; `surfaceStatusView` gets a **string** built from kind/distance/reason scalars — not SDK objects.

### Compile / test blockers (static)

- **No source-level compile blocker** seen: types/package visibility, `Coordinates2d`/`EGL14` imports, debug-only surface helpers, main tree untouched.
- Stale test header comments still say helpers “not implemented” — documentation debt only, not a compile fail.
- No `ArCoreSurfaceProbe` JVM test (needs Frame) — acceptable; policy + background CPU tests cover pure logic. **R did not run Codex’s focused compile** — not claimed GREEN here.

### Severity-ranked source result

| Sev | Item | Action |
| --- | --- | --- |
| — | H1–H6 | No remaining design/integration blocker for this diagnostic slice |
| Low | Overlay vs GL viewport size equality | Decorative; same layout parent — monitor if markers ever used as aiming UI |
| Low | Stale RED-phase comments in S/B tests | Optional cleanup by owning worker/Codex |
| Info | M2 first-min tie | Documented sufficient; no fix required |
| Info | Plane-finding cost (M3) | Still measure-on-device later; gates unchanged |

**Source disposition:** Bounded diagnostic helpers + Activity wiring **accepted for Codex verification** (compile/focused tests/full gate/device as Codex owns). This is **not** a build, APK, phone, or accuracy pass.

`SOURCE_REVIEW_READY` — return to Codex.
