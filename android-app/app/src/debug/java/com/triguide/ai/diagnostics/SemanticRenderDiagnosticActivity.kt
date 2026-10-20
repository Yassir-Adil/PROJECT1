package com.triguide.ai.diagnostics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.triguide.ai.R

/** DUMP-protected, debug-only synthetic drawing self-test; no camera/model/session acquisition. */
class SemanticRenderDiagnosticActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var session: SemanticRenderSession? = null
    private var patternView: PatternView? = null
    private var closed = false
    private var canvasCalls = 0
    private var paintedCells = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seconds = intent.getIntExtra("triguide.diagnostic.duration_seconds", 5)
        if (seconds !in 3..15) { finishDiagnostic("invalid_duration", true); return }
        session = SemanticRenderSession(SystemClock.elapsedRealtimeNanos(), seconds)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(20,20,20))
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.synthetic_render_disclaimer)
            textSize = 18f; setTextColor(Color.WHITE)
            setPadding(dp(16),dp(16),dp(16),dp(16))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        patternView = PatternView(this)
        root.addView(patternView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f))
        root.addView(Button(this).apply {
            text = getString(R.string.synthetic_render_stop); minHeight = dp(56)
            setOnClickListener { finishDiagnostic("user_stop") }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
        Log.i(TAG, "SYNTHETIC_RENDER_START duration_seconds=$seconds source=synthetic camera=false model=false navigation=none")
        handler.postDelayed({ finishDiagnostic("duration") }, seconds * 1000L)
    }

    private inner class PatternView(context: Context) : View(context) {
        private val painter = SemanticGridPainter()
        private val cells = SemanticPaintGrid.synthetic()
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w,h,oldw,oldh)
            if (w > 0 && h > 0 && session?.allowDraw(SystemClock.elapsedRealtimeNanos()) == true) {
                postInvalidateOnAnimation()
            }
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (session?.allowDraw(SystemClock.elapsedRealtimeNanos()) != true) return
            val painted = painter.draw(canvas, width, height, cells, resources.displayMetrics.density)
            if (painted > 0) { canvasCalls++; paintedCells += painted }
        }
    }

    private fun finishDiagnostic(reason: String, failed: Boolean = false) {
        if (closed) return
        closed = true
        session?.stop()
        handler.removeCallbacksAndMessages(null)
        patternView?.invalidate()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val status = when {
            failed -> "FAIL"
            reason != "duration" -> "INTERRUPTED"
            canvasCalls == 0 -> "NO_DRAW"
            else -> "COMPLETE"
        }
        Log.i(TAG, "SYNTHETIC_RENDER_RESULT status=$status reason=$reason canvas_calls=$canvasCalls " +
            "painted_cells=$paintedCells source=synthetic camera=false model=false no_image_storage=true " +
            "human_visibility=unverified navigation=none")
        finish()
    }

    override fun onPause() { finishDiagnostic("paused"); super.onPause() }
    override fun onDestroy() { finishDiagnostic("destroyed"); super.onDestroy() }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    companion object { private const val TAG = "TriGuideRender" }
}
