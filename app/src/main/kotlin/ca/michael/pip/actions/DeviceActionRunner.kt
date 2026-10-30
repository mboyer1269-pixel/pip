package ca.michael.pip.actions

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.LocationManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.provider.Telephony
import android.telephony.SmsManager
import ca.michael.pip.R
import ca.michael.pip.core.actions.ActionCatalog
import ca.michael.pip.core.actions.DeviceAction
import ca.michael.pip.core.actions.Recipient
import ca.michael.pip.core.text.TextMatch
import ca.michael.pip.notify.PipNotificationListener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Prepares, describes and runs Block A actions (SPEC 13) on the device.
 * It never decides on its own: [execute] is called only after the owner
 * approved the card built from [prepare].
 */
class DeviceActionRunner(private val activity: Activity) {

    data class Contact(val name: String, val number: String) {
        fun label() = "$name ($number)"
    }

    sealed interface Prepared {
        /** Ready for S10. [choices] holds the recipients to pick from (one or more) for call / SMS. */
        data class Ready(
            val action: DeviceAction,
            val description: String,
            val choices: List<Contact> = emptyList(),
            val launch: Intent? = null,
        ) : Prepared
        /** Nothing to approve: [toolResult] goes to the brain, [ownerMessage] is shown. */
        data class Failed(val toolResult: String, val ownerMessage: String) : Prepared
    }

    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val shown = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy 'à' HH:mm", Locale.getDefault())

    /** Permissions needed before the card can show exact details (contact lookup stays local, FR-A.5). */
    fun preparePermissions(action: DeviceAction): List<String> = when (action) {
        is DeviceAction.Call -> if (action.to is Recipient.Name) listOf(Manifest.permission.READ_CONTACTS) else emptyList()
        is DeviceAction.SendSms -> if (action.to is Recipient.Name) listOf(Manifest.permission.READ_CONTACTS) else emptyList()
        else -> emptyList()
    }

    /** Permissions the approved action needs to run (FR-A.7). */
    fun executePermissions(action: DeviceAction): List<String> = when (action) {
        is DeviceAction.OpenApp -> emptyList()
        is DeviceAction.Call -> listOf(Manifest.permission.CALL_PHONE)
        is DeviceAction.SendSms -> listOf(Manifest.permission.SEND_SMS)
        is DeviceAction.FindContact -> listOf(Manifest.permission.READ_CONTACTS)
        is DeviceAction.ReadSms -> listOfNotNull(
            Manifest.permission.READ_SMS,
            if (action.from != null) Manifest.permission.READ_CONTACTS else null,
        )
        is DeviceAction.ReadCallLog -> listOf(Manifest.permission.READ_CALL_LOG)
        is DeviceAction.ReadCalendar -> listOf(Manifest.permission.READ_CALENDAR)
        is DeviceAction.AddCalendarEvent -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        DeviceAction.GetLocation -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        DeviceAction.GetSteps -> listOf(Manifest.permission.ACTIVITY_RECOGNITION)
        // Notification access is a system screen, not a runtime permission (SPEC 14).
        is DeviceAction.ReadNotifications -> emptyList()
    }

    fun prepare(action: DeviceAction, done: (Prepared) -> Unit) {
        io.execute {
            val result = try { prepareNow(action) } catch (e: Exception) {
                Prepared.Failed("failed: ${e.javaClass.simpleName}", s(R.string.act_failed))
            }
            main.post { done(result) }
        }
    }

