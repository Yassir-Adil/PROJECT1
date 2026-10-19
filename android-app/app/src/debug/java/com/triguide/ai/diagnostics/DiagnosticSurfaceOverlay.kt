package com.triguide.ai.diagnostics

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.triguide.ai.domain.DetectionReports

/** Muted blue wall, amber rug, gray floor. No green/go cue. Reuses preview center-crop mapping. */
@Composable
internal fun DiagnosticSurfaceOverlay(map: DiagnosticSurfaceMap?, modifier: Modifier = Modifier) {
    val bitmap = remember(map) {
        map?.let {
            val colors = IntArray(it.cells.size) { i ->
                when (it.cells[i]) {
                    DiagnosticSurfaces.WALL -> 0x505A8FB5
                    DiagnosticSurfaces.RUG -> 0x60C99B46
                    DiagnosticSurfaces.FLOOR -> 0x305F6D78
                    else -> 0
                }
            }
            Bitmap.createBitmap(colors, DiagnosticSurfaces.GRID, DiagnosticSurfaces.GRID, Bitmap.Config.ARGB_8888)
        }
    }
    // The renderer can retain a display-list reference after composition changes.
    // Let GC release these tiny 64x64 bitmaps; never recycle a GPU-retained frame.
    val paint = remember { Paint().apply { isFilterBitmap = false } }
    Canvas(modifier.clearAndSetSemantics { }) {
        val active = map ?: return@Canvas
        val image = bitmap ?: return@Canvas
        if (image.isRecycled) return@Canvas
        val bounds = DetectionReports.mapCenterCrop(0f, 0f, active.sourceWidth.toFloat(), active.sourceHeight.toFloat(),
            active.sourceWidth, active.sourceHeight, size.width, size.height, false) ?: return@Canvas
        val native = drawContext.canvas.nativeCanvas
        native.save()
        try {
            native.clipRect(0f, 0f, size.width, size.height)
            if (active.mirrored) native.scale(-1f, 1f, size.width / 2f, size.height / 2f)
            native.drawBitmap(image, Rect(0, 0, image.width, image.height),
                RectF(bounds.left, bounds.top, bounds.right, bounds.bottom), paint)
        } finally { native.restore() }
    }
}
