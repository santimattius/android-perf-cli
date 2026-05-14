package com.android.perfcli.sample.scenarios

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiskReadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "StrictMode: Disk Read",
                    onBadBehavior = {
                        // BAD: reading SharedPreferences on main thread — triggers DISK_READ violation
                        val value = getSharedPreferences("sample_prefs", Context.MODE_PRIVATE)
                            .getString("key", "default")
                        Log.d("DiskReadActivity", "Read (bad): $value")
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: read on IO dispatcher, deliver result to main
                            val value = withContext(Dispatchers.IO) {
                                getSharedPreferences("sample_prefs", Context.MODE_PRIVATE)
                                    .getString("key", "default")
                            }
                            Log.d("DiskReadActivity", "Read (fixed): $value")
                        }
                    },
                )
            }
        }
    }
}
