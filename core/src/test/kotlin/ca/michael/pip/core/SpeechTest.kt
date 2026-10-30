package ca.michael.pip.core

import ca.michael.pip.core.text.SpeechText
import ca.michael.pip.core.text.VoiceOption
import ca.michael.pip.core.text.VoicePick
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechTest {
    @Test fun dropsMarkdownMarks() {
        assertEquals("Salut toi, voici le code ok.", SpeechText.clean("**Salut** _toi_, voici le `code` ok."))
    }

    @Test fun turnsHeadingsAndBulletsIntoSentences() {
        assertEquals("Demain. Dentiste à 9 h. Souper chez Léa.", SpeechText.clean("# Demain\n- Dentiste à 9 h\n* Souper chez Léa"))
    }

    @Test fun keepsLinkTextAndHidesBareUrls() {
        assertEquals("Lis la météo, ou le lien.", SpeechText.clean("Lis [la météo](https://meteo.example/qc), ou https://x.example/a?b=1."))
    }

    @Test fun dropsEmojisAndExtraSpaces() {
        assertEquals("Bravo ! Bien joué.", SpeechText.clean("Bravo 🎉 !   Bien joué 👍."))
    }

    @Test fun leavesPlainTextAlone() {
        assertEquals("Il fait 12 degrés, l'après-midi sera doux.", SpeechText.clean("Il fait 12 degrés, l'après-midi sera doux."))
    }

    private fun v(name: String, tag: String, quality: Int, network: Boolean = false, installed: Boolean = true) =
        VoiceOption(name, tag, quality, network, installed)

    @Test fun picksTheBestInstalledVoiceForTheRegion() {
        val voices = listOf(
            v("fr-ca-legacy", "fr-CA", 300),
            v("fr-ca-neural", "fr-CA", 500),
            v("fr-fr-best", "fr-FR", 500),
            v("fr-ca-missing", "fr-CA", 500, installed = false),
        )
        assertEquals("fr-ca-neural", VoicePick.best(voices, "fr-CA")?.name)
    }

    @Test fun fallsBackToTheSameLanguageThenPrefersOfflineOnTies() {
        val voices = listOf(
            v("fr-fr-net", "fr-FR", 400, network = true),
            v("fr-fr-local", "fr-FR", 400),
            v("en-ca", "en-CA", 500),
        )
        assertEquals("fr-fr-local", VoicePick.best(voices, "fr-CA")?.name)
    }

    @Test fun returnsNullWhenNoVoiceSpeaksTheLanguage() {
        assertNull(VoicePick.best(listOf(v("en-us", "en-US", 500)), "fr-CA"))
    }
}
