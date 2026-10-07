package com.superapp.app.features.dashboard.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Shows live values for one sensor. The listener exists only while [active] is true, so it is
 * removed when the user turns it off, switches sensor/category, or the screen is not visible.
 */
@Composable
fun LiveSensorValues(sensor: Sensor, active: Boolean) {
    val context = LocalContext.current
    var values by remember(sensor) { mutableStateOf<FloatArray?>(null) }
    var supported by remember(sensor) { mutableStateOf(true) }

    DisposableEffect(sensor, active) {
        if (!active) return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { values = event.values.copyOf() }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        supported = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sm.unregisterListener(listener)
            values = null
        }
    }

    if (active) {
        Text(
            when {
                !supported -> "Live values are not available for this sensor."
                values == null -> "Waiting for a reading..."
                else -> values!!.take(6).mapIndexed { i, v -> "v[$i] = %.3f".format(v) }.joinToString("   ")
            }
        )
    }
}
