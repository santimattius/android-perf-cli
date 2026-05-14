package com.android.perfcli.sample

import android.app.Application
import android.os.StrictMode

class SampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.ENABLE_COLD_START_DELAY) {
            Thread.sleep(2_000)
        }

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}
