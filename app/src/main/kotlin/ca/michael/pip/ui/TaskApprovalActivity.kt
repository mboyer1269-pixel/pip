package ca.michael.pip.ui

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.LinearLayout
import android.widget.TextView
import ca.michael.pip.R
import ca.michael.pip.core.acp.AcpIntent
import ca.michael.pip.core.acp.PendingTask
import ca.michael.pip.pip
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * S7 — Task approval. One pending task at a time: what will happen in Pip's
 * own words, the orchestrator's wording, the full arguments, time left.
 * Approve or Refuse — no "always" option (ACP-9).
 */
class TaskApprovalActivity : Activity() {
    private lateinit var column: LinearLayout
    private var timeLeft: TextView? = null
    private var shownTaskId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val onBroker: () -> Unit = { handler.post { render() } }
    private val countdown = object : Runnable {
        override fun run() {
            updateTimeLeft()
            handler.postDelayed(this, 1_000)
        }
    }
    private val pretty = Json { prettyPrint = true }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        column = Ui.screen(this, R.string.nav_tasks)
    }

    override fun onResume() {
        super.onResume()
        pip.broker.addListener(onBroker)
        render()
        handler.post(countdown)
    }

    override fun onPause() {
        pip.broker.removeListener(onBroker)
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun current(): PendingTask? = pip.broker.pending().firstOrNull()

    private fun render() {
        val task = current()
        if (task?.taskId == shownTaskId && task != null) return
        shownTaskId = task?.taskId
        column.removeAllViews()
        timeLeft = null
        if (task == null) {
            column.addView(Ui.text(this, getString(R.string.approval_none)))
            return
        }
        column.addView(Ui.text(this, getString(R.string.approval_header), 18f, bold = true))
        column.addView(Ui.text(this, task.intent.name, 14f))
        column.addView(Ui.text(this, getString(R.string.approval_if_yes, describe(task.intent)), 16f))
        if (task.summary.isNotBlank()) column.addView(Ui.text(this, getString(R.string.approval_orchestrator_says, task.summary), 14f))
        column.addView(Ui.text(this, getString(R.string.approval_details), 14f, bold = true))
        column.addView(Ui.text(this, pretty.encodeToString(JsonElement.serializer(), task.args), 13f).apply { setTextIsSelectable(true) })
        timeLeft = Ui.text(this, "", 15f, bold = true).also { column.addView(it) }
        updateTimeLeft()
        column.addView(Ui.row(this,
            Ui.button(this, getString(R.string.refuse), Ui.Style.DANGER) { decide(task, approve = false) },
            Ui.button(this, getString(R.string.approve), Ui.Style.PRIMARY) { decide(task, approve = true) },
        ))
    }

    private fun decide(task: PendingTask, approve: Boolean) {
        val message = if (approve) {
            if (pip.broker.approve(task.taskId)) R.string.executed else R.string.expired
        } else {
            if (pip.broker.refuse(task.taskId)) R.string.refused else R.string.expired
        }
        shownTaskId = null
        render()
        column.addView(Ui.text(this, getString(message), 16f, bold = true), 0)
    }

    private fun updateTimeLeft() {
        val task = current() ?: return
        if (task.taskId != shownTaskId) { render(); return }
        val ms = pip.broker.timeLeftMs(task)
        val s = ms / 1000
        timeLeft?.text = getString(R.string.approval_time_left, String.format("%d:%02d", s / 60, s % 60))
    }

    /** Pip's own description of exactly what will happen (BRAND.md S7). */
    private fun describe(intent: AcpIntent): String = when (intent) {
        AcpIntent.DeviceStatus -> getString(R.string.desc_device_status)
        AcpIntent.SnapshotLast -> getString(R.string.desc_snapshot_last)
        is AcpIntent.MessageShow -> getString(R.string.desc_message_show, intent.text)
        is AcpIntent.ReminderCreate -> getString(
            R.string.desc_reminder_create, intent.text,
            intent.dueAt?.let { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault()).format(it) }
                ?: getString(R.string.no_date),
        )
    }
}
