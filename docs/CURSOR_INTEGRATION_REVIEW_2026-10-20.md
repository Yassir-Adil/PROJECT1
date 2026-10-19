# Cursor integration review — 2026-10-20

**Role:** Independent READ-ONLY integration review
**Queue context:** Initial review used an earlier no-phone snapshot; later USB connection is recorded in the follow-up. Physical depth not measured by this reviewer.
**Scope:** Source review only of listed Raw Depth + memory files and traced callers.
**Not done:** Gradle, unit tests, ADB, install, network, Git, product edits.

## Verdict

**Blocking source findings: yes (memory wiring).** Raw Depth probe path is fail-closed and resource-ordered in source; no blocking camera/lifecycle defect found in the reviewed activity/decoder/gate alone. Memory integration in `TriGuideRoot` introduces a reproducible in-memory / durability regression. No physical depth accuracy or device GREEN/RED is asserted here.

## Call graph (traced)

| Symbol | Callers |
| --- | --- |
| `RawDepthFrameStats.summarize` | `RawDepthDiagnosticActivity.summarizeAndGate` only (`debug`) |
| `RawDepthSampleGate.accept` / `stop` | `RawDepthDiagnosticActivity` only |
| `RawDepthDiagnosticActivity` | Debug manifest DUMP activity; **no** in-app `TriGuideRoot` / Scene Lab launcher |
| `BoundedAtomicTextFile` | `LocalGuidanceMemory`, `PersonConsentStore` |
| `CoalescingMemoryWriter` | `AppServices` in `TriGuideRoot`; `offer` from `AssistCameraDrive`, `CameraScreen.runDescribe`, Simulator `CameraInformed`; `close` on root `DisposableEffect` |
| `LocalGuidanceMemory.note` / `noteSummary` | Only via `memoryWrites.offer { … }` from UI (plus direct calls in JVM tests) |
| `PersonConsentStore` | `Settings` → `PersonConsentSection`; diagnostics status line; **not** via coalescing writer |

## Blocking / actionable findings

### F1 — CONFLATED writer wraps full `note` / `noteSummary` (in-memory + disk)

- **Where:** `TriGuideRoot.kt` ~239 (`memoryWrites`), ~672 (`noteSummary`), ~1076 (`AssistCameraDrive` `note`), ~1125 (Simulator `CameraInformed` `note`).
- **What:** Each `offer` lambda calls `LocalGuidanceMemory.note` / `noteSummary`, which mutate the in-memory maps **and** fsync under the same path. `CoalescingMemoryWriter` uses `Channel.CONFLATED`, so pending lambdas are dropped.
- **Consequence:** Intermediate cue/summary snapshots never update RAM or disk. Under ~20 Hz guidance offers vs slow fsync, most frames are discarded before `notes` changes. `summaryLine()` / `recent()` (Diagnostics) can stay stale relative to live detections. This is a behavioural regression vs pre-wiring direct `note()` on the guidance loop (in-memory was updated every call even when disk was slow).
- **Fix direction (for Codex):** Update in-memory state on the caller thread (or a non-conflated path); `offer` only the durable `BoundedAtomicTextFile.write` snapshot. Do not conflate mutations of `notes` / `sessionSummary`.
- **Evidence class:** Source-reproducible integration defect. **Not** measured on device.

### F2 — `LocalGuidanceMemory.clear()` ignores failed `objectsStore.delete()`

- **Where:** `LocalGuidanceMemory.kt` 99–105 (`objectsStore.delete()` return unused; memory cleared unconditionally).
- **What:** If `delete()` returns `false` (non-file path, I/O error), `notes` is empty but `objects.tsv` can remain.
- **Consequence:** Next process start `load()` resurrects “cleared” labels. Asymmetric with `PersonConsentStore.delete` / `confirmPending`, which roll back on durable failure (`PersonIdentity.kt` 111–116, 161–165).
- **Evidence class:** Source-reproducible API defect. No production UI caller found today; tests call `clear()`. Still a durable privacy/correctness hole if/when UI exposes clear.

