package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
    private val time = "2026-11-06T10:00:00Z"

    private fun section(id: SectionId, vararg entries: Entry) = Section(id, entries.toList())

    private fun all(): Map<SectionId, Section> = SectionId.entries.associateWith { section(it, Entry("Key", "Value")) }

    @Test
    fun headerCarriesTheNotice() {
        val report = ReportBuilder.build(all(), time, "1.0.0")
        val lines = report.lines()
        assertEquals("DeviceSpec report", lines[0])
        assertEquals("Generated: $time", lines[1])
        assertEquals("App version: 1.0.0", lines[2])
        assertTrue(report.contains("fingerprint and model identify the device type"))
        assertTrue(report.contains(ReportBuilder.NOT_READ))
    }

    @Test
    fun sectionsFollowTheEnumOrderEvenIfTheMapDoesNot() {
        val shuffled = all().toList().reversed().toMap()
        val report = ReportBuilder.build(shuffled, time, "1")
        val positions = SectionId.entries.map { report.indexOf("== ${it.title} ==") }
        assertTrue(positions.all { it >= 0 })
        assertEquals(positions.sorted(), positions)
    }

    @Test
    fun missingOrEmptySectionsAreUnavailable() {
        val report = ReportBuilder.build(mapOf(SectionId.MEDIA to section(SectionId.MEDIA)), time, "1")
        assertTrue(report.contains("== Build ==\nUnavailable\n"))
        assertTrue(report.contains("== Media ==\nUnavailable\n"))
    }

    @Test
    fun entriesAreWrittenWithGroupsAndBareValues() {
        val report = ReportBuilder.build(
            mapOf(
                SectionId.FEATURES to section(
                    SectionId.FEATURES,
                    Entry("Top", "level"),
                    Entry("", "android.hardware.nfc", "Hardware"),
                    Entry("", "android.hardware.wifi", "Hardware"),
                    Entry("", "android.software.webview", "Software"),
                ),
            ),
            time,
            "1",
        )
        assertTrue(
            report.contains(
                "== System features ==\nTop: level\n-- Hardware --\nandroid.hardware.nfc\nandroid.hardware.wifi\n" +
                    "-- Software --\nandroid.software.webview\n",
            ),
        )
    }

    @Test
    fun aValueCannotForgeReportLines() {
        val forged = Entry("Model\n== Evil ==", "Pixel\n== Build ==\nFingerprint: fake‮")
        val report = ReportBuilder.build(mapOf(SectionId.BUILD to section(SectionId.BUILD, forged)), time, "1")
        assertEquals(1, report.lines().count { it == "== Build ==" })
        assertFalse(report.lines().contains("== Evil =="))
        assertFalse(report.contains('‮'))
        assertTrue(report.contains("Model == Evil ==: Pixel == Build == Fingerprint: fake\n"))
    }

    @Test
    fun emptyValueIsUnavailable() {
        val report = ReportBuilder.build(mapOf(SectionId.BUILD to section(SectionId.BUILD, Entry("Key", " "))), time, "1")
        assertTrue(report.contains("Key: Unavailable\n"))
    }

    @Test
    fun longReportsAreCutAtALineAndSayItWasCut() {
        val entries = List(2000) { Entry("Key$it", "v".repeat(100)) }
        val report = ReportBuilder.build(mapOf(SectionId.BUILD to Section(SectionId.BUILD, entries)), time, "1", 5_000)
        assertTrue(report.length <= 5_000)
        assertTrue(report.endsWith("[Report truncated at 5000 characters]\n"))
        report.lines().filter { it.startsWith("Key") }.forEach { assertEquals(100, it.substringAfter(": ").length) }
    }

    @Test
    fun theDefaultLimitHolds() {
        val entries = List(Limits.MAX_ENTRIES) { Entry("Key$it", "v".repeat(Limits.MAX_VALUE_CHARS)) }
        val sections = SectionId.entries.associateWith { Section(it, entries) }
        assertTrue(ReportBuilder.build(sections, time, "1").length <= Limits.MAX_REPORT_CHARS)
    }

    @Test
    fun aTinyLimitDoesNotCrash() {
        assertEquals("[Report truncated at 1 characters]\n", ReportBuilder.build(all(), time, "1", 1))
    }
}
