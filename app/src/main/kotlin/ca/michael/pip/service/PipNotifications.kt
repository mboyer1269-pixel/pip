package ca.michael.pip.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import ca.michael.pip.AcpState
import ca.michael.pip.R
import ca.michael.pip.VoiceState
import ca.michael.pip.core.notebook.NotebookEntry
import ca.michael.pip.pip
import ca.michael.pip.ui.MainActivity
import ca.michael.pip.ui.NotebookActivity
import ca.michael.pip.ui.TaskApprovalActivity

/** S1 and the other notifications. Wording from BRAND.md section 4 (S1). */
class PipNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        manager.createNotificationChannels(listOf(
            NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
            NotificationChannel(CH_REMINDERS, context.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CH_TASKS, context.getString(R.string.channel_tasks), NotificationManager.IMPORTANCE_HIGH),
        ))
    }

    fun enabled(): Boolean = manager.areNotificationsEnabled()

    /** S1: state, ACP state, last task; Stop in every state (FR-1.2). */
    fun status(): Notification {
        val runtime = context.pip
        val state = runtime.state
        val pending = runtime.broker.pending().size

        val stateLine = when {
            state.errorReason != null -> context.getString(R.string.state_error, state.errorReason)
            pending > 0 -> context.getString(R.string.state_waiting)
            state.voice == VoiceState.LISTENING -> context.getString(R.string.state_listening)
            state.voice == VoiceState.THINKING -> context.getString(R.string.state_thinking)
            state.voice == VoiceState.SPEAKING -> context.getString(R.string.state_speaking)
            else -> context.getString(R.string.state_ready)
        }
        val acpLine = context.getString(when (state.acp) {
            AcpState.CONNECTED -> R.string.acp_connected
            AcpState.CONNECTING -> R.string.acp_connecting
            AcpState.OFF -> R.string.acp_off
        })
        val lines = listOfNotNull(
            stateLine,
            acpLine,
            state.lastTask?.let { context.getString(R.string.last_task, it.first, it.second) },
        )

        val builder = Notification.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(stateLine)
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(context.getColor(R.color.pip_indigo))
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(activity(MainActivity::class.java, 1))
            .setDeleteIntent(service(PipService.ACTION_REPOST, 3))
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_stop), service(PipService.ACTION_STOP, 2)).build())
            .addAction(Notification.Action.Builder(null, context.getString(R.string.action_open), activity(MainActivity::class.java, 4)).build())
        if (pending > 0) {
            builder.addAction(Notification.Action.Builder(null, context.getString(R.string.action_see_task), activity(TaskApprovalActivity::class.java, 5)).build())
        }
        return builder.build().apply { flags = flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT }
    }

    fun refreshStatus() = manager.notify(ID_STATUS, status())

    fun taskPending(show: Boolean) {
        if (!show) { manager.cancel(ID_TASK); return }
        manager.notify(ID_TASK, Notification.Builder(context, CH_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.state_waiting))
            .setColor(context.getColor(R.color.pip_clementine))
            .setContentIntent(activity(TaskApprovalActivity::class.java, 6))
            .setAutoCancel(true)
            .build())
    }

    fun reminder(entry: NotebookEntry) = manager.notify(entry.id.hashCode(), Notification.Builder(context, CH_REMINDERS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.reminder_due, entry.text))
        .setContentIntent(activity(NotebookActivity::class.java, 7))
        .setAutoCancel(true)
        .build())

    fun overdue(count: Int) = manager.notify(ID_OVERDUE, Notification.Builder(context, CH_REMINDERS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.resources.getQuantityString(R.plurals.overdue_count, count, count))
        .setContentIntent(activity(NotebookActivity::class.java, 8))
        .setAutoCancel(true)
        .build())

    fun orchestratorMessage(text: String) = manager.notify(ID_MESSAGE, Notification.Builder(context, CH_REMINDERS)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.orchestrator_message, text))
        .setStyle(Notification.BigTextStyle().bigText(context.getString(R.string.orchestrator_message, text)))
        .setContentIntent(activity(MainActivity::class.java, 9))
        .setAutoCancel(true)
        .build())

    private fun activity(cls: Class<*>, code: Int) = PendingIntent.getActivity(
        context, code, Intent(context, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun service(action: String, code: Int) = PendingIntent.getService(
        context, code, Intent(context, PipService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ID_STATUS = 1
        const val ID_TASK = 2
        const val ID_OVERDUE = 3
        const val ID_MESSAGE = 4
        const val CH_STATUS = "pip_status"
        const val CH_REMINDERS = "pip_reminders"
        const val CH_TASKS = "pip_tasks"
    }
}
