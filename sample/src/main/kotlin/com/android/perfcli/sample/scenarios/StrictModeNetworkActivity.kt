package com.android.perfcli.sample.scenarios

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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URL

class NetworkActivity : ComponentActivity() {
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "StrictMode: Network",
                    onBadBehavior = {
                        // BAD: network call on main thread — triggers NETWORK StrictMode violation
                        runCatching { URL("https://example.com").openStream().close() }
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: OkHttp call on IO dispatcher
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    client.newCall(
                                        Request.Builder().url("https://example.com").build()
                                    ).execute().also { Log.d("NetworkActivity", "Code: ${it.code}") }.close()
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}
