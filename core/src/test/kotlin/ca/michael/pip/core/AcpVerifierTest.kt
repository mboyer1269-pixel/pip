package ca.michael.pip.core

import ca.michael.pip.core.acp.EnvelopeVerifier
import ca.michael.pip.core.acp.RejectReason
import ca.michael.pip.core.acp.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** T-9: bad signature, wrong agent_id, stale ts, replayed nonce are rejected. */
class AcpVerifierTest {
    private val clock = FakeClock()
    private val verifier = EnvelopeVerifier({ Peer.SECRET }, clock)

    private fun reason(frame: String) = (verifier.verify(frame) as Verdict.Rejected).reason

    @Test fun acceptsAFreshSignedFrame() {
        assertIs<Verdict.Accepted>(verifier.verify(Peer.frame(ts = clock.now)))
    }

    @Test fun rejectsBadSignature() {
        assertEquals(RejectReason.BAD_SIGNATURE, reason(Peer.frame(ts = clock.now, secret = ByteArray(32))))
        assertEquals(RejectReason.BAD_SIGNATURE, reason(Peer.frame(ts = clock.now, tamper = true)))
    }

    @Test fun rejectsWrongAgent() {
        assertEquals(RejectReason.WRONG_AGENT, reason(Peer.frame(ts = clock.now, agentId = "other")))
    }

    @Test fun rejectsStaleAndFutureTimestamps() {
        assertEquals(RejectReason.STALE, reason(Peer.frame(ts = clock.now - 120_001)))
        assertEquals(RejectReason.STALE, reason(Peer.frame(ts = clock.now + 120_001)))
        assertIs<Verdict.Accepted>(verifier.verify(Peer.frame(ts = clock.now - 120_000)))
    }

    @Test fun rejectsReplayedNonceAndMsgId() {
        val frame = Peer.frame(ts = clock.now, nonce = "n1")
        assertIs<Verdict.Accepted>(verifier.verify(frame))
        assertEquals(RejectReason.REPLAY, reason(frame))
        assertEquals(RejectReason.REPLAY, reason(Peer.frame(ts = clock.now, nonce = "n1")))
    }

    @Test fun rejectsMalformedAndUnsupportedVersion() {
        assertEquals(RejectReason.MALFORMED, reason("not json"))
        assertEquals(RejectReason.MALFORMED, reason("""{"v":1}"""))
        assertEquals(RejectReason.UNSUPPORTED_VERSION, reason(Peer.frame(ts = clock.now, v = 2)))
    }

    @Test fun rejectsEverythingWhenNotPaired() {
        val unpaired = EnvelopeVerifier({ null }, clock)
        assertEquals(RejectReason.NOT_PAIRED, (unpaired.verify(Peer.frame(ts = clock.now)) as Verdict.Rejected).reason)
    }
}
