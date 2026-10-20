# 0.9.23/36 — storage resilience and isolated Raw Depth

Later phone follow-up: `docs/RAW_DEPTH_REDMI_2026-10-20.md`. Owner unlocked;
two actual timed attempts were interrupted, accepted depth0. The older
"screen locked / not attempted" status below describes the earlier checkpoint.

## Current result

Implemented and desktop-verified, installed on the personal Redmi over USB.
This is not a wall/rug-height accuracy pass or a movement-policy update.
Main detector-only guidance still HOLDs/UNKNOWN; no CLEAR/turn, Internet
permission, cloud/API key, new model or hardware was added.

## Tests first / independent review

- Cursor wrote storage and decoder tests first. Codex recorded missing-helper
  compile RED before implementation, then reviewed each worker's result.
- 84 focused JVM tests passed before the first full gate (456 tests total).
- Independent Cursor review found full RAM mutations being conflated together
  with disk jobs, plus RAM readers waiting on the writer's fsync lock.
- Seven additional isolation tests went compile RED before the corrective
  implementation. The first implementation then failed the existing oversized
  legacy-file preservation test: session startup was overwriting rejected data.
  Fixed startup to leave object bytes untouched until actual object mutation;
  the old test was not weakened. Final full gate passed all424 app tests.
- Follow-up independent source review marked F1/F2/F3 resolved and F4 an explicit
  best-effort note-persistence limitation. Review did not itself run tests.
- Cursor QA shell commands were rejected in its session; Codex ran the gate and
  APK install directly. No permission bypass or Cursor approval setting change.

## Final offline gate

`outputs/reports/quality-gate/20261019T172320780644Z/summary.json` — PASS.
The UTC directory is Oct19; the owner-facing Malaysia date is Oct20.

| Check | Measured result |
| --- | --- |
| App JVM | 424, no failures/errors/skips |
| sim-core JVM | 10, no failures/errors/skips |
| Python APK/policy | 19, no failures/errors/skips |
| Python evaluation | 10, no failures/errors/skips |
| Total | 463; baseline was377 |
| Debug/release builds | PASS, offline; no new dependency download |
| Lint | 0 errors/fatal;54 debug /34 release warnings retained |
| APK policy | Four pinned debug models, one release model; no private image |
| Isolation | DUMP-protected debug probe; no ARCore/probe in release |
| Permissions | Existing CAMERA, VIBRATE and internal receiver only; no INTERNET |
| Known private Git roots | 0 tracked private artifacts |

Source/config/test fingerprint:
`721db8244c0b89e07d06867d7b135b20018d81ff7819bb27302ce60fae95edfd`
(166 files; documentation excluded by the gate's declared scope).

Final debug APK SHA256:
`3029bc642888e6ea20cccd28195156e22037ca23382d3988ac532679f1d5db58`
(118,395,402 bytes), staged at `outputs/apk/TriGuideAI-0.9.23-debug.apk`.
Release unsigned SHA256:
`42a5d318f5ba650706f006ef32fb3c99009e036fcd4dc4346806df42065d1e13`.
No release/APK was uploaded publicly.

## What changed

1. Bounded strict-UTF8 reads, atomic staged/fsynced replacement with no unsafe
   overwrite fallback, malformed-input validation, load caps, saturating counts
   and reboot-safe insertion chronology. Failed durable person save/delete does
   not claim success; consent defaults off and no faces are stored.
2. RAM notes update immediately; only disk writes are conflated into one worker
   plus one latest pending job using the existing coroutines runtime. Immutable
   combined snapshots avoid losing a description when a later label arrives.
   Separate disk locking keeps routine readers off fsync. Durable clear has an
   explicit Boolean API; queued old jobs cannot resurrect successfully cleared
   notes. No navigation/speech jobs enter this queue.
3. Isolated debug Raw Depth instrument reuses the official ARCore sample's
   lifecycle/GL pattern and existing SDK. Full UINT16 depth + UINT8 confidence,
   bounded stride-aware decoder, distinct timestamp/tracking/processing-age gate,
   at-most10Hz sampling, nested Image close, terminal Stop/pause/timeout.
   No RGB/depth file acquisition or image recording; only aggregate metrics.
   Apache source attribution/license retained separately from SDK terms.

## Actual Redmi evidence this round

- Original installed0.9.22/35 APK preserved before install; SHA256
  `05e3bcbbb8df465cd8b91cd1de3c89f19605ec5a3e5f300ddd48b16a3c5473ca`.
- `adb install -r`: Success; package reports0.9.23/36. Pulled installed APK
  matches the final gated debug hash exactly. App data was not cleared.
- Invalid duration0 probe: one `RAW_DEPTH_RESULT status=FAIL
  reason=invalid_duration`, zero camera/depth frames/accepted samples, followed
  by `RAW_DEPTH_CLOSED`. This is a successful refusal test, not depth success.
- Phone screen subsequently locked; no lock bypass. A valid timed raw-depth
  probe, Stop/background lifecycle, main UI regression, heard voice and TalkBack
  are **not tested for0.9.23** yet. App left force-stopped, no camera running.
- Existing earlier0.9.22 UI smoke remains historical evidence only.

Private local evidence: `outputs/reports/cursor-20261019-next/` (ignored),
including RED logs, first integration failure, final gate and installed APK
backups. Nothing in this report proves wall distance, mat/step height, drop-off
recall, total camera latency, battery/thermal stability or walking safety.

## Remaining gates, in order

1. Unlock the connected Redmi; deliberately run a bounded20s raw probe while
   stationary, with only small hand motion if needed. Verify accepted distinct
   raw timestamps, confidence coverage, rejection reasons, processing latency,
   Stop/background close, and zero late publications. NO_DEPTH is a valid
   diagnostic outcome, not permission to lower confidence or invent distance.
2. On held-out consented scenes with measured references, evaluate wall/floor,
   flat mat vs raised edge and steps. Verify image geometry/alignment/ground
   reference and distance/height error before proposing depth-to-hazard fusion.
   Do not integrate into CLEAR/turn just because depth values are available.

Ready models/datasets are already evaluated in the existing roadmap; reuse them
when they address a measured failure. Defer new training, model replacement,
hardware/cloud/purchases and broad UI/database migration until evidence justifies
them. Local issue intake uses `docs/FIELD_ISSUE_WORKFLOW.md`; there is no automatic
capture/upload feature.

Known limitations: two files are not one atomic transaction; a failed clear may
partially remove a file but returnsfalse and does not claim success. Note writes
are best-effort, can fail/cancel at shutdown, and do not promise cold-start
durability. A failed consent-disable write takes effect in RAM, not necessarily
after restart. Power loss, real filesystem faults and main-looper performance
are not certified by JVM tests. Lint warnings and dependency/license completeness
remain open; no blanket safety or security certification.

## Source backup

The owner authorized a PUBLIC sanitized source backup, superseding private-only
history. Baseline e0859e6 is remote-verified and also copied as a SHA-verified Git
bundle to the owner's phone. Current final source was committed by Codex at
`999bb71f75f5426a1c47da452461817b0c3c020e` and pushed to the existing
`cursor/phase0-project-foundation` branch. A separate `git ls-remote` confirmed
that exact SHA on origin after push; this is an actual remote backup, not a
pending Cursor task. This receipt is documentation only; the accepted APK/source
fingerprint above did not change.
Exclude private media, logs, datasets, APKs, weights and the unrelated dirty
owner-request rule. Do not change repository visibility or force-push.
