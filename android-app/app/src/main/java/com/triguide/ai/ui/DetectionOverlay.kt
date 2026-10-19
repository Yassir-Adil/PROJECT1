package com.triguide.ai.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triguide.ai.domain.DetectionFrame
import com.triguide.ai.domain.DetectionReports
import java.util.Locale

/**
 * Debug boxes only. TalkBack stays quiet here so raw, changing labels are not announced;
 * on-demand Describe remains the spoken snapshot path.
 */
@Composable
fun DetectionOverlay(
    frame: DetectionFrame?,
    showBoxes: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = frame?.takeIf { showBoxes && it.fromRealDetector }
    val textSizePx = with(LocalDensity.current) { 16.sp.toPx() }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val drawn = active ?: return@Canvas
        val native = drawContext.canvas.nativeCanvas
        val boxPaint = Paint().apply {
            color = android.graphics.Color.rgb(255, 225, 74)
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            isAntiAlias = true
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.rgb(17, 17, 17)
            textSize = textSizePx
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val backgroundPaint = Paint().apply {
            color = android.graphics.Color.rgb(255, 225, 74)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val bounds = Rect()
        drawn.objects.forEach { detected ->
            val mapped = DetectionReports.mapCenterCrop(
                boxLeft = detected.left,
                boxTop = detected.top,
                boxRight = detected.right,
                boxBottom = detected.bottom,
                imageWidth = drawn.imageWidth,
                imageHeight = drawn.imageHeight,
                viewWidth = size.width,
                viewHeight = size.height,
                mirrorHorizontally = drawn.mirrorHorizontally,
            ) ?: return@forEach
            native.drawRect(mapped.left, mapped.top, mapped.right, mapped.bottom, boxPaint)
            val text = "${detected.label} ${String.format(Locale.US, "%.2f", detected.confidence)}"
            textPaint.getTextBounds(text, 0, text.length, bounds)
            val textLeft = mapped.left.coerceAtLeast(0f)
            val textBaseline = mapped.top.coerceAtLeast(bounds.height().toFloat() + 4f)
            native.drawRect(
                textLeft,
                textBaseline - bounds.height() - 4f,
                textLeft + bounds.width() + 12f,
                textBaseline + 6f,
                backgroundPaint,
            )
            native.drawText(text, textLeft + 6f, textBaseline, textPaint)
        }
    }
}
