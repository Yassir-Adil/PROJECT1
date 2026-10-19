package com.triguide.ai.diagnostics

import android.content.Context
import android.content.Intent

/** No debug models, ARCore, diagnostic activity intent or control in a release build. */
object SceneLabLauncher {
    const val available = false
    fun intent(@Suppress("UNUSED_PARAMETER") context: Context): Intent? = null
}
