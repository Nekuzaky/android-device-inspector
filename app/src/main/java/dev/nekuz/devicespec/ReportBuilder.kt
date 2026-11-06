package dev.nekuz.devicespec

object ReportBuilder {
    const val NOTICE = "Note: the fingerprint and model identify the device type and are included in this report."
    const val NOT_READ = "Not read: IMEI, serial number, Android ID, MAC address, accounts, phone numbers, advertising ID."

    fun build(
        sections: Map<SectionId, Section>,
        generatedAtUtc: String,
        appVersion: String,
        maxChars: Int = Limits.MAX_REPORT_CHARS,
    ): String {
        val lines = ArrayList<String>()
        lines.add("DeviceSpec report")
        lines.add("Generated: ${Text.clean(generatedAtUtc)}")
        lines.add("App version: ${Text.clean(appVersion)}")
        lines.add(NOTICE)
        lines.add(NOT_READ)
        for (id in SectionId.entries) {
            lines.add("")
            lines.add("== ${id.title} ==")
            val section = sections[id]
            if (section == null || section.entries.isEmpty()) {
                lines.add(Text.UNAVAILABLE)
                continue
            }
            var group = ""
            for (entry in section.entries) {
                if (entry.group != group) {
                    group = entry.group
                    if (group.isNotEmpty()) lines.add("-- ${Text.clean(group)} --")
                }
                lines.add(line(entry))
            }
        }
        return join(lines, maxChars)
    }

    private fun line(entry: Entry): String {
        val label = Text.clean(entry.label, Limits.MAX_LABEL_CHARS)
        val value = Text.clean(entry.value).ifEmpty { Text.UNAVAILABLE }
        return if (label.isEmpty()) value else "$label: $value"
    }

    private fun join(lines: List<String>, maxChars: Int): String {
        val tail = "[Report truncated at $maxChars characters]"
        val limit = (maxChars - tail.length - 1).coerceAtLeast(0)
        val out = StringBuilder()
        for (line in lines) {
            if (out.length + line.length + 1 > limit) {
                out.append(tail).append('\n')
                return out.toString()
            }
            out.append(line).append('\n')
        }
        return out.toString()
    }
}
