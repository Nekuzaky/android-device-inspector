package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelsTest {
    @Test
    fun statusLabelsHandleUnknownValues() {
        assertEquals("None", Labels.thermalStatus(0))
        assertEquals("Shutdown", Labels.thermalStatus(6))
        assertEquals("Unknown (42)", Labels.thermalStatus(42))
        assertEquals("Active, per user (file-based encryption)", Labels.encryptionStatus(5))
        assertEquals("Unknown (-1)", Labels.encryptionStatus(-1))
        assertEquals("Not declared", Labels.performanceClass(0))
        assertEquals("34", Labels.performanceClass(34))
    }

    @Test
    fun nightModeReadsTheMask() {
        assertEquals("On", Labels.nightMode(0x20 or 0x01))
        assertEquals("Off", Labels.nightMode(0x10))
        assertEquals("Undefined", Labels.nightMode(0))
        assertEquals("Custom schedule", Labels.nightSetting(3))
    }

    @Test
    fun vulkanVersionDecodes() {
        assertEquals("1.3.0", Labels.vulkanVersion((1 shl 22) or (3 shl 12)))
        assertEquals("1.1.131", Labels.vulkanVersion((1 shl 22) or (1 shl 12) or 131))
        assertEquals(Text.UNAVAILABLE, Labels.vulkanVersion(0))
        assertEquals(Text.UNAVAILABLE, Labels.vulkanVersion(-1))
    }

    @Test
    fun deqpLevelDecodesDatesAndKeepsOddValues() {
        assertEquals("2022-03-01", Labels.deqpLevel((2022 shl 16) or (3 shl 8) or 1))
        assertEquals("5", Labels.deqpLevel(5))
        assertEquals(Text.UNAVAILABLE, Labels.deqpLevel(0))
    }

    @Test
    fun glEsVersionDecodes() {
        assertEquals("3.2", Labels.glEsVersion(0x00030002))
        assertNull(Labels.glEsVersion(0))
    }

    @Test
    fun settingsFlags() {
        assertEquals("Enabled", Labels.flag("1"))
        assertEquals("Disabled", Labels.flag(" 0 "))
        assertEquals("Not set", Labels.flag(null))
        assertEquals("Not set", Labels.flag(""))
        assertEquals("odd", Labels.flag("odd"))
    }

    @Test
    fun stayAwakeBits() {
        assertEquals("Off", Labels.stayAwake("0"))
        assertEquals("While charging: AC, USB", Labels.stayAwake("3"))
        assertEquals("While charging: AC, USB, Wireless, Dock", Labels.stayAwake("15"))
        assertEquals("Not set", Labels.stayAwake(null))
        assertEquals("abc", Labels.stayAwake("abc"))
        assertEquals("-1", Labels.stayAwake("-1"))
        assertEquals("16", Labels.stayAwake("16"))
    }

    @Test
    fun animatorScales() {
        assertEquals("Off (0x)", Labels.animatorScale("0"))
        assertEquals("Off (0x)", Labels.animatorScale("0.0"))
        assertEquals("0.5x", Labels.animatorScale("0.5"))
        assertEquals("1x", Labels.animatorScale("1.0"))
        assertEquals("Not set", Labels.animatorScale(null))
        assertEquals("fast", Labels.animatorScale("fast"))
        assertEquals("-2", Labels.animatorScale("-2"))
        assertEquals("NaN", Labels.animatorScale("NaN"))
    }

    @Test
    fun featureGrouping() {
        assertEquals("Hardware", Labels.featureGroup("android.hardware.nfc"))
        assertEquals("Software", Labels.featureGroup("android.software.webview"))
        assertEquals("Other", Labels.featureGroup("com.oem.thing"))
        assertTrue(Labels.isGraphicsFeature("android.hardware.vulkan.level"))
        assertTrue(Labels.isGraphicsFeature("android.software.opengles.deqp.level"))
        assertTrue(Labels.isGraphicsFeature("android.hardware.opengles.aep"))
        assertFalse(Labels.isGraphicsFeature("android.hardware.nfc"))
        assertEquals("android.hardware.nfc", Labels.featureText("android.hardware.nfc", 0))
        assertEquals("a (version 3)", Labels.featureText("a", 3))
    }

    @Test
    fun sensorValuesAreBoundedAndSafe() {
        val shown = Labels.sensorValues(floatArrayOf(1.5f, Float.NaN, 0f), "m/s²")
        assertEquals(listOf("Value 0" to "1.5 m/s²", "Value 1" to Text.UNAVAILABLE, "Value 2" to "0 m/s²"), shown)
        assertEquals(Limits.MAX_SENSOR_VALUES, Labels.sensorValues(FloatArray(500), "").size)
        assertEquals(emptyList<Pair<String, String>>(), Labels.sensorValues(FloatArray(0), ""))
    }

    @Test
    fun sensorDetailShowsEveryField() {
        assertEquals(
            "Vendor: Bosch · Type: android.sensor.light · Range: 78.5 · Resolution: 0.001 · Power: 0.17 mA · Wake-up: No",
            Labels.sensorDetail("Bosch", "android.sensor.light", 78.5f, 0.001f, 0.17f, false),
        )
        val missing = Labels.sensorDetail(null, null, null, null, null, null)
        assertEquals(6, Regex(Text.UNAVAILABLE).findAll(missing).count())
        assertTrue(Labels.sensorDetail("v", "t", Float.NaN, 1f, 1f, true).contains("Range: ${Text.UNAVAILABLE}"))
    }

    @Test
    fun sensorUnits() {
        assertEquals("lx", Labels.sensorUnit("android.sensor.light"))
        assertEquals("", Labels.sensorUnit("com.oem.unknown"))
        assertEquals("", Labels.sensorUnit(null))
    }

    @Test
    fun codecText() {
        assertEquals(
            "Decoder · Hardware · video/avc (max 3840 x 2160); audio/opus (max 48000 Hz, 8 ch)",
            Labels.codecDetail(
                false,
                true,
                false,
                listOf(Labels.codecVideoType("video/avc", 3840, 2160), Labels.codecAudioType("audio/opus", 48000, 8)),
            ),
        )
        assertEquals("Encoder · Software · Alias · x", Labels.codecDetail(true, false, true, listOf("x")))
        assertEquals("Decoder · Unavailable · Unavailable", Labels.codecDetail(false, null, null, emptyList()))
        assertEquals("video/x", Labels.codecVideoType("video/x", null, 10))
        assertEquals("audio/x", Labels.codecAudioType("audio/x", null, null))
        assertEquals("audio/x (2 ch)", Labels.codecAudioType("audio/x", null, 2))
    }
}
