package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

class ProcParserTest {
    @Test
    fun cpuListParsesRangesAndSingles() {
        assertEquals(listOf(0, 1, 2, 3), ProcParser.parseCpuList("0-3\n"))
        assertEquals(listOf(0, 2, 3, 7), ProcParser.parseCpuList("0,2-3,7"))
        assertEquals(listOf(5), ProcParser.parseCpuList(" 5 "))
    }

    @Test
    fun cpuListIgnoresMalformedInput() {
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList(null))
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList(""))
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList("abc"))
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList("3-1"))
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList("-1"))
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList("1-"))
        assertEquals(listOf(1, 4), ProcParser.parseCpuList("x,1,,y-z,4,99999999999"))
    }

    @Test
    fun cpuListIsBounded() {
        assertEquals(Limits.MAX_CPUS, ProcParser.parseCpuList("0-2147483647").size)
        assertEquals(listOf(0, 1), ProcParser.parseCpuList("0-5", maxCpus = 2))
        assertEquals(emptyList<Int>(), ProcParser.parseCpuList("5000"))
    }

    @Test
    fun frequencyParsing() {
        assertEquals(1_804_800L, ProcParser.parseKhz("1804800\n"))
        assertNull(ProcParser.parseKhz(null))
        assertNull(ProcParser.parseKhz(""))
        assertNull(ProcParser.parseKhz("<unsupported>"))
        assertNull(ProcParser.parseKhz("0"))
        assertNull(ProcParser.parseKhz("-1"))
        assertNull(ProcParser.parseKhz("99999999999999999999"))
        assertNull(ProcParser.parseKhz("100000001"))
    }

    @Test
    fun meminfoParsesKibibyteLines() {
        val text = "MemTotal:       7812345 kB\nSwapTotal:      2097148 kB\nSwapFree:             0 kB\n"
        val values = ProcParser.parseMeminfo(text)
        assertEquals(7_812_345L, values["MemTotal"])
        assertEquals(2_097_148L, values["SwapTotal"])
        assertEquals(0L, values["SwapFree"])
    }

    @Test
    fun meminfoSkipsMalformedLines() {
        val text = "garbage\n:12 kB\nNoNumber: kB\nBad: abc kB\nNegative: -5 kB\nWrongUnit: 5 MB\nHuge: 99999999999999999999 kB\nGood: 5 kB\nNoUnit: 7\n"
        assertEquals(mapOf("Good" to 5L, "NoUnit" to 7L), ProcParser.parseMeminfo(text))
        assertEquals(emptyMap<String, Long>(), ProcParser.parseMeminfo(null))
        assertEquals(emptyMap<String, Long>(), ProcParser.parseMeminfo(""))
    }

    @Test
    fun cpuinfoParsesArmFormat() {
        val text = """
            processor	: 0
            Features	: fp asimd aes
            CPU part	: 0xd05

            processor	: 1
            Features	: fp asimd aes
            CPU part	: 0xd05

            processor	: 2
            CPU part	: 0xd41

            Hardware	: Qualcomm Technologies, Inc SM8150
        """.trimIndent()
        val info = ProcParser.parseCpuInfo(text)
        assertEquals(3, info.processors)
        assertEquals("Qualcomm Technologies, Inc SM8150", info.hardware)
        assertEquals("fp asimd aes", info.features)
        assertEquals(mapOf("0xd05" to 2, "0xd41" to 1), info.parts)
    }

    @Test
    fun cpuinfoParsesX86Format() {
        val info = ProcParser.parseCpuInfo("processor : 0\nmodel name : Virtual CPU\nflags : sse sse2\n")
        assertEquals("Virtual CPU", info.modelName)
        assertEquals("sse sse2", info.features)
        assertEquals(1, info.processors)
    }

    @Test
    fun cpuinfoNeverKeepsTheSerial() {
        val secret = "00000000abcd1234"
        val info = ProcParser.parseCpuInfo("processor : 0\nSerial : $secret\nHardware : Board\n")
        assertFalse(info.toString().contains(secret))
    }

    @Test
    fun cpuinfoSurvivesMalformedInput() {
        val empty = CpuInfo(0, null, null, null, emptyMap())
        assertEquals(empty, ProcParser.parseCpuInfo(null))
        assertEquals(empty, ProcParser.parseCpuInfo(""))
        assertEquals(empty, ProcParser.parseCpuInfo(":::\n\u0000\nprocessor : x\nHardware :\n : value\n"))
    }

    @Test
    fun cpuinfoBoundsDistinctParts() {
        val text = (0 until 100).joinToString("\n") { "CPU part : 0x$it" }
        assertEquals(16, ProcParser.parseCpuInfo(text).parts.size)
    }

    @Test
    fun readBoundedStopsAtTheLimit() {
        val input = ByteArrayInputStream(ByteArray(1000) { 'a'.code.toByte() })
        assertEquals(10, ProcParser.readBounded(input, 10).length)
    }

    @Test
    fun readBoundedHandlesShortReadsAndEof() {
        val chunks = object : InputStream() {
            private var left = 5
            override fun read(): Int = if (left-- > 0) 'b'.code else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val value = read()
                if (value < 0) return -1
                b[off] = value.toByte()
                return 1
            }
        }
        assertEquals("bbbbb", ProcParser.readBounded(chunks, 64))
        assertEquals("", ProcParser.readBounded(ByteArrayInputStream(ByteArray(0)), 64))
        assertEquals("", ProcParser.readBounded(ByteArrayInputStream(ByteArray(4)), 0))
    }

    @Test
    fun readBoundedToleratesInvalidUtf8() {
        val bytes = byteArrayOf(0x41, 0xC3.toByte(), 0x28, 0xFF.toByte())
        assertTrue(ProcParser.readBounded(ByteArrayInputStream(bytes)).startsWith("A"))
    }
}