### F3 — Guidance-memory lock held across fsync on the coalesced worker

- **Where:** `LocalGuidanceMemory.noteLocked` → `writeObjects` / `writeSession` (`155–173`) under `synchronized(lock)`; invoked from IO worker via F1 wiring. Main-thread readers: `recent()` / `summaryLine()` (`90–97`), Diagnostics compose (`TriGuideRoot.kt` ~1379, ~1442).
- **What:** Worker holds the same lock for UTF-8 encode + staging + `fd.sync()` + atomic move.
- **Consequence:** A Diagnostics/Settings read of memory status on the main thread can block for the full sync duration → jank / ANR risk on slow storage. Person consent writes remain correctly synchronous on the UI path (intentional for confirm/fail speech) and are out of scope for coalescing.
- **Evidence class:** Source-reproducible threading design issue. **Not** timed on Redmi.

### F4 — Object/session durable writes ignore `write()` failure (no rollback)

- **Where:** `LocalGuidanceMemory.writeObjects` 166; `writeSession` 171–173 (Boolean discarded). Contrast `PersonConsentStore.writePeople` / enable path.
- **What:** After successful in-memory update, a failed atomic write leaves RAM ahead of disk.
- **Consequence:** Cold start loses notes/session text without any caller-visible failure. Acceptable only if product treats notes as strictly best-effort **and** F1 is fixed so RAM is not also conflation-lossy; today both fail together.
- **Evidence class:** Source-reproducible durability asymmetry.

## Raw Depth — reviewed, no blocking source defect

Checked against D1/D2/D3 contracts (close both Images; reject repeated/mismatched timestamps; stop on pause/timeout; no RGB/file retention; no movement cues):

| Check | Source observation |
| --- | --- |
| Image close | `processFrame` closes confidence then depth in `finally` (`RawDepthDiagnosticActivity.kt` 435–464) |
| Empty depth | `validDepthPixels <= 0 \|\| confidentPixels <= 0` rejected; timeout with `accepted==0` → `NO_DEPTH` (527–533, 720–725) |
| Gate | `RawDepthSampleGate` separates AR timestamps vs elapsed clocks; `confidence_mismatch`, `depth_not_new`, `sensor_stale`, `processing_stale`, `stop()` (gate 18–47) |
| Lifecycle / deadlock | `requestStop` never under `frameInUseLock`; `pauseHardwareOnce` = GL `onPause` then `session.pause`; GL errors `runOnUiThread` stop (633–668, 380–387) |
| Fail-closed startup | Missing CAMERA / not `SUPPORTED_INSTALLED` / unsupported RAW / restore → FAIL + finish (122–157, 757–771) |
| Decoder | CPU-only; absolute LE UINT16 / UINT8; no buffer mutation; zero depth not percentile (RawDepthFrameStats) |
| Navigation / files | Logs + on-screen text only; `no_image_storage=true`; no CLEAR / walk commands |

### Non-blocking Raw Depth notes (not raised as blockers)

- **`FocusMode.AUTO`** (`configureSession` 270–271) with no FIXED fallback — called out in probe result as unverified; research note #10 preferred fixed focus for integration. Quality risk only; fail-closed if configure throws.
- **Up to `width*height` `IntArray` on GL thread** (`RawDepthFrameStats` 72) — GC / `processing_stale` risk at large planes; diagnostic-only.
- **No mutual exclusion with main `CameraXSource`** — DUMP-only entry; concurrent live guidance + probe can contend for the camera (resume fail-closed). Launch policy is Codex-owned.
- **Physical mm / tracking / Redmi depth:** **unverified**. Logs label `metric_accuracy=unverified` / `NOT_calibrated`. Queue: no ADB device → no measured depth result.

## Consent / privacy (PersonConsentStore)

No blocking consent defect found in reviewed source:

- Default off; enable requires durable `writeFeature(true)` else memory stays off (`136–147`).
- Disable clears pending in memory even if disk fails (`149–153`).
- `confirmPending` / `delete` roll back maps when `writePeople()` fails; spoken failure lines do not claim save (`111–116`, 272–277).
- `recognize()` / `storeFace()` remain non-functional; face flag forced false on load.

