package com.android.perfcli.sample.scenarios

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen

class PermissionDenialActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        setContent {
            SampleTheme {
                ScenarioScreen(
                    title = "Permission Denial",
                    onBadBehavior = {
                        // BAD: access location without ACCESS_FINE_LOCATION in manifest
                        // produces SecurityException / permission denial log captured by perf-cli
                        try {
                            @Suppress("MissingPermission")
                            val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                            Log.d("PermissionDenialActivity", "Location: $loc")
                        } catch (e: SecurityException) {
                            Log.w("PermissionDenialActivity", "Permission denied (expected): ${e.message}")
                        }
                    },
                    onFixedBehavior = {
                        // FIXED: initiate runtime permission request before accessing location
                        ActivityCompat.requestPermissions(
                            this@PermissionDenialActivity,
                            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            1001,
                        )
                    },
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 &&
            grantResults.isNotEmpty() &&
            grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("PermissionDenialActivity", "Permission granted")
        }
    }
}
