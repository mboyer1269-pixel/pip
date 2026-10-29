package ca.michael.pip.core

import ca.michael.pip.core.text.TextMatch
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextMatchTest {
    @Test fun recognizesSpokenScreenRequests() {
        assertTrue(TextMatch.asksForScreen("Pip, regarde mon écran s'il te plaît"))
        assertTrue(TextMatch.asksForScreen("Can you look at my screen?"))
        assertFalse(TextMatch.asksForScreen("Quel temps fait-il ?"))
    }

    @Test fun guessesLanguage() {
        assertTrue(TextMatch.looksEnglish("what is the weather like"))
        assertFalse(TextMatch.looksEnglish("c'est quoi la météo"))
    }
}
