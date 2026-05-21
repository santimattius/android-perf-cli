package com.android.perfcli.sample.scenarios

import android.content.Context
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WakeLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Wake Lock Leak",
                    onBadBehavior = {
                        // BAD: acquire with no release — wakelock held indefinitely
                        powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK,
                            "sample:bad-lock",
                        ).acquire()
                        Log.w("WakeLockActivity", "Wakelock acquired — never released (bad path)")
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: always release in finally
                            val lock = powerManager.newWakeLock(
                                PowerManager.PARTIAL_WAKE_LOCK,
                                "sample:good-lock",
                            )
                            try {
                                lock.acquire()
                                delay(2_000)
                            } finally {
                                if (lock.isHeld) lock.release()
                                Log.d("WakeLockActivity", "Wakelock released (fixed path)")
                            }
                        }
                    },
                )
            }
        }
    }
}
