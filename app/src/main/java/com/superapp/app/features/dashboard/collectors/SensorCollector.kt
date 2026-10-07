package com.superapp.app.features.dashboard.collectors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import com.superapp.app.features.dashboard.InfoRow
import com.superapp.app.features.dashboard.InfoSection

/** Static sensor metadata only; no listener is registered here. Live values are in the UI layer. */
object SensorCollector {

    fun collect(context: Context): List<Pair<InfoSection, Sensor>> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getSensorList(Sensor.TYPE_ALL)
            .sortedBy { it.type }
            .map { s -> describe(s) to s }
    }

    /** Stable key identifying a sensor in the UI. */
    fun key(s: Sensor): String = "${s.type}:${s.name}:${s.vendor}"

    private fun describe(s: Sensor): InfoSection {
        val rows = mutableListOf(
            InfoRow("Type", typeName(s)),
            InfoRow("Vendor", s.vendor.takeIf { it.isNotBlank() }),
            InfoRow("Version", s.version.toString()),
            InfoRow("Power usage", "${s.power} mA"),
            InfoRow("Resolution", s.resolution.toString(), "Unit depends on the sensor type"),
            InfoRow("Maximum range", s.maximumRange.toString(), "Unit depends on the sensor type"),
            InfoRow("Minimum delay", if (s.minDelay > 0) "${s.minDelay} µs" else if (s.minDelay == 0) "Not a streaming sensor" else null)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            rows += InfoRow("Wake-up sensor", if (s.isWakeUpSensor) "Yes" else "No")
            rows += InfoRow("Reporting mode", when (s.reportingMode) {
                Sensor.REPORTING_MODE_CONTINUOUS -> "Continuous"
                Sensor.REPORTING_MODE_ON_CHANGE -> "On change"
                Sensor.REPORTING_MODE_ONE_SHOT -> "One shot"
                Sensor.REPORTING_MODE_SPECIAL_TRIGGER -> "Special trigger"
                else -> null
            })
        }
        return InfoSection(title = s.name, rows = rows)
    }

    private fun typeName(s: Sensor): String = when (s.type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_PRESSURE -> "Barometer"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient temperature"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative humidity"
        Sensor.TYPE_STEP_COUNTER -> "Step counter"
        Sensor.TYPE_STEP_DETECTOR -> "Step detector"
        else -> s.stringType ?: "Type ${s.type}"
    }
}
