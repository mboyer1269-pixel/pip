package ca.michael.pip.core

import ca.michael.pip.core.acp.AcpIntent
import ca.michael.pip.core.acp.AcpSigner
import ca.michael.pip.core.acp.Envelope
import ca.michael.pip.core.acp.EnvelopeFactory
import ca.michael.pip.core.acp.EnvelopeVerifier
import ca.michael.pip.core.acp.IntentCatalog
import ca.michael.pip.core.acp.Outbound
import ca.michael.pip.core.acp.RejectReason
import ca.michael.pip.core.acp.TaskBroker
import ca.michael.pip.core.acp.TaskExecutor
import ca.michael.pip.core.acp.TaskOutcome
import ca.michael.pip.core.acp.Verdict
import ca.michael.pip.core.journal.EventKind
import ca.michael.pip.core.journal.MemoryJournal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Phase 6 approval scenarios, end to end on the device side:
 * signed frame in → verify → pending → owner decision → signed reply out,
 * with the orchestrator checking every reply's signature.
 */
class AcpScenarioTest {
    private val clock = FakeClock()
    private var secret: ByteArray? = Peer.SECRET.copyOf()
    private val journal = MemoryJournal()
    private val ran = mutableListOf<AcpIntent>()
    private val wire = mutableListOf<String>()

    private val factory = EnvelopeFactory({ secret }, clock)
    private val verifier = EnvelopeVerifier({ secret }, clock)
    private val broker = TaskBroker(
        clock, journal,
        executor = TaskExecutor { ran.add(it); TaskOutcome(true, buildJsonObject { put("ok", JsonPrimitive(true)) }) },
        outbound = Outbound { type, body -> factory.create(type, body)?.let { wire.add(it.toWire()) } != null },
    )

    /** What the device does with each incoming frame (mirrors AcpConnection.onMessage). */
    private fun receive(frame: String) {
        when (val v = verifier.verify(frame)) {
            is Verdict.Accepted -> if (v.envelope.type == "task") broker.onTask(v.envelope)
            is Verdict.Rejected -> broker.onEnvelopeRejected(v.reason, v.detail)
        }
    }

    private fun task(id: String, intent: String = "device.status", args: JsonObject = JsonObject(emptyMap())) =
        Peer.frame(body = Peer.task(id, intent, args), ts = clock.now)

    /** The orchestrator's side: every reply must verify with the pairing secret. */
    private fun repliesAsPeerSeesThem(): List<Envelope> = wire.map { frame ->
        val (env, unsigned) = Envelope.parse(frame)
        assertTrue(AcpSigner.verify(unsigned, env.sig, Peer.SECRET), "reply signature verifies")
        assertEquals("pip", env.agentId)
        env
    }

    private fun outcome(id: String) = repliesAsPeerSeesThem()
        .single { it.type == "decision" && (it.body["task_id"] as JsonPrimitive).content == id }
        .body["outcome"].let { (it as JsonPrimitive).content }

    private fun kinds(id: String) = journal.all().filter { it.taskId == id }.map { it.kind }

    @Test fun approved() {
        receive(task("A"))
        assertTrue(ran.isEmpty(), "waits for the owner")
        broker.approve("A")
        assertEquals(listOf<AcpIntent>(AcpIntent.DeviceStatus), ran.toList())
        assertEquals(listOf(EventKind.RECEIVED, EventKind.PENDING, EventKind.APPROVED, EventKind.EXECUTED), kinds("A"))
        assertEquals("approved", outcome("A"))
        assertTrue(repliesAsPeerSeesThem().any { it.type == "result" && (it.body["task_id"] as JsonPrimitive).content == "A" })
    }

    @Test fun refused() {
        receive(task("R", "reminder.create", buildJsonObject { put("text", JsonPrimitive("Acheter du lait")) }))
        broker.refuse("R")
        broker.approve("R")
        assertTrue(ran.isEmpty(), "refusal runs nothing, later approval is ignored")
        assertEquals(listOf(EventKind.RECEIVED, EventKind.PENDING, EventKind.REFUSED), kinds("R"))
        assertEquals("refused", outcome("R"))
        assertTrue(repliesAsPeerSeesThem().none { it.type == "result" })
    }

    @Test fun expired() {
        receive(task("E", "message.show", buildJsonObject { put("text", JsonPrimitive("Salut")) }))
        clock.advance(TaskBroker.EXPIRY_MS)
        broker.expireDue()
        broker.approve("E")
        assertTrue(ran.isEmpty(), "expiry runs nothing, late approval is ignored")
        assertEquals(listOf(EventKind.RECEIVED, EventKind.PENDING, EventKind.EXPIRED), kinds("E"))
        assertEquals("expired", outcome("E"))
    }

    @Test fun oneDecisionPerTaskNoBatch() {
        receive(task("1")); receive(task("2"))
        broker.approve("1")
        assertEquals(1, ran.size, "approving one task leaves the other pending")
        assertEquals(listOf("2"), broker.pending().map { it.taskId })
    }

    @Test fun rePairingRotatesAndRevokeRejectsEverything() {
        val old = task("old")
        secret = AcpSigner.newSecret()               // re-pairing: new secret, old one gone
        receive(old)
        assertEquals("envelope:bad_signature:signature", journal.all().last().detail)
        secret = null                                // revoke
        receive(Peer.frame(ts = clock.now, secret = Peer.SECRET))
        assertEquals("envelope:not_paired:no pairing", journal.all().last().detail)
        assertTrue(ran.isEmpty() && broker.pending().isEmpty())
        assertTrue(wire.isEmpty(), "nothing is signed or sent without a pairing secret")
    }

    @Test fun unauthenticatedFramesGetNoReply() {
        receive(Peer.frame(ts = clock.now, secret = ByteArray(32)))
        receive(Peer.frame(ts = clock.now, agentId = "someone-else"))
        assertTrue(wire.isEmpty(), "Pip does not answer frames it could not authenticate")
        assertEquals(2, journal.all().count { it.kind == EventKind.REJECTED })
    }

    @Test fun closedListIsExactlyFourIntents() {
        assertEquals(listOf("device.status", "snapshot.last", "message.show", "reminder.create"), IntentCatalog.NAMES)
        assertIs<Verdict.Rejected>(EnvelopeVerifier({ secret }, clock).verify(Peer.frame(ts = clock.now, v = 2)))
            .also { assertEquals(RejectReason.UNSUPPORTED_VERSION, it.reason) }
    }
}
