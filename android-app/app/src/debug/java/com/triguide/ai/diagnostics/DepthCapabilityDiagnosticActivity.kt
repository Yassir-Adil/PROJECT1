package com.triguide.ai.diagnostics

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session

/** No install prompt, resume, image acquisition, camera binding, API key or cloud configuration. */
class DepthCapabilityDiagnosticActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var session: Session? = null
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "DEPTH_CAPABILITY status=UNAVAILABLE reason=missing_camera_permission frames=0 navigation=none")
                return
            }
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            session = Session(this)
            val automatic = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            val raw = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
            Log.i(TAG, "DEPTH_CAPABILITY status=CHECKED availability=$availability automatic=$automatic raw=$raw " +
                "frames=0 camera_resumed=false metric_accuracy=unverified navigation=none")
        } catch (error: Exception) {
            Log.w(TAG, "DEPTH_CAPABILITY status=UNAVAILABLE reason=${error.javaClass.simpleName} " +
                "frames=0 navigation=none", error)
        } finally {
            try { session?.close() } finally { finish() }
        }
    }
    companion object { private const val TAG = "TriGuideDepthCapability" }
}
