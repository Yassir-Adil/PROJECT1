package com.triguide.ai.diagnostics

import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triguide.ai.ui.DetectionOverlay
import com.triguide.ai.ui.TriGuideTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** The original charcoal/sand theme and yellow object boxes, without any path-clear color cue. */
@Composable
internal fun CombinedDiagnosticScreen(
    preview: PreviewView,
    companion: DiagnosticObjectCompanion,
    segmenterStatus: StateFlow<String>,
    stop: () -> Unit,
) {
    val frame by companion.detector.latestFrame.collectAsStateWithLifecycle()
    val readiness by companion.detector.readiness.collectAsStateWithLifecycle()
    val running by companion.running.collectAsStateWithLifecycle()
    val caption by companion.caption.collectAsStateWithLifecycle()
    val voiceStatus by companion.speaker.status.collectAsStateWithLifecycle()
    val voiceSettings by companion.voiceSettings.collectAsStateWithLifecycle()
    val status by segmenterStatus.collectAsStateWithLifecycle()
    val surface by companion.surfaces.collectAsStateWithLifecycle()
    val refreshTick by produceState(0L) {
        while (true) { delay(100); value = value + 1L }
    }
    val visible = remember(frame, readiness, running, refreshTick) {
        // Tick only expires old boxes between results. A cached tick timestamp can
        // precede a newly delivered result and falsely disable Describe as "future".
        DiagnosticObjectPresentation.freshFrame(frame, readiness, SystemClock.elapsedRealtime(), running)
    }
    val visibleSurface = remember(surface, running, refreshTick) {
        DiagnosticSurfaces.fresh(surface, SystemClock.elapsedRealtime(), running)
    }
    TriGuideTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رؤية مشتركة", style = MaterialTheme.typography.titleLarge)
                Text("أشياء + جدار/أرضية • تجربة فقط، ليست إرشادات للمشي", style = MaterialTheme.typography.bodySmall)
                Text("أزرق: جدار • كهرماني: سجادة • رمادي: أرضية — توقعات لا تقيس الارتفاع", style = MaterialTheme.typography.bodySmall)
                Text(status, style = MaterialTheme.typography.bodySmall, minLines = 2, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
                    AndroidView(factory = { preview }, modifier = Modifier.matchParentSize())
                    DiagnosticSurfaceOverlay(visibleSurface, Modifier.matchParentSize())
                    DetectionOverlay(visible, showBoxes = visible != null, modifier = Modifier.matchParentSize())
                }
                Text("الكاشف: $readiness • مربعات حديثة: ${visible?.objects?.size ?: 0}",
                    style = MaterialTheme.typography.bodySmall)
                Text(caption, minLines = 2, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(if (!voiceSettings.speechEnabled || voiceSettings.quietMode) "الصوت مكتوم في الإعدادات؛ الوصف المكتوب متاح." else voiceStatus,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = companion::describe, enabled = running && visible != null,
                        modifier = Modifier.weight(1f).heightIn(min = 56.dp)) { Text("وصف صوتي") }
                    Button(onClick = stop, modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Text("إيقاف") }
                }
            }
        }
    }
}
