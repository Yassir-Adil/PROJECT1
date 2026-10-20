# Shared execution queue — 2026-10-19

## Active pair — 2026-10-20, same-Frame provenance and honest observed coverage

FINAL DISPOSITION:0.9.27/40 installed/hash verified;499-test full gate PASS
(460+10+19+10),0 failure/error/skip; builds/APK policy pass, Lint0 errors55/34
warnings.48 focused tests passed; R corrected-source review accepted. Actual
20.16s Redmi probe:553 distinct/521 tracking frames,142 decoded pairs,9 diagnostic
admits,113 empty-confidence and20 repeated-depth rejects; COMPLETE then CLOSED.
Observed confident coverage0.411581% across all decoded/reprojected pairs, not
navigation coverage. Sensor age/calibration/wall/step safety remain unverified.
All Cursor workers finished; no owner action needed now. Main guidance unchanged.
Full evidence/next ranked options: RAW_DEPTH_PAIRING_AND_COVERAGE_2026-10-20.md.
The phase instructions/RED records below are retained as execution history.

Baseline f72e54e /0.9.26,467 tests; preserve unrelated owner-rule dirty edit.
The prior equality/lag gate was conservative application policy, not a proven
SDK timestamp contract. Official Frame documents same-current-Frame pixel
correspondence; Image documents source-specific clocks. Diagnostic-only revision:
prove co-acquisition within one unchanged Frame and active resource scope,
compare camera/depth stamps only to prior stamps from their own source, and
retain the250ms local monotonic processing-age gate. No tolerance, confidence
threshold reduction, new dependency, camera sharing or production navigation.

| Task | Owner | Exclusive write scope | Sequence |
| --- | --- | --- | --- |
| P tests then implementation | Cursor P | RawDepthSampleGate.kt, new RawDepthPairScope.kt and their testDebug tests; CURSOR_PAIR_SCOPE_RESULT_2026-10-20.md | Tests ONLY first; Codex records RED, then releases implementation |
| C tests then implementation | Cursor C | new RawDepthCoverageStats.kt and its testDebug test; CURSOR_COVERAGE_RESULT_2026-10-20.md | Tests ONLY first; Codex records RED, then releases implementation |
| Integration / QA / phone / backup | Codex | Activity, version/policy/shared docs, full gate, ADB, scoped Git | After independent review; no overlapping edits/builds |

P contract: RawDepthPairScope.withFrame(frame, timestampOf, acquireDepth,
acquireConfidence, block) captures one frame object, checks positive unchanged
frame timestamp around acquisition, closes confidence then depth on all exits,
and invalidates a private-constructor active proof after block. Generic
AutoCloseable resources permit CPU tests. Block receives (scope, depth, confidence).
Gate.accept(pair, depthTimestampNs, confidenceTimestampNs, receivedElapsedNs,
completedElapsedNs, tracking) rejects inactive proof, invalid source stamps,
tracking loss, local stale/rollback, duplicate/backward frame or depth, and stop.
It never subtracts unrelated clocks or demands depth/confidence equality.
Remove the obsolete maxSensorLagNs option; keep maxProcessingAgeNs default250ms.
Test changed Frame, acquire/process/close failures, exact-once cleanup, closed
proof, cross-clock unequal valid stamps, new/reprojected data and all negative
gates. Rejections must not poison later valid admission. No navigation authority.

C contract: RawDepthCoverageStats(maxPairs:Int=10000), record(summary):Boolean,
snapshot():RawDepthCoverageSnapshot, stop(). Immutable scalar snapshot fields:
decodedPairs, sampledPixels, validDepthPixels, confidentPixels,
pairsWithConfidentPixels (Long); validFraction/confidentFraction (Double? null
before observation); capacityReached (Boolean). Count all successfully decoded
pairs regardless of later timing/pixel rejection; these are NOT accepted samples
or unique new estimates. Validate sampled1..1048576 and0<=confident<=valid<=sampled
before mutation; bounded maxPairs, reject after capacity or terminal stop.
Test zero versus missing, weighted fractions, sparse/empty summaries, malformed
counts, capacity, immutable snapshots, stopped/idempotent. No Images/buffers held.

