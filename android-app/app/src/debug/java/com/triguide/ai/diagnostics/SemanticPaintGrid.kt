package com.triguide.ai.diagnostics

/**
 * Typed paint-only cell: column/row/label with no world geometry.
 *
 * Synthetic labels are self-test pattern input only. They are never scene
 * observations, depth evidence, clearance, or a safe-path identity.
 */
internal data class SemanticPaintCell(
    val column: Int,
    val row: Int,
    val label: Int,
)

/**
 * Pure helpers for an 8×8 paint grid used by synthetic render attribution.
 * Does not schedule, observe cameras, or alter navigation state.
 */
internal object SemanticPaintGrid {
    private const val GRID = 8
    private const val CELL_COUNT = GRID * GRID
    private val SYNTHETIC_LABELS = intArrayOf(1, 4, 29)

    private const val COLOUR_WALL_LIKE = 0xff5faaff.toInt()
    private const val COLOUR_FLOOR_LIKE = 0xffaf91ff.toInt()
    private const val COLOUR_RUG_LIKE = 0xfff5be46.toInt()

    /** Deterministic 64-cell pattern using only legend labels 1 / 4 / 29. */
    fun synthetic(): List<SemanticPaintCell> =
        List(CELL_COUNT) { index ->
            SemanticPaintCell(
                column = index % GRID,
                row = index / GRID,
                label = SYNTHETIC_LABELS[index % SYNTHETIC_LABELS.size],
            )
        }

    /**
     * Exact 64 cells, unique in-bounds positions (0..7), labels in 0..31.
     * Does not require legend colours; unknown labels remain valid grid structure.
     */
    fun valid(cells: List<SemanticPaintCell>): Boolean {
        if (cells.size != CELL_COUNT) return false
        val seen = HashSet<Long>(CELL_COUNT)
        for (cell in cells) {
            if (cell.column !in 0 until GRID || cell.row !in 0 until GRID) return false
            if (cell.label !in 0..31) return false
            val key = (cell.column.toLong() shl 32) or (cell.row.toLong() and 0xffffffffL)
            if (!seen.add(key)) return false
        }
        return seen.size == CELL_COUNT
    }

    /** Opaque ARGB for live-style legend labels only; null otherwise (no safety green). */
    fun colour(label: Int): Int? =
        when (label) {
            1 -> COLOUR_WALL_LIKE
            4 -> COLOUR_FLOOR_LIKE
            29 -> COLOUR_RUG_LIKE
            else -> null
        }

    /**
     * Copies column/row/label only; strips all world geometry.
     * Returns empty when geometry or labels cannot form a valid paint grid.
     */
    fun fromDepth(cells: List<SemanticDepthCell>): List<SemanticPaintCell> {
        if (cells.size != CELL_COUNT) return emptyList() // bound before allocation or iteration
        val paint = cells.map { SemanticPaintCell(it.column, it.row, it.label) }
        return if (valid(paint)) paint else emptyList()
    }
}
