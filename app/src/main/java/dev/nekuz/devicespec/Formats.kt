package dev.nekuz.devicespec

import java.util.Locale

object Numbers {
    fun fixed(value: Double, decimals: Int): String = String.format(Locale.ROOT, "%.${decimals}f", value)

    fun trimmed(value: Double, decimals: Int = 2): String {
        val text = fixed(value, decimals)
        return if (text.contains('.')) text.trimEnd('0').trimEnd('.') else text
    }

    fun compact(value: Float): String = when {
        value.isNaN() || value.isInfinite() -> Text.UNAVAILABLE
        else -> trimmed(value.toDouble(), 4)
    }
}

object Sizes {
    private val UNITS = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

    fun bytes(value: Long): String {
        if (value < 0) return Text.UNAVAILABLE
        if (value < 1024) return "$value B"
        var scaled = value.toDouble()
        var unit = 0
        while (scaled >= 1024 && unit < UNITS.lastIndex) {
            scaled /= 1024
            unit++
        }
        return "${Numbers.fixed(scaled, 2)} ${UNITS[unit]}"
    }

    fun mebibytes(value: Int): String = if (value < 0) Text.UNAVAILABLE else "$value MiB"

    fun kibibytesToBytes(value: Long): Long = when {
        value < 0 -> -1
        value > Long.MAX_VALUE / 1024 -> Long.MAX_VALUE
        else -> value * 1024
    }
}

object Frequencies {
    fun khz(value: Long): String = when {
        value <= 0 -> Text.UNAVAILABLE
        value >= 1_000_000 -> "${Numbers.fixed(value / 1_000_000.0, 2)} GHz"
        else -> "${Numbers.trimmed(value / 1_000.0, 1)} MHz"
    }

    fun hz(value: Float): String = when {
        value.isNaN() || value.isInfinite() || value <= 0f -> Text.UNAVAILABLE
        else -> "${Numbers.trimmed(value.toDouble(), 2)} Hz"
    }

    fun cpuLine(minKhz: Long?, maxKhz: Long?, currentKhz: Long?): String? {
        if (minKhz == null && maxKhz == null && currentKhz == null) return null
        val range = when {
            minKhz != null && maxKhz != null -> "${khz(minKhz)} to ${khz(maxKhz)}"
            maxKhz != null -> "up to ${khz(maxKhz)}"
            minKhz != null -> "from ${khz(minKhz)}"
            else -> null
        }
        val current = currentKhz?.let { "now ${khz(it)}" }
        return listOfNotNull(range, current).joinToString(", ")
    }
}

object DisplayMath {
    fun pxToDp(px: Int, density: Float): Int? {
        if (px < 0 || density.isNaN() || density.isInfinite() || density <= 0f) return null
        return Math.round(px / density)
    }

    fun sizeDp(widthPx: Int, heightPx: Int, density: Float): String? {
        val width = pxToDp(widthPx, density) ?: return null
        val height = pxToDp(heightPx, density) ?: return null
        return "$width x $height dp"
    }

    fun sizePx(width: Int, height: Int): String? =
        if (width < 0 || height < 0) null else "$width x $height px"

    fun densityBucket(dpi: Int): String? = when {
        dpi <= 0 -> null
        dpi == 213 -> "tvdpi"
        dpi <= 120 -> "ldpi"
        dpi <= 160 -> "mdpi"
        dpi <= 240 -> "hdpi"
        dpi <= 320 -> "xhdpi"
        dpi <= 480 -> "xxhdpi"
        else -> "xxxhdpi"
    }

    fun rotationDegrees(rotation: Int): String? = when (rotation) {
        0 -> "0°"
        1 -> "90°"
        2 -> "180°"
        3 -> "270°"
        else -> null
    }

    fun hdrType(type: Int): String = when (type) {
        1 -> "Dolby Vision"
        2 -> "HDR10"
        3 -> "HLG"
        4 -> "HDR10+"
        else -> "Type $type"
    }
}
