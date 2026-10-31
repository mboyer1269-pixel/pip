package ca.michael.pip.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import ca.michael.pip.AcpState
import ca.michael.pip.PipRuntime
import ca.michael.pip.R
import ca.michael.pip.actions.DeviceActionRunner
import ca.michael.pip.core.actions.ActionCatalog
import ca.michael.pip.core.actions.ActionParse
import ca.michael.pip.core.actions.DeviceAction
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
    private lateinit var statePill: TextView
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
    private lateinit var actions: DeviceActionRunner
    private var turnGeneration = 0
    private var openCard: AlertDialog? = null
    private var permissionCallback: ((Boolean) -> Unit)? = null

    /** The snapshot attached to the last turn, shown on request (FR-3.5). */
    private var lastTurnSnapshot: String? = null
    /** Set when a gesture asked to talk while Pip was still starting (push to talk). */
    private var pendingTalk = false
    /** Screen text read by a bubble long press, attached to the next turn only. */
    private var pendingSnapshot: String? = null
    /** Pip's face winks until this time (fun pack: success, tap on the face). */
    private var winkUntil = 0L
    /** Pip laughs after a tap on the face, and waves hello when the screen opens. */
    private var tickleUntil = 0L
    private var helloUntil = 0L
    private var oopsUntil = 0L

    private val onState: () -> Unit = {
        render()
        if (pendingTalk && pip.state.running) {
            pendingTalk = false
            onTalk()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceIo = VoiceController(this, pip.settings, this)
        actions = DeviceActionRunner(this)
        val col = Ui.screen(this, R.string.app_name)
        val cream = getColor(R.color.pip_cream)

        // Header: name, on/off state, settings.
        val title = Ui.text(this, getString(R.string.app_name), 22f, bold = true).apply { setTextColor(getColor(R.color.pip_indigo)) }
        statePill = Ui.pill(this, "", R.color.pip_card, R.color.pip_ink)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title)
            addView(statePill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(context, 10) })
            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
            addView(Ui.iconButton(context, R.drawable.ic_settings, getString(R.string.nav_settings), R.color.pip_card, R.color.pip_indigo) {
                open(BrainSettingsActivity::class.java)
            }, LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)))
        }
        col.addView(header)

        // Hero card: Pip, the greeting, on/off and the indicators.
        val hero = Ui.card(this, R.color.pip_indigo, 28, 20).apply { gravity = Gravity.CENTER_HORIZONTAL }
        face = PipFaceView(this).apply {
            contentDescription = getString(R.string.face_desc)
            setOnClickListener { onFaceTapped() }
        }
        hero.addView(face, Ui.full(this, heightDp = 190))
        hero.addView(Ui.text(this, Fun.greeting(this), 18f).apply { setTextColor(cream); gravity = Gravity.CENTER })
        onOff = Ui.button(this, "", Ui.Style.PRIMARY) { toggleRunning() }
        hero.addView(onOff, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ui.dp(this@MainActivity, 8) })
        hint = Ui.text(this, "", 14f).apply { setTextColor(cream); gravity = Gravity.CENTER }.also { hero.addView(it) }
        indicators = Ui.text(this, "", 12f).apply { setTextColor(getColor(R.color.pip_cream)); alpha = 0.85f; gravity = Gravity.CENTER }.also { hero.addView(it) }
        orchestratorLine = Ui.text(this, "", 15f, bold = true).apply { setTextColor(cream) }.also { hero.addView(it) }
        col.addView(hero, Ui.full(this, 12))

        // Conversation card.
        val talkCard = Ui.card(this)
        youLine = Ui.text(this, "", 14f).apply { setTextColor(getColor(R.color.pip_muted)) }.also { talkCard.addView(it) }
        pipLine = Ui.text(this, "").also { talkCard.addView(it) }
        seeSnapshot = Ui.button(this, getString(R.string.see_snapshot)) { showSnapshot() }.also { talkCard.addView(it, Ui.full(this, 8)) }
        interrupt = Ui.button(this, getString(R.string.interrupt)) { voiceIo.stopSpeaking() }.also { talkCard.addView(it, Ui.full(this, 8)) }
        col.addView(talkCard, Ui.full(this, 14))

        // Suggestion chips: each one only starts a normal turn (actions still go through S10).
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (res in Fun.CHIPS) {
            chips.addView(Ui.button(this, getString(res)) { runTurn(getString(res)) }.apply {
                background = Ui.touchable(context, Ui.shape(context, R.color.pip_card, 999, R.color.pip_chip_line, 2))
                setTypeface(typeface, android.graphics.Typeface.NORMAL)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(this@MainActivity, 8) })
        }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }, Ui.full(this, 14))

        // Type a message.
        input = EditText(this).apply {
            setHint(R.string.type_hint)
            background = Ui.shape(context, R.color.pip_card, 16, R.color.pip_line, 2)
            setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0)
            minHeight = Ui.dp(context, 48)
        }
        val send = Ui.iconButton(this, R.drawable.ic_send, getString(R.string.send), R.color.pip_indigo, R.color.pip_cream) {
            val words = input.text.toString().trim()
            if (words.isNotEmpty()) { input.setText(""); runTurn(words) }
        }
        col.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(input, LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1f))
            addView(send, LinearLayout.LayoutParams(Ui.dp(context, 48), Ui.dp(context, 48)).apply { marginStart = Ui.dp(context, 8) })
        }, Ui.full(this, 14))

        screenToggle = CheckBox(this).apply { setText(R.string.screen_toggle) }.also { col.addView(it, Ui.full(this, 6)) }
        screenHint = Ui.text(this, getString(R.string.screen_toggle_off_hint), 13f).apply { setTextColor(getColor(R.color.pip_muted)) }.also { col.addView(it) }

        // Talk: the big pocket-shaped button (BRAND.md 5.6).
        talk = Ui.button(this, getString(R.string.talk), Ui.Style.PRIMARY) { onTalk() }.apply {
            textSize = 20f
            background = Ui.touchable(context, GradientDrawable().apply {
                setColor(getColor(R.color.pip_clementine))
                val t = Ui.dp(context, 26).toFloat(); val b = Ui.dp(context, 36).toFloat()
                cornerRadii = floatArrayOf(t, t, t, t, b, b, b, b)
            })
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_mic, 0, 0, 0)
            compoundDrawableTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.pip_ink))
            compoundDrawablePadding = Ui.dp(context, 10)
            elevation = Ui.dp(context, 4).toFloat()
        }
        col.addView(talk, Ui.full(this, 12, 72))

        // Other screens.
        col.addView(Ui.row(this,
            Ui.button(this, getString(R.string.nav_notebook)) { open(NotebookActivity::class.java) },
            Ui.button(this, getString(R.string.nav_tasks)) { open(TaskApprovalActivity::class.java) },
            Ui.button(this, getString(R.string.nav_journal)) { open(JournalActivity::class.java) },
        ), Ui.full(this, 16))
        col.addView(Ui.row(this,
            Ui.button(this, getString(R.string.nav_acp)) { open(AcpSettingsActivity::class.java) },
            Ui.button(this, getString(R.string.nav_setup)) { open(SetupActivity::class.java) },
        ), Ui.full(this, 8))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        pip.state.addListener(onState)
        helloUntil = System.currentTimeMillis() + 2_000
        face.postDelayed({ render() }, 2_050)
        render()
        consumeGesture()
    }

    /**
     * Push to talk (SPEC 16): the bubble, the S1 « Parler » action and the
     * system assistant gesture all land here. The gesture is the owner's
     * explicit request, so Pip turns on if needed, then listens.
     */
    private fun consumeGesture() {
        val assist = intent.action == Intent.ACTION_ASSIST
        val talk = intent.getBooleanExtra(EXTRA_TALK, false)
        if (!assist && !talk) return
        setIntent(Intent(intent).setAction(null).apply { removeExtra(EXTRA_TALK) })

        // A long press on the bubble may have read the screen for this turn (FR-B.6).
        when (val look = pip.bubbleLook.also { pip.bubbleLook = null }) {
            is Capture.Ok -> {
                pendingSnapshot = look.text
                pip.lastOwnerSnapshot = look.text
                lastTurnSnapshot = look.text
                pipLine.setText(R.string.looked_ready)
            }
            Capture.ServiceOff -> pipLine.setText(R.string.err_screen_off)
            Capture.NoOtherApp -> pipLine.setText(R.string.err_no_other_app)
            null -> {}
        }
        if (pip.state.running) onTalk() else { pendingTalk = true; toggleRunning() }
    }

    override fun onPause() {
        pip.state.removeListener(onState)
        // The mic never stays open behind another screen.
        voiceIo.cancelListening()
        if (pip.state.voice == VoiceState.LISTENING) pip.state.update { voice = VoiceState.READY }
        super.onPause()
    }

    override fun onDestroy() {
        // Nothing may come back to a destroyed screen: drop the turn and any open card.
        turnGeneration++
        openCard?.dismiss()
        openCard = null
        pip.cancelBrain()
        voiceIo.release()
        super.onDestroy()
    }

    private fun open(cls: Class<*>) = startActivity(Intent(this, cls))

    private fun render() {
        val s = pip.state
        if (!s.running) {
            voiceIo.cancelListening()
            voiceIo.stopSpeaking()
            // FR-1.3: Stop also closes any S10 card; nothing approved later can run.
            if (openCard != null) {
                turnGeneration++
                openCard?.dismiss()
                openCard = null
            }
        }
        onOff.setText(if (s.running) R.string.turn_off else R.string.turn_on)
        statePill.setText(if (s.running) R.string.state_on_pill else R.string.state_off_pill)
        hint.text = if (s.running) "" else getString(R.string.pip_off_hint)
        val a11y = PipAccessibilityService.isOn()
        indicators.text = listOf(
            getString(if (pip.brainConfigured()) R.string.indicator_brain_ready else R.string.indicator_brain_missing),
            getString(if (a11y) R.string.indicator_screen_on else R.string.indicator_screen_off),
            getString(if (s.acp == AcpState.CONNECTED) R.string.acp_connected else R.string.acp_off),
        ).joinToString("  ·  ")
        orchestratorLine.text = s.orchestratorMessage?.let { getString(R.string.orchestrator_message, it) } ?: ""
        screenToggle.isEnabled = a11y && s.running
        if (!a11y) screenToggle.isChecked = false
        screenHint.visibility = if (a11y) View.GONE else View.VISIBLE
        seeSnapshot.visibility = if (lastTurnSnapshot != null) View.VISIBLE else View.GONE
        talk.isEnabled = s.running
        talk.setText(if (s.voice == VoiceState.LISTENING) R.string.talk_listening else R.string.talk)
        interrupt.visibility = if (s.voice == VoiceState.SPEAKING) View.VISIBLE else View.GONE
        val now = System.currentTimeMillis()
        face.mood = when {
            now < tickleUntil -> PipFaceView.Mood.TICKLED
            now < winkUntil -> PipFaceView.Mood.SUCCESS
            !s.running -> PipFaceView.Mood.SLEEP
            now < helloUntil -> PipFaceView.Mood.HELLO
            pip.broker.pending().isNotEmpty() -> PipFaceView.Mood.WAITING
            s.voice == VoiceState.LISTENING -> PipFaceView.Mood.LISTENING
            s.voice == VoiceState.THINKING -> PipFaceView.Mood.THINKING
            s.voice == VoiceState.SPEAKING -> PipFaceView.Mood.SPEAKING
            now < oopsUntil -> PipFaceView.Mood.ERROR
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
            REQ_ACTION -> {
                val callback = permissionCallback
                permissionCallback = null
                val ok = permissions.isNotEmpty() && permissions.indices.all {
                    grantResults.getOrNull(it) == PackageManager.PERMISSION_GRANTED ||
                        permissions[it] == Manifest.permission.ACCESS_FINE_LOCATION // approximate position is enough
                }
                callback?.invoke(ok)
            }
            REQ_MIC -> if (granted) voiceIo.startListening() else pipLine.setText(R.string.err_mic_refused)
            REQ_NOTIFICATIONS -> if (granted) startPip() else {
                pendingTalk = false
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
        oops()
    }

    override fun onSpeaking() = pip.state.update { voice = VoiceState.SPEAKING }
    override fun onSpeakingDone() = pip.state.update { voice = VoiceState.READY }

    // --- One turn (FR-4.6) ---

    private fun runTurn(words: String) {
        if (!pip.state.running) { hint.setText(R.string.pip_off_hint); return }
        // FR-A.6: a new turn cancels any action card still waiting.
        turnGeneration++
        openCard?.dismiss()
        openCard = null
        youLine.text = getString(R.string.you_label) + " : " + words
        pipLine.text = ""

        // Screen context only when asked on this turn (FR-3.1).
        var snapshot: String? = pendingSnapshot
        pendingSnapshot = null
        if (snapshot == null && (screenToggle.isChecked || TextMatch.asksForScreen(words))) {
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
        val generation = turnGeneration
        pip.askBrain(words, snapshot, excerpt) { turn, result -> onBrain(turn, result, generation) }
    }

    private fun onBrain(turn: PipRuntime.Turn, result: BrainResult, generation: Int) {
        // A result for an older turn is dropped (FR-A.6).
        if (generation != turnGeneration || isDestroyed) return
        when (result) {
            is BrainResult.Answer -> speakAnswer(turn.words, result.text)
            is BrainResult.ActionRequest -> {
                pip.state.update { voice = VoiceState.READY }
                result.text?.let { pipLine.text = getString(R.string.pip_label) + " : " + it }
                // FR-A.9: with actions off, proposals are ignored even if a server sends them anyway.
                if (!pip.settings.actionsEnabled && turn.round == 0) return
                if (turn.round >= ActionCatalog.MAX_ROUNDS) {
                    // FR-A.6: no more rounds for this turn.
                    if (result.text == null) pipLine.setText(R.string.act_round_limit)
                    return
                }
                handleActions(turn, result, generation)
            }
            else -> {
                pip.state.update { voice = VoiceState.READY }
                errorText(result)?.let { pipLine.text = getString(R.string.pip_label) + " : " + it }
                if (result !is BrainResult.Answer && result !is BrainResult.ActionRequest && result != BrainResult.Cancelled) oops()
            }
        }
    }

    private fun speakAnswer(words: String, text: String) {
        pipLine.text = getString(R.string.pip_label) + " : " + text
        if (pip.settings.speakAnswers && pip.state.running) {
            pip.state.update { voice = VoiceState.SPEAKING }
            voiceIo.speak(text, TextMatch.looksEnglish(words))
        } else {
            pip.state.update { voice = VoiceState.READY }
        }
    }

    // --- Block A: proposed actions, one S10 card each (SPEC 13) ---

    private fun handleActions(turn: PipRuntime.Turn, request: BrainResult.ActionRequest, generation: Int) {
        val results = mutableListOf<Pair<String, String>>()
        val calls = request.calls
        fun next(i: Int) {
            if (generation != turnGeneration) return // FR-A.6: a new turn cancels what was waiting.
            if (i == calls.size) {
                pip.state.update { voice = VoiceState.THINKING }
                pip.continueTurn(turn, request.assistant, results) { t, r -> onBrain(t, r, generation) }
                return
            }
            val call = calls[i]
            if (i >= ActionCatalog.MAX_ACTIONS_PER_TURN) {
                results += call.id to "not shown: at most ${ActionCatalog.MAX_ACTIONS_PER_TURN} actions per turn"
                next(i + 1)
                return
            }
            when (val parsed = ActionCatalog.parse(call.name, call.argumentsJson)) {
                is ActionParse.Rejected -> { results += call.id to "rejected: ${parsed.why}"; next(i + 1) }
                is ActionParse.Ok -> decide(parsed.action, generation) { r -> results += call.id to r; next(i + 1) }
            }
        }
        next(0)
    }

    private fun decide(action: DeviceAction, generation: Int, done: (String) -> Unit) {
        withPermissions(actions.preparePermissions(action)) { prepOk ->
            if (!prepOk) {
                pipLine.setText(R.string.act_permission_refused)
                done("permission refused by the owner")
                return@withPermissions
            }
            actions.prepare(action) { prepared ->
                if (generation != turnGeneration) return@prepare
                when (prepared) {
                    is DeviceActionRunner.Prepared.Failed -> {
                        pipLine.text = getString(R.string.pip_label) + " : " + prepared.ownerMessage
                        done(prepared.toolResult)
                    }
                    is DeviceActionRunner.Prepared.Ready -> showCard(prepared) { approved, choice ->
                        if (!approved) {
                            pipLine.setText(R.string.refused)
                            done("refused by the owner")
                            return@showCard
                        }
                        withPermissions(actions.executePermissions(action)) { granted ->
                            if (!granted) {
                                pipLine.setText(R.string.act_permission_refused)
                                done("permission refused by the owner")
                            } else {
                                if (!pip.state.running || generation != turnGeneration) return@withPermissions
                                actions.execute(prepared, choice) { result ->
                                    if (!result.startsWith("failed")) wink()
                                    done(result)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** S10: one action in Pip's words; Approve armed after 1 s; no "always" (FR-A.2, FR-A.3). */
    private fun showCard(p: DeviceActionRunner.Prepared.Ready, decided: (Boolean, DeviceActionRunner.Contact?) -> Unit) {
        if (isFinishing || isDestroyed) return
        val pad = Ui.dp(this, 20)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0) }
        box.addView(Ui.text(this, p.description))
        var choice = p.choices.firstOrNull()
        if (p.choices.size == 1) box.addView(Ui.text(this, p.choices[0].label(), bold = true))
        if (p.choices.size > 1) {
            // FR-A.5: the owner picks; the candidate list never reaches the brain.
            box.addView(Ui.text(this, getString(R.string.act_choose_contact), 14f))
            box.addView(RadioGroup(this).apply {
                p.choices.forEachIndexed { i, c -> addView(RadioButton(context).apply { text = c.label(); id = i + 1 }) }
                check(1)
                setOnCheckedChangeListener { _, id -> choice = p.choices[id - 1] }
            })
        }
        if (p.action.sendsDataToBrain) box.addView(Ui.text(this, getString(R.string.act_sends_data), 13f))
        var answered = false
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.approval_header)
            .setView(ScrollView(this).apply { addView(box) })
            .setCancelable(false)
            .setPositiveButton(R.string.approve) { _, _ -> if (!answered) { answered = true; decided(true, choice) } }
            .setNegativeButton(R.string.refuse) { _, _ -> if (!answered) { answered = true; decided(false, null) } }
            .create()
        dialog.setOnShowListener {
            val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ok.filterTouchesWhenObscured = true
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).filterTouchesWhenObscured = true
            ok.isEnabled = false
            ok.postDelayed({ ok.isEnabled = true }, ARM_DELAY_MS)
        }
        openCard = dialog
        dialog.show()
        // Shown as a rounded panel rising from the bottom, like a pocket opening.
        dialog.window?.apply {
            setBackgroundDrawable(Ui.shape(this@MainActivity, R.color.pip_cream, 28))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /** Asks only what is missing, at the moment of use (FR-A.7). */
    private fun withPermissions(permissions: List<String>, then: (Boolean) -> Unit) {
        val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED &&
                !(it == Manifest.permission.ACCESS_FINE_LOCATION && coarse) // approximate position is enough
        }
        if (missing.isEmpty()) { then(true); return }
        permissionCallback = then
        requestPermissions(missing.toTypedArray(), REQ_ACTION)
    }

    // --- Fun pack: Pip's personality (no permission, nothing sent) ---

    private fun wink(ms: Long = 2_000) {
        winkUntil = System.currentTimeMillis() + ms
        render()
        face.postDelayed({ render() }, ms + 50)
    }

    /** Pip looks sorry for a moment after something went wrong. */
    private fun oops() {
        oopsUntil = System.currentTimeMillis() + 2_500
        render()
        face.postDelayed({ render() }, 2_550)
    }

    private fun onFaceTapped() {
        tickleUntil = System.currentTimeMillis() + 1_200
        render()
        face.postDelayed({ render() }, 1_250)
        pipLine.text = getString(R.string.pip_label) + " : " + Fun.quip(this)
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
        is BrainResult.ActionRequest -> result.text
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
        /** Set by the bubble: open Pip listening (FR-B.4). */
        const val EXTRA_TALK = "ca.michael.pip.extra.TALK"
        private const val REQ_MIC = 1
        private const val REQ_NOTIFICATIONS = 2
        private const val REQ_ACTION = 3
        private const val ARM_DELAY_MS = 1_000L
    }
}
