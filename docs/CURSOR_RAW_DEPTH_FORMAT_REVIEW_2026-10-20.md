# Cursor review — Raw Depth image format guard (2026-10-20)

Latest source disposition: format fix and tracking telemetry accepted in the
follow-up sections below. Initial missing-helper observations are historical;
phone measurements belong to Codex's RAW_DEPTH_D16_FIX_2026-10-20.md, not this review.

**Scope:** Read-only source/docs review for the `unexpected_depth_format_48` probe failure on installed 0.9.23. No product, test, manifest, dependency, model, shell, build, ADB, or Git changes by this worker.

**Sources read:** `AGENTS.md`, `docs/WORK_QUEUE.md`, `RawDepthDiagnosticActivity.kt`, `RawDepthFrameStats.kt`, `RawDepthSampleGate.kt`, `RawDepthImageFormatTest.kt` (present), debug `AndroidManifest.xml` (isolation only). Official primary refs fetched: ARCore Java `Frame.acquireRawDepthImage16Bits`, Android `HardwareBuffer.D_16`, Android `ImageFormat.DEPTH16` / `Y8`. No outputs, private media, or raw phone logs were read.

**Ownership note:** Codex owns Kotlin/tests/Gradle/ADB/Git. Source may be mid-fix.

---

## Verdict

**Blocking source defect confirmed** in the current diagnostic activity format guard. It rejects the format that official ARCore docs and the Redmi runtime both use (`48` = `HardwareBuffer.D_16`), because it incorrectly requires `ImageFormat.DEPTH16`.

**Helper status:** `RawDepthImageFormat` / `requireRaw16BitDepth` is **absent** from the tree. Only the regression test file exists. Do not treat the test as a landed fix.

---

## Official contract (primary)

| API / constant | Spec | Numeric |
| --- | --- | --- |
| `Frame.acquireRawDepthImage16Bits()` | Depth image format is **`HardwareBuffer.D_16`**: single little-endian 16-bit plane, millimetres, range 0..65535; 0 = no estimate | — |
| `HardwareBuffer.D_16` | Format: 16 bits depth | **48** (`0x30`) |
| `ImageFormat.DEPTH16` | Packed depth + confidence in one 16-bit sample (confidence in top 3 bits; range via `>> 13` / `& 0x1FFF`) | **1144402265** (`0x44363159`) |
| Confidence (`acquireRawDepthConfidenceImage`) | Y8 / 8-bit unsigned 0..255; separate image | `ImageFormat.Y8` = 538982489 |

Deprecated `acquireRawDepthImage()` still documents the old 13-bit layout (top 3 bits cleared). That is **not** the 16-bit raw API TriGuide calls. Applying a 13-bit mask (`0x1FFF`) to `acquireRawDepthImage16Bits` data would be incorrect for the current API.

`WORK_QUEUE.md` D1 already forbids arbitrary 13-bit masking. The probe task text also already specified expected formats as **D_16 depth / Y8 confidence**.

---

## Finding F1 — BLOCKING: wrong depth format constant

**File:** `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthDiagnosticActivity.kt` (approx. lines 475–477)

```kotlin
if (depth.format != ImageFormat.DEPTH16) {
    throw IllegalStateException("unexpected_depth_format_${depth.format}")
}
```

**Why this matches the Redmi failure:** On first TRACKING frame that successfully acquires raw depth, `depth.format` is `48`. The guard expects `ImageFormat.DEPTH16` (≈1.14e9), so it throws `unexpected_depth_format_48`. That exception is caught in `processFrame` and stops the probe with `failed = true` (`depth_process_IllegalStateException`). Fail-closed stop behaviour is appropriate; the **predicate** is wrong.

**Required fix direction (for Codex, not applied here):** Accept **only** `HardwareBuffer.D_16` (strict equality to 48 / `HardwareBuffer.D_16`). Do **not**:

- accept “any 16-bit-looking” format,
- accept `ImageFormat.DEPTH16` as a synonym for raw 16-bit mm,
- introduce a 13-bit depth mask to “make DEPTH16 work”.

`ImageFormat.DEPTH16` is a different Android Camera2 packing contract (confidence in the upper bits). Treating it as full unsigned millimetre depth would corrupt decoding even if a device ever returned that format on this API path.

---

## Finding F2 — Mid-fix: helper missing; tests only

**Present:** `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/RawDepthImageFormatTest.kt`

**Absent:** any production/debug source defining `RawDepthImageFormat` or `requireRaw16BitDepth(...)`.

The new tests correctly express the intended contract:

- accept format `48` / assert `HardwareBuffer.D_16 == 48`
- reject `ImageFormat.DEPTH16` (packed confidence contract)
- reject Y8 / YUV / JPEG and unknown values as depth

**Must be verified after Codex lands the helper + wires the activity:**

