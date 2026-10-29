package ca.michael.pip.core

import ca.michael.pip.core.acp.AcpSigner
import ca.michael.pip.core.acp.Envelope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class FakeClock(var now: Long = 1_800_000_000_000L) : Clock {
    override fun nowMs() = now
    fun advance(ms: Long) { now += ms }
}

/** Plays the orchestrator side: builds frames the way a peer would. */
object Peer {
    val SECRET = ByteArray(32) { (it * 7 + 3).toByte() }

    fun frame(
        type: String = "task",
        body: JsonObject = task("t-1", "device.status", JsonObject(emptyMap())),
        ts: Long,
        agentId: String = "pip",
        nonce: String = "nonce-" + java.util.UUID.randomUUID(),
        msgId: String = java.util.UUID.randomUUID().toString(),
        v: Int = 1,
        secret: ByteArray = SECRET,
        tamper: Boolean = false,
    ): String {
        val draft = Envelope(v, agentId, msgId, ts, nonce, type, body, "")
        val sig = AcpSigner.sign(draft.unsignedJson(), secret)
        val signed = draft.copy(sig = sig, body = if (tamper) task("t-evil", "device.status", JsonObject(emptyMap())) else body)
        return signed.toWire()
    }

    fun task(id: String, intent: String, args: JsonObject, summary: String = "s") = buildJsonObject {
        put("task_id", JsonPrimitive(id))
        put("intent", JsonPrimitive(intent))
        put("args", args)
        put("summary", JsonPrimitive(summary))
    }
}
