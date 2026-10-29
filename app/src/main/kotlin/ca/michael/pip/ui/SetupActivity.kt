package ca.michael.pip.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import ca.michael.pip.R
import ca.michael.pip.screen.PipAccessibilityService
import ca.michael.pip.service.PipNotifications

/**
 * S3 — Setup & permissions. One row per capability: what it gives, what is
 * lost if refused, its state. Each permission is asked here or at the moment
 * of use, never all at startup (V1-7).
 */
class SetupActivity : Activity() {
    private lateinit var column: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = Ui.screen(this, R.string.setup_title)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        column.removeAllViews()
        column.addView(Ui.text(this, getString(R.string.setup_intro)))

        val notifOk = PipNotifications(this).enabled()
        row(R.string.cap_notifications, R.string.cap_notifications_gives, R.string.cap_notifications_lost, notifOk) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED || blocked(Manifest.permission.POST_NOTIFICATIONS)) {
                // Granted but turned off at app level, or the system no longer shows the prompt.
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } else {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        val micOk = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        row(R.string.cap_mic, R.string.cap_mic_gives, R.string.cap_mic_lost, micOk) {
            if (!blocked(Manifest.permission.RECORD_AUDIO)) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 2)
            else startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }

        row(R.string.cap_a11y, R.string.cap_a11y_gives, R.string.cap_a11y_lost, PipAccessibilityService.isOn()) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        // INTERNET is granted at install; shown so the owner knows what it is for.
        row(R.string.cap_network, R.string.cap_network_gives, R.string.cap_network_lost, true, null)

        column.addView(Ui.spacer(this))
        column.addView(Ui.button(this, getString(R.string.later)) { finish() })
    }

    private fun row(title: Int, gives: Int, lost: Int, ok: Boolean, action: (() -> Unit)?) {
        column.addView(Ui.spacer(this, 8))
        column.addView(Ui.text(this, getString(title) + " — " + getString(if (ok) R.string.granted else R.string.refused_state), 17f, bold = true))
        column.addView(Ui.text(this, getString(gives), 15f))
        column.addView(Ui.text(this, getString(R.string.if_refused, getString(lost)), 14f))
        if (!ok && action != null) {
            val label = if (title == R.string.cap_a11y) R.string.open_settings else R.string.allow
            column.addView(Ui.button(this, getString(label)) { action() })
        }
    }

    private fun prefs() = getSharedPreferences("pip_setup", MODE_PRIVATE)

    /** True once the system has stopped showing the prompt for [permission]. */
    private fun blocked(permission: String) = prefs().getBoolean(permission, false)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissions.forEachIndexed { i, permission ->
            val denied = grantResults.getOrNull(i) != PackageManager.PERMISSION_GRANTED
            if (denied && !shouldShowRequestPermissionRationale(permission)) prefs().edit().putBoolean(permission, true).apply()
        }
        render()
    }
}
