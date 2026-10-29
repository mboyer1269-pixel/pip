package ca.michael.pip.core.acp

import ca.michael.pip.core.Clock
import kotlin.math.abs

enum class RejectReason { MALFORMED, UNSUPPORTED_VERSION, NOT_PAIRED, BAD_SIGNATURE, WRONG_AGENT, STALE, REPLAY }

sealed interface Verdict {
    data class Accepted(val envelope: Envelope) : Verdict
    data class Rejected(val reason: RejectReason, val detail: String) : Verdict
}

/**
 * Checks every incoming frame before anything reads its body (ACP-12).
 * Order: format, version, pairing, signature, agent id, freshness, replay.
 * Signature comes before the other field checks so unsigned frames learn nothing.
 */
class EnvelopeVerifier(
    private val secret: () -> ByteArray?,
    private val clock: Clock,
    private val windowMs: Long = FRESHNESS_WINDOW_MS,
) {
    private val seenNonces = HashMap<String, Long>()
    private val seenMsgIds = HashMap<String, Long>()

    @Synchronized
    fun verify(frame: String): Verdict {
        val (envelope, unsigned) = try {
            Envelope.parse(frame)
        } catch (e: AcpFormatException) {
            return Verdict.Rejected(RejectReason.MALFORMED, e.message ?: "malformed")
        }
        if (envelope.v != Envelope.VERSION) return Verdict.Rejected(RejectReason.UNSUPPORTED_VERSION, "v=${envelope.v}")
        val key = secret() ?: return Verdict.Rejected(RejectReason.NOT_PAIRED, "no pairing")
        if (!AcpSigner.verify(unsigned, envelope.sig, key)) return Verdict.Rejected(RejectReason.BAD_SIGNATURE, "signature")
        if (envelope.agentId != Envelope.AGENT_ID) return Verdict.Rejected(RejectReason.WRONG_AGENT, "agent_id")

        val now = clock.nowMs()
        if (abs(now - envelope.ts) > windowMs) return Verdict.Rejected(RejectReason.STALE, "ts")

        prune(now)
        if (envelope.nonce in seenNonces || envelope.msgId in seenMsgIds) {
            return Verdict.Rejected(RejectReason.REPLAY, "seen")
        }
        // Keep each entry until the frame could no longer pass the freshness check.
        val keepUntil = envelope.ts + windowMs
        seenNonces[envelope.nonce] = keepUntil
        seenMsgIds[envelope.msgId] = keepUntil
        return Verdict.Accepted(envelope)
    }

    private fun prune(now: Long) {
        seenNonces.values.removeIf { it < now }
        seenMsgIds.values.removeIf { it < now }
    }

    companion object {
        /** 120 seconds either way, confirmed by the owner (SPEC Q-3). */
        const val FRESHNESS_WINDOW_MS = 120_000L
    }
}