    private fun prepareNow(action: DeviceAction): Prepared = when (action) {
        is DeviceAction.OpenApp -> {
            val app = findApp(action.appName)
            if (app == null) Prepared.Failed("no installed app named ${action.appName}", s(R.string.act_no_app, action.appName))
            else Prepared.Ready(action, s(R.string.act_open_app, app.first), launch = app.second)
        }
        is DeviceAction.Call -> recipients(action.to)?.let { Prepared.Ready(action, s(R.string.act_call), it) }
            ?: noContact(action.to)
        is DeviceAction.SendSms -> recipients(action.to)?.let { Prepared.Ready(action, s(R.string.act_sms, action.text), it) }
            ?: noContact(action.to)
        is DeviceAction.FindContact -> Prepared.Ready(action, s(R.string.act_find_contact, action.query))
        is DeviceAction.ReadSms -> Prepared.Ready(action,
            action.from?.let { s(R.string.act_read_sms_from, action.limit, it) } ?: s(R.string.act_read_sms, action.limit))
        is DeviceAction.ReadCallLog -> Prepared.Ready(action, s(R.string.act_read_calls, action.limit))
        is DeviceAction.ReadCalendar -> Prepared.Ready(action,
            s(R.string.act_read_calendar, (action.fromDate ?: LocalDate.now()).toString(), action.days))
        is DeviceAction.AddCalendarEvent -> Prepared.Ready(action, s(R.string.act_add_event, action.title,
            action.start.format(shown), action.durationMinutes) + (action.location?.let { "\n" + s(R.string.act_event_place, it) } ?: ""))
        DeviceAction.GetLocation -> Prepared.Ready(action, s(R.string.act_location))
        DeviceAction.GetSteps -> Prepared.Ready(action, s(R.string.act_steps))
        is DeviceAction.ReadNotifications -> if (!PipNotificationListener.granted(activity)) {
            // T-C2: say how to turn it on, open the screen, send nothing.
            main.post { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            Prepared.Failed("notification access is off", s(R.string.act_notif_access_off))
        } else {
            Prepared.Ready(action, action.app?.let { s(R.string.act_read_notifications_from, action.limit, it) }
                ?: s(R.string.act_read_notifications, action.limit))
        }
    }

    private fun noContact(to: Recipient) = (to as Recipient.Name).let {
        Prepared.Failed("no contact named ${it.name}", s(R.string.act_no_contact, it.name))
    }

    /** A number stands for itself; a name is looked up in the contacts, on the device only. Null when nothing matches. */
    private fun recipients(to: Recipient): List<Contact>? = when (to) {
        is Recipient.Number -> listOf(Contact(to.digits, to.digits))
        is Recipient.Name -> contactsMatching(to.name, 6).ifEmpty { null }
    }

    /** Runs one approved action; [done] gets the tool result for the brain, on the main thread. */
    fun execute(prepared: Prepared.Ready, choice: Contact?, done: (String) -> Unit) {
        val action = prepared.action
        if (action is DeviceAction.OpenApp || action is DeviceAction.Call) {
            // These open another screen: they must start from the activity, on the main thread.
            done(try { startOnMain(prepared, choice) } catch (e: Exception) { "failed: ${e.javaClass.simpleName}" })
            return
        }
        // Sensors can throw (permission revoked, provider gone): report it, never crash.
        if (action == DeviceAction.GetLocation) {
            try { location(done) } catch (e: Exception) { done("failed: ${e.javaClass.simpleName}") }
            return
        }
        if (action == DeviceAction.GetSteps) {
            try { steps(done) } catch (e: Exception) { done("failed: ${e.javaClass.simpleName}") }
            return
        }
        io.execute {
            val result = try { runNow(action, choice) } catch (e: Exception) { "failed: ${e.javaClass.simpleName}" }
            main.post { done(result) }
        }
    }

    @SuppressLint("MissingPermission") // Checked by the caller before execute (FR-A.7).
    private fun startOnMain(prepared: Prepared.Ready, choice: Contact?): String = when (val a = prepared.action) {
        is DeviceAction.OpenApp -> {
            activity.startActivity(prepared.launch!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "opened ${a.appName}"
        }
        is DeviceAction.Call -> {
            val c = choice ?: return "failed: no recipient"
            activity.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", c.number, null)))
            "call started to ${c.name}"
        }
        else -> "failed: wrong path"
    }

    @SuppressLint("MissingPermission") // Checked by the caller before execute (FR-A.7).
    private fun runNow(action: DeviceAction, choice: Contact?): String = when (action) {
        is DeviceAction.SendSms -> {
            val c = choice ?: return "failed: no recipient"
            val sms = activity.getSystemService(SmsManager::class.java)
            sms.sendMultipartTextMessage(c.number, null, sms.divideMessage(action.text), null, null)
            "sms handed to the phone for sending to ${c.name}"
        }
        is DeviceAction.FindContact -> JsonArray(contactsMatching(action.query, 10).map {
            buildJsonObject { put("name", JsonPrimitive(it.name)); put("number", JsonPrimitive(it.number)) }
        }).toString()
        is DeviceAction.ReadSms -> readSms(action)
        is DeviceAction.ReadCallLog -> readCalls(action.limit)
        is DeviceAction.ReadCalendar -> readCalendar(action)
        is DeviceAction.AddCalendarEvent -> addEvent(action)
        is DeviceAction.ReadNotifications -> PipNotificationListener.read(action.app, action.limit)
            ?: run { PipNotificationListener.rebind(activity); "failed: notification access is starting, ask again in a moment" }
        else -> "failed: wrong path"
    }

    // --- Lookups ---

    private fun findApp(name: String): Pair<String, Intent>? {
        val pm = activity.packageManager
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != activity.packageName }
        val wanted = TextMatch.normalize(name).trim()
        val labelled = launchers.map { it to it.loadLabel(pm).toString() }
        val hit = labelled.firstOrNull { TextMatch.normalize(it.second) == wanted }
            ?: labelled.firstOrNull { TextMatch.normalize(it.second).contains(wanted) }
            ?: return null
        val info = hit.first.activityInfo
        return hit.second to Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(info.packageName, info.name)
    }

    private fun contactsMatching(query: String, max: Int): List<Contact> {
        val out = LinkedHashMap<String, Contact>()
        activity.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$query%"),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext() && out.size < max) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                out.putIfAbsent(number.filter { it.isDigit() || it == '+' }, Contact(name, number))
            }
        }
        return out.values.toList()
    }

    private fun tail(number: String) = number.filter { it.isDigit() }.takeLast(7)

    @SuppressLint("MissingPermission")
    private fun readSms(a: DeviceAction.ReadSms): String {
        val wanted: Set<String>? = a.from?.let { from ->
            when (val r = ActionCatalog.recipientOf(from)) {
                is Recipient.Number -> setOf(tail(r.digits))
                is Recipient.Name -> contactsMatching(r.name, 10).map { tail(it.number) }.toSet()
            }
        }
        if (wanted != null && wanted.isEmpty()) return "no contact named ${a.from}"
        val list = mutableListOf<JsonObject>()
        activity.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY),
            null, null, "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && list.size < a.limit) {
                val address = c.getString(0) ?: ""
                if (wanted != null && tail(address) !in wanted) continue
                list += buildJsonObject {
                    put("from", JsonPrimitive(address))
                    put("at", JsonPrimitive(Instant.ofEpochMilli(c.getLong(1)).toString()))
                    put("text", JsonPrimitive((c.getString(2) ?: "").take(300)))
                }
            }
        }
        return JsonArray(list).toString()
    }

    @SuppressLint("MissingPermission")
    private fun readCalls(limit: Int): String {
        val list = mutableListOf<JsonObject>()
        activity.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION),
            null, null, "${CallLog.Calls.DATE} DESC",
        )?.use { c ->
            while (c.moveToNext() && list.size < limit) {
                list += buildJsonObject {
                    put("number", JsonPrimitive(c.getString(0) ?: ""))
                    put("name", JsonPrimitive(c.getString(1) ?: ""))
                    put("type", JsonPrimitive(when (c.getInt(2)) {
                        CallLog.Calls.INCOMING_TYPE -> "incoming"
                        CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                        CallLog.Calls.MISSED_TYPE -> "missed"
                        else -> "other"
                    }))
                    put("at", JsonPrimitive(Instant.ofEpochMilli(c.getLong(3)).toString()))
                    put("duration_s", JsonPrimitive(c.getLong(4)))
                }
            }
        }
        return JsonArray(list).toString()
    }

    @SuppressLint("MissingPermission")
    private fun readCalendar(a: DeviceAction.ReadCalendar): String {
        val zone = ZoneId.systemDefault()
        val from = (a.fromDate ?: LocalDate.now()).atStartOfDay(zone)
        val begin = from.toInstant().toEpochMilli()
        val end = from.plusDays(a.days.toLong()).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin); ContentUris.appendId(it, end)
        }.build()
        val list = mutableListOf<JsonObject>()
        activity.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
            null, null, "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && list.size < 30) {
                list += buildJsonObject {
                    put("title", JsonPrimitive(c.getString(0) ?: ""))
                    put("begin", JsonPrimitive(Instant.ofEpochMilli(c.getLong(1)).atZone(zone).toLocalDateTime().toString()))
                    put("end", JsonPrimitive(Instant.ofEpochMilli(c.getLong(2)).atZone(zone).toLocalDateTime().toString()))
                    put("location", JsonPrimitive(c.getString(3) ?: ""))
                    put("all_day", JsonPrimitive(c.getInt(4) == 1))
                }
            }
        }
        return JsonArray(list).toString()
    }

    @SuppressLint("MissingPermission")
    private fun addEvent(a: DeviceAction.AddCalendarEvent): String {
        val calendarId = writableCalendar() ?: return "failed: no writable calendar on this phone"
        val zone = ZoneId.systemDefault()
        val start = a.start.atZone(zone).toInstant().toEpochMilli()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, a.title)
            put(CalendarContract.Events.DTSTART, start)
            put(CalendarContract.Events.DTEND, start + a.durationMinutes * 60_000L)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            a.location?.let { put(CalendarContract.Events.EVENT_LOCATION, it) }
        }
        val event = activity.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return "failed: calendar refused the event"
        // Without a reminder row the calendar never alerts: ring at the event's start.
        val alert = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, ContentUris.parseId(event))
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        val alerted = activity.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, alert) != null
        return "event added: ${a.title} at ${a.start}" + if (alerted) " (alert at start)" else " (no alert: the calendar refused it)"
    }

    @SuppressLint("MissingPermission")
    private fun writableCalendar(): Long? {
        activity.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
            "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null,
        )?.use { c ->
            var first: Long? = null
            while (c.moveToNext()) {
                if (c.getInt(1) == 1) return c.getLong(0)
                if (first == null) first = c.getLong(0)
            }
            return first
        }
        return null
    }

    // --- Sensors (asynchronous, with a timeout) ---

    @SuppressLint("MissingPermission")
    private fun location(done: (String) -> Unit) {
        val lm = activity.getSystemService(LocationManager::class.java)
        val enabled = lm.getProviders(true)
        // GPS needs the precise permission; with approximate only, use the other providers.
        val fine = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val provider = listOfNotNull(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, if (fine) LocationManager.GPS_PROVIDER else null)
            .firstOrNull { it in enabled } ?: run { done("failed: location is turned off"); return }
        val finished = AtomicBoolean(false)
        val cancel = CancellationSignal()
        main.postDelayed({ if (finished.compareAndSet(false, true)) { cancel.cancel(); done("failed: no position within 20 s") } }, 20_000)
        lm.getCurrentLocation(provider, cancel, activity.mainExecutor) { loc ->
            if (!finished.compareAndSet(false, true)) return@getCurrentLocation
            done(if (loc == null) "failed: no position" else buildJsonObject {
                put("lat", JsonPrimitive(Math.round(loc.latitude * 10_000) / 10_000.0))
                put("lon", JsonPrimitive(Math.round(loc.longitude * 10_000) / 10_000.0))
                put("accuracy_m", JsonPrimitive(loc.accuracy.toInt()))
            }.toString())
        }
    }

    private fun steps(done: (String) -> Unit) {
        val sm = activity.getSystemService(SensorManager::class.java)
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: run { done("failed: this phone has no step counter"); return }
        val finished = AtomicBoolean(false)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (!finished.compareAndSet(false, true)) return
                sm.unregisterListener(this)
                done(buildJsonObject { put("steps_since_reboot", JsonPrimitive(event.values[0].toLong())) }.toString())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        main.postDelayed({
            if (finished.compareAndSet(false, true)) { sm.unregisterListener(listener); done("failed: no step reading within 5 s") }
        }, 5_000)
    }

    fun hasAll(permissions: List<String>) =
        permissions.all { activity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun s(id: Int, vararg args: Any) = activity.getString(id, *args)
}
