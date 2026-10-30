package ca.michael.pip.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.Toast
import ca.michael.pip.R
import ca.michael.pip.bubble.BubbleController
import ca.michael.pip.pip
import java.time.Instant

/**
 * The resident foreground service (V1-1). Started only by the owner from S2
 * (FR-1.1); no boot start. While it runs, S1 is showing with Stop.
 */
class PipService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notifications: PipNotifications
    private lateinit var bubble: BubbleController
    private val notifiedReminders = HashSet<String>()
    private var ticks = 0L

    private val onStateChange: () -> Unit = {
        if (pip.state.running) notifications.refreshStatus()
    }
    private val onBrokerChange: () -> Unit = {
        handler.post {
            notifications.taskPending(pip.broker.pending().isNotEmpty())
            if (pip.state.running) notifications.refreshStatus()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            pip.broker.expireDue()
            if (ticks++ % REMINDER_EVERY_TICKS == 0L) checkReminders()
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notifications = PipNotifications(this).also { it.createChannels() }
        bubble = BubbleController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPip()
                return START_NOT_STICKY
            }
            ACTION_REPOST -> {
                // The owner swiped S1 away while Pip runs: S1 must stay (FR-1.2). Stop is on it.
                if (pip.state.running) notifications.refreshStatus()
                return START_NOT_STICKY
            }
            ACTION_BUBBLE -> {
                // FR-B.3: the bubble exists only while Pip runs.
                if (pip.state.running) bubble.sync(pip.settings.bubbleEnabled) else stopSelf()
                return START_NOT_STICKY
            }
        }
        // FR-1.4: no notification, no background Pip.
        if (!notifications.enabled()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(PipNotifications.ID_STATUS, notifications.status(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!pip.state.running) {
            pip.state.update { running = true; errorReason = null }
            pip.state.addListener(onStateChange)
            pip.broker.addListener(onBrokerChange)
            announceOverdue()
            handler.post(ticker)
        }
        bubble.sync(pip.settings.bubbleEnabled)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        bubble.hide()
        handler.removeCallbacksAndMessages(null)
        pip.state.removeListener(onStateChange)
        pip.broker.removeListener(onBrokerChange)
        if (pip.state.running) pip.stopAll()
        notifications.taskPending(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** FR-1.3, in order: tasks cancelled and journaled, ACP closed, brain cancelled, notification removed. */
    private fun stopPip() {
        bubble.hide()
        handler.removeCallbacksAndMessages(null)
        pip.stopAll()
        notifications.taskPending(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Toast.makeText(this, R.string.pip_stopped, Toast.LENGTH_SHORT).show()
    }

    /** FR-5.6: reminders that fell due while Pip was stopped are shown as overdue on start. */
    private fun announceOverdue() {
        val now = Instant.now()
        val overdue = safeEntries().filter { it.isOverdue(now) }
        overdue.forEach { notifiedReminders += it.id }
        if (overdue.isNotEmpty()) notifications.overdue(overdue.size)
    }

    /** FR-5.6: due reminders produce a notification while Pip runs. */
    private fun checkReminders() {
        val now = Instant.now()
        for (entry in safeEntries()) {
            if (entry.id !in notifiedReminders && entry.isOverdue(now)) {
                notifiedReminders += entry.id
                notifications.reminder(entry)
            }
        }
    }

    private fun safeEntries() = try { pip.notebook.all() } catch (e: Exception) { emptyList() }

    companion object {
        const val ACTION_STOP = "ca.michael.pip.action.STOP"
        const val ACTION_REPOST = "ca.michael.pip.action.REPOST"
        const val ACTION_BUBBLE = "ca.michael.pip.action.BUBBLE"

        /** Asks a running Pip to show or hide the bubble after a settings change. */
        fun syncBubble(context: Context) {
            if (context.pip.state.running) {
                context.startService(Intent(context, PipService::class.java).setAction(ACTION_BUBBLE))
            }
        }
        private const val REMINDER_EVERY_TICKS = 30L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PipService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, PipService::class.java).setAction(ACTION_STOP))
        }
    }
}
