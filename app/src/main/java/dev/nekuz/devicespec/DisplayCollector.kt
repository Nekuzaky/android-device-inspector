package dev.nekuz.devicespec

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display

object DisplayCollector {
    private const val MAX_MODES = 64

    fun collect(context: Context): Section {
        val b = SectionBuilder(SectionId.DISPLAY)
        val display = Text.attempt { currentDisplay(context) }
        val config = Text.attempt { Configuration(context.resources.configuration) }
        val metrics = Text.attempt { realMetrics(display) }
        val window = Text.attempt { windowSize(context) }
        val density = metrics?.density ?: Text.attempt { context.resources.displayMetrics.density } ?: 0f
        val dpi = metrics?.densityDpi ?: config?.densityDpi ?: 0

        b.group("Size")
        b.add("Display size (px)") { metrics?.let { DisplayMath.sizePx(it.widthPixels, it.heightPixels) } }
        b.add("Display size (dp)") { metrics?.let { DisplayMath.sizeDp(it.widthPixels, it.heightPixels, it.density) } }
        b.add("Native mode size (px)") { display?.mode?.let { DisplayMath.sizePx(it.physicalWidth, it.physicalHeight) } }
        b.add("Window size (px)") { window?.let { DisplayMath.sizePx(it.x, it.y) } }
        b.add("Window size (dp)") { window?.let { DisplayMath.sizeDp(it.x, it.y, density) } }
        b.add("Smallest width") { config?.smallestScreenWidthDp?.takeIf { it > 0 }?.let { "$it dp (sw${it}dp)" } }
        b.add("Configured screen") {
            config?.let { if (it.screenWidthDp > 0 && it.screenHeightDp > 0) "${it.screenWidthDp} x ${it.screenHeightDp} dp" else null }
        }
        b.add("Orientation") { config?.let { Labels.orientation(it.orientation) } }
        b.add("Rotation") { display?.let { DisplayMath.rotationDegrees(it.rotation) } }

        b.group("Density")
        b.add("Density scale") { density.takeIf { it > 0f }?.let { Numbers.trimmed(it.toDouble(), 3) } }
        b.add("Density") { dpi.takeIf { it > 0 }?.let { "$it dpi" } }
        b.add("Density bucket") { DisplayMath.densityBucket(dpi) }
        b.add("Physical dpi (x, y)") {
            metrics?.let { "${Numbers.compact(it.xdpi)}, ${Numbers.compact(it.ydpi)}" }
        }

        b.group("Refresh rates")
        b.add("Current refresh rate") { display?.let { Frequencies.hz(it.refreshRate) } }
        b.add("Supported refresh rates") {
            display?.mode?.alternativeRefreshRatesText(display) ?: display?.let { supportedRates(it) }
        }
        b.add("Display modes") {
            display?.supportedModes?.take(MAX_MODES)?.joinToString("; ") {
                "${it.physicalWidth} x ${it.physicalHeight} at ${Frequencies.hz(it.refreshRate)}"
            }
        }

        b.group("Colour and HDR")
        b.add("HDR supported types") {
            display?.let(::hdrTypes)?.let { types ->
                if (types.isEmpty()) "None" else types.take(16).joinToString(", ", transform = DisplayMath::hdrType)
            }
        }
        b.add("HDR max luminance") { display?.hdrCapabilities?.desiredMaxLuminance?.let { "${Numbers.compact(it)} cd/m²" } }
        b.add("HDR screen (configuration)") { config?.let { Labels.yesNo(it.isScreenHdr) } }
        b.add("Wide colour gamut (display)") { display?.let { Labels.yesNo(it.isWideColorGamut) } }
        b.add("Wide colour gamut (configuration)") { config?.let { Labels.yesNo(it.isScreenWideColorGamut) } }
        b.add("Preferred wide gamut colour space") { display?.preferredWideGamutColorSpace?.name }

        b.group("Cutout")
        val cutoutKnown = display != null && Text.attempt { display.cutout; true } == true
        val cutout = Text.attempt { display?.cutout }
        b.add("Display cutout") { if (cutoutKnown) Labels.yesNo(cutout != null) else null }
        if (cutout != null) {
            b.add("Safe insets (left, top, right, bottom)") {
                "${cutout.safeInsetLeft}, ${cutout.safeInsetTop}, ${cutout.safeInsetRight}, ${cutout.safeInsetBottom} px"
            }
            b.add("Cutout areas") { cutout.boundingRects.size.toString() }
        }

        b.group("Text and theme")
        b.add("Font scale") { config?.let { Numbers.trimmed(it.fontScale.toDouble(), 3) } }
        b.add("Night mode (current)") { config?.let { Labels.nightMode(it.uiMode) } }
        b.add("Night mode (setting)") {
            val manager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            manager?.let { Labels.nightSetting(it.nightMode) }
        }
        return b.build()
    }

    @Suppress("DEPRECATION")
    private fun hdrTypes(display: Display): IntArray? = display.hdrCapabilities?.supportedHdrTypes

    private fun currentDisplay(context: Context): Display? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context is Activity) return context.display
        val manager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return manager?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(display: Display?): DisplayMetrics? {
        val target = display ?: return null
        return DisplayMetrics().also { target.getRealMetrics(it) }
    }

    private fun windowSize(context: Context): Point? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context is Activity) {
            val bounds = context.windowManager.currentWindowMetrics.bounds
            return Point(bounds.width(), bounds.height())
        }
        val metrics = context.resources.displayMetrics
        return Point(metrics.widthPixels, metrics.heightPixels)
    }

    private fun Display.Mode.alternativeRefreshRatesText(display: Display): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val rates = (listOf(refreshRate) + alternativeRefreshRates.take(MAX_MODES).toList()).distinct().sorted()
        return if (rates.size > 1) rates.joinToString(", ", transform = Frequencies::hz) else supportedRates(display)
    }

    private fun supportedRates(display: Display): String? {
        val rates = display.supportedModes?.take(MAX_MODES)?.map { it.refreshRate }?.distinct()?.sorted()
        return rates?.takeIf { it.isNotEmpty() }?.joinToString(", ", transform = Frequencies::hz)
    }
}
