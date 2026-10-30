package ca.michael.pip.core.actions

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Who a call or SMS goes to: a number as typed, or a name resolved on the device (FR-A.5). */
sealed interface Recipient {
    data class Number(val digits: String) : Recipient
    data class Name(val name: String) : Recipient
}

/**
 * The closed list of Block A actions (SPEC 13.2). Nothing else exists:
 * a proposal outside this list can never run.
 */
sealed interface DeviceAction {
    val name: String
    /** True when approving sends device data to the brain (FR-A.4). */
    val sendsDataToBrain: Boolean get() = false

    data class OpenApp(val appName: String) : DeviceAction { override val name = "open_app" }
    data class Call(val to: Recipient) : DeviceAction { override val name = "call" }
    data class SendSms(val to: Recipient, val text: String) : DeviceAction { override val name = "send_sms" }
    data class FindContact(val query: String) : DeviceAction {
        override val name = "find_contact"; override val sendsDataToBrain = true
    }
    data class ReadSms(val from: String?, val limit: Int) : DeviceAction {
        override val name = "read_sms"; override val sendsDataToBrain = true
    }
    data class ReadCallLog(val limit: Int) : DeviceAction {
        override val name = "read_call_log"; override val sendsDataToBrain = true
    }
    data class ReadCalendar(val fromDate: LocalDate?, val days: Int) : DeviceAction {
        override val name = "read_calendar"; override val sendsDataToBrain = true
    }
    data class AddCalendarEvent(
        val title: String,
        val start: LocalDateTime,
        val durationMinutes: Int,
        val location: String?,
    ) : DeviceAction { override val name = "add_calendar_event" }
    data object GetLocation : DeviceAction {
        override val name = "get_location"; override val sendsDataToBrain = true
    }
    data object GetSteps : DeviceAction {
        override val name = "get_steps"; override val sendsDataToBrain = true
    }
}

sealed interface ActionParse {
    data class Ok(val action: DeviceAction) : ActionParse
    data class Rejected(val why: String) : ActionParse
}

object ActionCatalog {
    const val MAX_ACTIONS_PER_TURN = 3
    const val MAX_ROUNDS = 2
    const val MAX_SMS = 1000

    val NAMES = listOf(
        "open_app", "call", "send_sms", "find_contact", "read_sms",
        "read_call_log", "read_calendar", "add_calendar_event", "get_location", "get_steps",
    )

    private val NUMBER = Regex("^\\+?[0-9 ().\\-]{3,24}$")

    /** A string is a phone number when it has only dialable characters and at least 3 digits. */
    fun recipientOf(raw: String): Recipient {
        val t = raw.trim()
        return if (NUMBER.matches(t) && t.count { it.isDigit() } >= 3) {
            Recipient.Number((if (t.startsWith("+")) "+" else "") + t.filter { it.isDigit() })
        } else {
            Recipient.Name(t)
        }
    }

    /** Validates one proposal from the brain. Untrusted input (SEC-9). */
    fun parse(name: String, argumentsJson: String): ActionParse {
        val args = try {
            if (argumentsJson.isBlank()) JsonObject(emptyMap()) else Json.parseToJsonElement(argumentsJson).jsonObject
        } catch (e: Exception) {
            return ActionParse.Rejected("arguments are not a JSON object")
        }
        // Optional fields sent as null count as absent.
        val a = JsonObject(args.filterValues { it !is JsonNull })
        return try {
            ActionParse.Ok(build(name, a))
        } catch (e: Invalid) {
            ActionParse.Rejected(e.message ?: "invalid")
        }
    }

    private class Invalid(message: String) : Exception(message)

