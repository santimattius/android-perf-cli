package com.android.perfcli.sample.scenarios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AnrActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "ANR Hint",
                    onBadBehavior = {
                        lifecycleScope.launch {
                            // BAD: blocks main thread for 6s — triggers ANR dialog after 5s
                            Thread.sleep(6_000)
                        }
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: equivalent wait on background dispatcher
                            withContext(Dispatchers.Default) { delay(6_000) }
                        }
                    },
                )
            }
        }
    }
}
