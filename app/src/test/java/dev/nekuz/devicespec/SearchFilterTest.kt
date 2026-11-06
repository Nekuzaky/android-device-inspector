package dev.nekuz.devicespec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterTest {
    private val build = Section(
        SectionId.BUILD,
        listOf(
            Entry("Model", "Pixel 7", "Device type"),
            Entry("Fingerprint", "google/panther/panther:14/UQ1A", "Device type"),
            Entry("API level", "34", "Android"),
        ),
    )
    private val graphics = Section(
        SectionId.GRAPHICS,
        listOf(
            Entry("Renderer", "Mali-G710", "OpenGL ES"),
            Entry("", "GL_KHR_texture_compression_astc_ldr", "OpenGL ES extensions (1)"),
        ),
    )
    private val sections = listOf(build, graphics)

    @Test
    fun emptyQueryKeepsEverything() {
        assertEquals(sections, SearchFilter.filter(sections, ""))
        assertEquals(sections, SearchFilter.filter(sections, "   \n "))
    }

    @Test
    fun matchesValuesAcrossSectionsIgnoringCase() {
        val result = SearchFilter.filter(sections, "PANTHER")
        assertEquals(listOf(SectionId.BUILD), result.map { it.id })
        assertEquals(listOf("Fingerprint"), result.single().entries.map { it.label })
    }

    @Test
    fun matchesLabelsGroupsAndSectionTitles() {
        assertEquals(listOf("API level"), SearchFilter.filter(sections, "api").single().entries.map { it.label })
        assertEquals(1, SearchFilter.filter(sections, "extensions").single().entries.size)
        assertEquals(2, SearchFilter.filter(sections, "gpu").single().entries.size)
    }

    @Test
    fun allTokensMustMatchOneEntry() {
        assertEquals(listOf("Renderer"), SearchFilter.filter(sections, "mali renderer").single().entries.map { it.label })
        assertTrue(SearchFilter.filter(sections, "mali pixel").isEmpty())
    }

    @Test
    fun specialCharactersAreLiteral() {
        assertTrue(SearchFilter.filter(sections, ".*").isEmpty())
        assertTrue(SearchFilter.filter(sections, "[").isEmpty())
        assertEquals(1, SearchFilter.filter(sections, "g710").single().entries.size)
    }

    @Test
    fun orderIsPreserved() {
        assertEquals(listOf(SectionId.BUILD, SectionId.GRAPHICS), SearchFilter.filter(sections, "e").map { it.id })
    }

    @Test
    fun longQueriesAreBounded() {
        val tokens = SearchFilter.tokens(List(100) { "a" }.joinToString(" "))
        assertEquals(Limits.MAX_QUERY_TOKENS, tokens.size)
        assertEquals(Limits.MAX_QUERY_CHARS, SearchFilter.tokens("x".repeat(5000)).single().length)
    }

    @Test
    fun filteringAnEmptySectionDropsIt() {
        assertTrue(SearchFilter.filter(listOf(Section(SectionId.MEDIA, emptyList())), "x").isEmpty())
    }
}
