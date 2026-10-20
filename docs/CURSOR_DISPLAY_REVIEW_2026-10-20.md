# Independent display/reason source review —2026-10-20

CursorH reason-only patch reviewed by Codex: no statuses, thresholds, components,
labels, height classification, unknown64 or admission changed. New precise
reason branches plus backwards-compatible default field only; no H test claim.

Separate read-only R initial review:

| Finding | Codex disposition |
| --- | --- |
| M1:expired onDraw may need explicit Canvas erase | Not established as a retained-pixel defect. Normal invalidate re-records View display lists; no retained bitmap here. Avoid broad Canvas.clear on parent drawing. Added one empty redraw backstop on observed expiry, never extends result lifetime. Physical screen clearing remains unverified. |
| M2:paint can cross freshness boundary before end-check, hiding commands from counter | Fixed with actual tests-first newfieldRED: submitted calls/cells tracked separately from fresh canvas samples/calls/cells and rejected-after-paint. Invalidated mid-paint queues empty redraw. No claim of compositor/humanvisibility. |
| M3:alpha40/briefcells may not be seen | Real usability hypothesis, not proof of render failure; style/lifetime unchanged until actual counters+owner check. Do not weaken stale gates. |
| L1:more reason-edge tests | Added malformed size/structure/partial geometry and overflowing finite-coordinate span tests. These already-correct branches passed; no fabricatedRED. |
| L2:workeraccepted/UIpublished/Canvas counts differ legitimately | Explicitly documented as different gates/stages, not one performance/success count. |

Codex also found local publication-clock rollback could replace current sample:
new actual assertionRED then lastPublishedElapsedNs guard, rejected update does
not poison current or next identity. Terminalstop/repaints/empty draw gates remain.
Final focused **113tests/0fail** actualCodex run; fullgate/Rrereview in progress.

Post-fix independent R re-review COMPLETE: **no remaining scoped High/Medium**.
M2/counter layers and publication rollback guard accepted; M1 expressly retracted
as an unestablished retained-bitmap defect, empty redraw accepted as defensive
backstop only. Same-thread UI paint/clear/stop serialization retained. Ground
thresholds/status/admission unchanged. M3 remains unverified visibility limitation.
No reviewer test or runtime execution claim. Final stable gate still separate.

Codex final stable gate810tests/builds/APKpolicies PASS, source219file hashmatched
before install; this does not change source-review scope or prove phonevisibility.

Codex subsequent layout audit found maxLines-only dynamic status heights could
resize the weighted GL viewport and self-invalidate pending samples.0.9.35 sets
exact line budgets6/5/4 using existing TextView.setLines, with source-contract
RED→GREEN (not device layout proof), and geometry-invalidations counter. Narrow
source review/full gate pending; no claim this reproduces0.9.33 visual failure.

Final narrow0.9.35 source review complete: no remaining scopedHigh/Medium;
setLinesAPI placement/counterlock/lifecycle accepted, staticregex guard explicitly
NOTAndroidmeasurement. Codex811fulltests/build/policyPASS, installedhashverified.
No035physicalvalidation yet; shorterpreview/rotation/font-scale usability remain.

Primary drawing contract checked2026-10-20:
[Android hardware drawing/display lists](https://developer.android.com/develop/ui/views/graphics/hardware-accel).
Documentation/source reasoning is not physical presentation validation.
