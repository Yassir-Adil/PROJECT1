package com.triguide.ai.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

internal fun confirmPulse(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(VibrationEffect.createOneShot(50L, VibrationEffect.DEFAULT_AMPLITUDE))
}

/**
 * Records taps without consuming them, so scrolling and buttons still receive the pointer.
 * A long press and a drag past touch slop are not taps.
 */
@Composable
fun Modifier.observeScreenTaps(onTap: () -> Unit): Modifier {
    val current = rememberUpdatedState(onTap)
    return pointerInput(Unit) {
        val slop = viewConfiguration.touchSlop
        val maxTapMs = viewConfiguration.longPressTimeoutMillis
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerId = down.id
            val origin = down.position
            val startedAt = down.uptimeMillis
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                val delta = change.position - origin
                if (delta.x * delta.x + delta.y * delta.y > slop * slop) {
                    moved = true
                }
                if (!change.pressed) {
                    val duration = change.uptimeMillis - startedAt
                    if (!moved && duration >= 0L && duration < maxTapMs) {
                        current.value()
                    }
                    break
                }
            }
        }
    }
}
