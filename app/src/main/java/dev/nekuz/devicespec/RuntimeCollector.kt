package dev.nekuz.devicespec

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import java.io.File
import java.io.FileInputStream

object RuntimeCollector {
    private const val CPU_ROOT = "/sys/devices/system/cpu"
    private const val MAX_LISTED_CPUS = 64

    private val VM_PROPERTIES = listOf(
        "java.vm.name",
        "java.vm.version",
        "java.vm.vendor",
        "java.vm.specification.version",
        "java.runtime.name",
        "java.runtime.version",
        "java.specification.version",
        "os.arch",
        "os.name",
    )

    fun collect(context: Context): Section {
        val b = SectionBuilder(SectionId.RUNTIME)
        val activity = Text.attempt { context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager }
        val memory = Text.attempt { ActivityManager.MemoryInfo().also { info -> activity!!.getMemoryInfo(info) } }
        val runtime = Runtime.getRuntime()

        b.group("Heap")
        b.add("Max heap (maxMemory)") { Sizes.bytes(runtime.maxMemory()) }
        b.add("Allocated heap (totalMemory)") { Sizes.bytes(runtime.totalMemory()) }
        b.add("Free in allocated heap (freeMemory)") { Sizes.bytes(runtime.freeMemory()) }
        b.add("Memory class") { activity?.let { Sizes.mebibytes(it.memoryClass) } }
        b.add("Large memory class") { activity?.let { Sizes.mebibytes(it.largeMemoryClass) } }

        b.group("RAM")
        b.add("RAM total") { memory?.let { Sizes.bytes(it.totalMem) } }
        b.add("RAM available") { memory?.let { Sizes.bytes(it.availMem) } }
        b.add("Low memory threshold") { memory?.let { Sizes.bytes(it.threshold) } }
        b.add("Low memory now") { memory?.let { Labels.yesNo(it.lowMemory) } }
        b.add("Low RAM device") { activity?.let { Labels.yesNo(it.isLowRamDevice) } }
        val meminfo = Text.attempt { ProcParser.parseMeminfo(readFile("/proc/meminfo", Limits.MAX_FILE_BYTES)) }.orEmpty()
        b.add("Swap total") { meminfo["SwapTotal"]?.let { Sizes.bytes(Sizes.kibibytesToBytes(it)) } }
        b.add("Swap free") { meminfo["SwapFree"]?.let { Sizes.bytes(Sizes.kibibytesToBytes(it)) } }

        b.group("Performance")
        b.add("Media performance class") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Labels.performanceClass(Build.VERSION.MEDIA_PERFORMANCE_CLASS)
            } else {
                null
            }
        }
        b.add("Thermal status") {
            val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            power?.let { Labels.thermalStatus(it.currentThermalStatus) }
        }

        b.group("CPU")
        b.add("Available cores") { runtime.availableProcessors().toString() }
        val cpuList = Text.attempt { ProcParser.parseCpuList(readFile("$CPU_ROOT/present", Limits.MAX_SHORT_FILE_BYTES)) }.orEmpty()
        b.add("Present cores") { cpuList.size.takeIf { it > 0 }?.toString() }
        val cpuInfo = Text.attempt { ProcParser.parseCpuInfo(readFile("/proc/cpuinfo", Limits.MAX_FILE_BYTES)) }
        b.add("Hardware (/proc/cpuinfo)") { cpuInfo?.hardware }
        b.add("Model name (/proc/cpuinfo)") { cpuInfo?.modelName }
        b.add("CPU parts (/proc/cpuinfo)") {
            cpuInfo?.parts?.takeIf { it.isNotEmpty() }?.entries?.joinToString(", ") { "${it.key} x ${it.value}" }
        }
        b.add("CPU features (/proc/cpuinfo)") { cpuInfo?.features }
        val cores = cpuList.ifEmpty { (0 until runtime.availableProcessors()).toList() }.take(MAX_LISTED_CPUS)
        for (core in cores) {
            b.add("Core $core frequency") { coreFrequency(core) }
        }

        b.group("ART and JVM")
        b.add("64-bit process") { Labels.yesNo(Process.is64Bit()) }
        for (name in VM_PROPERTIES) {
            b.add(name) { System.getProperty(name) }
        }
        return b.build()
    }

    private fun coreFrequency(core: Int): String? {
        val base = "$CPU_ROOT/cpu$core/cpufreq"
        val min = readKhz("$base/cpuinfo_min_freq")
        val max = readKhz("$base/cpuinfo_max_freq")
        val current = readKhz("$base/scaling_cur_freq")
        return Frequencies.cpuLine(min, max, current)
    }

    private fun readKhz(path: String): Long? =
        Text.attempt { ProcParser.parseKhz(readFile(path, Limits.MAX_SHORT_FILE_BYTES)) }

    private fun readFile(path: String, maxBytes: Int): String =
        FileInputStream(File(path)).use { ProcParser.readBounded(it, maxBytes) }
}
