package com.triguide.ai.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.triguide.ai.domain.PerformanceMonitor
import com.triguide.ai.domain.SpeechDirective
import com.triguide.ai.domain.VoiceCues
import com.triguide.ai.domain.VoiceGateInput
import com.triguide.ai.domain.VoiceGuidanceGate
import com.triguide.ai.vision.MediaPipeObjectDetectorEngine
import com.triguide.ai.voice.AndroidVoiceSpeaker
import com.triguide.ai.voice.VoiceSettingsStore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Reuses the existing detector/narrator. Speech only follows an explicit Describe click. */
internal class DiagnosticObjectCompanion(context: Context) {
    val performance = PerformanceMonitor()
    val detector = MediaPipeObjectDetectorEngine(context, performance)
    val speaker = AndroidVoiceSpeaker(context)
    private val settings = VoiceSettingsStore(context)
    val voiceSettings get() = settings.settings
    private val voiceGate = VoiceGuidanceGate()
    private val paused = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val captionState = MutableStateFlow("المربعات أسماء متوقعة؛ اضغط وصف صوتي عند الحاجة.")
    val caption = captionState.asStateFlow()
    private val runningState = MutableStateFlow(true)
    val running = runningState.asStateFlow()
    val descriptionRequests = AtomicInteger()
    val speechAccepted = AtomicInteger()
    val surfaces = MutableStateFlow<DiagnosticSurfaceMap?>(null)

    /** Called synchronously before the shared ImageProxy owner closes it. */
    fun analyze(image: ImageProxy, front: Boolean) {
        if (!paused.get()) detector.analyze(image, front)
    }

    fun describe() {
        if (paused.get()) return
        descriptionRequests.incrementAndGet()
        val latestFrame = detector.latestFrame.value
        val readiness = detector.readiness.value
        val latestSurface = surfaces.value
        val now = SystemClock.elapsedRealtime() // Read after snapshots, not before concurrent completion.
        val line = DiagnosticObjectPresentation.description(latestFrame,
            readiness, now, running = !paused.get())
        if (line == null) {
            captionState.value = "انتظر صورة حديثة من كاشف الأشياء."
            Log.i(TAG, "DESCRIBE_REQUEST origin=user result=no_fresh_frame")
            return
        }
        val preference = settings.current().sanitized()
        val language = speaker.resolve(preference.language)
        val surface = DiagnosticSurfaces.fresh(latestSurface, now, running = !paused.get())
        val combined = DiagnosticSurfaces.combinedDescription(surface, line,
            DiagnosticObjectPresentation.freshFrame(latestFrame, readiness, now, !paused.get())?.objects?.isNotEmpty() == true)
        val english = combined.english
        val arabic = combined.arabic
        captionState.value = if (language == com.triguide.ai.domain.ResolvedVoiceLanguage.ARABIC) arabic else english
        if (!DiagnosticObjectPresentation.maySpeak(preference, !paused.get()) || !speaker.ready.value) {
            if (!DiagnosticObjectPresentation.maySpeak(preference, !paused.get())) {
                captionState.value += "\nالصوت مكتوم وفق إعداداتك الحالية."
            }
            Log.i(TAG, "DESCRIBE_REQUEST origin=user result=text_only speech_enabled=${preference.speechEnabled} " +
                "quiet=${preference.quietMode} speaker_ready=${speaker.ready.value}")
            return
        }
        val directive = voiceGate.consider(VoiceGateInput(VoiceCues.sceneBrief(english, arabic),
            preference, now, language))
        if (directive is SpeechDirective.Speak) {
            val accepted = speaker.speak(directive) {
                if (!paused.get()) voiceGate.onUtteranceFinished(SystemClock.elapsedRealtime())
            }
            if (accepted) speechAccepted.incrementAndGet() else {
                voiceGate.abandonLastUtterance(now)
                captionState.value += "\nتعذر بدء الصوت؛ الوصف مكتوب."
            }
            Log.i(TAG, "DESCRIBE_REQUEST origin=user speech_accepted=$accepted audible_audio=unverified")
        } else {
            captionState.value += "\nانتظر قليلاً قبل إعادة الوصف."
            Log.i(TAG, "DESCRIBE_REQUEST origin=user result=voice_gate_suppressed")
        }
    }

    /** Main-thread stop: hide stale boxes and stop audio before waiting for native camera work. */
    fun pause() {
        if (!paused.compareAndSet(false, true)) return
        runningState.value = false
        surfaces.value = null
        detector.clearFrame()
        speaker.close()
    }

    /** Called only after camera callbacks are unbound and serialized submission has finished. */
    fun close() {
        if (!closed.compareAndSet(false, true)) return
        pause()
        detector.close()
        detector.clearFrame()
    }

    companion object { private const val TAG = "TriGuideObjects" }
}
