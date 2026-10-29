package ca.michael.pip.acp

import android.content.Context
import android.os.BatteryManager
import ca.michael.pip.BuildConfig
import ca.michael.pip.Network
import ca.michael.pip.PipRuntime
import ca.michael.pip.core.acp.AcpIntent
import ca.michael.pip.core.acp.TaskExecutor
import ca.michael.pip.core.acp.TaskOutcome
import ca.michael.pip.core.notebook.EntryKind
import ca.michael.pip.core.notebook.NotebookEntry
import ca.michael.pip.service.PipNotifications
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Runs the four closed-list intents (SPEC 7.5) after the owner approved them.
 * There is no code path here for anything else.
 */
class DeviceTaskExecutor(private val context: Context, private val runtime: PipRuntime) : TaskExecutor {

    override fun execute(intent: AcpIntent): TaskOutcome = when (intent) {
        AcpIntent.DeviceStatus -> {
            val battery = context.getSystemService(BatteryManager::class.java)
            TaskOutcome(true, buildJsonObject {
                put("pip_version", JsonPrimitive(BuildConfig.VERSION_NAME))
                put("battery_percent", JsonPrimitive(battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1))
                put("charging", JsonPrimitive(battery?.isCharging ?: false))
                put("network", JsonPrimitive(Network.type(context)))
                put("service", JsonPrimitive(if (runtime.state.running) "running" else "stopped"))
            })
        }
        AcpIntent.SnapshotLast -> TaskOutcome(true, buildJsonObject {
            // Never a new capture: only what the owner captured on the device.
            put("snapshot", runtime.lastOwnerSnapshot?.let { JsonPrimitive(it) } ?: JsonNull)
        })
        is AcpIntent.MessageShow -> {
            runtime.state.update { orchestratorMessage = intent.text }
            PipNotifications(context).orchestratorMessage(intent.text)
            TaskOutcome(true, buildJsonObject { put("shown", JsonPrimitive(true)) })
        }
        is AcpIntent.ReminderCreate -> {
            val entry = NotebookEntry(kind = EntryKind.REMINDER, text = intent.text, createdAt = Instant.now(), dueAt = intent.dueAt)
            runtime.notebook.upsert(entry)
            TaskOutcome(true, buildJsonObject { put("id", JsonPrimitive(entry.id)) })
        }
    }
}