Workers read AGENTS/current scope, do not edit shared docs/Activity/Gradle/policy,
run shells/build/ADB/Git, access private outputs, or bypass denied permissions.
Stop after test phase until Codex explicitly resumes the same worker.

C RED recorded by Codex: offline test compile fails in20s, unresolved
RawDepthCoverageStats (and new PairScope tests); ignored coverage-red.log under
outputs/reports/raw-depth-pair-20261020/. C is released for helper source only.
P RED independently recorded in pair-red.log: missing RawDepthPairScope/Proof
and pair API after both test files landed. P now released for source only.
Cursor R design review requires private proof construction after both acquires,
finally invalidation, dual cleanup even on close failures, and explicit unknown
physical sensor age. Codex freshly fetched official Frame/Image contracts; R's
own re-fetch was approval-blocked and is not counted as independent live fetch.

## Final0.9.26 checkpoint —2026-10-20

Strict D_16 guard corrected and four RED-first tests pass. Direct SDK tracking
reason telemetry added after a full0.9.24 NO_DEPTH run; no gate/threshold change.
Final0.9.26/39:467 tests/builds/APK policy PASS, Lint0 errors with55/34 warnings,
installed hash verified. Lit view reached537/566 tracking and146 validated pairs.
Last20.18s:194/551 tracking,46 pairs,40 insufficient-pixel and6 timing rejects,
0 accepted; CLOSED. Timestamp numeric difference -67.484ms does not prove clocks
compatible. SDK pixel correspondence documented; exact image timestamp equality
not guaranteed in reviewed text. Current gate unchanged.
Cursor format+telemetry review and primary timing research complete; no worker
remains active. Codex checked contracts/phone evidence. No source
overlap with unrelated owner-rule edit, which must remain preserved unstaged.
Next: tests-first typed same-Frame acquisition provenance and clock-domain review,
then diagnostic admission evaluation; no invented tolerance. Accepted depth and
calibrated geometry remain unverified. No owner action or new image upload is
currently needed. No purchases/model/safety changes.
Evidence: RAW_DEPTH_D16_FIX_2026-10-20.md. Earlier candidate notes below are history.

## Active correction — 2026-10-20, Raw Depth format guard

Owner coordinated a no-input foreground probe. On installed0.9.23 it reached
TRACKING and actual depth acquisition, then failed `unexpected_depth_format_48`.
Official ARCore returns HardwareBuffer.D_16 (48); the activity incorrectly
required the unrelated packed ImageFormat.DEPTH16. This is a confirmed source
bug, superseding speculation from the earlier interrupted runs.
Codex wrote four regression tests and recorded compile RED before adding the
strict format helper and wiring. Candidate0.9.24/37 is in the full offline gate.
Cursor owns ONLY the read-only format review document, with a follow-up after
the helper landed. Codex alone owns source, Gradle, install and phone tests.
No model, confidence threshold, image lifetime, navigation or permission change.

## Phone follow-up — 2026-10-20

Owner unlocked Redmi. Two actual20s probes were interrupted on pause at15.49s
(426 nontracking frames) and0.76s (0 frames), accepted depth0. Second run has
RESULT+CLOSED; first explicit close unobserved. Back/relaunch/debug-app events
and intermittent USB are recorded without attributing their origin. No guard
weakened or source/model/permission changed. Await a coordinated20s no-input
foreground interval; this is not blocked on phone unlock alone anymore.
`docs/RAW_DEPTH_REDMI_2026-10-20.md` supersedes earlier phone status below.

## Current owner update — 2026-10-20

