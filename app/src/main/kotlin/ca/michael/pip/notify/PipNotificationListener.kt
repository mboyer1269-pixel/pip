package ca.michael.pip.notify

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import ca.michael.pip.core.text.TextMatch
import java.lang.ref.WeakReference
import java.time.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Block C (SPEC 14). Lets Pip read the notifications currently shown, and
 * only when an approved `read_notifications` action asks (FR-C.2): it does
 * not override onNotificationPosted, so nothing is read as notifications arrive.
 */
class PipNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = WeakReference(this)
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    private fun snapshot(app: String?, limit: Int): String {
        val pm = packageManager
        val wanted = app?.let { TextMatch.normalize(it).trim() }
        val shown = try { activeNotifications.orEmpty() } catch (e: SecurityException) { emptyArray() }
        val items = shown.asSequence()
            // FR-C.4: never Pip's own, never ongoing ones or group summaries.
            .filter { it.packageName != packageName && !it.isOngoing }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .sortedByDescending { it.postTime }
            .mapNotNull { sbn ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
                } catch (e: Exception) {
                    sbn.packageName
                }
                if (wanted != null && !TextMatch.normalize(label).contains(wanted) && !sbn.packageName.contains(wanted)) {
                    return@mapNotNull null
                }
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                    ?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                buildJsonObject {
                    put("app", JsonPrimitive(label))
                    put("title", JsonPrimitive(title.take(MAX_TEXT)))
                    put("text", JsonPrimitive(text.take(MAX_TEXT)))
                    put("at", JsonPrimitive(Instant.ofEpochMilli(sbn.postTime).toString()))
                }
            }
            .take(limit)
            .toList()
        return JsonArray(items).toString()
    }

    companion object {
        private const val MAX_TEXT = 200
        @Volatile private var instance: WeakReference<PipNotificationListener>? = null

        fun component(context: Context) = ComponentName(context, PipNotificationListener::class.java)

        /** True when the owner turned Pip on in the system "Notification access" screen. */
        fun granted(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))

        /** Null when the service is not connected yet. */
        fun read(app: String?, limit: Int): String? = instance?.get()?.snapshot(app, limit)

        fun rebind(context: Context) = NotificationListenerService.requestRebind(component(context))
    }
}
