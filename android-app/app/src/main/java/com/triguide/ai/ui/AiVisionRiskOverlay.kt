package com.triguide.ai.ui

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triguide.ai.domain.AiVisionOverlayModel
import com.triguide.ai.domain.AiVisionTint
import com.triguide.ai.domain.DetectionFrame
import com.triguide.ai.domain.DetectionReports
import com.triguide.ai.domain.PathRiskAnalyzer
import java.util.Locale

/**
 * Research path-risk overlay. TalkBack stays quiet; colors are visual diagnostics only.
 * Green walk lane only with validated fusion evidence; labeled not a safety signal.
 */
@Composable
fun AiVisionRiskOverlay(
    model: AiVisionOverlayModel,
    frame: DetectionFrame?,
    modifier: Modifier = Modifier,
) {
    if (!model.enabled) return
    val textSizePx = with(LocalDensity.current) { 14.sp.toPx() }
    val strokePx = with(LocalDensity.current) { 3.dp.toPx() }
    val captionSizePx = with(LocalDensity.current) { 12.sp.toPx() }
    Canvas(modifier = modifier.clearAndSetSemantics { }) {
        val native = drawContext.canvas.nativeCanvas
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val leftX = model.corridorLeft * w
        val rightX = model.corridorRight * w

        // Full-height corridor wash (subtle).
        val washPaint = Paint().apply {
            color = argbFor(model.bandTint, alpha = if (model.showOpenWalkLane) 0x28 else 0x55)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        native.drawRect(leftX, 0f, rightX, h, washPaint)

        // Strong green walk strip in the lower corridor when no detector obstacle is seen.
        if (model.showOpenWalkLane) {
            val laneTop = (model.laneTopY.coerceIn(0f, 1f) * h)
            val lanePaint = Paint().apply {
                color = argbFor(AiVisionTint.OPEN_LANE, alpha = 0x88)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            native.drawRect(leftX, laneTop, rightX, h, lanePaint)
            val laneEdge = Paint().apply {
                color = argbFor(AiVisionTint.OPEN_LANE, alpha = 0xEE)
                style = Paint.Style.STROKE
                strokeWidth = strokePx * 1.4f
                isAntiAlias = true
            }
            native.drawLine(leftX, laneTop, leftX, h, laneEdge)
            native.drawLine(rightX, laneTop, rightX, h, laneEdge)
            native.drawLine(leftX, laneTop, rightX, laneTop, laneEdge)
        }

        val edgePaint = Paint().apply {
            color = argbFor(model.bandTint, alpha = 0xDD)
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            isAntiAlias = true
        }
        native.drawLine(leftX, 0f, leftX, h, edgePaint)
        native.drawLine(rightX, 0f, rightX, h, edgePaint)

        val labelPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = textSizePx
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val labelBg = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val boxStroke = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = strokePx
            isAntiAlias = true
        }
        val bounds = Rect()

        // Prefer normalized layers from PathRisk; fall back to mapping detector pixels
        // when fixtures omit box extents but a live frame is present.
        if (model.boxes.isNotEmpty()) {
            model.boxes.forEach { layer ->
                val left = layer.boxLeftNormalized * w
                val top = layer.boxTopNormalized * h
                val right = layer.boxRightNormalized * w
                val bottom = layer.boxBottomNormalized * h
                boxStroke.color = argbFor(layer.tint, alpha = 0xFF)
                labelBg.color = argbFor(layer.tint, alpha = 0xE6)
                native.drawRect(left, top, right, bottom, boxStroke)
                val text = "${layer.label} ${String.format(Locale.US, "%.2f", layer.confidence)}"
                labelPaint.getTextBounds(text, 0, text.length, bounds)
                val textLeft = left.coerceAtLeast(0f)
                val textBaseline = top.coerceAtLeast(bounds.height().toFloat() + 4f)
                native.drawRect(
                    textLeft,
                    textBaseline - bounds.height() - 4f,
                    textLeft + bounds.width() + 12f,
                    textBaseline + 6f,
                    labelBg,
                )
                native.drawText(text, textLeft + 6f, textBaseline, labelPaint)
            }
        } else if (frame != null && frame.fromRealDetector) {
            // Empty PathRisk corridor cues (UNKNOWN) — still draw detector boxes in off-path green.
            frame.objects.forEach { detected ->
                val mapped = DetectionReports.mapCenterCrop(
                    boxLeft = detected.left,
                    boxTop = detected.top,
                    boxRight = detected.right,
                    boxBottom = detected.bottom,
                    imageWidth = frame.imageWidth,
                    imageHeight = frame.imageHeight,
                    viewWidth = w,
                    viewHeight = h,
                    mirrorHorizontally = frame.mirrorHorizontally,
                ) ?: return@forEach
                boxStroke.color = argbFor(AiVisionTint.UNKNOWN_OFF_PATH, alpha = 0xFF)
                labelBg.color = argbFor(AiVisionTint.UNKNOWN_OFF_PATH, alpha = 0xE6)
                native.drawRect(mapped.left, mapped.top, mapped.right, mapped.bottom, boxStroke)
                val text = "${detected.label} ${String.format(Locale.US, "%.2f", detected.confidence)}"
                labelPaint.getTextBounds(text, 0, text.length, bounds)
                val textLeft = mapped.left.coerceAtLeast(0f)
                val textBaseline = mapped.top.coerceAtLeast(bounds.height().toFloat() + 4f)
                native.drawRect(
                    textLeft,
                    textBaseline - bounds.height() - 4f,
                    textLeft + bounds.width() + 12f,
                    textBaseline + 6f,
                    labelBg,
                )
                native.drawText(text, textLeft + 6f, textBaseline, labelPaint)
            }
        }

        val captionPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = captionSizePx
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val captionBg = Paint().apply {
            color = android.graphics.Color.argb(0xB3, 0x11, 0x11, 0x11)
            style = Paint.Style.FILL
        }
        val shortCaption = if (model.showOpenWalkLane) {
            "Green · ${com.triguide.ai.domain.OpenLaneCue.DIAGNOSTIC_LABEL}"
        } else {
            "AI Vision · corridor ${PathRiskAnalyzer.CORRIDOR_LEFT}–${PathRiskAnalyzer.CORRIDOR_RIGHT} · " +
                model.pathRisk.name + " · diagnostic only / not a safety signal"
        }
        captionPaint.getTextBounds(shortCaption, 0, shortCaption.length, bounds)
        val pad = 10f
        native.drawRect(0f, h - bounds.height() - pad * 2f, w, h, captionBg)
        native.drawText(shortCaption, pad, h - pad, captionPaint)
    }
}

private fun argbFor(tint: AiVisionTint, alpha: Int): Int {
    val a = alpha.coerceIn(0, 255)
    return when (tint) {
        // Neutral gray-blue for unknown/off-path evidence. It must not look like
        // a positive walkable-region signal.
        AiVisionTint.UNKNOWN_OFF_PATH -> android.graphics.Color.argb(a, 0x70, 0x78, 0x88)
        // Stronger lane green is reserved for a validated positive evidence gate.
        AiVisionTint.OPEN_LANE -> android.graphics.Color.argb(a, 0x1F, 0xC2, 0x6B)
        AiVisionTint.POSSIBLE -> android.graphics.Color.argb(a, 0xE6, 0xB8, 0x00)
        AiVisionTint.BLOCKED -> android.graphics.Color.argb(a, 0xD0, 0x32, 0x32)
        AiVisionTint.RESEARCH_CLEAR_MARKER -> android.graphics.Color.argb(a, 0x2F, 0x80, 0xED)
    }
}
