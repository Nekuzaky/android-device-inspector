package dev.nekuz.devicespec

object Text {
    const val UNAVAILABLE = "Unavailable"
    const val TRUNCATED = "… [truncated]"

    fun clean(raw: String, max: Int = Limits.MAX_VALUE_CHARS): String {
        val out = StringBuilder(minOf(raw.length, max))
        var index = 0
        var truncated = false
        while (index < raw.length) {
            if (out.length >= max) {
                truncated = true
                break
            }
            val char = raw[index]
            when (Character.getType(char).toByte()) {
                Character.CONTROL,
                Character.LINE_SEPARATOR,
                Character.PARAGRAPH_SEPARATOR,
                Character.SPACE_SEPARATOR -> {
                    if (isBlankBreak(char) && out.isNotEmpty() && out.last() != ' ') out.append(' ')
                }
                Character.FORMAT -> Unit
                Character.SURROGATE -> {
                    val next = raw.getOrNull(index + 1)
                    if (char.isHighSurrogate() && next != null && next.isLowSurrogate()) {
                        if (out.length + 2 > max) {
                            truncated = true
                            break
                        }
                        out.append(char).append(next)
                        index++
                    }
                }
                else -> out.append(char)
            }
            index++
        }
        val text = out.toString().trim()
        return if (truncated) text + TRUNCATED else text
    }

    private fun isBlankBreak(char: Char): Boolean = when (Character.getType(char).toByte()) {
        Character.CONTROL -> char == '\n' || char == '\r' || char == '\t'
        else -> true
    }

    fun guard(block: () -> String?): String = try {
        val raw = block()
        if (raw == null) UNAVAILABLE else clean(raw).ifEmpty { UNAVAILABLE }
    } catch (t: Throwable) {
        UNAVAILABLE
    }

    fun <T> attempt(block: () -> T?): T? = try {
        block()
    } catch (t: Throwable) {
        null
    }

    fun quietly(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            return
        }
    }
}

class SectionBuilder(private val id: SectionId, private val maxEntries: Int = Limits.MAX_ENTRIES) {
    private val entries = ArrayList<Entry>()
    private var currentGroup = ""
    private var dropped = 0

    fun group(name: String) {
        currentGroup = Text.clean(name, Limits.MAX_LABEL_CHARS)
    }

    fun add(label: String, sensor: SensorRef? = null, value: () -> String?) {
        if (entries.size >= maxEntries) {
            dropped++
            return
        }
        entries.add(Entry(Text.clean(label, Limits.MAX_LABEL_CHARS), Text.guard(value), currentGroup, sensor))
    }

    fun guarded(label: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            add(label) { null }
        }
    }

    fun build(): Section {
        if (dropped > 0) {
            entries.add(Entry("List truncated", "$dropped more entries not shown", currentGroup))
        }
        return Section(id, entries.toList())
    }
}
