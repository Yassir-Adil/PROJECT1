# Render attribution and isolated drawing self-test —0.9.36/49

## Actual device acceptance — supersedes pending notes below

0.9.36/49 USBinstalled/basehashverified. Owner-ready5s synthetic COMPLETE:
2Canvascalls/128cells, owner confirms grid/colours/disclaimer. Separatelyready
20.17sAUTOMATIC+semantics COMPLETE+CLOSED:22rawadmits,8fresh/0stale/0groundcandidate;
UI8/View8/freshCanvas8,370paintedcells,1expiredprepaintdraw rejected. Owner confirms
colouredcells over roompreview. Phone force-stopped/noPID. Receiptp50/p90
56.857769/241.440692ms n8; no causal comparison with035, which had1rawadmit.
Groundsupport0–4,6too_few_geometry_samples/2no_bottom_floor_component; all64height
UNKNOWN. Visibility confirmed THIS run, not wall/floor/rug accuracy or navigation.
Full metrics/hashes/limits PHONE_RENDER_0936_2026-10-20.md. No further owner action.

## Final engineering checkpoint

Stable gate `20261020T143606116459Z` **PASS836tests**:
715app+10sim+25policy+86evaluation,0fail/error/skip; bothAPKs/model pins,
permission/debug isolation and known-private-root policyPASS. Source230files
SHA256 `e62a859d436beaf9b7d8fa17d6f02d874ebf064ebc1bb5de110bef88e408e55d`
independently rechecked after gate. Debug/stagedAPK SHA256
`a91df49c8dfff7cf4dc7a04a97c349448d07647948db193c6cd22ba09a8ac66e`;
releaseunsigned `675a14ecbe847c79761817d255a9913cfc192f9563e3eb3c975a4d21c47d986c`.
Final independent R rereview no remaining scopedHigh/Medium; all worker tasks
finished. No test/build/runtime claim attributed to Cursor.

Lint0fatal/error;51debug/14release warnings. Debug rose by6 SetTextI18n warnings
for synthetic lab text concatenation/literals; normalrelease unchanged. Moving
lab copy to debug-localized XML resources is a small follow-up, not a suppressed
warning or device-safety fix. Prior custom-view constructor warning remains.

0.9.36 artifact is staged locally; NOT yet installed or physically tested.
Owner asked to unlock/look for5s synthetic grid; awaiting readiness. No camera
start or new room/movement request. Earlier pending gate wording is history.

## Scope and actual trigger

Owner clarified that the intended coloured semantic grid did NOT appear on
0.9.35. Recorded publish1/Canvas0/submitted0 and one239.720308ms result within
the original250ms receipt allowance cannot identify the precise missed-draw
stage. Do not claim a model failure, confirmed compositor bug or physical fix.

This round implements the first recovery stage as two coherent improvements:
bounded render-stage attribution, and a synthetic drawing self-test using the
same painter. Diagnostic2D/raw-depth decoupling stays the NEXT stage; it has not
been silently implemented or enabled. Normal camera/navigation/voice unchanged.

## Implemented

1. `SemanticFusionPublication.decision` exposes STOPPED, NOT_TRACKING,
   REVISION_CHANGED, INVALID_TIMESTAMP, CLOCK_ROLLBACK and EXPIRED while
   `canPublish` remains the same display eligibility. ALLOWED is NOT permission
   to move. Original receipt250ms boundary remains inclusive and unextended.
2. `SemanticDisplayTrace` counts UI delivery, View acceptance, onDraw entry and
   rejected reasons separately. Original receipt age and worker completion→UI
   queue p50/p90 use local elapsed clock only, last60 samples with explicit n.
   Minimum remaining UI budget is session-scoped; timing includes rejected UI
   deliveries and is not an accepted-only inference distribution. Terminal stop
   freezes trace. No per-frame log flood, images, coordinates or sensor-time math.
3. Shared `SemanticGridPainter` preserves exact live blue/purple/amber palette,
   fillalpha40/strokealpha180 and one density-wide stroke. `SemanticPaintCell`
   has no world geometry; converting real depth cells strips coordinates and
   rejects wrong count BEFORE copying. No opaque retained bitmap/Canvas.clear.
