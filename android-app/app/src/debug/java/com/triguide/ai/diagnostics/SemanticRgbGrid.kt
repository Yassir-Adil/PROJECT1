package com.triguide.ai.diagnostics

/** Direct SDK VIEW→IMAGE points for RGB-only labels; never a depth reprojection or geometry source. */
internal object SemanticRgbGrid {
    data class Decoded(val cells: List<SemanticPaintCell>, val unmappedCells: Int)

    fun decode(width: Int, height: Int, quarterTurns: Int,
        imageUvs: List<Pair<Double,Double>>, classMap: IntArray): Decoded {
        require(width > 0 && height > 0 && width.toLong()*height <= 1_048_576L)
        require(quarterTurns in 0..3 && imageUvs.size == 64)
        require(classMap.size == DiagnosticSegmenter.SIZE*DiagnosticSegmenter.SIZE)
        require(classMap.all { it in 0..31 })
        var unmapped = 0
        val cells = imageUvs.mapIndexed { index, point ->
            val oriented = SemanticImageOrientation.pixel(width,height,point.first,point.second,quarterTurns)
            val position = oriented?.let { SemanticFusionMapping.modelIndex(it.width,it.height,
                it.x.toDouble()/it.width,it.y.toDouble()/it.height) }
            if (position == null) unmapped++
            SemanticPaintCell(index%8,index/8,position?.let { classMap[it] } ?: 0)
        }
        return Decoded(cells,unmapped)
    }
}
