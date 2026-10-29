package ca.michael.pip.core.acp

import ca.michael.pip.core.Clock
import ca.michael.pip.core.journal.EventKind
import ca.michael.pip.core.journal.Journal
import ca.michael.pip.core.journal.JournalEvent
import java.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A task waiting for the owner's decision on the device (ACP-5). */
data class PendingTask(
    val taskId: String,
    val intent: AcpIntent,
    val args: JsonObject,
    val summary: String,
    val receivedAtMs: Long,
)

/** What running an approved intent produced. */
data class TaskOutcome(val ok: Boolean, val data: JsonObject)

/** Runs one approved intent on the device. Only [TaskBroker.approve] calls it. */
fun interface TaskExecutor {
    fun execute(intent: AcpIntent): TaskOutcome
}

/** Sends one signed envelope to the orchestrator. Returns false when nothing could be sent. */
fun interface Outbound {
    fun send(type: String, body: JsonObject): Boolean
}

/**
 * Per-task approval (SPEC 7.3). Each task is pending until the owner approves
 * or refuses it, or until [expiryMs] passes. There is no global approval and
 * no batch decision: every call names exactly one task.
 */
class TaskBroker(
    private val clock: Clock,
    private val journal: Journal,
    private val executor: TaskExecutor,
    private val outbound: Outbound,
    private val expiryMs: Long = EXPIRY_MS,
) {
    private val pending = LinkedHashMap<String, PendingTask>()
    private val decided = HashSet<String>()
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) = synchronized(this) { listeners += l }
    fun removeListener(l: () -> Unit) = synchronized(this) { listeners -= l }

    @Synchronized
    fun pending(): List<PendingTask> = pending.values.toList()

    /** Milliseconds left before [task] expires, never negative. */
    fun timeLeftMs(task: PendingTask): Long =
        (task.receivedAtMs + expiryMs - clock.nowMs()).coerceAtLeast(0)

    /** An incoming envelope failed verification: journal it, run nothing. */
    fun onEnvelopeRejected(reason: RejectReason, detail: String) {
        log(EventKind.REJECTED, null, null, "envelope:${reason.name.lowercase()}:$detail")
        notifyListeners()
    }

    /** A verified envelope of type `task` arrived. */
    fun onTask(envelope: Envelope) {
        val body = envelope.body
        val taskId = (body["task_id"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        val intentName = (body["intent"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val args = body["args"] as? JsonObject
        val summary = (body["summary"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: ""

        log(EventKind.RECEIVED, taskId, intentName, null)
        if (taskId == null || intentName == null || args == null || body.keys != setOf("task_id", "intent", "args", "summary")) {
            reject(taskId, intentName, "malformed body")
            return
        }
        synchronized(this) {
            if (taskId in pending || taskId in decided) {
                reject(taskId, intentName, "duplicate task_id")
                return
            }
        }
        when (val parsed = IntentCatalog.parse(intentName, args)) {
            is IntentParse.Ok -> {
                synchronized(this) {
                    pending[taskId] = PendingTask(taskId, parsed.intent, args, summary.take(500), clock.nowMs())
                }
                log(EventKind.PENDING, taskId, intentName, null)
            }
            IntentParse.Unknown -> reject(taskId, intentName, "unknown intent")
            IntentParse.Forbidden -> reject(taskId, intentName, "forbidden intent")
            is IntentParse.BadArgs -> reject(taskId, intentName, "bad args: ${parsed.why}")
        }
        notifyListeners()
    }

    /** The owner tapped Approve for this one task. Runs it at most once. */
    fun approve(taskId: String): Boolean {
        expireDue()
        val task = take(taskId) ?: return false
        log(EventKind.APPROVED, task.taskId, task.intent.name, null)
        decision(task.taskId, "approved", "owner approved")
        val outcome = try {
            executor.execute(task.intent)
        } catch (e: Exception) {
            TaskOutcome(false, buildJsonObject { put("error", JsonPrimitive(e.javaClass.simpleName)) })
        }
        log(if (outcome.ok) EventKind.EXECUTED else EventKind.FAILED, task.taskId, task.intent.name, null)
        outbound.send("result", buildJsonObject {
            put("task_id", JsonPrimitive(task.taskId))
            put("ok", JsonPrimitive(outcome.ok))
            put("data", outcome.data)
        })
        notifyListeners()
        return true
    }

    /** The owner tapped Refuse. Nothing runs. */
    fun refuse(taskId: String): Boolean {
        val task = take(taskId) ?: return false
        log(EventKind.REFUSED, task.taskId, task.intent.name, null)
        decision(task.taskId, "refused", "owner refused")
        notifyListeners()
        return true
    }

    /** Moves every task older than [expiryMs] to expired. Nothing runs. */
    fun expireDue() {
        val now = clock.nowMs()
        val expired = synchronized(this) {
            pending.values.filter { now - it.receivedAtMs >= expiryMs }.onEach { pending.remove(it.taskId); decided += it.taskId }
        }
        for (task in expired) {
            log(EventKind.EXPIRED, task.taskId, task.intent.name, null)
            decision(task.taskId, "expired", "no answer within 5 minutes")
        }
        if (expired.isNotEmpty()) notifyListeners()
    }

    /** Pip is stopping: every pending task is cancelled, nothing runs (FR-1.3). */
    fun cancelAll(reason: String) {
        val all = synchronized(this) { pending.values.toList().also { pending.clear(); it.forEach { t -> decided += t.taskId } } }
        for (task in all) {
            log(EventKind.CANCELLED, task.taskId, task.intent.name, reason)
            decision(task.taskId, "cancelled", reason)
        }
        if (all.isNotEmpty()) notifyListeners()
    }

    private fun take(taskId: String): PendingTask? = synchronized(this) {
        pending.remove(taskId)?.also { decided += it.taskId }
    }

    private fun reject(taskId: String?, intent: String?, why: String) {
        log(EventKind.REJECTED, taskId, intent, why)
        if (taskId != null) decision(taskId, "rejected", why)
        notifyListeners()
    }

    private fun decision(taskId: String, outcome: String, reason: String) {
        outbound.send("decision", buildJsonObject {
            put("task_id", JsonPrimitive(taskId))
            put("outcome", JsonPrimitive(outcome))
            put("reason", JsonPrimitive(reason))
        })
    }

    private fun log(kind: EventKind, taskId: String?, intent: String?, detail: String?) {
        journal.append(JournalEvent(Instant.ofEpochMilli(clock.nowMs()), kind, taskId, intent, detail))
    }

    private fun notifyListeners() {
        val copy = synchronized(this) { listeners.toList() }
        copy.forEach { it() }
    }

    companion object {
        /** ACP-8: no answer within 5 minutes of receipt means expired. */
        const val EXPIRY_MS = 5 * 60 * 1000L
    }
}