The owner explicitly reversed the privacy choice: PROJECT1 may remain PUBLIC;
reviewed source backup is approved, excluding private images/logs/datasets/debug
weights. This supersedes the private-only restriction in historical text below.
Initial local authentication failed (401). A later read of origin confirmed
e0859e66707f4b1451fe4fc1ffdb580b7af91bf9 on the existing branch, and authenticated
`git push --dry-run` succeeded. The final source improvement commit
999bb71f75f5426a1c47da452461817b0c3c020e was pushed by Codex and separately
remote-ref verified on the existing branch. Do not attribute an
observed external push to a Cursor task that did not actually perform it.
Baseline e0859e6 is also stored on the owner's Redmi as a verified 4,059,915-byte
Git bundle at Download/TriGuideBackups/TriGuide-source-e0859e6-20261019.bundle.
SHA256 e8eb8a3750760c70a8e379d05bd87b53739cd3a26f02cb46e9ba180e479367f6.
S1/D1 and I/O tests have compile-RED evidence. Independent Cursor review found
a RAM/disk conflation regression and reader-lock issue after the initial84-test
focused pass. Seven new tests went RED before Codex split immediate RAM mutation
from coalesced immutable disk snapshots. An existing regression caught startup
overwriting an oversized legacy file; fixed without weakening the test.
Final gate PASS:424 app +10 sim +19 policy +10 evaluation =463 tests; both
builds, Lint (0 errors,54/34 warnings), APK policy pass. Follow-up source review
has no remaining blocker. Exact evidence: STORAGE_AND_RAW_DEPTH_2026-10-20.md.
Cursor QA shell commands were rejected, so that worker was canceled; Codex owns
Gradle/build/install directly. Do not bypass Cursor's permission configuration.
Redmi0.9.23/36 installed and pulled-hash verified; original0.9.22 APK preserved.
Invalid-duration probe refuses/closes with zero frames. Screen locked again;
valid depth, current UI/voice and geometry remain untested. App force-stopped.

Read this, AGENTS.md and the newest bridge record before each task. Codex owns
architecture, integration, phone access, policy files and final acceptance.
Cursor workers own only their listed files. No broad refactor, reset/checkout,
network permission, cloud upload, paid key, new model, CLEAR or turn. User has
authorized a PUBLIC sanitized GitHub source backup in the current update above.
Private-only statements in earlier records are superseded. Do not publish
private captures/logs/weights or silently change the repository's visibility.

Baseline saved: local commit e0859e6, verified Git bundle in ignored
outputs/reports/cursor-20261019-next/. A local commit/bundle is not an off-device
backup. Existing private captures and educational weights remain excluded.

## Ownership and order

| Task | Owner | Write scope | State |
| --- | --- | --- | --- |
| S1/S2 storage | Cursor storage worker + Codex review | memory helpers/stores and resilience tests; own result | 37 new tests GREEN |
| D1/D2 decoder | Cursor depth worker + Codex review | debug RawDepthFrameStats and tests; own result | 20 tests GREEN |
| D3 bounded ARCore probe | Cursor Android worker + Codex review | debug RawDepthDiagnosticActivity; own result | built; invalid-duration refusal PASS; two interrupted runs, valid depth unverified |
| Gate / memory I/O integration | Codex + bounded Cursor I/O worker | RawDepthSampleGate; CoalescingMemoryWriter; minimal root wiring; tests | 16 + 5 tests GREEN |
| QA / APK | Codex after Cursor shell rejection | full offline gate, new staged APK | accepted463 tests; installed/hash verified |
| Independent integration review | separate Cursor reviewer | read-only source; own integration review document only | initial + follow-up complete, no blocker |
| Backup final reviewed source | Codex, Cursor shell unavailable | exact approved Git paths/commit/push to existing public branch; no product edits | 999bb71 pushed and exact remote ref verified; private media/weights excluded |

Main Kotlin source roots are android-app/app/src/main/java/com/triguide/ai/;
main tests use src/test/kotlin/com/triguide/ai/. Debug files use src/debug/java/
and src/testDebug/kotlin/com/triguide/ai/diagnostics/. Result files are separate;
workers do not edit this queue, shared bridge, build files, other worker files,
or existing dirty work. No worker builds/ADB/commits until explicitly released.
Tests first: write the tests and return; Codex records RED before implementation.

## S1 / S2 detailed storage contract

