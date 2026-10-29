package ca.michael.pip.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.LinearLayout
import ca.michael.pip.R
import ca.michael.pip.core.journal.EventKind
import ca.michael.pip.core.journal.JournalEvent
import ca.michael.pip.pip
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** S8 — Task journal: every ACP event with its time and outcome (ACP-16). */
class JournalActivity : Activity() {
    private lateinit var column: LinearLayout
    private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = Ui.screen(this, R.string.journal_title)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        column.removeAllViews()
        val events = pip.journal.all()
        if (events.isEmpty()) {
            column.addView(Ui.text(this, getString(R.string.journal_empty)))
            return
        }
        for (e in events.asReversed()) {
            val who = listOfNotNull(e.intent, e.taskId).joinToString(" · ")
            column.addView(Ui.text(this, time.format(e.at) + "  " + label(e) + if (who.isNotEmpty()) "\n$who" else "", 14f))
        }
        column.addView(Ui.spacer(this, 20))
        column.addView(Ui.button(this, getString(R.string.clear_journal), Ui.Style.DANGER) {
            AlertDialog.Builder(this)
                .setMessage(R.string.clear_journal_confirm)
                .setPositiveButton(R.string.continue_) { _, _ -> pip.journal.clear(); render() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        })
    }

    private fun label(e: JournalEvent): String = when (e.kind) {
        EventKind.RECEIVED -> getString(R.string.ev_received)
        EventKind.PENDING -> getString(R.string.ev_pending)
        EventKind.APPROVED -> getString(R.string.ev_approved)
        EventKind.REFUSED -> getString(R.string.ev_refused)
        EventKind.EXPIRED -> getString(R.string.ev_expired)
        EventKind.REJECTED -> getString(R.string.ev_rejected, reason(e.detail))
        EventKind.EXECUTED -> getString(R.string.ev_executed)
        EventKind.FAILED -> getString(R.string.ev_failed)
        EventKind.CANCELLED -> if (e.detail == "cancelled by stop") getString(R.string.cancelled_by_stop) else getString(R.string.ev_cancelled)
    }

    /** Owner-facing words for each rejection reason written by the broker. */
    private fun reason(detail: String?): String {
        val d = detail ?: return ""
        return getString(when {
            d.startsWith("envelope:bad_signature") -> R.string.reason_bad_signature
            d.startsWith("envelope:wrong_agent") -> R.string.reason_wrong_agent
            d.startsWith("envelope:stale") -> R.string.reason_stale
            d.startsWith("envelope:replay") -> R.string.reason_replay
            d.startsWith("envelope:unsupported_version") -> R.string.reason_version
            d.startsWith("envelope:not_paired") -> R.string.reason_not_paired
            d.startsWith("envelope:") || d == "malformed body" -> R.string.reason_malformed
            d == "unknown intent" -> R.string.reason_unknown_task
            d == "forbidden intent" -> R.string.reason_forbidden_task
            d == "duplicate task_id" -> R.string.reason_duplicate
            else -> R.string.reason_bad_args
        })
    }
}
