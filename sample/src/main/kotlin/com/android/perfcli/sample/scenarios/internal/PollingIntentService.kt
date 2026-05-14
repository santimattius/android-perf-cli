package com.android.perfcli.sample.scenarios.internal

import android.app.IntentService
import android.content.Intent
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * BAD path for BackgroundNetworkActivity.
 *
 * Uses the legacy IntentService API (deprecated in API 30) to perform HTTP polling.
 * IntentService runs on a background thread but the pattern itself is the anti-pattern:
 * it was commonly used for long-running background work, often with repeated polling.
 */
@Suppress("DEPRECATION")
class PollingIntentService : IntentService("PollingIntentService") {

    private val httpClient = OkHttpClient()

    override fun onHandleIntent(intent: Intent?) {
        try {
            val request = Request.Builder().url("https://example.com").build()
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "Polling response: ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Polling request failed", e)
        }
    }

    companion object {
        private const val TAG = "PollingIntentService"
    }
}
