package ca.michael.pip

import android.content.Context
import android.os.Handler
import android.os.Looper
import ca.michael.pip.acp.AcpConnection
import ca.michael.pip.acp.DeviceTaskExecutor
import ca.michael.pip.core.Clock
import ca.michael.pip.core.acp.AcpHostPolicy
import ca.michael.pip.core.acp.TaskBroker
import ca.michael.pip.core.actions.ActionCatalog
import ca.michael.pip.core.brain.BrainClient
import ca.michael.pip.core.brain.BrainConfig
import ca.michael.pip.core.brain.BrainResult
import ca.michael.pip.core.brain.BrainUrl
import ca.michael.pip.core.brain.ChatMessage
import ca.michael.pip.core.brain.TurnBuilder
import ca.michael.pip.core.brain.UrlConnectionTransport
import ca.michael.pip.core.journal.EventKind
import ca.michael.pip.journal.FileJournal
import ca.michael.pip.notebook.NotebookStore
import ca.michael.pip.security.SecretStore
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** The single owner of Pip's live parts while the app process exists. */
class PipRuntime(private val app: Context) {
    val clock: Clock = Clock.SYSTEM
    val settings = PipSettings(app)
    val secrets = SecretStore(app)
    val notebook = NotebookStore(app)
    val journal = FileJournal(app)
    val state = PipState()

    private val main = Handler(Looper.getMainLooper())
    private val transport = UrlConnectionTransport()
    private val brain = BrainClient(transport) { Network.isAvailable(app) }
    private val brainThread = Executors.newSingleThreadExecutor()
    @Volatile private var brainTask: Future<*>? = null

    /** Recent conversation of the current session (FR-4.6). Cleared on Stop. */
    private val history = mutableListOf<ChatMessage>()

    /** The last snapshot the owner captured on the device; `snapshot.last` reads only this. */
    @Volatile var lastOwnerSnapshot: String? = null

    @Volatile private var acpLink: AcpConnection? = null

    val broker = TaskBroker(
        clock = clock,
        journal = journal,
        executor = DeviceTaskExecutor(app, this),
        outbound = { type, body -> acpLink?.send(type, body) ?: false },
    )

    init {
        broker.addListener { refreshLastTask() }
    }

    fun allowlist(): List<String> = BuildConfig.ACP_ALLOWLIST.toList()

    fun brainConfigured(): Boolean =
        secrets.hasBrainKey() && BrainUrl.normalize(settings.brainBaseUrl) != null && settings.brainModel.isNotBlank()

    private fun brainConfig() = BrainConfig(settings.brainBaseUrl, settings.brainModel, secrets.brainKey() ?: "")

    /** One owner turn and its follow-up rounds with the brain (SPEC 13.3). */
    data class Turn(val words: String, val messages: List<ChatMessage>, val round: Int = 0)

    private fun localNow(): String =
        ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm, EEEE, VV", Locale.FRENCH))

    /** One brain turn. [onResult] runs on the main thread. */
    fun askBrain(words: String, snapshot: String?, excerpt: String?, onResult: (Turn, BrainResult) -> Unit) {
        val past = synchronized(history) { history.toList() }
        val actions = settings.actionsEnabled
        val messages = TurnBuilder.messages(past, words, snapshot, excerpt, actionsEnabled = actions, localNow = localNow())
        submit(Turn(words, messages), onResult)
    }

    /** Sends the owner's decisions on proposed actions back to the brain, same turn. */
    fun continueTurn(turn: Turn, assistant: ChatMessage, results: List<Pair<String, String>>, onResult: (Turn, BrainResult) -> Unit) {
        submit(Turn(turn.words, TurnBuilder.followUp(turn.messages, assistant, results), turn.round + 1), onResult)
    }

    private fun submit(turn: Turn, onResult: (Turn, BrainResult) -> Unit) {
        cancelBrain()
        // Once tool calls are in the history the tool list must stay, so it follows the turn's first choice.
        val tools = if (settings.actionsEnabled || turn.round > 0) ActionCatalog.tools() else null
        brainTask = brainThread.submit {
            val result = brain.ask(brainConfig(), turn.messages, tools)
            if (result is BrainResult.Answer) synchronized(history) {
                // History keeps the owner's words and Pip's final answer only; attachments and tool data are never resent.
                history += ChatMessage("user", turn.words)
                history += ChatMessage("assistant", result.text)
            }
            if (!Thread.currentThread().isInterrupted) main.post { onResult(turn, result) }
        }
    }

    /** S4 "Test": one tiny request with the saved settings. */
    fun testBrain(onResult: (BrainResult) -> Unit) {
        brainThread.submit {
            val result = brain.ask(brainConfig(), TurnBuilder.messages(emptyList(), "Réponds seulement : ok", null, null))
            main.post { onResult(result) }
        }
    }

    fun cancelBrain() {
        brain.cancel()
        brainTask?.cancel(true)
        brainTask = null
    }

    enum class ConnectResult { OK, NOT_RUNNING, NOT_PAIRED, HOST_REFUSED }

    /** ACP-2/3: outbound wss:// only, to a build-time listed host only. */
    fun connectAcp(): ConnectResult {
        if (!state.running) return ConnectResult.NOT_RUNNING
        if (!secrets.hasAcpSecret()) return ConnectResult.NOT_PAIRED
        val url = AcpHostPolicy.urlFor(settings.acpHost, allowlist()) ?: return ConnectResult.HOST_REFUSED
        disconnectAcp()
        acpLink = AcpConnection(this, url).also { it.open() }
        return ConnectResult.OK
    }

    fun disconnectAcp() {
        acpLink?.close()
        acpLink = null
        state.update { acp = AcpState.OFF }
    }

    /** FR-1.3: everything Stop must do, in order. */
    fun stopAll() {
        broker.cancelAll("cancelled by stop")
        disconnectAcp()
        cancelBrain()
        synchronized(history) { history.clear() }
        state.update {
            running = false
            voice = VoiceState.READY
            acp = AcpState.OFF
        }
    }

    private fun refreshLastTask() {
        val terminal = setOf(EventKind.EXECUTED, EventKind.FAILED, EventKind.REFUSED, EventKind.EXPIRED, EventKind.REJECTED, EventKind.CANCELLED)
        val last = journal.all().lastOrNull { it.kind in terminal && it.taskId != null } ?: return
        val outcome = app.getString(
            when (last.kind) {
                EventKind.EXECUTED -> R.string.ev_executed
                EventKind.FAILED -> R.string.ev_failed
                EventKind.REFUSED -> R.string.ev_refused
                EventKind.EXPIRED -> R.string.ev_expired
                EventKind.CANCELLED -> R.string.ev_cancelled
                else -> R.string.ev_received
            }
        ).let { if (last.kind == EventKind.REJECTED) app.getString(R.string.ev_rejected, last.detail ?: "") else it }
        state.update { lastTask = (last.intent ?: "?") to outcome }
    }
}
