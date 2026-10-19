# Cursor D3 — Raw Depth probe result

**Status: FIXES_READY**

Worker: Cursor Android worker D3 (review-fix)
Date: 2026-10-20
Build / ADB / physical probe: **not run** (out of scope; no Gradle/ADB claims).

## Exact files changed (this review-fix pass)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/RawDepthDiagnosticActivity.kt`
2. `docs/CURSOR_RAW_DEPTH_PROBE_RESULT.md` (this file)

No other files touched. Decoder/gate, manifests, policy, dependencies, Gradle, ADB, Git untouched.

## Exact corrections (Codex review items 1–8)

| # | Issue | Correction |
| --- | --- | --- |
| 1 | Wrong `ImageFormat` package | Import `android.graphics.ImageFormat` (keep `android.media.Image`). |
| 2 | Configure exception leaked Session | Assign `session = created` **before** `configureSession`; unsupported path no longer early-`close()`s separately — `onDestroy` / fail-closed owns close. |
| 3 | Deadlock: `requestStop` under `frameInUseLock` | `onResume` catches are **outside** `synchronized(frameInUseLock)`; never stop/pause GL while holding the frame lock. |
| 4 | Timestamp / tracking / cadence | Skip `timestamp <= 0`, repeated, or out-of-order (`<= last`); count tracking/nontracking only on distinct frames; **return on `!TRACKING`** before acquire; set `lastSampleAdmissionElapsedNs` **before** acquire so `NotYetAvailable` is 10 Hz-capped. |
| 5 | `Activity.display` needs API 30 | `displayRotation()`: API 30+ (`R`) uses `display?.rotation` with deprecated fallback; API 26–29 uses `windowManager.defaultDisplay.rotation` only. |
| 6 | Empty depth treated as usable | After summarize, `validDepthPixels <= 0 \|\| confidentPixels <= 0` → `countRejection("no_valid_confident_pixels")`, no accept; full timeout with accepted==0 stays `NO_DEPTH`, never `COMPLETE`. |
| 7 | Result before drain / duplicate pause | Unified `requestStop` → `pauseHardwareOnce()` (GL `onPause` then `session.pause`, idempotent via `hardwarePaused`) → **then** `logResultOnce`. `onPause` only calls `requestStop("paused")` (no second pause sequence). |
| 8 | Latency window + post-stop publish | `receiptToSummaryMs` keeps **last 600** (drop index 0 when full). Sample log / UI bail on `stopping`; UI runnable still gated. |

### Naming (log / UI)

- **Depth distances:** `median_depth_mm` / `p90_depth_mm` (UI: “Median … / p90 …”).
- **Receipt→summary latency:** `receipt_to_summary_p50_ms` / `receipt_to_summary_p90_ms` (p50 ≈ median of the bounded latency list; not labeled “median” in logs to avoid conflating with depth median).

## Remaining unverified phone risks

1. No compile/device evidence from this worker — gate + D2 + manifest wiring still Codex/other.
2. Frame-lock / GLSurfaceView pause ordering reviewed in source only; not exercised on Redmi.
3. `FocusMode.AUTO` configure failure still fail-closed (no FIXED retry).
4. Motion-stereo walls may yield only `NO_DEPTH` / `no_valid_confident_pixels` — expected, not product CLEAR.
5. Threshold 128 remains diagnostic selection only.
6. ARCore sole camera owner while probe runs — launch path must not overlap CameraX.

## NEXT

Codex: gate + manifest/launch + Gradle after D2 GREEN; physical Redmi probe when released. D3 review-fix stops here.
