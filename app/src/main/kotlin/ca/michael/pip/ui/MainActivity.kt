package ca.michael.pip.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import ca.michael.pip.AcpState
import ca.michael.pip.R
import ca.michael.pip.VoiceState
import ca.michael.pip.core.brain.BrainResult
import ca.michael.pip.core.notebook.ExcerptPicker
import ca.michael.pip.core.text.TextMatch
import ca.michael.pip.pip
import ca.michael.pip.screen.Capture
import ca.michael.pip.screen.PipAccessibilityService
import ca.michael.pip.service.PipNotifications
import ca.michael.pip.service.PipService
import ca.michael.pip.voice.VoiceController

/** S2 — Home. */
class MainActivity : Activity(), VoiceController.Listener {
    private lateinit var face: PipFaceView
    private lateinit var onOff: Button
    private lateinit var hint: TextView
    private lateinit var indicators: TextView
    private lateinit var orchestratorLine: TextView
    private lateinit var youLine: TextView
    private lateinit var pipLine: TextView
    private lateinit var seeSnapshot: Button
    private lateinit var talk: Button
    private lateinit var interrupt: Button
    private lateinit var input: EditText
    private lateinit var screenToggle: CheckBox
    private lateinit var screenHint: TextView
    private lateinit var voiceIo: VoiceController

    /** The snapshot attached to the last turn, shown on request (FR-3.5). */
    private var lastTurnSnapshot: String? = null
    private val onState: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceIo = VoiceController(this, pip.settings, this)
        val col = Ui.screen(this, R.string.app_name)

