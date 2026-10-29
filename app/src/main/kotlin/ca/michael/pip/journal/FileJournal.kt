package ca.michael.pip.journal

import android.content.Context
import ca.michael.pip.core.journal.EventKind
import ca.michael.pip.core.journal.Journal
import ca.michael.pip.core.journal.JournalEvent
import java.io.File
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * On-device ACP task journal (ACP-16), one JSON object per line in app-private
 * storage. Holds task ids, intents and reasons only: never a secret or key (ACP-17).
 */
class FileJournal(context: Context) : Journal {
    private val file = File(context.filesDir, "journal.jsonl")

    @Synchronized
    override fun append(event: JournalEvent) {
        val line = buildJsonObject {
            put("at", JsonPrimitive(event.at.toString()))
            put("kind", JsonPrimitive(event.kind.name))
            put("task_id", event.taskId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("intent", event.intent?.let { JsonPrimitive(it.take(80)) } ?: JsonNull)
            put("detail", event.detail?.let { JsonPrimitive(it.take(200)) } ?: JsonNull)
        }.toString()
        file.appendText(line + "\n")
    }

    @Synchronized
    override fun all(): List<JournalEvent> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            try {
                val o = Json.parseToJsonElement(line).jsonObject
                fun opt(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
                JournalEvent(
                    at = Instant.parse(o.getValue("at").jsonPrimitive.content),
                    kind = EventKind.valueOf(o.getValue("kind").jsonPrimitive.content),
                    taskId = opt("task_id"),
                    intent = opt("intent"),
                    detail = opt("detail"),
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    @Synchronized
    override fun clear() {
        file.delete()
    }
}
