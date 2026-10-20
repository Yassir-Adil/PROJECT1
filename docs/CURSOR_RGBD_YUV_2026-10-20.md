# CURSOR_RGBD_YUV — Yuv420Rgb

**Status:** `OPTIMIZED_SOURCE` — heap fast path after Codex baseline/equivalence ownership; no timing claim here
**Worker:** Cursor Y
**Date:** 2026-10-20
**Baseline:** 8a72aca / 0.9.31/44 (queue ACTIVE: single-camera semantic/depth diagnostic integration)

## Optimization follow-up (Codex-authorized)

Codex reported baseline converter + equivalence tests PASS before this edit; host 640×480 timing was measured by Codex (not this worker). Redmi fusion showed worker-prepare much slower than NNAPI on a measured path where conversion is a *plausible* bottleneck — not isolated proof. This pass optimizes copied heap `ByteBuffer.wrap` planes only; **no Redmi speedup claimed**.

### Changed files (this pass only)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/Yuv420Rgb.kt` — heap-array fast path + shared-structure buffer fallback
2. `docs/CURSOR_RGBD_YUV_2026-10-20.md` — this update

No other files. Did **not** edit `Yuv420RgbTest.kt` or Codex-owned `Yuv420RgbEquivalenceTest`. No shell/Gradle/ADB/Git/network/private outputs.

### Rationale

| Before | After (same BT.601 / validation / no mutation) |
| --- | --- |
| Every sample: Long `sampleIndex` + absolute `ByteBuffer.get` ×3 | Writable heap: `array()` + `arrayOffset+position`, Int row/chroma offsets |
| Re-read U/V every luma pixel | Reuse U/V for chroma 2×2 pair (and chroma row for paired luma rows) |
| Direct / read-only same slow path | Still absolute `get`; identical loop pairing so pixels match heap |

Fast path only when **all three** planes are writable heap (`hasArray && !isReadOnly`) and `arrayOffset+position` fits Int (Long check; else fall back). Slice/`arrayOffset` origins supported. Validation + Long `requiredBytes` unchanged and still run before any sample access or `IntArray` alloc.

### Preserved contract

- Limited-range BT.601 integers exactly as before; clamp; ARGB8888; unrotated full frame
- Positive dims; area ≤ 1_048_576 (Long); stride/remaining gates; `IllegalArgumentException`
- Never mutates position/limit/order/bytes
- No downsampling, filtering, or colour-space change
- No timing pass/fail threshold in source

### Ownership / evidence (not run here)

- Existing 25 `Yuv420RgbTest` cases (pads/interleaved/positions/rejects) — Codex executes
- Codex-owned `Yuv420RgbEquivalenceTest` (640×480 heap vs read-only direct + slice `arrayOffset`) — do not edit; Codex measures
- This worker makes **no** GREEN, host-ms, or Redmi latency claim

## Prior implementation notes (retained)

Hand fixtures: BLACK `0xFF000000`, WHITE `0xFFFFFFFF`, RED `0xFFFF0000`, ASYM_LEFT `0xFFFF3636`, ASYM_RIGHT `0xFF4676FF`, gray81 `0xFF4C4C4C`.

## Genuine unverified limits

- Heap fast path targets **copied** CPU-image wraps used by the fusion worker; live ARCore plane buffers may be direct → generic path
- Optimization does not change colourimetry, rotation, or fusion freshness; never CLEAR / walkability / movement permission
- Host p50/p90 and Redmi prepare vs NNAPI numbers belong to Codex measurement docs — not re-asserted here
- Dual-row / dual-column chroma reuse is indexing only; odd width/height last row/column still share the final chroma cell as before

## NEXT

Codex independently re-runs equivalence + focused tests and any host timing. Cursor Y stops here.
