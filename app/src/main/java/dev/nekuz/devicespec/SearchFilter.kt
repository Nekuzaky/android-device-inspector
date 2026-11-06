package dev.nekuz.devicespec

import java.util.Locale

object SearchFilter {
    private val WHITESPACE = Regex("\\s+")

    fun tokens(query: String): List<String> = query
        .take(Limits.MAX_QUERY_CHARS)
        .trim()
        .lowercase(Locale.ROOT)
        .split(WHITESPACE)
        .filter { it.isNotEmpty() }
        .take(Limits.MAX_QUERY_TOKENS)

    fun matches(section: SectionId, entry: Entry, tokens: List<String>): Boolean = tokens.all { token ->
        entry.value.contains(token, ignoreCase = true) ||
            entry.label.contains(token, ignoreCase = true) ||
            entry.group.contains(token, ignoreCase = true) ||
            section.title.contains(token, ignoreCase = true)
    }

    fun filter(sections: List<Section>, query: String): List<Section> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return sections
        return sections.mapNotNull { section ->
            val hits = section.entries.filter { matches(section.id, it, tokens) }
            if (hits.isEmpty()) null else Section(section.id, hits)
        }
    }
}
