package com.triguide.ai.diagnostics

/** Common VIEW-only publication. Neither branch is a navigation instruction. */
internal sealed interface SemanticObservation {
    val revision: Long
    val receivedNs: Long
    val frameTimestampNs: Long
    val completedElapsedNs: Long
    val paintCells: List<SemanticPaintCell>
    val unmappedCells: Int
    val kind: String
}

/** No world points, ground reference or height result can be attached to this type. */
internal class Semantic2DObservation(
    override val revision: Long,
    override val receivedNs: Long,
    override val frameTimestampNs: Long,
    override val completedElapsedNs: Long,
    cells: List<SemanticPaintCell>,
    override val unmappedCells: Int,
) : SemanticObservation {
    init {
        require(revision >= 0 && receivedNs >= 0 && frameTimestampNs > 0)
        require(completedElapsedNs >= receivedNs && unmappedCells in 0..64)
        require(SemanticPaintGrid.valid(cells)) // bounded before snapshot copy
    }
    override val paintCells = cells.toList()
    override val kind = "rgb_only"
}

/** Geometry is available only through the pre-existing admitted same-frame raw path. */
internal data class SemanticFusionResult(
    override val revision: Long,
    override val receivedNs: Long,
    override val frameTimestampNs: Long,
    val cells: List<SemanticDepthCell>,
    val ground: SemanticGroundReading,
    override val unmappedCells: Int,
    override val completedElapsedNs: Long = receivedNs,
) : SemanticObservation {
    override val paintCells = SemanticPaintGrid.fromDepth(cells)
    override val kind = "raw_fused"
}
