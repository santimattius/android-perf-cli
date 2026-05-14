package com.android.perfcli.sample.scenarios

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Crash",
                    onBadBehavior = {
                        // BAD: posts uncaught exception to main looper — kills the process
                        // deliberately NOT wrapped in runCatching so the crash is observable
                        Handler(Looper.getMainLooper()).post {
                            throw RuntimeException("sample-crash")
                        }
                    },
                    onFixedBehavior = {
                        // FIXED: caught exception — app survives
                        try {
                            throw RuntimeException("sample-crash")
                        } catch (e: RuntimeException) {
                            Log.w("CrashActivity", "Caught intentional error: ${e.message}")
                        }
                    },
                )
            }
        }
    }
}
