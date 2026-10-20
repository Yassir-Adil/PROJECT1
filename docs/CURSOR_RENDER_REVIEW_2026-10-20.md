# Independent renderer review —2026-10-20

## Final source acceptance

Independent rereview: **no remaining scoped High/Medium**. Bounds retention,
identitycallback/detach cleanup, boundedcopy, synthetic monotonic/terminal expiry
and explicit trace scopes accepted. Source-only, no execution claim. ResidualLow:
duplicated diagnostic250ms constant and helper-vs-Android coverage. Codex stable
fullgate836tests/build/policyPASS, independently rechecked source fingerprint.
No physical rendering acceptance yet. Older pending wording below is history.

Source-only Cursor R reviewed display decisions/trace, live overlay, painter,
synthetic session/Activity and debug manifest allowlist. No executed tests,
build, phone access or writes claimed by reviewer. Codex independently owns all
RED/GREEN, integration and gate results.

| Initial finding | Disposition |
| --- | --- |
| INVALID_BOUNDS discarded otherwise fresh result | Fixed: retry retains publication only within original receipt250ms; terminal/content rejections clear. Missing-policy API RED then GREEN. This was an introduced source-level recovery risk, not the old Redmi cause proved. |
| Anonymous expiry invalidates could outlive publication | Fixed: one replaceable/cancelled identity-checked Runnable, detach cleanup. Two-source-guard RED included callback/size tests. Prior1Hz cadence reduces normal overlap; no claim it caused existing failure. |
| Synthetic size pass may require retry | Added onSizeChanged invalidation while synthetic session active. Android normally redraws on layout already; accepted as defensive explicit wiring, not reproduced phone fault. |
| Counter interpretation / helper-only coverage | Logs keep navigation=none/human_visibility=unverified; UI timing includes rejects, minimum budget session-scoped. Source guards are not Android runtime proof. |

Codex additionally added three actual failing regressions for bounded copy and
synthetic clock/expiry re-entry, then fixed them. Palette/lifetime/raw gates and
normal guidance unchanged. Focused103 and policy25 executed PASS. Final source
rereview and full gate pending; append actual completion below.
