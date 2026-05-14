package com.android.perfcli.sample.scenarios

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen

class BackgroundWifiActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var scanRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Background Wi-Fi Scans",
                    onBadBehavior = {
                        // BAD: repeated startScan() every 2s — drains battery
                        val runnable = object : Runnable {
                            override fun run() {
                                @Suppress("DEPRECATION")
                                wifiManager.startScan()
                                Log.w("BackgroundWifiActivity", "Wi-Fi scan triggered (bad path)")
                                handler.postDelayed(this, 2_000)
                            }
                        }
                        scanRunnable = runnable
                        handler.post(runnable)
                    },
                    onFixedBehavior = {
                        // FIXED: stop the loop and perform a single passive scan
                        scanRunnable?.let { handler.removeCallbacks(it) }
                        scanRunnable = null
                        @Suppress("DEPRECATION")
                        wifiManager.startScan()
                        Log.d("BackgroundWifiActivity", "Single Wi-Fi scan (fixed path)")
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanRunnable?.let { handler.removeCallbacks(it) }
    }
}