1. Helper exists under the debug diagnostics package (or another debug-only source set reachable by the activity and `testDebug`).
2. `requireRaw16BitDepth` accepts **only** `HardwareBuffer.D_16` (or literal 48 with the same meaning); rejects `DEPTH16` and all other codes.
3. `RawDepthDiagnosticActivity.summarizeAndGate` calls that helper (or an equivalent single equality) instead of `ImageFormat.DEPTH16`.
4. Confidence remains guarded as `ImageFormat.Y8` only.
5. No 13-bit / `0x1FFF` masking is added in the helper, activity, or `RawDepthFrameStats`.
6. Regression tests compile and pass under the project’s existing `testDebug` setup (Android stub constants for `HardwareBuffer.D_16` / `ImageFormat.*`).

Until the helper exists and the activity is rewired, the installed probe path remains broken for real D_16 frames.

---

## Confidence Y8 — OK (no defect)

Activity guard:

```kotlin
if (confidence.format != ImageFormat.Y8) {
    throw IllegalStateException("unexpected_confidence_format_${confidence.format}")
}
```

Matches official Raw Depth guide (“Confidence images are returned in Y8 format”) and the separate confidence image API. Keep this strict. Do not fold confidence into the depth plane.

`RawDepthFrameStats` already treats confidence as unsigned 0..255 bytes with a diagnostic threshold default of 128 — consistent with the ARCore confidence docs (half-confidence filter guidance is diagnostic only here).

---

## Buffer / image lifecycle — OK (no blocking defect)

In `processFrame`:

- Nested acquire of depth then confidence.
- `NotYetAvailableException` counted, not fatal.
- `confidenceImage?.close()` in inner `finally`; `depthImage?.close()` in outer `finally`.
- Format / size failures thrown from `summarizeAndGate` still hit those `finally` blocks before the outer `Exception` stop path.

No retained `Image` references outside the acquire window. Plane buffers are passed into `RawDepthFrameStats.summarize` without ownership transfer; the decoder documents that it does not mutate buffer position/order/bytes.

---

## Decoder vs format guard — OK; keep full UINT16 LE

`RawDepthFrameStats.readUint16Le` reconstructs full unsigned 16-bit little-endian values (no `0x1FFF` mask). That matches `acquireRawDepthImage16Bits` (0..65535 mm), not the deprecated 13-bit API. After the format guard fix, leave the decoder as-is on this point.

---

## Failure handling — OK shape; root cause is the guard

- Startup: fail-closed for missing camera permission, ARCore not `SUPPORTED_INSTALLED`, raw depth unsupported, invalid duration, restored activity.
- Runtime: unexpected acquire/process exceptions stop the probe (`failed = true`); `NotYetAvailable` and non-TRACKING frames do not.
- Empty / zero valid-confident pixels reject without accepting samples; sample gate remains fail-closed for stale/mismatch/reprojection cases.
- UI/logs repeatedly state diagnostic-only, no navigation, missing depth ≠ clear.

Once D_16 is accepted, a wrong-format failure should become rare; keeping fail-closed for unknown formats remains correct.

---

## Debug-only isolation — OK

- Activity lives under `app/src/debug/java/.../diagnostics/`.
- Declared only in `app/src/debug/AndroidManifest.xml` with `android.permission.DUMP`, `excludeFromRecents`, `noHistory`.
- No evidence in the reviewed debug probe path of writing RGB/depth files or issuing movement/CLEAR cues.
- Decoder and sample gate are diagnostic-only contracts (CPU stats / admission), not navigation policy.

Main-app navigation sources were not opened for this bounded review beyond confirming the probe is debug-isolated by source set + manifest.

---

## Non-blocking notes (verify, do not expand scope)

1. **Activity wiring:** Even a perfect helper leaves the probe broken until lines 475–477 stop comparing to `ImageFormat.DEPTH16`.
2. **Import cleanup:** After the fix, `ImageFormat` may still be needed for `Y8`; `HardwareBuffer` (or a helper constant) is needed for depth.
3. **Do not “fix” by accepting both DEPTH16 and D_16** for this acquire path: different pixel contracts.
4. **Sample gate / stats:** No format bug found in `RawDepthSampleGate` or `RawDepthFrameStats` related to `unexpected_depth_format_48`.

---

## Checklist for Codex before claiming fixed

| Item | Status now | After fix |
| --- | --- | --- |
| Strict `HardwareBuffer.D_16` (48) accepted | FAIL (activity rejects 48) | Required |
| `ImageFormat.DEPTH16` rejected as depth | Activity wrongly requires it; tests intend reject | Required |
| No universal “16-bit OK” / no 13-bit mask | Decoder OK; activity wrong constant | Keep |
| Confidence `Y8` only | OK | Keep |
| Image close on all paths | OK | Keep |
| Helper `RawDepthImageFormat` present | **ABSENT** | Required |
| Activity uses helper / D_16 equality | Uses `DEPTH16` | Required |
| Debug/DUMP isolation | OK | Keep |
| Redmi re-probe after install | Not done by this review | Codex/owner |

---

## Conclusion

