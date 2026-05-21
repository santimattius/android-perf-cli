package com.android.perfcli.sample.scenarios.internal

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * FIXED path for BackgroundNetworkActivity.
 *
 * Uses WorkManager with a CoroutineWorker to perform a single HTTP request
 * in the background. WorkManager handles lifecycle, constraints, and retries properly.
 */
class PollingWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    private val httpClient = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url("https://example.com").build()
            httpClient.newCall(request).execute().use { response ->
                Log.d(TAG, "WorkManager response: ${response.code}")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager request failed", e)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "PollingWorker"
    }
}
