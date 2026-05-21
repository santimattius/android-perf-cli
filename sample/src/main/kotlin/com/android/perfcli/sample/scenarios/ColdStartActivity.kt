package com.android.perfcli.sample.scenarios

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen

class ColdStartActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Cold Start Delay",
                    onBadBehavior = {
                        Toast.makeText(
                            this@ColdStartActivity,
                            "Build & install the 'coldStart' variant: ./gradlew :sample:assembleColdStart\n" +
                                "Then force-stop and relaunch to see 2s Application.onCreate() delay.",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onFixedBehavior = {
                        Toast.makeText(
                            this@ColdStartActivity,
                            "The debug/release variant has ENABLE_COLD_START_DELAY=false. " +
                                "Use lazy init and background threads — no blocking in Application.onCreate().",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }
}
