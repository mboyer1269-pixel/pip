package ca.michael.pip.core

import ca.michael.pip.core.acp.AcpIntent
import ca.michael.pip.core.acp.Envelope
import ca.michael.pip.core.acp.TaskBroker
import ca.michael.pip.core.acp.Outbound
import ca.michael.pip.core.acp.TaskExecutor
import ca.michael.pip.core.acp.TaskOutcome
import ca.michael.pip.core.journal.EventKind
import ca.michael.pip.core.journal.MemoryJournal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** T-6, T-7, T-8, T-10. */
class TaskBrokerTest {
    private val clock = FakeClock()
    private val journal = MemoryJournal()
    private val executed = mutableListOf<AcpIntent>()
    private val sent = mutableListOf<Pair<String, JsonObject>>()
    private val broker = TaskBroker(
        clock, journal,
        executor = TaskExecutor { executed.add(it); TaskOutcome(true, JsonObject(emptyMap())) },
        outbound = Outbound { type, body -> sent.add(type to body) },
    )

    private fun envelope(id: String, intent: String, args: JsonObject = JsonObject(emptyMap())) =
        Envelope(1, "pip", "m-$id", clock.now, "n-$id", "task", Peer.task(id, intent, args), "sig")

    private fun kinds(id: String) = journal.all().filter { it.taskId == id }.map { it.kind }
    private fun decisionOutcome(id: String) = sent.single { it.first == "decision" && (it.second["task_id"] as JsonPrimitive).content == id }
        .second["outcome"].let { (it as JsonPrimitive).content }

    @Test fun approvedTaskRunsExactlyOnce() {
        broker.onTask(envelope("a", "device.status"))
        assertTrue(executed.isEmpty(), "nothing runs before approval")
        assertTrue(broker.approve("a"))
        assertFalse(broker.approve("a"), "a second approval does nothing")
        assertEquals(listOf<AcpIntent>(AcpIntent.DeviceStatus), executed.toList())
        assertEquals(listOf(EventKind.RECEIVED, EventKind.PENDING, EventKind.APPROVED, EventKind.EXECUTED), kinds("a"))
        assertEquals("approved", decisionOutcome("a"))
        assertTrue(sent.any { it.first == "result" })
    }

    @Test fun refusedTaskRunsNothing() {
        broker.onTask(envelope("r", "device.status"))
        assertTrue(broker.refuse("r"))
        assertFalse(broker.approve("r"))
        assertTrue(executed.isEmpty())
        assertEquals(listOf(EventKind.RECEIVED, EventKind.PENDING, EventKind.REFUSED), kinds("r"))
        assertEquals("refused", decisionOutcome("r"))
    }

    @Test fun taskExpiresAtFiveMinutes() {
        broker.onTask(envelope("e", "device.status"))
        clock.advance(5 * 60 * 1000L - 1)
        broker.expireDue()
        assertEquals(1, broker.pending().size, "still pending one millisecond before expiry")
        clock.advance(1)
        assertFalse(broker.approve("e"), "approval at 5 minutes is too late")
        assertTrue(executed.isEmpty())
        assertEquals(listOf(EventKind.RECEIVED, EventKind.PENDING, EventKind.EXPIRED), kinds("e"))
        assertEquals("expired", decisionOutcome("e"))
    }

    @Test fun unknownAndForbiddenIntentsAreRejected() {
        broker.onTask(envelope("u", "calendar.read"))
        broker.onTask(envelope("s", "shell.exec", buildJsonObject { put("cmd", JsonPrimitive("ls")) }))
        broker.onTask(envelope("i", "android.intent", buildJsonObject { put("action", JsonPrimitive("x")) }))
        broker.onTask(envelope("c", "camera.capture"))
        broker.onTask(envelope("m", "sms.send"))
        assertTrue(broker.pending().isEmpty())
        assertTrue(executed.isEmpty())
        assertEquals("unknown intent", journal.all().single { it.taskId == "u" && it.kind == EventKind.REJECTED }.detail)
        for (id in listOf("s", "i", "c", "m")) {
            assertEquals("forbidden intent", journal.all().single { it.taskId == id && it.kind == EventKind.REJECTED }.detail)
            assertEquals("rejected", decisionOutcome(id))
        }
    }

    @Test fun badArgumentsAreRejected() {
        broker.onTask(envelope("long", "message.show", buildJsonObject { put("text", JsonPrimitive("x".repeat(501))) }))
        broker.onTask(envelope("extra", "device.status", buildJsonObject { put("x", JsonPrimitive(1)) }))
        broker.onTask(envelope("date", "reminder.create", buildJsonObject {
            put("text", JsonPrimitive("t")); put("due_at", JsonPrimitive("tomorrow"))
        }))
        assertTrue(broker.pending().isEmpty())
    }

    @Test fun stopCancelsEveryPendingTask() {
        broker.onTask(envelope("p1", "device.status"))
        broker.onTask(envelope("p2", "snapshot.last"))
        broker.cancelAll("stopped")
        assertTrue(broker.pending().isEmpty())
        assertTrue(executed.isEmpty())
        assertEquals(EventKind.CANCELLED, kinds("p1").last())
        assertEquals("cancelled", decisionOutcome("p2"))
    }

    @Test fun duplicateTaskIdIsRejected() {
        broker.onTask(envelope("d", "device.status"))
        broker.onTask(envelope("d", "device.status"))
        assertEquals(1, broker.pending().size)
        assertEquals(EventKind.REJECTED, kinds("d").last())
    }
}
