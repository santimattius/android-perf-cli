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

class FrozenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Frozen Frame (>700ms)",
                    onBadBehavior = {
                        lifecycleScope.launch {
                            // BAD: blocks main thread for 1200ms — well above the 700ms frozen-frame threshold
                            Thread.sleep(1_200)
                        }
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: equivalent wait on background dispatcher — main thread stays responsive
                            withContext(Dispatchers.Default) { delay(1_200) }
                        }
                    },
                )
            }
        }
    }
}
