package ca.michael.pip.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import ca.michael.pip.R
import ca.michael.pip.core.brain.BrainResult
import ca.michael.pip.core.brain.BrainUrl
import ca.michael.pip.pip
import ca.michael.pip.service.PipService

/** S4 — Settings › Brain, plus the voice settings of SPEC section 9. */
class BrainSettingsActivity : Activity() {
    private lateinit var url: EditText
    private lateinit var model: EditText
    private lateinit var key: EditText
    private lateinit var keyState: TextView
    private lateinit var feedback: TextView

    override fun onResume() {
        super.onResume()
        // Back from the system overlay screen: show or hide the bubble accordingly.
        PipService.syncBubble(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = Ui.screen(this, R.string.brain_title)
        val s = pip.settings

        col.addView(Ui.text(this, getString(R.string.brain_url_label), bold = true))
        url = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(s.brainBaseUrl)
        }.also { col.addView(it) }

        col.addView(Ui.text(this, getString(R.string.brain_model_label), bold = true))
        model = EditText(this).apply { setSingleLine(); setText(s.brainModel) }.also { col.addView(it) }

        col.addView(Ui.text(this, getString(R.string.brain_key_label), bold = true))
        keyState = Ui.text(this, "", 14f).also { col.addView(it) }
        // Write-only: the field never shows a saved key (FR-4.3).
        key = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO
        }.also { col.addView(it) }
        col.addView(Ui.text(this, getString(R.string.brain_key_note), 13f))

        col.addView(Ui.row(this,
            Ui.button(this, getString(R.string.save), Ui.Style.PRIMARY) { save() },
            Ui.button(this, getString(R.string.test)) { test() },
        ))
        col.addView(Ui.button(this, getString(R.string.clear_key), Ui.Style.DANGER) {
            pip.secrets.clearBrainKey()
            renderKey()
        })
        feedback = Ui.text(this, "", 15f).also { col.addView(it) }

        col.addView(Ui.spacer(this, 20))
        col.addView(Ui.text(this, getString(R.string.bubble_section), 18f, bold = true))
        col.addView(CheckBox(this).apply {
            setText(R.string.bubble_toggle)
            isChecked = s.bubbleEnabled
            setOnCheckedChangeListener { _, on ->
                s.bubbleEnabled = on
                // FR-B.2: the system screen grants "Display over other apps".
                if (on && !Settings.canDrawOverlays(this@BrainSettingsActivity)) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                }
                PipService.syncBubble(this@BrainSettingsActivity)
            }
        })
        col.addView(Ui.text(this, getString(R.string.bubble_note), 13f))

        col.addView(Ui.spacer(this, 20))
        col.addView(Ui.text(this, getString(R.string.voice_section), 18f, bold = true))
        // FR-A.9: the owner can turn device actions off.
        col.addView(CheckBox(this).apply {
            setText(R.string.actions_toggle)
            isChecked = s.actionsEnabled
            setOnCheckedChangeListener { _, on -> s.actionsEnabled = on }
        })
        col.addView(CheckBox(this).apply {
            setText(R.string.speak_answers)
            isChecked = s.speakAnswers
            setOnCheckedChangeListener { _, on -> s.speakAnswers = on }
        })
        // FR-2.2: one-time opt-in, with the warning in the label itself.
        col.addView(CheckBox(this).apply {
            setText(R.string.asr_opt_in)
            isChecked = s.systemAsrOptIn
            setOnCheckedChangeListener { _, on -> s.systemAsrOptIn = on }
        })
        col.addView(Ui.text(this, getString(R.string.speech_lang_label), bold = true))
        val fr = RadioButton(this).apply { setText(R.string.lang_fr); id = 1 }
        val en = RadioButton(this).apply { setText(R.string.lang_en); id = 2 }
        col.addView(RadioGroup(this).apply {
            addView(fr); addView(en)
            check(if (s.speechLanguage.startsWith("en")) 2 else 1)
            setOnCheckedChangeListener { _, checked -> s.speechLanguage = if (checked == 2) "en-CA" else "fr-CA" }
        })
        renderKey()
    }

    private fun renderKey() {
        keyState.setText(if (pip.secrets.hasBrainKey()) R.string.brain_key_saved else R.string.brain_key_none)
    }

    private fun save(): Boolean {
        val raw = url.text.toString()
        val normalized = BrainUrl.normalize(raw)
        if (raw.isNotBlank() && normalized == null) {
            // FR-4.2: refused at input.
            feedback.setText(R.string.brain_url_refused)
            return false
        }
        pip.settings.brainBaseUrl = normalized ?: ""
        pip.settings.brainModel = model.text.toString().trim()
        val typed = key.text.toString().trim()
        if (typed.isNotEmpty()) pip.secrets.setBrainKey(typed)
        key.setText("")
        renderKey()
        feedback.setText(R.string.saved)
        return true
    }

    private fun test() {
        if (!save()) return
        feedback.setText(R.string.state_thinking)
        pip.testBrain { result ->
            feedback.text = when (result) {
                is BrainResult.Answer, is BrainResult.ActionRequest -> getString(R.string.test_ok)
                BrainResult.NoKey -> getString(R.string.err_no_key)
                BrainResult.NotConfigured -> getString(R.string.err_not_configured)
                BrainResult.NoNetwork -> getString(R.string.err_no_network)
                BrainResult.Unreachable -> getString(R.string.err_unreachable)
                is BrainResult.KeyRefused -> getString(R.string.err_key_refused)
                is BrainResult.ServerError -> result.detail?.let { getString(R.string.err_server_detail, result.code, it) }
                ?: getString(R.string.err_server, result.code)
                BrainResult.Timeout -> getString(R.string.err_timeout)
                BrainResult.Malformed -> getString(R.string.err_malformed)
                BrainResult.Cancelled -> ""
            }
        }
    }
}
