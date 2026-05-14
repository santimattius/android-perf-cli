package com.android.perfcli.sample.scenarios

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import com.android.perfcli.sample.ui.SampleTheme
import com.android.perfcli.sample.ui.ScenarioScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class JankActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SampleTheme {
                var badRunning by remember { mutableStateOf(false) }
                var frame by remember { mutableIntStateOf(0) }

                if (badRunning) {
                    LaunchedEffect(frame) {
                        if (frame < 60) {
                            // BAD: sleep on main dispatcher — drops frames
                            Thread.sleep(30)
                            frame++
                        } else {
                            badRunning = false
                            frame = 0
                        }
                    }
                }

                ScenarioScreen(
                    title = "Jank Frames",
                    onBadBehavior = {
                        badRunning = true
                        frame = 0
                    },
                    onFixedBehavior = {
                        lifecycleScope.launch {
                            // FIXED: heavy work on background dispatcher
                            withContext(Dispatchers.Default) {
                                repeat(60) { Thread.sleep(30) }
                            }
                        }
                    },
                )

                if (badRunning) {
                    val progress = frame / 60f
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.Red,
                            radius = 40f,
                            center = Offset(size.width * progress, size.height / 2f),
                        )
                    }
                }
            }
        }
    }
}
