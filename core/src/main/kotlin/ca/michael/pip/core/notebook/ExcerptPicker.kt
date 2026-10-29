package ca.michael.pip.core.notebook

import ca.michael.pip.core.text.TextMatch

/**
 * Decides when a turn would benefit from notebook content and proposes the
 * exact excerpt (FR-5.5). The excerpt is only a proposal: nothing is sent
 * unless the owner taps "Send this once" on S9.
 */
object ExcerptPicker {
    const val MAX_ENTRIES = 3

    private val NOTEBOOK_WORDS = setOf(
        "carnet", "note", "notes", "promesse", "promesses", "rappel", "rappels", "promis",
        "notebook", "promise", "promises", "reminder", "reminders", "promised",
    )

    fun propose(ownerWords: String, entries: List<NotebookEntry>): String? {
        if (entries.isEmpty()) return null
        val tokens = TextMatch.words(ownerWords)
        val mentionsNotebook = tokens.any { it in NOTEBOOK_WORDS }
        val keywords = tokens.filter { it.length >= 4 && it !in NOTEBOOK_WORDS }.toSet()

        val matching = entries.filter { e -> !e.done && TextMatch.words(e.text).any { it in keywords } }
        val picked = when {
            matching.isNotEmpty() -> matching
            mentionsNotebook -> entries.filter { !it.done }.sortedByDescending { it.createdAt }
            else -> emptyList()
        }.take(MAX_ENTRIES)
        if (picked.isEmpty()) return null

        return picked.joinToString("\n") { e ->
            val due = e.dueAt?.let { " (due $it)" } ?: ""
            "- ${e.kind.wire}: ${e.text}$due"
        }
    }
}
