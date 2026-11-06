package dev.nekuz.devicespec

enum class SectionId(val title: String, val summary: String, val refreshOnReturn: Boolean) {
    BUILD("Build", "Model, fingerprint, Android version, patch, ABIs, kernel", false),
    DISPLAY("Display", "Size, density, refresh rates, HDR, cutout, night mode", true),
    RUNTIME("Runtime", "Heap limits, RAM, performance class, CPU, thermal, ART", true),
    STORAGE("Storage", "Internal space, encryption state, external volumes", true),
    GRAPHICS("GPU and graphics", "OpenGL ES, Vulkan and graphics features", false),
    MEDIA("Media", "Codecs, encoder or decoder, hardware or software", false),
    SENSORS("Sensors", "Vendor, range, resolution, power, live values", false),
    FEATURES("System features", "Everything the system declares as available", false),
    DEVELOPER("Developer", "Developer options, ADB, stay awake, animator scales", true),
}

data class SensorRef(val type: Int, val name: String, val wakeUp: Boolean)

data class Entry(
    val label: String,
    val value: String,
    val group: String = "",
    val sensor: SensorRef? = null,
)

data class Section(val id: SectionId, val entries: List<Entry>) {
    companion object {
        fun failed(id: SectionId) = Section(id, listOf(Entry(id.title, Text.UNAVAILABLE)))
    }
}

object Limits {
    const val MAX_VALUE_CHARS = 2_000
    const val MAX_LABEL_CHARS = 200
    const val MAX_ENTRIES = 2_000
    const val MAX_REPORT_CHARS = 64_000
    const val MAX_FILE_BYTES = 256 * 1024
    const val MAX_SHORT_FILE_BYTES = 256
    const val MAX_LINES = 16_384
    const val MAX_CPUS = 256
    const val MAX_CODEC_TYPES = 32
    const val MAX_SENSOR_VALUES = 16
    const val MAX_QUERY_CHARS = 100
    const val MAX_QUERY_TOKENS = 8
}