## Evidence boundaries

| Claim | Status |
| --- | --- |
| Source correctness of listed Kotlin | Reviewed above |
| JVM / instrumented test GREEN/RED | **Not run** (Codex testing) |
| APK policy / assemble | **Not run** |
| Redmi Raw Depth / wall distances | **Not measured**; must not be inferred from decoder unit fixtures |
| Depth median/p90 as real metres | **Unverified** by design in probe logs |

## Explicit non-requests

No model, dependency, FocusMode product change, or navigation-policy change is requested by this review. Fixes for F1–F4 are wiring/durability only.

---

**REVIEW_READY**

## Follow-up source review — memory RAM/disk separation (same day)

**Scope:** Re-read `LocalGuidanceMemory.kt`, `TriGuideRoot` `AppServices` wiring, and all seven methods in `MemoryPersistenceIsolationTest.kt`. Read-only; no Gradle/ADB/Git/network. Phone USB presence noted by owner — **this follow-up still makes no build or physical-depth claims.**

### Updated wiring (observed)

- `TriGuideRoot` ~237–241: `CoalescingMemoryWriter` only receives `scheduleWrite { persistLatest }` closures; `note` / `noteSummary` called directly (~675, ~1079, ~1128).
- `LocalGuidanceMemory`: short `lock` for RAM + immutable `DiskSnapshot`; separate `diskLock` for `persistLatest` / `clearPersisted`; public `constructor(directory)` preserved; internal ctor injects `scheduleWrite` + `beforePersist`.

### F1–F4 disposition

| ID | Disposition | Basis |
| --- | --- | --- |
| **F1** | **Resolved** | RAM updates sync under short `lock` (`note` 62–69, `noteSummary` 97–109). Only `persistLatest` is scheduled/conflated (`209–216`, root `scheduleWrite` → `offer`). Test `conflatingDiskJobsDoesNotDropRamLabelsOrSummary` encodes the contract. |
| **F2** | **Resolved** | `clearPersisted(): Boolean` (127–140): on delete/session write failure returns `false` and does **not** clear RAM; on success updates `latestSnapshot`/`lastPersistedSnapshot` to cleared so queued `persistLatest` cannot resurrect (`===` short-circuit). Legacy `clear()` delegates. Tests `failedDurableClearKeepsRamAndReportsFailure`, `queuedWriteAfterSuccessfulClearCannotResurrectLabelsOrSummary`. |
| **F3** | **Resolved** | Disk fsync path uses `diskLock` only (`persistLatest` 219–230); readers/mutations use `lock` without waiting on blocked disk. Test `readersAndMutationsDoNotWaitForBlockedDiskWork`. |
| **F4** | **Accepted (non-blocking)** | Best-effort notes: failed writes leave `lastPersistedSnapshot` unchanged → retryable; no API announces durable note success. Tests `failedPersistenceCanRetrySameSnapshot`, `rejectedSchedulingDoesNotLoseRamOrThrowIntoGuidance`. Cold-start loss after failed note persist remains an accepted notes posture, not a consent-class claim. |

### Concrete remaining blocker

**None** from this follow-up source pass on the memory isolation change set.

Residual (non-blocking): if `objectsStore.delete()` succeeds and `sessionStore.write` then fails inside `clearPersisted`, the method correctly returns `false` and keeps RAM, but `objects.tsv` is already gone — process death before a healing `persistLatest` can lose labels despite a failed clear. Not a successful-clear resurrection bug; independent two-file clear is not a single atomic transaction.

### Isolation tests (source contract coverage; not executed here)

Seven tests present: conflation keeps RAM; old job writes latest snapshot; readers unblocked by disk; failed clear keeps RAM; post-clear queued job cannot resurrect; schedule throw keeps RAM; failed persist retries. **Pass/fail not verified in this review.**

---

**FOLLOWUP_REVIEW_READY**
