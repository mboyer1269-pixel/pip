package ca.michael.pip.core.notebook

import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class EntryKind(val wire: String) {
    NOTE("note"), PROMISE("promise"), REMINDER("reminder");

    companion object {
        fun fromWire(s: String) = entries.firstOrNull { it.wire == s } ?: throw IllegalArgumentException("kind")
    }
}

/** One notebook record (SPEC FR-5.1, 6.1). */
data class NotebookEntry(
    val id: String = UUID.randomUUID().toString(),
    val kind: EntryKind,
    val text: String,
    val createdAt: Instant,
    val dueAt: Instant? = null,
    val done: Boolean = false,
) {
    fun isOverdue(now: Instant): Boolean = kind == EntryKind.REMINDER && !done && dueAt != null && dueAt.isBefore(now)
}

/** The plaintext form, a JSON array of records, before it is sealed. */
object NotebookCodec {
    fun encode(entries: List<NotebookEntry>): String = JsonArray(entries.map { e ->
        buildJsonObject {
            put("id", JsonPrimitive(e.id))
            put("kind", JsonPrimitive(e.kind.wire))
            put("text", JsonPrimitive(e.text))
            put("created_at", JsonPrimitive(e.createdAt.toString()))
            put("due_at", e.dueAt?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
            put("done", JsonPrimitive(e.done))
        }
    }).toString()

    fun decode(json: String): List<NotebookEntry> = Json.parseToJsonElement(json).jsonArray.map { el ->
        val o: JsonObject = el.jsonObject
        NotebookEntry(
            id = o.getValue("id").jsonPrimitive.content,
            kind = EntryKind.fromWire(o.getValue("kind").jsonPrimitive.content),
            text = o.getValue("text").jsonPrimitive.content,
            createdAt = Instant.parse(o.getValue("created_at").jsonPrimitive.content),
            dueAt = (o["due_at"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { Instant.parse(it.content) },
            done = o.getValue("done").jsonPrimitive.boolean,
        )
    }
}