There **is** a concrete blocking source defect: `RawDepthDiagnosticActivity` validates raw depth against `ImageFormat.DEPTH16` instead of `HardwareBuffer.D_16` (48), which explains `unexpected_depth_format_48` after the first TRACKING depth acquire. Confidence Y8, image close lifecycle, fail-closed error handling, debug isolation, and full UINT16 LE decoding are in good shape for this slice. The new format helper is **not in source yet**; regression tests alone are insufficient until the helper lands and the activity is rewired to a strict D_16 guard.

---

## Follow-up disposition — 2026-10-20 (source re-read after 0.9.24 wiring)

**Scope:** Read-only re-check of landed helper + activity + four format tests. No shell/build/ADB/Git, no product edits, no other docs, no private logs. This disposition is **source review only**.

### Source review: CLEAR (prior F1/F2 addressed in tree)

| Check | Result |
| --- | --- |
| Helper present | `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthImageFormat.kt` |
| Strict accept | `format != HardwareBuffer.D_16` → `IllegalStateException`; equality only (not “any 16-bit”) |
| Packed DEPTH16 rejected | Helper has no DEPTH16 accept path; test `rejectsPackedDepth16RatherThanTreatingItAsFullUnsignedDepth` calls `requireRaw16BitDepth(ImageFormat.DEPTH16)` and expects throw |
| Activity wiring | `summarizeAndGate` calls `RawDepthImageFormat.requireRaw16BitDepth(depth.format)` before plane use; former `depth.format != ImageFormat.DEPTH16` comparison is gone |
| Y8 guard | Unchanged: `confidence.format != ImageFormat.Y8` still throws `unexpected_confidence_format_*` |
| No 13-bit mask | Helper has none; `RawDepthFrameStats.readUint16Le` still full UINT16 LE (`lo \| (hi << 8)`), no `0x1FFF` |
| Four tests | Accept 48/`D_16`; reject `DEPTH16`; reject Y8/YUV/JPEG; reject −1/0/49/`Int.MAX_VALUE` |

No remaining **source** format-guard defect for the reported `unexpected_depth_format_48` class of failure. Prior checklist items for helper presence, activity D_16 wiring, and strict DEPTH16 rejection are satisfied in the files re-read above.

### Explicitly not verified by this follow-up

- **Build / unit-test execution:** Not run by this worker. Codex’s reported compile-RED-then-fix and “full offline gate running” are outside this review’s evidence. Do not treat this disposition as gate PASS.
- **Install / Redmi probe:** Not run. Claimed app version **0.9.24/37** and post-fix phone behaviour (TRACKING → accepted D_16 samples vs. new failures) remain Codex/owner evidence, not Cursor-confirmed.
- **Lifecycle / isolation:** Not re-audited in depth; earlier review found them OK and the format change does not alter Image close or debug/DUMP placement.

### Disposition

**Source format fix: accepted for this bounded review.** Remaining risk is execution/device confirmation only (Gradle gate + Redmi re-probe under Codex ownership).

---

## Follow-up disposition — 2026-10-20 (trackingFailureReason telemetry)

**Scope:** Read-only check of `maybeReportTracking` wiring in latest `RawDepthDiagnosticActivity.kt`. No source/test/Gradle/ADB/Git/private logs. No phone claims beyond the counts stated for the completed 0.9.24 run.

**Context (as stated, not re-measured here):** 0.9.24 passed 467 tests/build/Lint and installed/hash-matched. One actual 20s probe: 566 distinct camera frames, all nontracking, no depth; duration timer closed correctly — format path not reached. Candidate 0.9.25/38 was building under Codex; not verified by this worker.

### Telemetry source check: OK

| Risk | Observation |
| --- | --- |
| Acquire depth while paused / non-TRACKING | `maybeReportTracking` only on `!tracking` **after** distinct-frame admit, then **`return`** before any `acquireRawDepth*` |
| Loosen gates / sample admission | No `RawDepthSampleGate.accept`, no sample counters, no format/summarize path on this branch |
| Duplicate-frame counting | Call is after `if (!isNewDistinctFrame) return`; repeats/`<= lastCameraTimestampNs` never enter |
| Log/UI flood | Log + UI share existing 1 Hz (`UI_LOG_MIN_INTERVAL_NS`) via `lastSampleLogElapsedNs` / `lastUiElapsedNs`; reason tallies use existing `statsLock` + `countRejection` (scalar map, not per-frame logs) |
| Navigation / speech | `Log.i` + `updateStatus` TextView only; `navigation=none` in log; no TTS/speech APIs; status text states no admitted depth / no walking |
| Publish after stop | `stopping.get()` at entry, again before log, and inside queued UI before `updateStatus` |

### Disposition

**Tracking-failure telemetry: accepted for this bounded source review** — diagnostic-only, fail-closed relative to depth acquire and gates. It does not re-validate the format fix (still unexercised on device until a TRACKING + depth run). Build/install of 0.9.25/38 and any later probe remain Codex/owner evidence.
