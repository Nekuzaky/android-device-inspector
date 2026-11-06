package dev.nekuz.devicespec

import java.io.InputStream

data class CpuInfo(
    val processors: Int,
    val hardware: String?,
    val modelName: String?,
    val features: String?,
    val parts: Map<String, Int>,
)

object ProcParser {
    private const val MAX_KHZ = 100_000_000L

    fun readBounded(input: InputStream, maxBytes: Int = Limits.MAX_FILE_BYTES): String {
        val buffer = ByteArray(maxBytes.coerceAtLeast(0))
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return String(buffer, 0, total, Charsets.UTF_8)
    }

    fun parseCpuList(text: String?, maxCpus: Int = Limits.MAX_CPUS): List<Int> {
        if (text == null) return emptyList()
        val found = sortedSetOf<Int>()
        for (token in text.take(Limits.MAX_FILE_BYTES).trim().split(',').take(maxCpus)) {
            val part = token.trim()
            val dash = part.indexOf('-')
            val start: Int?
            val end: Int?
            if (dash > 0) {
                start = part.substring(0, dash).toIntOrNull()
                end = part.substring(dash + 1).toIntOrNull()
            } else {
                start = part.toIntOrNull()
                end = start
            }
            if (start == null || end == null || start < 0 || end < start) continue
            var cpu = start
            while (cpu <= end && cpu < maxCpus) {
                found.add(cpu)
                cpu++
            }
        }
        return found.toList()
    }

    fun parseKhz(text: String?): Long? {
        val value = text?.trim()?.toLongOrNull() ?: return null
        return value.takeIf { it in 1..MAX_KHZ }
    }

    fun parseMeminfo(text: String?): Map<String, Long> {
        if (text == null) return emptyMap()
        val values = LinkedHashMap<String, Long>()
        for (line in text.lineSequence().take(Limits.MAX_LINES)) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            val tokens = line.substring(colon + 1).trim().split(WHITESPACE)
            val number = tokens.firstOrNull()?.toLongOrNull() ?: continue
            if (number < 0 || key.isEmpty()) continue
            val unit = tokens.getOrNull(1)
            if (unit != null && !unit.equals("kB", ignoreCase = true)) continue
            values[key] = number
        }
        return values
    }

    fun parseCpuInfo(text: String?): CpuInfo {
        var processors = 0
        var hardware: String? = null
        var modelName: String? = null
        var features: String? = null
        val parts = LinkedHashMap<String, Int>()
        text?.lineSequence()?.take(Limits.MAX_LINES)?.forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val key = line.substring(0, colon).trim().lowercase()
            val value = line.substring(colon + 1).trim()
            when (key) {
                "processor" -> if (value.toIntOrNull() != null) processors++
                "hardware" -> if (hardware == null && value.isNotEmpty()) hardware = value
                "model name" -> if (modelName == null && value.isNotEmpty()) modelName = value
                "features", "flags" -> if (features == null && value.isNotEmpty()) features = value
                "cpu part" -> if (value.isNotEmpty() && (value in parts || parts.size < MAX_PARTS)) {
                    parts[value] = (parts[value] ?: 0) + 1
                }
            }
        }
        return CpuInfo(processors, hardware, modelName, features, parts)
    }

    private const val MAX_PARTS = 16
    private val WHITESPACE = Regex("\\s+")
}
