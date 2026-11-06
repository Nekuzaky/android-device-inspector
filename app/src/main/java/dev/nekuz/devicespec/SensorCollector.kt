package dev.nekuz.devicespec

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager

object SensorCollector {
    fun collect(context: Context): Section {
        val b = SectionBuilder(SectionId.SENSORS)
        val sensors = Text.attempt { list(context) }
        if (sensors == null) {
            b.add("Sensors") { null }
            return b.build()
        }
        if (sensors.isEmpty()) {
            b.add("Sensors") { "None" }
            return b.build()
        }
        b.group("${sensors.size} sensors")
        for (sensor in sensors) {
            val name = Text.clean(Text.attempt { sensor.name } ?: Text.UNAVAILABLE, Limits.MAX_LABEL_CHARS)
            val type = Text.attempt { sensor.type }
            val wakeUp = Text.attempt { sensor.isWakeUpSensor } == true
            b.add(name, type?.let { SensorRef(it, name, wakeUp) }) { describe(sensor) }
        }
        return b.build()
    }

    fun list(context: Context): List<Sensor>? {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        return manager?.getSensorList(Sensor.TYPE_ALL)
    }

    fun describe(sensor: Sensor): String = Labels.sensorDetail(
        Text.attempt { sensor.vendor },
        Text.attempt { sensor.stringType },
        Text.attempt { sensor.maximumRange },
        Text.attempt { sensor.resolution },
        Text.attempt { sensor.power },
        Text.attempt { sensor.isWakeUpSensor },
    )

    fun find(context: Context, type: Int, name: String, wakeUp: Boolean): Sensor? =
        list(context)?.firstOrNull {
            it.type == type && it.isWakeUpSensor == wakeUp && Text.clean(it.name, Limits.MAX_LABEL_CHARS) == name
        }
}
