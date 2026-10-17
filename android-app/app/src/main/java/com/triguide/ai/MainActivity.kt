package com.triguide.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.triguide.ai.ui.TriGuideTheme
import com.triguide.ai.ui.TriGuideRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TriGuideTheme {
                TriGuideRoot()
            }
        }
    }
}
