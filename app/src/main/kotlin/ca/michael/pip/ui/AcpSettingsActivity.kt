package ca.michael.pip.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Bundle
import android.os.PersistableBundle
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import ca.michael.pip.AcpState
import ca.michael.pip.PipRuntime
import ca.michael.pip.R
import ca.michael.pip.core.acp.AcpSigner
import ca.michael.pip.pip
import java.text.DateFormat
import java.util.Date

/** S5 — Settings › ACP: host from the build allowlist, pairing, connection. */
class AcpSettingsActivity : Activity() {
    private lateinit var column: LinearLayout
    private val onState: () -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ACP-13: the view that shows the pairing secret blocks screenshots.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        column = Ui.screen(this, R.string.acp_title)
    }

    override fun onResume() {
        super.onResume()
        pip.state.addListener(onState)
        render()
    }

    override fun onPause() {
        pip.state.removeListener(onState)
        super.onPause()
    }

    private fun render() {
        column.removeAllViews()
        val allow = pip.allowlist()

        column.addView(Ui.text(this, getString(R.string.acp_host_label), 18f, bold = true))
        if (allow.isEmpty()) {
            column.addView(Ui.text(this, getString(R.string.acp_empty_allowlist)))
        } else {
            if (pip.settings.acpHost !in allow) pip.settings.acpHost = allow.first()
            column.addView(RadioGroup(this).apply {
                allow.forEachIndexed { i, host -> addView(RadioButton(context).apply { text = host; id = i + 1 }) }
                check(allow.indexOf(pip.settings.acpHost) + 1)
                setOnCheckedChangeListener { _, id -> pip.settings.acpHost = allow[id - 1] }
            })
        }

        val paired = pip.secrets.hasAcpSecret()
        column.addView(Ui.text(this,
            if (paired) getString(R.string.acp_paired_since, DateFormat.getDateTimeInstance().format(Date(pip.settings.acpPairedAtMs)))
            else getString(R.string.acp_not_paired)))
        column.addView(Ui.text(this, getString(when (pip.state.acp) {
            AcpState.CONNECTED -> R.string.acp_connected
            AcpState.CONNECTING -> R.string.acp_connecting
            AcpState.OFF -> R.string.acp_off
        })))

        if (!paired) {
            column.addView(Ui.button(this, getString(R.string.pair), Ui.Style.PRIMARY) { pair() })
        } else {
            column.addView(Ui.row(this,
                Ui.button(this, getString(R.string.repair)) { confirm(R.string.repair_confirm) { pair() } },
                Ui.button(this, getString(R.string.revoke), Ui.Style.DANGER) { confirm(R.string.revoke_confirm) { revoke() } },
            ))
        }
        if (pip.state.acp == AcpState.OFF) {
            column.addView(Ui.button(this, getString(R.string.connect)) { connect() })
        } else {
            column.addView(Ui.button(this, getString(R.string.disconnect)) { pip.disconnectAcp() })
        }
    }

    /** ACP-13/14: a new 32-byte secret, shown once, stored sealed; the old one is gone. */
    private fun pair() {
        pip.disconnectAcp()
        val secret = AcpSigner.newSecret()
        pip.secrets.setAcpSecret(secret)
        pip.settings.acpPairedAtMs = System.currentTimeMillis()
        val code = AcpSigner.b64(secret)
        secret.fill(0)
        val view = Ui.text(this, getString(R.string.pairing_text) + "\n\n" + code, 16f).apply {
            setTextIsSelectable(true)
            val p = Ui.dp(this@AcpSettingsActivity, 20); setPadding(p, p, p, 0)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.pairing_title)
            .setView(view)
            .setCancelable(false)
            .setNeutralButton(R.string.copy) { _, _ -> copySensitive(code) }
            .setPositiveButton(R.string.done, null)
            .show()
            .window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        render()
    }

    private fun copySensitive(code: String) {
        val clip = ClipData.newPlainText("pip", code)
        // Marks the clip as sensitive so the system does not preview it.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun revoke() {
        pip.disconnectAcp()
        pip.secrets.clearAcpSecret()
        pip.settings.acpPairedAtMs = 0L
        render()
    }

    private fun connect() {
        val message = when (pip.connectAcp()) {
            PipRuntime.ConnectResult.OK -> null
            PipRuntime.ConnectResult.NOT_RUNNING -> R.string.acp_need_running
            PipRuntime.ConnectResult.NOT_PAIRED -> R.string.acp_need_pairing
            PipRuntime.ConnectResult.HOST_REFUSED -> R.string.acp_host_refused
        }
        message?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        render()
    }

    private fun confirm(message: Int, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(R.string.continue_) { _, _ -> action() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
