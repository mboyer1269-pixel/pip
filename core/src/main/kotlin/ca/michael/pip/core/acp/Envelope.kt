package ca.michael.pip.core.acp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** One ACP message (SPEC 6.3). [raw] keeps the exact received object for signature checks. */
data class Envelope(
    val v: Int,
    val agentId: String,
    val msgId: String,
    val ts: Long,
    val nonce: String,
    val type: String,
    val body: JsonObject,
    val sig: String,
) {
    /** The envelope without `sig`: the object that is signed. */
    fun unsignedJson(): JsonObject = buildJsonObject {
        put(F_V, JsonPrimitive(v))
        put(F_AGENT, JsonPrimitive(agentId))
        put(F_MSG_ID, JsonPrimitive(msgId))
        put(F_TS, JsonPrimitive(ts))
        put(F_NONCE, JsonPrimitive(nonce))
        put(F_TYPE, JsonPrimitive(type))
        put(F_BODY, body)
    }

    fun toWire(): String = CanonicalJson.encode(
        JsonObject(unsignedJson() + (F_SIG to JsonPrimitive(sig)))
    )

    companion object {
        const val VERSION = 1
        const val AGENT_ID = "pip"
        const val MAX_FRAME_CHARS = 64 * 1024

        const val F_V = "v"
        const val F_AGENT = "agent_id"
        const val F_MSG_ID = "msg_id"
        const val F_TS = "ts"
        const val F_NONCE = "nonce"
        const val F_TYPE = "type"
        const val F_BODY = "body"
        const val F_SIG = "sig"

        val TYPES = setOf("hello", "task", "decision", "result", "status", "error")
        private val FIELDS = setOf(F_V, F_AGENT, F_MSG_ID, F_TS, F_NONCE, F_TYPE, F_BODY, F_SIG)

        /**
         * Parses one frame. Returns the envelope and the exact received object
         * minus `sig`, or throws [AcpFormatException] when the JSON is malformed,
         * a field is missing, has the wrong type, or an unknown field is present.
         */
        fun parse(frame: String): Pair<Envelope, JsonObject> {
            if (frame.length > MAX_FRAME_CHARS) throw AcpFormatException("frame too large")
            val element = try {
                Json.parseToJsonElement(frame)
            } catch (e: Exception) {
                throw AcpFormatException("not JSON")
            }
            val obj = element as? JsonObject ?: throw AcpFormatException("not an object")
            if (obj.keys != FIELDS) throw AcpFormatException("unexpected field set")

            fun str(name: String): String {
                val p = obj[name] as? JsonPrimitive ?: throw AcpFormatException("$name missing")
                if (!p.isString) throw AcpFormatException("$name not a string")
                return p.content
            }
            fun num(name: String): JsonPrimitive {
                val p = obj[name] as? JsonPrimitive ?: throw AcpFormatException("$name missing")
                if (p.isString) throw AcpFormatException("$name not a number")
                return p
            }

            val v = num(F_V).intOrNull ?: throw AcpFormatException("v not an integer")
            val ts = num(F_TS).longOrNull ?: throw AcpFormatException("ts not an integer")
            val body = obj[F_BODY] as? JsonObject ?: throw AcpFormatException("body not an object")
            val envelope = Envelope(
                v = v,
                agentId = str(F_AGENT),
                msgId = str(F_MSG_ID),
                ts = ts,
                nonce = str(F_NONCE),
                type = str(F_TYPE),
                body = body,
                sig = str(F_SIG),
            )
            return envelope to JsonObject(obj - F_SIG)
        }
    }
}

class AcpFormatException(message: String) : Exception(message)
