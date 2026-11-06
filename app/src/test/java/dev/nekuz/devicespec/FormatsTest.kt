package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class FormatsTest {
    @Test
    fun bytesUseBinaryUnits() {
        assertEquals("0 B", Sizes.bytes(0))
        assertEquals("1023 B", Sizes.bytes(1023))
        assertEquals("1.00 KiB", Sizes.bytes(1024))
        assertEquals("1.50 MiB", Sizes.bytes(1024L * 1024 * 3 / 2))
        assertEquals("5.91 GiB", Sizes.bytes(6_345_000_000L))
        assertEquals("8.00 EiB", Sizes.bytes(Long.MAX_VALUE))
    }

    @Test
    fun negativeBytesAreUnavailable() {
        assertEquals(Text.UNAVAILABLE, Sizes.bytes(-1))
        assertEquals(Text.UNAVAILABLE, Sizes.mebibytes(-1))
    }

    @Test
    fun kibibytesConversionDoesNotOverflow() {
        assertEquals(2048L, Sizes.kibibytesToBytes(2))
        assertEquals(-1L, Sizes.kibibytesToBytes(-5))
        assertEquals(Long.MAX_VALUE, Sizes.kibibytesToBytes(Long.MAX_VALUE))
    }

    @Test
    fun formattingIgnoresTheDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.50 KiB", Sizes.bytes(1536))
            assertEquals("1.80 GHz", Frequencies.khz(1_804_800))
            assertEquals("59.94 Hz", Frequencies.hz(59.94f))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun cpuFrequenciesPickTheUnit() {
        assertEquals("1.80 GHz", Frequencies.khz(1_804_800))
        assertEquals("800 MHz", Frequencies.khz(800_000))
        assertEquals("1.5 MHz", Frequencies.khz(1_500))
        assertEquals(Text.UNAVAILABLE, Frequencies.khz(0))
        assertEquals(Text.UNAVAILABLE, Frequencies.khz(-1))
    }

    @Test
    fun refreshRatesAreTrimmedAndSafe() {
        assertEquals("60 Hz", Frequencies.hz(60f))
        assertEquals("120 Hz", Frequencies.hz(119.99999f))
        assertEquals(Text.UNAVAILABLE, Frequencies.hz(0f))
        assertEquals(Text.UNAVAILABLE, Frequencies.hz(Float.NaN))
        assertEquals(Text.UNAVAILABLE, Frequencies.hz(Float.POSITIVE_INFINITY))
    }

    @Test
    fun cpuLineShowsWhatWasReadable() {
        assertNull(Frequencies.cpuLine(null, null, null))
        assertEquals("300 MHz to 2.40 GHz, now 1.20 GHz", Frequencies.cpuLine(300_000, 2_400_000, 1_200_000))
        assertEquals("up to 2.40 GHz", Frequencies.cpuLine(null, 2_400_000, null))
        assertEquals("from 300 MHz", Frequencies.cpuLine(300_000, null, null))
        assertEquals("now 1.20 GHz", Frequencies.cpuLine(null, null, 1_200_000))
    }

    @Test
    fun dpFollowsDensity() {
        assertEquals(360, DisplayMath.pxToDp(1080, 3f))
        assertEquals(411, DisplayMath.pxToDp(1080, 2.625f))
        assertEquals(0, DisplayMath.pxToDp(0, 3f))
        assertEquals("360 x 800 dp", DisplayMath.sizeDp(1080, 2400, 3f))
    }

    @Test
    fun invalidDpInputGivesNull() {
        assertNull(DisplayMath.pxToDp(100, 0f))
        assertNull(DisplayMath.pxToDp(100, -1f))
        assertNull(DisplayMath.pxToDp(100, Float.NaN))
        assertNull(DisplayMath.pxToDp(100, Float.POSITIVE_INFINITY))
        assertNull(DisplayMath.pxToDp(-1, 2f))
        assertNull(DisplayMath.sizeDp(100, 100, 0f))
        assertNull(DisplayMath.sizePx(-1, 10))
    }

    @Test
    fun densityBuckets() {
        val expected = mapOf(
            120 to "ldpi",
            160 to "mdpi",
            213 to "tvdpi",
            240 to "hdpi",
            280 to "xhdpi",
            320 to "xhdpi",
            400 to "xxhdpi",
            420 to "xxhdpi",
            480 to "xxhdpi",
            560 to "xxxhdpi",
            640 to "xxxhdpi",
            800 to "xxxhdpi",
        )
        expected.forEach { (dpi, bucket) -> assertEquals(bucket, DisplayMath.densityBucket(dpi)) }
        assertNull(DisplayMath.densityBucket(0))
        assertNull(DisplayMath.densityBucket(-160))
    }

    @Test
    fun rotationAndHdrLabels() {
        assertEquals("90°", DisplayMath.rotationDegrees(1))
        assertNull(DisplayMath.rotationDegrees(7))
        assertEquals("HDR10", DisplayMath.hdrType(2))
        assertEquals("Type 99", DisplayMath.hdrType(99))
    }
}
