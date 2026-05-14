package com.android.perfcli.sample

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.android.perfcli.sample.scenarios.AnrActivity
import com.android.perfcli.sample.scenarios.BackgroundNetworkActivity
import com.android.perfcli.sample.scenarios.BackgroundWifiActivity
import com.android.perfcli.sample.scenarios.ColdStartActivity
import com.android.perfcli.sample.scenarios.CrashActivity
import com.android.perfcli.sample.scenarios.DiskReadActivity
import com.android.perfcli.sample.scenarios.DiskWriteActivity
import com.android.perfcli.sample.scenarios.FrozenActivity
import com.android.perfcli.sample.scenarios.JankActivity
import com.android.perfcli.sample.scenarios.MemoryLeakActivity
import com.android.perfcli.sample.scenarios.NetworkActivity
import com.android.perfcli.sample.scenarios.PermissionDenialActivity
import com.android.perfcli.sample.scenarios.WakeLockActivity
import com.android.perfcli.sample.ui.SampleTheme

class LauncherActivity : ComponentActivity() {

    private val scenarios = listOf(
        "Jank Frames" to JankActivity::class.java,
        "Frozen Frame (>700ms)" to FrozenActivity::class.java,
        "ANR Hint" to AnrActivity::class.java,
        "Crash" to CrashActivity::class.java,
        "StrictMode: Disk Write" to DiskWriteActivity::class.java,
        "StrictMode: Disk Read" to DiskReadActivity::class.java,
        "StrictMode: Network" to NetworkActivity::class.java,
        "Wake Lock Leak" to WakeLockActivity::class.java,
        "Permission Denial" to PermissionDenialActivity::class.java,
        "Memory Leak" to MemoryLeakActivity::class.java,
        "Cold Start Delay" to ColdStartActivity::class.java,
        "Background Network" to BackgroundNetworkActivity::class.java,
        "Background Wi-Fi Scans" to BackgroundWifiActivity::class.java,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(scenarios) { (label, cls) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            modifier = Modifier.clickable {
                                startActivity(Intent(this@LauncherActivity, cls))
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