    private fun build(name: String, a: JsonObject): DeviceAction {
        fun only(vararg keys: String) {
            val extra = a.keys - keys.toSet()
            if (extra.isNotEmpty()) throw Invalid("unknown arguments: ${extra.sorted().joinToString()}")
        }
        fun str(key: String, max: Int, required: Boolean = true): String? {
            val p = a[key] ?: if (required) throw Invalid("$key is required") else return null
            if (p !is JsonPrimitive || !p.isString) throw Invalid("$key must be a string")
            val v = p.content.trim()
            if (v.isEmpty()) { if (required) throw Invalid("$key is empty") else return null }
            if (v.length > max) throw Invalid("$key is longer than $max characters")
            return v
        }
        fun int(key: String, min: Int, max: Int, default: Int): Int {
            val p = a[key] ?: return default
            val v = (p as? JsonPrimitive)?.intOrNull ?: throw Invalid("$key must be an integer")
            if (v < min || v > max) throw Invalid("$key must be between $min and $max")
            return v
        }
        return when (name) {
            "open_app" -> { only("app_name"); DeviceAction.OpenApp(str("app_name", 100)!!) }
            "call" -> { only("to"); DeviceAction.Call(recipientOf(str("to", 100)!!)) }
            "send_sms" -> { only("to", "text"); DeviceAction.SendSms(recipientOf(str("to", 100)!!), str("text", MAX_SMS)!!) }
            "find_contact" -> { only("name"); DeviceAction.FindContact(str("name", 100)!!) }
            "read_sms" -> { only("from", "limit"); DeviceAction.ReadSms(str("from", 100, required = false), int("limit", 1, 10, 5)) }
            "read_call_log" -> { only("limit"); DeviceAction.ReadCallLog(int("limit", 1, 10, 5)) }
            "read_calendar" -> {
                only("from_date", "days")
                val from = str("from_date", 10, required = false)?.let {
                    try { LocalDate.parse(it) } catch (e: DateTimeParseException) { throw Invalid("from_date must be YYYY-MM-DD") }
                }
                DeviceAction.ReadCalendar(from, int("days", 1, 14, 1))
            }
            "add_calendar_event" -> {
                only("title", "start", "duration_minutes", "location")
                val start = try {
                    LocalDateTime.parse(str("start", 19)!!)
                } catch (e: DateTimeParseException) {
                    throw Invalid("start must be YYYY-MM-DDTHH:MM")
                }
                DeviceAction.AddCalendarEvent(
                    str("title", 200)!!, start, int("duration_minutes", 5, 1440, 60), str("location", 200, required = false),
                )
            }
            "get_location" -> { only(); DeviceAction.GetLocation }
            "get_steps" -> { only(); DeviceAction.GetSteps }
            else -> throw Invalid("unknown action")
        }
    }

    /** The action list offered to the brain as OpenAI-compatible tools (SPEC 13.3). */
    fun tools(): JsonArray = buildJsonArray {
        add(tool("open_app", "Open an installed app on the owner's phone, by its visible name. The owner approves first.",
            props("app_name" to s("App name as shown on the phone")), "app_name"))
        add(tool("call", "Place a phone call. The owner approves first.",
            props("to" to s("Contact name or phone number")), "to"))
        add(tool("send_sms", "Send an SMS. The owner approves first and sees the full text.",
            props("to" to s("Contact name or phone number"), "text" to s("Message text, at most $MAX_SMS characters")), "to", "text"))
        add(tool("find_contact", "Look up contacts by name (names and numbers). The owner approves sending the result.",
            props("name" to s("Name or part of a name")), "name"))
        add(tool("read_sms", "Read recent received SMS. The owner approves sending them.",
            props("from" to s("Optional: only messages from this contact name or number"), "limit" to i("How many, 1 to 10 (default 5)"))))
        add(tool("read_call_log", "Read recent calls. The owner approves sending them.",
            props("limit" to i("How many, 1 to 10 (default 5)"))))
        add(tool("read_calendar", "Read calendar events. The owner approves sending them.",
            props("from_date" to s("YYYY-MM-DD, default today"), "days" to i("Number of days, 1 to 14 (default 1)"))))
        add(tool("add_calendar_event", "Add an event to the owner's calendar. The owner approves first.",
            props("title" to s("Event title"), "start" to s("Local start time, YYYY-MM-DDTHH:MM"),
                "duration_minutes" to i("Duration, 5 to 1440 (default 60)"), "location" to s("Optional place")), "title", "start"))
        add(tool("get_location", "Get the phone's current position. The owner approves sending it.", props()))
        add(tool("get_steps", "Get the step counter (steps since the phone last restarted). The owner approves sending it.", props()))
    }

    private fun s(d: String) = buildJsonObject { put("type", JsonPrimitive("string")); put("description", JsonPrimitive(d)) }
    private fun i(d: String) = buildJsonObject { put("type", JsonPrimitive("integer")); put("description", JsonPrimitive(d)) }
    private fun props(vararg p: Pair<String, JsonObject>) = JsonObject(p.toMap())

    private fun tool(name: String, description: String, properties: JsonObject, vararg required: String) = buildJsonObject {
        put("type", JsonPrimitive("function"))
        put("function", buildJsonObject {
            put("name", JsonPrimitive(name))
            put("description", JsonPrimitive(description))
            put("parameters", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", properties)
                put("required", JsonArray(required.map { JsonPrimitive(it) }))
                put("additionalProperties", JsonPrimitive(false))
            })
        })
    }
}
