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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.triguide.ai.domain.LiveVisionSwipeDirection
import kotlin.math.abs

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

internal fun successPulse(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.applicationContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    vibrator.vibrate(
        VibrationEffect.createWaveform(longArrayOf(0L, 40L, 40L, 60L), intArrayOf(0, 180, 0, 255), -1),
    )
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

/**
 * Eyes-free Live Vision swipes. Does not steal taps (triple-tap still works).
 * Minimum travel is [LiveVisionGestureDetector.MIN_SWIPE_DP] in density-independent pixels.
 */
@Composable
fun Modifier.observeLiveVisionSwipes(
    onSwipe: (LiveVisionSwipeDirection) -> Unit,
): Modifier {
    val current = rememberUpdatedState(onSwipe)
    val minPx = with(LocalDensity.current) { com.triguide.ai.domain.LiveVisionGestureDetector.MIN_SWIPE_DP.dp.toPx() }
    return pointerInput(minPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerId = down.id
            val origin = down.position
            var last = origin
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                last = change.position
                if (!change.pressed) {
                    val delta = last - origin
                    val direction = classifySwipe(delta, minPx)
                    if (direction != null) {
                        current.value(direction)
                    }
                    break
                }
            }
        }
    }
}

internal fun classifySwipe(delta: Offset, minPx: Float): LiveVisionSwipeDirection? {
    val ax = abs(delta.x)
    val ay = abs(delta.y)
    if (ax < minPx && ay < minPx) return null
    return if (ay >= ax) {
        if (delta.y < 0f) LiveVisionSwipeDirection.UP else LiveVisionSwipeDirection.DOWN
    } else {
        if (delta.x > 0f) LiveVisionSwipeDirection.RIGHT else LiveVisionSwipeDirection.LEFT
    }
}
