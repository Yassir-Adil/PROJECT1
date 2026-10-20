# Semantic canvas / ground-reason diagnostics0.9.34 —2026-10-20

## Follow-up0.9.35/48 — stable diagnostic text height

Codex found an additional concrete source-level layout risk after the810-test
0.9.34 checkpoint: status/surface/semantic TextViews used WRAP_CONTENT with only
maxLines. Accepted-result/expiry text can change line count, resize the weighted
GLSurfaceView, trigger display-geometry revision invalidation and clear its own
overlay. That path is possible from source; it is NOT a reproduced root cause of
the owner's0.9.33 colour report without new phone data.

Use existing Android TextView.setLines(6/5/4) so dynamic status content reserves
fixed line budgets. This is exact line-height layout per official API, not a
hardcoded pixel size. It may reduce initial preview area; viewport minimum-size/
rotation/font-scale usability remains a physical check. No nav/threshold/250ms/
1Hz/paint styling change. Added semantic_geometry_invalidations counter for the
next run, counting SDK display-geometry refreshes, not semantics/accuracy.

Actual new source-contract unittest RED for all3oldmaxLines definitions, then
GREEN. This is explicitly a SOURCE guard, not an Android measurement/golden
render/UI test. Full gate and narrow independent review in progress.0.9.34
remains installed while0.9.35 is being verified; no new camera confirmation yet.
[Official TextView.setLines](https://developer.android.com/reference/android/widget/TextView#setLines(int)).

Final0.9.35 stable gate20261020T102847617148Z **PASS811tests**:694app+10sim+
21policy+86eval,0fail/error/skip; bothAPKs/model/permission/debugisolation/private-
rootpolicyPASS. Lint0errors,45debug/14release warnings. Narrow independent R
review no remaining scopedHigh/Medium. Source220files SHA256
`a128a9ba28735efbde89d66819c181a24d933aea05a3ae573307e6c3ca586697`.
DebugAPK/installedbase SHA256
`3f451b6d21b823bcf4a4b8f636f7d5cbf96911382ad5ad89a5c63dad1667ea04`;
releaseunsigned `2be53838911776ddc408f023ae7e70e02768992a44cf44353cb78bf18d215867`.
USBinstall48/0.9.35 succeeded, exact installedhash checked, appforce-stopped/noPID.
**No0.9.34/0.9.35 camera run yet**: fresh owner scene confirmation still pending.
Owner's last actual visual report remains uprightimage/no-colouredcells on0.9.33.

## Measured trigger, not a guessed fix

Actual0.9.33 run is in PHONE_SEMANTIC_0933_2026-10-20.md:13freshfusion/0stale,
0groundcandidate; owner confirms upright image but no colouredcells noticed.
Inference logs did not prove View.onDraw or human visibility. Supports reached6
but old insufficient_support status could not expose span/area/height rejection.
No original points recorded, so the old exact reject reason is unrecoverable.

## Two diagnostic-only changes

1. `SemanticOverlayStats` distinguishes main-thread publication, distinct fresh
   Canvas submissions, repeated draw calls/painted cells, cleared-without-draw,
   and bounded first-publish→canvas p50/p90. Checks monotonic sourceidentity,
   original receipt age, current publication, allowed stop/tracking/revision and
   terminal stop. Rejects empty/oversized painted counts. Canvas submission is
   NOT compositor presentation, phone-screen visibility or human confirmation.
2. `SemanticGroundReading.reason` names insufficient count, XZspan, triangle
   area/collinearity, nonhorizontalY and invalid/overflow cases; accepted result
   explicitly single_frame_floor_like_only. Old statuses/thresholds/components/
   geometry and unknown64 contract unchanged. Logs/UI include the reason.

No new colour styling/lifetime/1Hz/model/permission/production guidance change.
Live cells still expire250ms from receipt and are cleared on stop/tracking or
geometry invalidation. Instrumentation does NOT keep stale results visible.
No model/runtime/package download. Sourcecandidate0.9.34/47, normalappunchanged.

## Tests and review

- Codex wrote9new regression tests first and executed real compile RED for
  missing reason / SemanticOverlayStats before implementation.
- CursorH implemented ONLY ground helper/reasons/doc, independent scope; Codex
  reviewed diff and owns pure stats/Activity/overlay/tests. No worker test claim.
- Tests cover distinct failure causes with unchanged thresholds; canvas vs
  inference, duplicate paints/publications, stale boundary, cleared-withoutdraw,
  terminal stop and invalid/unpublished identities. Prior focused regressions
  executed successfully with the new tests.
- Full engineering gate / independent source review in progress. Candidate not
  phone-tested yet.0.9.33 runtime does not verify these new counters.

Initial109focused/806full tests/build/policy PASS preceded review improvements.
M2 paint-boundary counter ambiguity fixed with missing-fieldRED; actual13case
subset then caught local publication rollback assertionRED, fixed without
poisoning prior/next sample. Four additional tests (two reason edge cases already
correct) bring final focused **113PASS**. New submitted_calls/submitted_cells are
actual known-current paint commands; fresh canvas counts require end-of-paint
revision/age check. rejected_after_paint_calls exposes expiry/invalidation during
paint; cleared_without_fresh_canvas does not mean no drawRect ever occurred.
One empty redraw backstop added on expiry. No broad Canvas.clear, stale extension
or opacity change. Final stable fullgate and source rereview pending.
Review/disposition: CURSOR_DISPLAY_REVIEW_2026-10-20.md.

## Final engineering checkpoint

Stable gate20261020T102314054144Z **PASS810tests**:694app+10sim+20policy+86eval,
0fail/error/skip; both builds/model/permission/isolation/private-root policy pass.
Lint0error/fatal,45debug/14release warnings. Independent final R review no
remaining scopedHigh/Medium; no reviewer execution claim. No productionchanges.
Source219files SHA256 `42a8a21bddc5126d7fcb98406568e52e8c746fe276b249aa105c7454dc5dfbfa`,
independently rechecked before USBinstall.0.9.34/47 installed/basehash matched:
debug `218af4e42ab97ade0a90e3a3e8402d82d6fd5ef09706b97f7673da7054a73d5a`;
releaseunsigned `e22f184702c1af2f87abf8b666f715b5f74cc3c9c01bc7636e72b5ff144e9b27`.
New camera run awaits fresh owner setup; no implicit runtime/visibility pass.

## Next device acceptance

Fresh owner stationary floor-visible setup required. Verify exact installedhash,
COMPLETE+CLOSED, precise reason counts and SEMANTIC_OVERLAY_RESULT. If publication
is positive but Canvas0, investigate UI/draw path; if Canvaspositive but owner
still cannot see colours, presentation/briefness/contrast remain hypotheses—not
an inference failure or permission to extend stale live masks. Never call this
floor/obstacle/safe-walking accuracy. No image/point/audio storage or uploads.
