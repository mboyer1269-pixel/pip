package ca.michael.pip.core

import ca.michael.pip.core.notebook.EntryKind
import ca.michael.pip.core.notebook.ExcerptPicker
import ca.michael.pip.core.notebook.GcmBlob
import ca.michael.pip.core.notebook.NotebookCodec
import ca.michael.pip.core.notebook.NotebookEntry
import java.time.Instant
import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotebookTest {
    private val entries = listOf(
        NotebookEntry(kind = EntryKind.NOTE, text = "Code du garage: rouge", createdAt = Instant.parse("2026-10-01T10:00:00Z")),
        NotebookEntry(kind = EntryKind.REMINDER, text = "Appeler le garagiste", createdAt = Instant.parse("2026-10-02T10:00:00Z"),
            dueAt = Instant.parse("2026-10-03T10:00:00Z")),
    )

    @Test fun codecRoundTrips() {
        assertEquals(entries, NotebookCodec.decode(NotebookCodec.encode(entries)))
    }

    /** T-4 (format part): the sealed blob does not contain the plaintext, and each seal uses a fresh IV. */
    @Test fun sealedBlobIsNotPlaintext() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plain = NotebookCodec.encode(entries).toByteArray()
        val a = GcmBlob.seal(key, plain)
        val b = GcmBlob.seal(key, plain)
        assertFalse(String(a, Charsets.ISO_8859_1).contains("garage"))
        assertFalse(a.copyOf(GcmBlob.IV_BYTES).contentEquals(b.copyOf(GcmBlob.IV_BYTES)), "fresh IV per write")
        assertTrue(GcmBlob.open(key, a).contentEquals(plain))
        a[a.size - 1] = (a[a.size - 1] + 1).toByte()
        assertFailsWith<AEADBadTagException> { GcmBlob.open(key, a) }
    }

    @Test fun overdueOnlyForOpenRemindersPastDue() {
        val now = Instant.parse("2026-10-29T00:00:00Z")
        assertTrue(entries[1].isOverdue(now))
        assertFalse(entries[1].copy(done = true).isOverdue(now))
        assertFalse(entries[0].isOverdue(now))
    }

    @Test fun excerptIsProposedOnlyWhenRelevant() {
        assertNull(ExcerptPicker.propose("Quelle heure est-il ?", entries))
        val garage = assertNotNull(ExcerptPicker.propose("C'est quoi le code du garage ?", entries))
        assertTrue("rouge" in garage)
        assertNotNull(ExcerptPicker.propose("Qu'est-ce qu'il y a dans mon carnet ?", entries))
    }
}
