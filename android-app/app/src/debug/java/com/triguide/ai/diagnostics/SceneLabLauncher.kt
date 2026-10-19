package com.triguide.ai.diagnostics

import android.content.Context
import android.content.Intent

/** Variant seam: release has a no-op implementation and cannot open debug activities. */
object SceneLabLauncher {
    const val available = true
    fun intent(context: Context): Intent? =
        Intent(context, LiveSegmenterDiagnosticActivity::class.java)
            .putExtra(LiveSegmenterDiagnosticActivity.EXTRA_DURATION_SECONDS, 60)
            .putExtra(LiveSegmenterDiagnosticActivity.EXTRA_OBJECTS, true)
}