Preserve public store constructors/APIs and legacy filenames, no model/UI change.
Add tests first for: regular-file directory/unavailable storage cannot crash
construction/startSession/note/noteSummary/clear/person settings or delete;
oversized and malformed files are bounded/rejected without deleting originals;
invalid confidence NaN/infinity/out-of-range, zones, negative times/counts and
control-character labels are rejected/sanitized; max 40 object labels / 20
confirmed names enforced on LOAD too; count increment saturates instead of
overflow; old elapsedRealtime values never sort new-boot entries as older;
consent remains off by default and face storage/recognition remain disabled.
Failed durable person writes must never claim a name was saved. Disabling a
privacy feature must disable it in memory even if persistence fails.

Internal helper API expected by tests:
`BoundedAtomicTextFile(file: File, maxBytes: Int = 65536,
beforeReplace: (File) -> Unit = {})`, with `read(): String?`,
`write(text: String): Boolean`, `delete(): Boolean`.
Use existing platform same-directory atomic replacement (Android API26+ Java NIO
or a testable platform adapter), fsync written content, preserve old bytes if
staging/replace fails; no non-atomic overwrite fallback. Bounded UTF-8 reads,
catch storage exceptions (not VM fatal errors), cleanup only helper-owned staged
file. beforeReplace permits deterministic I/O interruption tests without mocks.
Do not introduce Room/DataStore/Android mock-test dependencies. Document actual
guarantees: atomic replacement is not power-loss proof of every filesystem.
Prefer file/insertion order for persisted history; elapsed realtime is not UTC.
Keep modifications to listed files. If minimal API additions are needed explain
them first in your result. Do not claim Android physical I/O from JVM alone.

## D1 / D2 exact diagnostic decoder contract

Define `internal data class RawDepthSummary(val sampledPixels: Int,
val validDepthPixels: Int, val confidentPixels: Int, val medianDepthMm: Int?,
val p90DepthMm: Int?)` and `internal object RawDepthFrameStats` with:
`summarize(width: Int, height: Int, depth: ByteBuffer, depthRowStride: Int,
depthPixelStride: Int, confidence: ByteBuffer, confidenceRowStride: Int,
confidencePixelStride: Int, confidenceThreshold: Int = 128): RawDepthSummary`.

CPU-only debug Kotlin, no Android imports or frame ownership. Decode unsigned
16-bit LITTLE_ENDIAN depth in millimetres; confidence unsigned 0..255. Zero depth
is missing, not free space. Threshold is diagnostic only, never calibration or
walkability. Counts cover every validated pixel (cap width*height at 1,048,576).
Percentiles over nonzero depth pixels with confidence >= threshold only, nearest
rank ceil(q*n)-1. Return null distances if none. Validate positive dimensions,
threshold 0..255, adequate non-overlapping row/pixel strides and remaining bytes
from each ByteBuffer's current position, including last row without final pad;
overflow-safe Long arithmetic. Reject malformed input via IllegalArgumentException.
Do not change input buffer positions, limits, byte order or underlying bytes.
Tests: unsigned >32767 / confidence >127, nonzero offsets, padded rows/pixel
stride, read-only/direct buffer, truncated last row, invalid/overflow dimensions,
overlap stride, invalid thresholds, empty confidence, zero depth, null aggregate,
percentile behavior and immutability. No arbitrary 13-bit depth masking.

Primary source: https://developers.google.com/ar/develop/java/depth/raw-depth
and SDK v1.57.0 official sample. No downloaded code/weights or new dependencies.
Later D3 must explicitly close both Images, reject repeated/reprojected depth
timestamps and misaligned confidence, stop onPause/timeout, keep ARCore sole
camera owner, retain no RGB/depth files, and never issue movement instructions.

## Evidence and completion

Each result file records exact changed files, tests written, RED/GREEN evidence
if actually run, unresolved risks and NEXT status. A written task is not proof
Cursor ran it. Codex verifies CLI exit/result and reviews diffs before acceptance.
One Gradle build at a time; other workers may write disjoint source concurrently,
but final gate runs only on stable source. New user issue captures are opt-in
local inputs, not automatic recordings or uploads. Human height/distance ground
truth and hearing/TalkBack need the owner; no eyes-closed walking test.
