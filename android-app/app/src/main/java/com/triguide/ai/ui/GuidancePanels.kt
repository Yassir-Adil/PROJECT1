package com.triguide.ai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triguide.ai.domain.IllustrativePath
import com.triguide.ai.domain.NavigationConfig
import com.triguide.ai.domain.NavigationDecision
import com.triguide.ai.domain.SteeringCommand
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GuidanceReadout(
    decision: NavigationDecision,
    applied: SteeringCommand,
    showPath: Boolean,
    modifier: Modifier = Modifier,
) {
    val manual = decision.perceptionLabel == NavigationConfig.MANUAL_LABEL
    val title = if (manual) "Manual" else decision.action.userLabel
    val confidenceText = if (manual) {
        "not used"
    } else {
        "${(decision.confidence * 100f).roundToInt().coerceIn(0, 100)} percent"
    }
    val wheelDegrees = Math.toDegrees(applied.steeringRadians)
    val wheelText = String.format(Locale.US, "%.1f", wheelDegrees)
    val speedText = String.format(Locale.US, "%.2f", applied.linearSpeedMps)
    val spoken = "Research and Simulation Mode. Decision $title. Reason: ${decision.reason} " +
        "Confidence $confidenceText. Front wheel response $wheelText degrees. " +
        "Virtual speed $speedText meters per second. Source ${decision.perceptionLabel}. " +
        IllustrativePath.LABEL
    Column(
        modifier = modifier.semantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Research and Simulation Mode",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
        )
        Text(
            text = "Decision: $title",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Reason: ${decision.reason}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Text(
            text = "Confidence: $confidenceText",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Text(
            text = "Front wheel response: $wheelText degrees",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Text(
            text = "Virtual speed: $speedText m/s",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Text(
            text = "Source: ${decision.perceptionLabel}",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        Text(
            text = IllustrativePath.LABEL,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
        )
        if (showPath) {
            IllustrativePathCanvas(
                command = applied,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
            )
        }
    }
}

@Composable
fun IllustrativePathCanvas(
    command: SteeringCommand,
    modifier: Modifier = Modifier,
) {
    val poses = IllustrativePath.preview(command)
    Canvas(
        modifier = modifier.semantics { contentDescription = IllustrativePath.LABEL },
    ) {
        val center = Offset(size.width / 2f, size.height * 0.82f)
        val scale = 70f
        val path = Path()
        poses.forEachIndexed { index, pose ->
            val x = center.x - (pose.yMeters * scale).toFloat()
            val y = center.y - (pose.xMeters * scale).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = Color(0xFFBDBDBD), style = Stroke(width = 4f))
        drawCircle(color = Color(0xFFFFE14A), radius = 6f, center = center)
    }
}
