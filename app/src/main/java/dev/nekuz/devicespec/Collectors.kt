package dev.nekuz.devicespec

import android.content.ContentResolver
import android.content.Context
import android.content.pm.FeatureInfo
import android.os.Build
import android.provider.Settings

object Collectors {
    fun collect(id: SectionId, context: Context): Section = when (id) {
        SectionId.BUILD -> build()
        SectionId.DISPLAY -> DisplayCollector.collect(context)
        SectionId.RUNTIME -> RuntimeCollector.collect(context)
        SectionId.STORAGE -> StorageCollector.collect(context)
        SectionId.GRAPHICS -> GraphicsCollector.collect(context)
        SectionId.MEDIA -> MediaCollector.collect()
        SectionId.SENSORS -> SensorCollector.collect(context)
        SectionId.FEATURES -> features(context)
        SectionId.DEVELOPER -> developer(context)
    }

    private fun build(): Section {
        val b = SectionBuilder(SectionId.BUILD)
        b.group("Device type")
        b.add("Model") { Build.MODEL }
        b.add("Manufacturer") { Build.MANUFACTURER }
        b.add("Brand") { Build.BRAND }
        b.add("Product") { Build.PRODUCT }
        b.add("Device") { Build.DEVICE }
        b.add("Fingerprint") { Build.FINGERPRINT }
        b.group("Android")
        b.add("Android version") { Build.VERSION.RELEASE }
        b.add("API level") { Build.VERSION.SDK_INT.toString() }
        b.add("Security patch") { Build.VERSION.SECURITY_PATCH }
        b.add("Build type") { Build.TYPE }
        b.add("Build tags") { Build.TAGS }
        b.group("Low level")
        b.add("Bootloader") { Build.BOOTLOADER }
        b.add("Supported ABIs") { Build.SUPPORTED_ABIS?.take(32)?.joinToString(", ") }
        b.add("Kernel (os.version)") { System.getProperty("os.version") }
        return b.build()
    }

    fun readFeatures(context: Context): List<FeatureInfo> =
        Text.attempt { context.packageManager.systemAvailableFeatures?.toList() }.orEmpty()

    private fun features(context: Context): Section {
        val b = SectionBuilder(SectionId.FEATURES)
        val features = readFeatures(context)
        if (features.isEmpty()) {
            b.add("System features") { null }
            return b.build()
        }
        val named = features.mapNotNull { info -> Text.attempt { info.name?.let { it to info.version } } }
        val required = features.firstNotNullOfOrNull { info ->
            Text.attempt { if (info.name == null) info.reqGlEsVersion else null }
        }
        b.group("Requirements")
        b.add("Required OpenGL ES version") { required?.let(Labels::glEsVersion) }
        val grouped = named.groupBy { Labels.featureGroup(it.first) }
        for (group in listOf("Hardware", "Software", "Other")) {
            val items = grouped[group].orEmpty().sortedBy { it.first }
            if (items.isEmpty()) continue
            b.group("$group (${items.size})")
            items.forEach { (name, version) -> b.add("") { Labels.featureText(name, version) } }
        }
        return b.build()
    }

    private fun developer(context: Context): Section {
        val b = SectionBuilder(SectionId.DEVELOPER)
        val resolver: ContentResolver? = Text.attempt { context.contentResolver }
        b.group("Debugging")
        b.add("Developer options") { resolver?.let { Labels.flag(global(it, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED)) } }
        b.add("ADB (USB debugging)") { resolver?.let { Labels.flag(global(it, Settings.Global.ADB_ENABLED)) } }
        b.add("Stay awake") { resolver?.let { Labels.stayAwake(global(it, Settings.Global.STAY_ON_WHILE_PLUGGED_IN)) } }
        b.group("Animation scales")
        b.add("Window animation scale") { resolver?.let { Labels.animatorScale(global(it, Settings.Global.WINDOW_ANIMATION_SCALE)) } }
        b.add("Transition animation scale") { resolver?.let { Labels.animatorScale(global(it, Settings.Global.TRANSITION_ANIMATION_SCALE)) } }
        b.add("Animator duration scale") { resolver?.let { Labels.animatorScale(global(it, Settings.Global.ANIMATOR_DURATION_SCALE)) } }
        return b.build()
    }

    private fun global(resolver: ContentResolver, name: String): String? = Settings.Global.getString(resolver, name)
}
