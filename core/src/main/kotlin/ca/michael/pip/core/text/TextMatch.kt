package ca.michael.pip.core.text

import java.text.Normalizer

/** Small local text helpers. Nothing here leaves the device. */
object TextMatch {
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun words(s: String): List<String> =
        normalize(s).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private val SCREEN_PHRASES = listOf(
        "regarde mon ecran", "regarde l ecran", "lis mon ecran", "qu est ce qu il y a sur mon ecran",
        "look at my screen", "read my screen", "what s on my screen", "whats on my screen",
    )

    /** FR-3.1: a spoken request for screen context, recognized on the device. */
    fun asksForScreen(s: String): Boolean {
        val n = words(s).joinToString(" ")
        return SCREEN_PHRASES.any { n.contains(it) }
    }

    private val EN = setOf("the", "is", "what", "you", "are", "can", "my", "how", "please", "and", "with", "this", "it", "do", "i")
    private val FR = setOf("le", "la", "les", "est", "quoi", "tu", "es", "peux", "mon", "ma", "comment", "et", "avec", "ce", "je", "de", "un", "une")

    /** FR-2.4: answer in English when the owner spoke English, French otherwise. */
    fun looksEnglish(s: String): Boolean {
        val w = words(s)
        return w.count { it in EN } > w.count { it in FR }
    }
}
