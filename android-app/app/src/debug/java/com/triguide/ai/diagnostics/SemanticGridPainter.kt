package com.triguide.ai.diagnostics

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Draws recognized legend cells with the existing live overlay fill/stroke style.
 * One [Paint] per painter instance. No clear/saveLayer, scheduling, or scene state.
 */
internal class SemanticGridPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * @return count of recognized cells that were actually [Canvas.drawRect]-painted;
     *         0 when bounds/density/grid are invalid (nothing drawn).
     */
    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        cells: List<SemanticPaintCell>,
        density: Float,
    ): Int {
        if (width <= 0 || height <= 0) return 0
        if (!density.isFinite() || density <= 0f) return 0
        if (!SemanticPaintGrid.valid(cells)) return 0

        val cellW = width / 8f
        val cellH = height / 8f
        var painted = 0
        for (cell in cells) {
            val color = SemanticPaintGrid.colour(cell.label) ?: continue
            val left = cell.column * cellW
            val top = cell.row * cellH
            val right = left + cellW
            val bottom = top + cellH

            paint.style = Paint.Style.FILL
            paint.color = color
            paint.alpha = FILL_ALPHA
            canvas.drawRect(left, top, right, bottom, paint)

            paint.style = Paint.Style.STROKE
            paint.alpha = STROKE_ALPHA
            paint.strokeWidth = density
            canvas.drawRect(left, top, right, bottom, paint)

            painted++
        }
        return painted
    }

    private companion object {
        const val FILL_ALPHA = 40
        const val STROKE_ALPHA = 180
    }
}
