package com.triguide.ai.domain

/**
 * Policy for a single soft non-speech tone when the camera analysis stream is ready.
 *
 * This is an application-active cue only. It does not mean the path is safe,
 * clear, or walkable. Emergency STOP / guidance pause must suppress it.
 */
object CameraActiveCue {
    /**
     * @param alreadyPlayedForThisBind true after the tone was played for the current
     *   camera bind / Streaming episode; reset when the stream leaves Streaming.
     * @param emergencyActive true while emergency stop / paused guidance owns the session.
     */
    fun shouldPlayStartupTone(
        cameraStreaming: Boolean,
        guidanceRunning: Boolean,
        alreadyPlayedForThisBind: Boolean,
        emergencyActive: Boolean,
    ): Boolean {
        if (emergencyActive) return false
        if (!cameraStreaming || !guidanceRunning) return false
        if (alreadyPlayedForThisBind) return false
        return true
    }
}
