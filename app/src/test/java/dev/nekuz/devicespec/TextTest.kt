package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextTest {
    @Test
    fun cleanFlattensLineBreaksAndControls() {
        assertEquals("a b c", Text.clean("a\nb\r\n\tc"))
        assertEquals("ab", Text.clean("a\u0000\u0007b"))
        assertEquals("a b", Text.clean("a b"))
        assertEquals("trim", Text.clean("  trim \n"))
    }

    @Test
    fun cleanRemovesBidiAndFormatCharacters() {
        assertEquals("abc", Text.clean("a‮b​c﻿"))
    }

    @Test
    fun cleanKeepsValidSurrogatesAndDropsBrokenOnes() {
        assertEquals("a😀b", Text.clean("a😀b"))
        assertEquals("ab", Text.clean("a\uD83Db"))
        assertEquals("ab", Text.clean("a\uDE00b"))
    }

    @Test
    fun cleanBoundsLengthWithoutSplittingSurrogates() {
        assertEquals("aaaaa" + Text.TRUNCATED, Text.clean("a".repeat(100), 5))
        assertEquals("aaaaa", Text.clean("aaaaa", 5))
        assertEquals("a" + Text.TRUNCATED, Text.clean("a😀", 2))
    }

    @Test
    fun guardCatchesEveryThrowable() {
        assertEquals(Text.UNAVAILABLE, Text.guard { throw RuntimeException("x") })
        assertEquals(Text.UNAVAILABLE, Text.guard { throw SecurityException("x") })
        assertEquals(Text.UNAVAILABLE, Text.guard { throw NoSuchMethodError("x") })
        assertEquals(Text.UNAVAILABLE, Text.guard { throw OutOfMemoryError("x") })
        assertEquals(Text.UNAVAILABLE, Text.guard { throw StackOverflowError("x") })
    }

    @Test
    fun guardTreatsMissingAndEmptyAsUnavailable() {
        assertEquals(Text.UNAVAILABLE, Text.guard { null })
        assertEquals(Text.UNAVAILABLE, Text.guard { "" })
        assertEquals(Text.UNAVAILABLE, Text.guard { " \n‮ " })
        assertEquals("ok", Text.guard { "ok" })
    }

    @Test
    fun attemptReturnsNullOnFailure() {
        assertNull(Text.attempt<String> { throw LinkageError("x") })
        assertEquals("x", Text.attempt { "x" })
    }

    @Test
    fun quietlySwallowsFailures() {
        Text.quietly { throw IllegalStateException("x") }
    }

    @Test
    fun builderKeepsGoingAfterAFailingValue() {
        val b = SectionBuilder(SectionId.BUILD)
        b.group("G")
        b.add("One") { "1" }
        b.add("Two") { throw NoClassDefFoundError("x") }
        b.add("Three") { "3" }
        val entries = b.build().entries
        assertEquals(listOf("1", Text.UNAVAILABLE, "3"), entries.map { it.value })
        assertTrue(entries.all { it.group == "G" })
    }

    @Test
    fun builderGuardedBlockAddsAnUnavailableRow() {
        val b = SectionBuilder(SectionId.STORAGE)
        b.guarded("Volumes") { throw IllegalArgumentException("x") }
        assertEquals(listOf(Entry("Volumes", Text.UNAVAILABLE)), b.build().entries)
    }

    @Test
    fun builderBoundsEntriesAndSaysSo() {
        val b = SectionBuilder(SectionId.FEATURES, maxEntries = 3)
        repeat(10) { index -> b.add("L$index") { "v" } }
        val entries = b.build().entries
        assertEquals(4, entries.size)
        assertEquals("List truncated", entries.last().label)
        assertEquals("7 more entries not shown", entries.last().value)
    }

    @Test
    fun builderCleansLabelsAndValues() {
        val b = SectionBuilder(SectionId.BUILD)
        b.group("Gr\noup")
        b.add("La\nbel") { "x".repeat(Limits.MAX_VALUE_CHARS + 50) }
        val entry = b.build().entries.single()
        assertEquals("Gr oup", entry.group)
        assertEquals("La bel", entry.label)
        assertEquals(Limits.MAX_VALUE_CHARS + Text.TRUNCATED.length, entry.value.length)
        assertFalse(entry.value.contains('\n'))
    }
}
