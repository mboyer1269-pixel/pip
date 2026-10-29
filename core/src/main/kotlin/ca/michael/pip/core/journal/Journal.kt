package ca.michael.pip.core.journal

import java.time.Instant

/** Every ACP task event that SPEC ACP-16 requires to be written down. */
enum class EventKind { RECEIVED, PENDING, APPROVED, REFUSED, EXPIRED, REJECTED, EXECUTED, FAILED, CANCELLED }

/** One journal line. Never holds the pairing secret or the brain key (ACP-17). */
data class JournalEvent(
    val at: Instant,
    val kind: EventKind,
    val taskId: String? = null,
    val intent: String? = null,
    val detail: String? = null,
)

interface Journal {
    fun append(event: JournalEvent)
    fun all(): List<JournalEvent>
    fun clear()
}

class MemoryJournal : Journal {
    private val events = mutableListOf<JournalEvent>()
    @Synchronized override fun append(event: JournalEvent) { events += event }
    @Synchronized override fun all(): List<JournalEvent> = events.toList()
    @Synchronized override fun clear() = events.clear()
}
