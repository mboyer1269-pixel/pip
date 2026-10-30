package ca.michael.pip.core.text

/**
 * Turns a brain answer into text that sounds natural when spoken (V1-2).
 * Answers often carry chat formatting (bold, bullets, links, emojis) that a
 * speech engine reads aloud or stumbles on.
 */
object SpeechText {
    private val LINK = Regex("\\[([^\\]]+)]\\([^)]*\\)")
    private val URL = Regex("https?://\\S*[^\\s.,;:!?)]")
    private val HEADING = Regex("^#+\\s*")
    private val BULLET = Regex("^(?:[-*•]|\\d+[.)])\\s+")
    private val MARKS = Regex("[*_`~]+")
    private val EMOJI = Regex("[\\p{So}\\uFE0F\\u200D]")
    private val SPACE_BEFORE_STOP = Regex("\\s+([.,)])")
    private val SPACES = Regex("\\s+")
    private val ENDS_SENTENCE = Regex("[.!?:;,]$")

    fun clean(text: String, english: Boolean = false): String {
        val sentences = text.lines()
            .map { it.trim().replace(HEADING, "").replace(BULLET, "").trim() }
            .filter { it.isNotEmpty() }
            .map { if (ENDS_SENTENCE.containsMatchIn(it)) it else "$it." }
        return sentences.joinToString(" ")
            .replace(LINK, "$1")
            .replace(URL, if (english) "the link" else "le lien")
            .replace(MARKS, "")
            .replace(EMOJI, "")
            .replace(SPACES, " ")
            .replace(SPACE_BEFORE_STOP, "$1")
            .trim()
    }
}

/** A text-to-speech voice as the platform describes it, reduced to what the choice needs. */
data class VoiceOption(
    val name: String,
    val localeTag: String,
    val quality: Int,
    val needsNetwork: Boolean,
    val installed: Boolean,
)

/** Picks the best installed voice: the exact region first, then the same language; highest quality, offline on ties. */
object VoicePick {
    fun best(options: List<VoiceOption>, wantedTag: String): VoiceOption? {
        val language = wantedTag.substringBefore('-').lowercase()
        val installed = options.filter { it.installed }
        val exact = installed.filter { it.localeTag.equals(wantedTag, ignoreCase = true) }
        val pool = exact.ifEmpty { installed.filter { it.localeTag.substringBefore('-').lowercase() == language } }
        return pool.sortedWith(compareByDescending<VoiceOption> { it.quality }.thenBy { it.needsNetwork }).firstOrNull()
    }
}
