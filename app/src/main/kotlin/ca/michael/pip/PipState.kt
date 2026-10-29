package ca.michael.pip

import android.os.Handler
import android.os.Looper

enum class VoiceState { READY, LISTENING, THINKING, SPEAKING }
enum class AcpState { OFF, CONNECTING, CONNECTED }

/** Everything S1 and S2 show. Changes are announced on the main thread. */
class PipState {
    @Volatile var running = false
    @Volatile var voice = VoiceState.READY
    @Volatile var acp = AcpState.OFF
    /** Set when Pip stopped because of an error; shown on S1/S2. */
    @Volatile var errorReason: String? = null
    /** Intent name and localized outcome of the last finished ACP task. */
    @Volatile var lastTask: Pair<String, String>? = null
    @Volatile var orchestratorMessage: String? = null

    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) = synchronized(listeners) { listeners += l }
    fun removeListener(l: () -> Unit) = synchronized(listeners) { listeners -= l }

    fun update(change: PipState.() -> Unit) {
        change()
        val copy = synchronized(listeners) { listeners.toList() }
        main.post { copy.forEach { it() } }
    }
}