4. `SemanticRenderDiagnosticActivity` is debug-only, DUMP-protected and uses no
   camera, ARCore session, model, depth, navigation, speech or image/file capture.
   It displays a clearly labelled SYNTHETIC pattern on dark background, default
   5s, allowed3..15s (invalid duration fails rather than clamps), with a Stop
   button and automatic stop. Synthetic lifetime is a different type from live
   receipt freshness and cannot reach ground evaluation. Stop and expiry are
   terminal, rollback is rejected; onPause/onDestroy cancel callbacks.
5. Temporary invalid View bounds retain an otherwise fresh live publication and
   retry only within its ORIGINAL deadline. Terminal/content rejects clear it.
   One cancelable identity-checked expiry callback replaces anonymous delayed
   invalidates and is canceled on replacement/clear/detach. Synthetic size
   changes request a redraw. These are source-level risks/backstops; not proven
   causes of the old Redmi report. No opacity, model, cadence or TTL increase.

## Tests-first execution and independent review

Codex executed real compile RED for missing decision/trace/paint APIs before
implementation. Cursor A was released ONLY for paint helper/painter + response
doc, no other writes or shell/build/test/Git/ADB/network. Codex reviewed actual
source, independently ran GREEN and fixed its unbounded pre-validation copy.

- Missing synthetic session compile RED separately recorded before source.
- Actual policy RED when adding reviewed fifth debug component, then allowlist
  expanded exactly to include the DUMP-protected self-test, not arbitrary entries.
- Three actual assertion/runtime failures: oversized input was read before count
  validation; synthetic expiry could reenter on rollback; backwards time could
  paint earlier. New tests first, then fixed; no threshold bypass.
- Independent Cursor R found temporary-bounds result loss and expiry/size-retry
  risks. New missing-clearsPublication API RED and two source-contract failures
  recorded before fixes. Source guards are NOT Android measurement/UI tests.
- Final focused **103tests/12suites PASS**,0fail/errors; policy **25PASS**.
  One default250ms gate coverage test already passed: no fabricated RED for it.
- Full stable engineering gate / final R source rereview are separate acceptance
  stages; their final result is recorded below when complete. No worker test or
  device execution claim. Review reports: CURSOR_PAINT_GRID_2026-10-20.md and
  CURSOR_RENDER_REVIEW_2026-10-20.md.

Ignored local evidence logs (not published): semantic-render-0936-red,
semantic-render-session-red, semantic-render-policy-red,
semantic-render-0936-review-red, semantic-render-isolation-red,
semantic-render-bounds-red, semantic-render-0936-focused-final,
semantic-render-policy-final and semantic-render-0936-fullgate, dated20261020.

## Physical acceptance NOT yet run

Do not treat JVM helper tests, source guards, Canvas command counts, Android
callback entry or a complete build as screen/compositor/human confirmation.
The actual installed phone remains the previous version until separately
verified. Do not automatically start the camera without fresh owner setup.

After stable gate and exactAPK hash/version check, ask owner to unlock and look
at the phone for the5s SYNTHETIC self-test. Explain expected blue/purple/amber
grid beforehand; no room setup or walking needed. It must show the explicit
synthetic/no-camera disclaimer. Record result and resource closure/stop; failed
or interrupted tests remain failures/interruptions, never inferred passes.

Only after synthetic drawing is observed, request a separate lit floor-visible
20s scene with feet still to inspect SEMANTIC_DISPLAY_TRACE alongside existing
SEMANTIC_OVERLAY_RESULT. Counters classify where display stops; no trace entry
authorizes walking, wall/floor identity, rug height or hazard accuracy.

## Explicit limits / next stage

No model/network/permission/cloud/training/hardware or production navigation
change. Research weights stay debug-only, public backup is sanitized source,
not APK/media/rawlogs/weights. Existing Lint warnings, real voice/TalkBack,
semantic accuracy, ground identity, sparse depth and sustained performance are
still separate gates. See DISPLAY_RECOVERY_PLAN_2026-10-20.md for typed2D
observation/geometry separation and data/reuse options after renderer acceptance.
