package com.android.perfcli.sample.scenarios

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiskWriteActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "StrictMode: Disk Write",
                    onBadBehavior = {
                        // BAD: commit() on main thread — triggers DISK_WRITE StrictMode violation
                        getSharedPreferences("sample_prefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString("key", "bad_value")
                            .commit()
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: apply() is async — no main-thread disk write
                            withContext(Dispatchers.IO) {
                                getSharedPreferences("sample_prefs", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("key", "fixed_value")
                                    .apply()
                            }
                        }
                    },
                )
            }
        }
    }
}
