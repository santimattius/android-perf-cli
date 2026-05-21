package com.android.perfcli.sample.scenarios

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.perfcli.sample.scenarios.internal.LeakHolder
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen
import java.lang.ref.WeakReference

class MemoryLeakActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Memory Leak",
                    onBadBehavior = {
                        // BAD: static strong reference prevents GC after rotation
                        LeakHolder.activityRef = this@MemoryLeakActivity
                        Log.w("MemoryLeakActivity", "Static reference set — rotate to see leak")
                    },
                    onFixedBehavior = {
                        // FIXED: WeakReference allows GC to collect the Activity
                        LeakHolder.activityRef = null
                        LeakHolder.weakRef = WeakReference(this@MemoryLeakActivity)
                        Log.d("MemoryLeakActivity", "WeakReference set — rotate to verify no leak")
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LeakHolder.activityRef = null
        LeakHolder.weakRef = null
    }
}
