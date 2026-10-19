# Cursor storage worker — review fixes result

**Status:** `REVIEW_FIXES_READY`
**User-facing work date:** 2026-10-20
**Worker:** Cursor STORAGE (source review follow-up)
**Prior:** S2 `IMPLEMENTATION_READY`; Codex RED `storage-review-red.log` (missing `readBoundedUtf8`). Original 29 tests had already passed in `integration-second.log` before Codex added seven regressions.

## Changed files (this slice)

| File | Action |
| --- | --- |
| `android-app/app/src/main/java/com/triguide/ai/memory/BoundedAtomicTextFile.kt` | TOCTOU-safe bounded read + cap validation + `readBoundedUtf8` |
| `android-app/app/src/main/java/com/triguide/ai/memory/PersonIdentity.kt` | delete rollback; fail-closed feature parse; asymmetric enable/disable |
| `android-app/app/src/main/java/com/triguide/ai/memory/LocalGuidanceMemory.kt` | **unchanged** this slice (no review defect in these seven) |
| `docs/CURSOR_STORAGE_RESULT.md` | this report |

No tests, Gradle, ADB, Git, UI, navigation, shared docs, or other-worker files edited.

## Fixes applied

### `BoundedAtomicTextFile`
- **`read()`** opens the real file stream in `use { }` and never calls `File.readBytes()` after a length check (TOCTOU).
- **`internal companion readBoundedUtf8(input, maxBytes)`**: reads at most `maxBytes + 1` via a bounded buffer loop; strict UTF-8; does **not** close the caller stream; returns `null` if oversize or malformed.
- **Cap:** constructor and `readBoundedUtf8` require `maxBytes in 0..1_048_576` (`ABSOLUTE_MAX_BYTES`); default remains `65_536`.
- **`beforeReplace(destination)`**, staging + `fd.sync()`, `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`, preserve old bytes on failure, no truncate fallback — unchanged.

### `PersonConsentStore`
- **`delete`:** on durable write failure, restore the full in-memory map and return `false` (never claims durable success; no empty failure branch).
- **Feature load:** enable only when `featureText.trim() == "enabled=true"`; corrupt/conflicting files fail closed (off).
- **`setFeatureEnabled`:** still returns `Unit`. Enable requires durable write success; failure leaves `featureEnabled = false`. Disable clears pending and sets memory off **immediately**, then best-effort disk write.
- Face/`recognize`/`storeFace` remain disabled; privacy defaults preserved for existing tests.

## Actual limits (honest)

| Limit | Value |
| --- | --- |
| Default text file byte cap | 65_536 |
| Absolute maxBytes (ctor / `readBoundedUtf8`) | 0 … 1_048_576 |
| Object labels | 40 (file/insertion chronology) |
| Confented people | 20 (file/insertion chronology) |
| Feature enable file | exact trim `enabled=true` only |
| Atomic replace | same-directory `ATOMIC_MOVE`; **not** power-loss proof on every FS |
| JVM unit tests | do not prove Android physical I/O durability |

## RED / GREEN evidence

- **This worker did not run Gradle or unit tests.** No pass/fail counts claimed.
- Codex owns re-run of the prior 29 + seven new regressions after this fix.

## NEXT

`REVIEW_FIXES_READY` → Codex verifies GREEN on storage tests. Storage worker does not self-advance.
