# Cursor A — SemanticPaintGrid / SemanticGridPainter (2026-10-20)

**Status: SOURCE_READY**

Exclusive scope after Codex compile RED for missing paint-grid APIs: two new
debug source files plus this note. No shell, build, unit/UI test run, ADB, Git,
or network. No Activity/manifest/Gradle/assets/model/test edits. Owner dirty
files preserved. No runtime or test outcome is claimed here.

## Implemented

### `SemanticPaintGrid.kt`

- `internal data class SemanticPaintCell(column, row, label)` — paint-only; no
  world geometry.
- `object SemanticPaintGrid`:
  - `synthetic()` — exactly 64 unique 8×8 cells; label set `{1, 4, 29}` only.
  - `valid(cells)` — size 64, unique in-bounds positions, labels `0..31`.
  - `colour(label)` — opaque ARGB `1→0xff5faaff`, `4→0xffaf91ff`,
    `29→0xfff5be46`; else `null` (no invented safety green).
  - `fromDepth(cells)` — copies column/row/label only; empty when the projected
    grid fails `valid`.

Synthetic pattern is self-test input only; never `SemanticDepthCell`, scene
evidence, clearance, or safe-path identity.

### `SemanticGridPainter.kt`

- `internal class SemanticGridPainter` with one `Paint` allocated per instance.
- `draw(canvas, width, height, cells, density): Int` returns the count of
  recognized legend cells actually `drawRect`-painted.
- Returns `0` (no paint) for non-positive width/height, non-finite/non-positive
  density, or `!valid(cells)`.
- Live style preserved: fill alpha 40, stroke alpha 180, `strokeWidth = density`,
  cell size `width/8f` × `height/8f`, same ARGB colours as above.
- No `Canvas.clear`, `saveLayer`, background erasure, opacity/lifetime/colour
  changes, scheduling, or camera/model/geometry/navigation state.

## Paths changed

- `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SemanticPaintGrid.kt`
- `android-app/app/src/debug/java/com/triguide/ai/diagnostics/SemanticGridPainter.kt`
- `docs/CURSOR_PAINT_GRID_2026-10-20.md`

## Explicit non-claims / caveats

- No compile, JVM, or Android UI test execution asserted by this worker.
- Pattern self-test dark background / Activity wiring owned by Codex.
- Palette and live overlay style unmatched beyond the contracts above; no
  freshness, gate, model, permission, or navigation change.
- Hand-off: SOURCE_READY for Codex GREEN against `SemanticPaintGridTest` and
  remaining integration.
