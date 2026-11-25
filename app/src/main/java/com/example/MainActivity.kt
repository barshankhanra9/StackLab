package com.example

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.engine.FocusStackProcessor
import com.example.ui.navigation.StackLabNav
import com.example.ui.theme.StackLabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        try {
            FocusStackProcessor.ensureOpenCvInitialized()
        } catch (t: Throwable) {
            Log.e("StackLab", "OpenCV pre-init failed; will retry on stack start", t)
        }

        setContent {
            StackLabTheme {
                StackLabNav()
            }
        }
    }
}
