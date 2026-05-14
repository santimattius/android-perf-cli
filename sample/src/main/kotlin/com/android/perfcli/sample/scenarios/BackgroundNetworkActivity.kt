package com.android.perfcli.sample.scenarios

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.android.perfcli.sample.scenarios.internal.PollingIntentService
import com.android.perfcli.sample.scenarios.internal.PollingWorker
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen

class BackgroundNetworkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Background Network",
                    onBadBehavior = {
                        // BAD: legacy IntentService for background HTTP — deprecated, unthrottled
                        val intent = Intent(this@BackgroundNetworkActivity, PollingIntentService::class.java)
                        ContextCompat.startForegroundService(this@BackgroundNetworkActivity, intent)
                        Log.w("BackgroundNetworkActivity", "IntentService started (bad path)")
                    },
                    onFixedBehavior = {
                        // FIXED: WorkManager handles lifecycle, battery, and deferral properly
                        val request = OneTimeWorkRequestBuilder<PollingWorker>().build()
                        WorkManager.getInstance(this@BackgroundNetworkActivity).enqueue(request)
                        Log.d("BackgroundNetworkActivity", "WorkManager task enqueued (fixed path)")
                    },
                )
            }
        }
    }
}
