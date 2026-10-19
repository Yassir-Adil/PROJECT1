package com.triguide.ai.voice

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Soft non-speech tones for diagnostic UX.
 *
 * - [playCameraActiveOnce]: one calm tick when the camera stream is ready.
 * - [playSoftDoubleBeep]: reserved for a future validated open-lane diagnostic only
 *   (must not be driven by detector-empty UNKNOWN).
 *
 * Never means path clear / safe to walk. Emergency STOP must call [release].
 */
class SoftOpenLaneBeeper {
    private val main = Handler(Looper.getMainLooper())
    private val playing = AtomicBoolean(false)
    private var tone: ToneGenerator? = null

    /** Single quiet tick: application/camera active — not a safety signal. */
    fun playCameraActiveOnce() {
        if (!playing.compareAndSet(false, true)) return
        main.post {
            try {
                val gen = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_ACTIVE).also { tone = it }
                gen.startTone(ToneGenerator.TONE_PROP_ACK, ACTIVE_MS)
                main.postDelayed({ playing.set(false) }, (ACTIVE_MS + 80).toLong())
            } catch (error: Exception) {
                Log.w(TAG, "Camera-active tone failed: ${error.message}")
                playing.set(false)
            }
        }
    }

    fun playSoftDoubleBeep() {
        if (!playing.compareAndSet(false, true)) return
        main.post {
            try {
                val gen = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, VOLUME_LANE).also { tone = it }
                gen.startTone(ToneGenerator.TONE_PROP_ACK, FIRST_MS)
                main.postDelayed({
                    try {
                        gen.startTone(ToneGenerator.TONE_PROP_ACK, SECOND_MS)
                    } catch (error: Exception) {
                        Log.w(TAG, "Second soft beep failed: ${error.message}")
                    } finally {
                        main.postDelayed({ playing.set(false) }, 120L)
                    }
                }, GAP_MS.toLong())
            } catch (error: Exception) {
                Log.w(TAG, "Soft open-lane beep failed: ${error.message}")
                playing.set(false)
            }
        }
    }

    fun release() {
        main.removeCallbacksAndMessages(null)
        playing.set(false)
        try {
            tone?.release()
        } catch (_: Exception) {
        }
        tone = null
    }

    companion object {
        private const val TAG = "TriGuideOpenLane"
        private const val VOLUME_ACTIVE = 22
        private const val VOLUME_LANE = 28
        private const val ACTIVE_MS = 110
        private const val FIRST_MS = 90
        private const val SECOND_MS = 70
        private const val GAP_MS = 160
    }
}
