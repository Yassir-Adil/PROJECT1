# CURSOR_SEMANTIC_GROUND — SemanticGroundDiagnostic

**Status:** `SOURCE_READY` (**no GREEN claim** — Codex owns focused verification)
**Worker:** Cursor H
**Date:** 2026-10-20
**Baseline:** 8a72aca / 0.9.31/44 (queue ACTIVE: single-camera semantic/depth diagnostic integration)
**Scope this turn:** Implementation release after Codex independent Gradle RED (`semantic-publication-ground-red-20261020.log`; private log not read here).

## Changed files (this pass)

1. `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SemanticGroundDiagnostic.kt` **(new)** — `SemanticDepthCell` + `SemanticGroundReading` + `SemanticGroundDiagnostic`
2. `android-app/app/src/testDebug/kotlin/com/triguide/ai/diagnostics/SemanticGroundDiagnosticTest.kt` — compile fixture fix only (`kotlin.random.Random`); **no contract weakening**
3. `docs/CURSOR_SEMANTIC_GROUND_2026-10-20.md` — this update

No other files touched. No shell/Gradle/ADB/Git/network/private outputs. No Activity, ARCore adapter, navigation, speech, model, or permission edits. Codex retains SDK mapping and temporal/freshness integration.

## Codex RED (independent, not re-run here)

Compile RED observed by Codex: unresolved `SemanticDepthCell` / `SemanticGroundDiagnostic` (and related) from the new testDebug suite. Source added only after that RED.

## API (unchanged contract)

```kotlin
internal data class SemanticDepthCell(
    val column: Int,
    val row: Int,
    val label: Int,
    val worldX: Double? = null,
    val worldY: Double? = null,
    val worldZ: Double? = null,
)

internal data class SemanticGroundReading(
    val status: String,
    val referenceYMeters: Double?,
    val supportCount: Int,
    val raisedSamples: Int,
    val flatRugSamples: Int,
    val belowSamples: Int,
    val unknownSamples: Int,
)

internal object SemanticGroundDiagnostic {
    fun evaluate(cells: List<SemanticDepthCell>): SemanticGroundReading
}
```

### Implementation notes

1. Exact 64-cell grid; duplicate / OOB column·row·label / size ≠ 64 → `invalid`.
2. Geometry: all-null allowed; partial or non-finite full triple → `invalid` (before seeding).
3. Four-connected label-`4` BFS **only** from bottom seeds `(3,7)` / `(4,7)`. Neither seed floor → `no_floor_component`. No diagonal edges; rug/table/wall never seed or expand the component.
4. Supports = component ∩ finite XYZ. Gates: `>=6`, xz spreads `>=0.20` m, some support triangle area `>=0.01` m², every support `|y-median|<=0.04` (no outlier drop). Fail → `insufficient_support` with measured `supportCount`, null ref, zero height buckets.
5. Success → `floor_like_candidate` only; `referenceYMeters` = median (even count = average of two central sorted Y). Stateless single-frame; no confidence / nav / temporal state.
6. Non-support height buckets vs median: `>0.08` raised, `<-0.08` below, rug29 `|Δ|<=0.04` flatRug, else / missing depth → unknown. Flat-rug never substitutes for failed supports.
7. Shoelace / spread / median reject non-finite arithmetic rather than inventing coverage.

### Honest test fix (not a weaken)

- `permutationOfValidGridPreservesReading` used `java.util.Random`, which is not `kotlin.random.Random` for `Iterable.shuffled`. Switched to `kotlin.random.Random(7)` so the suite compiles; assertions unchanged.

## Tests (39) — preserved

Same 39 `@Test` methods as the tests-first phase (invalid / no-component / insufficient / candidate / height buckets / wording ban). Contract thresholds unchanged.

## RED / GREEN evidence

- **RED:** Codex independent Gradle compile RED before this source (log name only; contents not read).
- **GREEN:** **Not run by this worker.** No invented GREEN. Codex owns `:app:testDebugUnitTest` for `SemanticGroundDiagnosticTest`.

## Genuine unverified limits

- No device frames, MOSAIC labels, ARCore world points, Activity wiring, or temporal confirmation.
- `floor_like_candidate` is not confirmed floor, walkability, CLEAR, or movement permission.
- Thresholds remain diagnostic test assumptions, not calibrated safety.

## NEXT

`SOURCE_READY` → Codex verifies focused tests. This worker stops here.
