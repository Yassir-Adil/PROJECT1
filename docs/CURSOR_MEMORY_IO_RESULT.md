# Cursor storage-performance — CoalescingMemoryWriter

**Status:** `IMPLEMENTATION_READY`
**User-facing work date:** 2026-10-20
**Worker:** Cursor isolated storage-performance
**Prior TESTS:** `TESTS_READY`. Codex RED: `outputs/reports/cursor-20261019-next/memory-io-red.log` — unresolved `CoalescingMemoryWriter`.

## Changed files (this implementation pass)

| File | Action |
| --- | --- |
| `android-app/app/src/main/java/com/triguide/ai/memory/CoalescingMemoryWriter.kt` | **new** helper |
| `docs/CURSOR_MEMORY_IO_RESULT.md` | **updated** this report |

**Unchanged:** `CoalescingMemoryWriterTest.kt` (including strengthened close-must-return-before-release).
Not touched: `BoundedAtomicTextFile`, `LocalGuidanceMemory`, `PersonIdentity`, root wiring, Gradle, ADB, Git, UI, navigation, speech, dependencies.

## Implementation summary

Exact API from tests:

```kotlin
internal class CoalescingMemoryWriter(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : Closeable {
    fun offer(write: () -> Unit): Boolean
    override fun close()
}
```

- `CoroutineScope(SupervisorJob() + injected dispatcher)`; one `Channel.CONFLATED`; single serial worker `launch`.
- `offer`: nonblocking `trySend`; returns `false` after close (AtomicBoolean gate + closed channel).
- `close`: CAS stop-accepting → `channel.close()` → drain pending via `tryReceive` → `scope.cancel()`. No `runBlocking`, join, or interrupt of a synchronous in-flight write body. Idempotent. Does **not** close the caller-owned dispatcher.
- Per write: `catch (Exception)` only (not `Error`); worker checks `isActive` before executing a dequeued next write so cancelled pending work never runs.
- No unbounded queue, no new executor/dependency/Android import.

**Scope:** best-effort label/summary note persistence only. Never guidance or speech.

## RED / GREEN evidence

- Codex RED recorded (missing symbol).
- **This worker did not run Gradle or unit tests.** No pass/fail counts claimed. Codex owns wiring and GREEN verification.

## Limits (honest)

- Cooperative cancel: an in-flight synchronous write runs to completion; only subsequent dequeued work is skipped.
- Intermediate offered snapshots are dropped by design (CONFLATED).
- JVM unit tests do not prove main-thread responsiveness on a real Android looper.
- Not wired into `LocalGuidanceMemory` / root by this worker.

## NEXT

`IMPLEMENTATION_READY` → Codex wires call sites if desired and runs `CoalescingMemoryWriterTest`. This worker stops here.
