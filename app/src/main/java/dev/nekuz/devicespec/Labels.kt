package dev.nekuz.devicespec

import java.util.Locale

object Labels {
    fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

    fun thermalStatus(status: Int): String = when (status) {
        0 -> "None"
        1 -> "Light"
        2 -> "Moderate"
        3 -> "Severe"
        4 -> "Critical"
        5 -> "Emergency"
        6 -> "Shutdown"
        else -> "Unknown ($status)"
    }

    fun encryptionStatus(status: Int): String = when (status) {
        0 -> "Unsupported"
        1 -> "Inactive"
        2 -> "Activating"
        3 -> "Active"
        4 -> "Active with the default key"
        5 -> "Active, per user (file-based encryption)"
        else -> "Unknown ($status)"
    }

    fun performanceClass(value: Int): String = when {
        value <= 0 -> "Not declared"
        else -> "$value"
    }

    fun nightMode(uiMode: Int): String = when (uiMode and 0x30) {
        0x10 -> "Off"
        0x20 -> "On"
        else -> "Undefined"
    }

    fun nightSetting(mode: Int): String = when (mode) {
        0 -> "Automatic"
        1 -> "Off"
        2 -> "On"
        3 -> "Custom schedule"
        else -> "Unknown ($mode)"
    }

    fun orientation(value: Int): String = when (value) {
        1 -> "Portrait"
        2 -> "Landscape"
        else -> "Undefined"
    }

    fun vulkanVersion(version: Int): String {
        if (version <= 0) return Text.UNAVAILABLE
        val major = version ushr 22
        val minor = (version ushr 12) and 0x3FF
        val patch = version and 0xFFF
        return "$major.$minor.$patch"
    }

    fun deqpLevel(level: Int): String {
        if (level <= 0) return Text.UNAVAILABLE
        val year = level ushr 16
        val month = (level ushr 8) and 0xFF
        val day = level and 0xFF
        if (year !in 2000..2199 || month !in 1..12 || day !in 1..31) return level.toString()
        return "%04d-%02d-%02d".format(Locale.ROOT, year, month, day)
    }

    fun glEsVersion(encoded: Int): String? {
        if (encoded <= 0) return null
        return "${encoded ushr 16}.${encoded and 0xFFFF}"
    }

    fun flag(raw: String?): String = when (val text = raw?.trim().orEmpty()) {
        "" -> "Not set"
        "0" -> "Disabled"
        "1" -> "Enabled"
        else -> text
    }

    fun stayAwake(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return "Not set"
        val bits = text.toIntOrNull()?.takeIf { it >= 0 } ?: return text
        if (bits == 0) return "Off"
        val sources = buildList {
            if (bits and 1 != 0) add("AC")
            if (bits and 2 != 0) add("USB")
            if (bits and 4 != 0) add("Wireless")
            if (bits and 8 != 0) add("Dock")
        }
        return if (sources.isEmpty()) text else "While charging: ${sources.joinToString(", ")}"
    }

    fun animatorScale(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return "Not set"
        val value = text.toFloatOrNull() ?: return text
        if (value.isNaN() || value.isInfinite() || value < 0f) return text
        return if (value == 0f) "Off (0x)" else "${Numbers.trimmed(value.toDouble(), 2)}x"
    }

    fun featureGroup(name: String): String = when {
        name.startsWith("android.hardware.") -> "Hardware"
        name.startsWith("android.software.") -> "Software"
        else -> "Other"
    }

    fun isGraphicsFeature(name: String): Boolean {
        val lower = name.lowercase()
        return lower.startsWith("android.hardware.vulkan") ||
            lower.startsWith("android.hardware.opengles") ||
            lower.startsWith("android.hardware.vr") ||
            lower.startsWith("android.software.vulkan") ||
            lower.startsWith("android.software.opengles") ||
            lower.startsWith("android.software.vr")
    }

    fun featureText(name: String, version: Int): String =
        if (version > 0) "$name (version $version)" else name

    fun sensorAccuracy(value: Int): String = when (value) {
        -1 -> "No contact"
        0 -> "Unreliable"
        1 -> "Low"
        2 -> "Medium"
        3 -> "High"
        else -> "Unknown ($value)"
    }

    fun sensorUnit(stringType: String?): String = when (stringType) {
        "android.sensor.accelerometer",
        "android.sensor.accelerometer_uncalibrated",
        "android.sensor.gravity",
        "android.sensor.linear_acceleration" -> "m/s²"
        "android.sensor.gyroscope",
        "android.sensor.gyroscope_uncalibrated" -> "rad/s"
        "android.sensor.magnetic_field",
        "android.sensor.magnetic_field_uncalibrated" -> "µT"
        "android.sensor.light" -> "lx"
        "android.sensor.pressure" -> "hPa"
        "android.sensor.proximity" -> "cm"
        "android.sensor.ambient_temperature" -> "°C"
        "android.sensor.relative_humidity" -> "%"
        else -> ""
    }

    fun sensorValues(values: FloatArray, unit: String, max: Int = Limits.MAX_SENSOR_VALUES): List<Pair<String, String>> =
        values.take(max.coerceAtLeast(0)).mapIndexed { index, value ->
            val text = Numbers.compact(value)
            val shown = if (unit.isEmpty() || text == Text.UNAVAILABLE) text else "$text $unit"
            "Value $index" to shown
        }

    fun sensorDetail(
        vendor: String?,
        stringType: String?,
        maximumRange: Float?,
        resolution: Float?,
        powerMilliamps: Float?,
        wakeUp: Boolean?,
    ): String = listOf(
        "Vendor: ${vendor ?: Text.UNAVAILABLE}",
        "Type: ${stringType ?: Text.UNAVAILABLE}",
        "Range: ${maximumRange?.let(Numbers::compact) ?: Text.UNAVAILABLE}",
        "Resolution: ${resolution?.let(Numbers::compact) ?: Text.UNAVAILABLE}",
        "Power: ${powerMilliamps?.let { "${Numbers.compact(it)} mA" } ?: Text.UNAVAILABLE}",
        "Wake-up: ${wakeUp?.let(::yesNo) ?: Text.UNAVAILABLE}",
    ).joinToString(" · ")

    fun codecDetail(encoder: Boolean, hardware: Boolean?, alias: Boolean?, types: List<String>): String {
        val kind = if (encoder) "Encoder" else "Decoder"
        val backend = when (hardware) {
            true -> "Hardware"
            false -> "Software"
            null -> Text.UNAVAILABLE
        }
        val aliasText = if (alias == true) " · Alias" else ""
        return "$kind · $backend$aliasText · ${types.joinToString("; ").ifEmpty { Text.UNAVAILABLE }}"
    }

    fun codecVideoType(type: String, maxWidth: Int?, maxHeight: Int?): String =
        if (maxWidth != null && maxHeight != null) "$type (max $maxWidth x $maxHeight)" else type

    fun codecAudioType(type: String, maxSampleRate: Int?, maxChannels: Int?): String {
        val parts = listOfNotNull(maxSampleRate?.let { "max $it Hz" }, maxChannels?.let { "$it ch" })
        return if (parts.isEmpty()) type else "$type (${parts.joinToString(", ")})"
    }
}
