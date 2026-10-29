package ca.michael.pip.core.acp

import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The closed list of intents (SPEC 7.5). Nothing else exists: an intent that
 * is not declared here can never run, approved or not.
 */
sealed interface AcpIntent {
    val name: String

    data object DeviceStatus : AcpIntent { override val name = "device.status" }
    data object SnapshotLast : AcpIntent { override val name = "snapshot.last" }
    data class MessageShow(val text: String) : AcpIntent { override val name = NAME
        companion object { const val NAME = "message.show" } }
    data class ReminderCreate(val text: String, val dueAt: Instant?) : AcpIntent { override val name = NAME
        companion object { const val NAME = "reminder.create" } }
}

sealed interface IntentParse {
    data class Ok(val intent: AcpIntent) : IntentParse
    data object Unknown : IntentParse
    data object Forbidden : IntentParse
    data class BadArgs(val why: String) : IntentParse
}

object IntentCatalog {
    const val MAX_TEXT = 500

    val NAMES = listOf(
        AcpIntent.DeviceStatus.name,
        AcpIntent.SnapshotLast.name,
        AcpIntent.MessageShow.NAME,
        AcpIntent.ReminderCreate.NAME,
    )

    /**
     * Words that mark a request the doctrine forbids outright: remote shell,
     * free-form Android intents or URLs, remote capture, SMS, calls.
     * Used only to word the rejection; anything outside [NAMES] is rejected anyway.
     */
    private val FORBIDDEN_WORDS = listOf(
        "shell", "exec", "command", "intent", "url", "open", "fetch", "browse",
        "camera", "photo", "screen", "record", "mic", "audio", "capture",
        "sms", "message.send", "call", "phone", "dial",
    )

    fun parse(intent: String, args: JsonObject): IntentParse = when (intent) {
        AcpIntent.DeviceStatus.name -> noArgs(args, AcpIntent.DeviceStatus)
        AcpIntent.SnapshotLast.name -> noArgs(args, AcpIntent.SnapshotLast)
        AcpIntent.MessageShow.NAME -> {
            if (args.keys != setOf("text")) IntentParse.BadArgs("expected only text")
            else text(args)?.let { IntentParse.Ok(AcpIntent.MessageShow(it)) } ?: IntentParse.BadArgs("text")
        }
        AcpIntent.ReminderCreate.NAME -> reminder(args)
        else -> if (FORBIDDEN_WORDS.any { intent.lowercase().contains(it) }) IntentParse.Forbidden else IntentParse.Unknown
    }

    private fun noArgs(args: JsonObject, intent: AcpIntent): IntentParse =
        if (args.isEmpty()) IntentParse.Ok(intent) else IntentParse.BadArgs("expected no args")

    private fun text(args: JsonObject): String? {
        val p = args["text"] as? JsonPrimitive ?: return null
        if (!p.isString) return null
        val t = p.content.trim()
        return if (t.isEmpty() || t.length > MAX_TEXT) null else t
    }

    private fun reminder(args: JsonObject): IntentParse {
        if (!setOf("text", "due_at").containsAll(args.keys) || "text" !in args) return IntentParse.BadArgs("expected text and optional due_at")
        val text = text(args) ?: return IntentParse.BadArgs("text")
        val dueRaw = args["due_at"]
        val due = when {
            dueRaw == null || dueRaw is kotlinx.serialization.json.JsonNull -> null
            dueRaw is JsonPrimitive && dueRaw.isString -> try {
                Instant.parse(dueRaw.content)
            } catch (e: DateTimeParseException) {
                return IntentParse.BadArgs("due_at")
            }
            else -> return IntentParse.BadArgs("due_at")
        }
        return IntentParse.Ok(AcpIntent.ReminderCreate(text, due))
    }
}