        face = PipFaceView(this)
        col.addView(face, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 170)))
        col.addView(Ui.text(this, getString(R.string.greeting)))
        onOff = Ui.button(this, "") { toggleRunning() }.also { col.addView(it) }
        hint = Ui.text(this, "", 14f).also { col.addView(it) }
        indicators = Ui.text(this, "", 14f).also { col.addView(it) }
        orchestratorLine = Ui.text(this, "", 15f, bold = true).also { col.addView(it) }

        col.addView(Ui.spacer(this))
        youLine = Ui.text(this, "").also { col.addView(it) }
        pipLine = Ui.text(this, "").also { col.addView(it) }
        seeSnapshot = Ui.button(this, getString(R.string.see_snapshot)) { showSnapshot() }.also { col.addView(it) }

        talk = Ui.button(this, getString(R.string.talk), Ui.Style.PRIMARY) { onTalk() }.also {
            col.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 72)))
        }
        interrupt = Ui.button(this, getString(R.string.interrupt)) { voiceIo.stopSpeaking() }.also { col.addView(it) }

        input = EditText(this).apply { setHint(R.string.type_hint) }
        col.addView(Ui.row(this, input))
        col.addView(Ui.button(this, getString(R.string.send)) {
            val words = input.text.toString().trim()
            if (words.isNotEmpty()) { input.setText(""); runTurn(words) }
        })

        screenToggle = CheckBox(this).apply { setText(R.string.screen_toggle) }.also { col.addView(it) }
        screenHint = Ui.text(this, getString(R.string.screen_toggle_off_hint), 13f).also { col.addView(it) }

        col.addView(Ui.spacer(this))
        col.addView(Ui.row(this,
            Ui.button(this, getString(R.string.nav_settings)) { open(BrainSettingsActivity::class.java) },
            Ui.button(this, getString(R.string.nav_notebook)) { open(NotebookActivity::class.java) },
        ))
        col.addView(Ui.row(this,
            Ui.button(this, getString(R.string.nav_tasks)) { open(TaskApprovalActivity::class.java) },
            Ui.button(this, getString(R.string.nav_journal)) { open(JournalActivity::class.java) },
        ))
        col.addView(Ui.row(this,
            Ui.button(this, getString(R.string.nav_acp)) { open(AcpSettingsActivity::class.java) },
            Ui.button(this, getString(R.string.nav_setup)) { open(SetupActivity::class.java) },
        ))
    }

    override fun onResume() {
        super.onResume()
        pip.state.addListener(onState)
        render()
    }

    override fun onPause() {
        pip.state.removeListener(onState)
        // The mic never stays open behind another screen.
        voiceIo.cancelListening()
        if (pip.state.voice == VoiceState.LISTENING) pip.state.update { voice = VoiceState.READY }
        super.onPause()
    }

    override fun onDestroy() {
        voiceIo.release()
        super.onDestroy()
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun render() {
        val s = pip.state
        if (!s.running) {
            voiceIo.cancelListening()
            voiceIo.stopSpeaking()
        }
        onOff.setText(if (s.running) R.string.turn_off else R.string.turn_on)
        hint.text = if (s.running) "" else getString(R.string.pip_off_hint)
        val a11y = PipAccessibilityService.isOn()
        indicators.text = listOf(
            getString(if (pip.brainConfigured()) R.string.indicator_brain_ready else R.string.indicator_brain_missing),
            getString(if (a11y) R.string.indicator_screen_on else R.string.indicator_screen_off),
            getString(if (s.acp == AcpState.CONNECTED) R.string.acp_connected else R.string.acp_off),
        ).joinToString("\n")
        orchestratorLine.text = s.orchestratorMessage?.let { getString(R.string.orchestrator_message, it) } ?: ""
        screenToggle.isEnabled = a11y && s.running
        if (!a11y) screenToggle.isChecked = false
        screenHint.visibility = if (a11y) View.GONE else View.VISIBLE
        seeSnapshot.visibility = if (lastTurnSnapshot != null) View.VISIBLE else View.GONE
        talk.isEnabled = s.running
        talk.setText(if (s.voice == VoiceState.LISTENING) R.string.talk_listening else R.string.talk)
        interrupt.visibility = if (s.voice == VoiceState.SPEAKING) View.VISIBLE else View.GONE
        face.mood = when {
            !s.running -> PipFaceView.Mood.STOPPING
            pip.broker.pending().isNotEmpty() -> PipFaceView.Mood.WAITING
            s.voice == VoiceState.LISTENING -> PipFaceView.Mood.LISTENING
            s.voice == VoiceState.THINKING -> PipFaceView.Mood.THINKING
            s.voice == VoiceState.SPEAKING -> PipFaceView.Mood.SPEAKING
            else -> PipFaceView.Mood.READY
        }
    }

    // --- Turning Pip on and off (FR-1.1, FR-1.4) ---

    private fun toggleRunning() {
        if (pip.state.running) { PipService.stop(this); return }
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
            return
        }
        startPip()
    }

    private fun startPip() {
        if (!PipNotifications(this).enabled()) {
            hint.setText(R.string.notif_denied_explain)
            open(SetupActivity::class.java)
            return
        }
        PipService.start(this)
    }

    // --- Talking (FR-2.1) ---

    private fun onTalk() {
        if (pip.state.voice == VoiceState.LISTENING) { voiceIo.stopListening(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        voiceIo.stopSpeaking()
        voiceIo.startListening()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        when (requestCode) {
            REQ_MIC -> if (granted) voiceIo.startListening() else pipLine.setText(R.string.err_mic_refused)
            REQ_NOTIFICATIONS -> if (granted) startPip() else {
                hint.setText(R.string.notif_denied_explain)
                open(SetupActivity::class.java)
            }
        }
    }

    override fun onListening() = pip.state.update { voice = VoiceState.LISTENING }

    override fun onHeard(text: String) {
        pip.state.update { voice = VoiceState.READY }
        runTurn(text)
    }

    override fun onVoiceError(messageRes: Int) {
        pip.state.update { voice = VoiceState.READY }
        pipLine.text = getString(R.string.pip_label) + " : " + getString(messageRes)
    }

    override fun onSpeaking() = pip.state.update { voice = VoiceState.SPEAKING }
    override fun onSpeakingDone() = pip.state.update { voice = VoiceState.READY }

    // --- One turn (FR-4.6) ---

    private fun runTurn(words: String) {
        if (!pip.state.running) { hint.setText(R.string.pip_off_hint); return }
        youLine.text = getString(R.string.you_label) + " : " + words
        pipLine.text = ""

        // Screen context only when asked on this turn (FR-3.1).
        var snapshot: String? = null
        if (screenToggle.isChecked || TextMatch.asksForScreen(words)) {
            when (val c = PipAccessibilityService.capture()) {
                is Capture.Ok -> {
                    snapshot = c.text
                    pip.lastOwnerSnapshot = c.text
                }
                Capture.ServiceOff -> pipLine.setText(R.string.err_screen_off)
                Capture.NoOtherApp -> pipLine.setText(R.string.err_no_other_app)
            }
            screenToggle.isChecked = false
        }
        lastTurnSnapshot = snapshot
        render()

        // Notebook excerpt only with per-turn approval (FR-5.5, S9).
        val excerpt = try { ExcerptPicker.propose(words, pip.notebook.all()) } catch (e: Exception) { null }
        if (excerpt == null) {
            send(words, snapshot, null)
            return
        }
        val body = Ui.text(this, getString(R.string.excerpt_body) + "\n\n" + excerpt, 15f).apply {
            val p = Ui.dp(this@MainActivity, 20); setPadding(p, p, p, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.excerpt_header)
            .setView(body)
            .setCancelable(false)
            .setPositiveButton(R.string.send_once) { _, _ -> send(words, snapshot, excerpt) }
            .setNegativeButton(R.string.dont_send) { _, _ ->
                pipLine.setText(R.string.excerpt_refused)
                send(words, snapshot, null)
            }
            .show()
    }

    private fun send(words: String, snapshot: String?, excerpt: String?) {
        pip.state.update { voice = VoiceState.THINKING }
        pip.askBrain(words, snapshot, excerpt) { result ->
            if (result is BrainResult.Answer) {
                pipLine.text = getString(R.string.pip_label) + " : " + result.text
                if (pip.settings.speakAnswers && pip.state.running) {
                    pip.state.update { voice = VoiceState.SPEAKING }
                    voiceIo.speak(result.text, TextMatch.looksEnglish(words))
                } else {
                    pip.state.update { voice = VoiceState.READY }
                }
                return@askBrain
            }
            pip.state.update { voice = VoiceState.READY }
            errorText(result)?.let { pipLine.text = getString(R.string.pip_label) + " : " + it }
        }
    }

    /** FR-4.4 / FR-4.5: each failure in its own honest words. */
    private fun errorText(result: BrainResult): String? = when (result) {
        BrainResult.NoKey -> getString(R.string.err_no_key)
        BrainResult.NotConfigured -> getString(R.string.err_not_configured)
        BrainResult.NoNetwork -> getString(R.string.err_no_network)
        BrainResult.Unreachable -> getString(R.string.err_unreachable)
        is BrainResult.KeyRefused -> getString(R.string.err_key_refused)
        is BrainResult.ServerError -> result.detail?.let { getString(R.string.err_server_detail, result.code, it) }
                ?: getString(R.string.err_server, result.code)
        BrainResult.Timeout -> getString(R.string.err_timeout)
        BrainResult.Malformed -> getString(R.string.err_malformed)
        BrainResult.Cancelled -> null
        is BrainResult.Answer -> result.text
    }

    private fun showSnapshot() {
        val text = lastTurnSnapshot ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.snapshot_title)
            .setMessage(text)
            .setPositiveButton(R.string.done, null)
            .show()
    }

    companion object {
        private const val REQ_MIC = 1
        private const val REQ_NOTIFICATIONS = 2
    }
}
