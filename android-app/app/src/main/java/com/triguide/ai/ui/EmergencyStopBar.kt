package com.triguide.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

private val StopRed = Color(0xFFB3261E)
private val OnStop = Color(0xFFFFF1EE)

/** One-touch stop. The pointer callback fires on down, not after a hold or tap release. */
@Composable
fun EmergencyStopBar(
    onConfirmedStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentStop = rememberUpdatedState(onConfirmedStop)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(StopRed)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Emergency stop. Tap once to pause guidance."
                onClick(label = "Stop guidance") {
                    currentStop.value()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    currentStop.value()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "STOP",
            color = OnStop,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
