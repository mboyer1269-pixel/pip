package ca.michael.pip.screen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import ca.michael.pip.core.snapshot.Role
import ca.michael.pip.core.snapshot.SnapshotFormatter
import ca.michael.pip.core.snapshot.SnapshotNode
import java.lang.ref.WeakReference
import java.time.Instant
import java.time.temporal.ChronoUnit

sealed interface Capture {
    data class Ok(val text: String) : Capture
    data object ServiceOff : Capture
    data object NoOtherApp : Capture
}

/**
 * Read-only screen context (V1-3). It reacts to no event: it reads window
 * content only when [capture] is called for the owner's current turn
 * (FR-3.1), performs no action of any kind (FR-3.2), skips password fields
 * and Pip's own windows (FR-3.4).
 */
class PipAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    private var budget = 0

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Deliberately empty: nothing is read in response to events.
    }

    override fun onInterrupt() {}

    private fun captureNow(): Capture {
        val own = packageName
        // The top-most application window that is not Pip's (for example the
        // other half of a split screen). Pip's own windows are never read.
        val root = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .sortedByDescending { it.layer }
            .mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() != own }
            ?: return Capture.NoOtherApp
        budget = MAX_NODES_READ
        val tree = convert(root, parentIsList = false, depth = 0)
        val time = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()
        return Capture.Ok(SnapshotFormatter.format(root.packageName?.toString() ?: "?", time, tree))
    }

    private fun convert(node: AccessibilityNodeInfo, parentIsList: Boolean, depth: Int): SnapshotNode {
        val role = roleOf(node, parentIsList)
        // A global budget keeps a huge screen (feed, web page) from freezing Pip.
        budget--
        val children = if (depth >= MAX_DEPTH || node.isPassword || budget <= 0) emptyList() else
            (0 until minOf(node.childCount, MAX_CHILDREN)).mapNotNull { i ->
                if (budget <= 0) null else node.getChild(i)?.let { convert(it, role == Role.LIST, depth + 1) }
            }
        return SnapshotNode(
            role = role,
            text = if (node.isPassword) null else (node.text ?: node.contentDescription)?.toString(),
            visible = node.isVisibleToUser,
            password = node.isPassword,
            checked = node.isCheckable && node.isChecked,
            selected = node.isSelected,
            disabled = !node.isEnabled,
            focused = node.isFocused,
            children = children,
        )
    }

    private fun roleOf(node: AccessibilityNodeInfo, parentIsList: Boolean): Role {
        val cls = node.className?.toString() ?: ""
        return when {
            node.isEditable || cls.endsWith("EditText") -> Role.FIELD
            node.isCheckable || cls.endsWith("Switch") || cls.endsWith("CheckBox") || cls.endsWith("ToggleButton") || cls.endsWith("RadioButton") -> Role.TOGGLE
            cls.endsWith("Button") -> Role.BUTTON
            cls.endsWith("ImageView") -> Role.IMAGE
            cls.endsWith("ListView") || cls.endsWith("RecyclerView") || cls.endsWith("GridView") || node.collectionInfo != null -> Role.LIST
            parentIsList -> Role.ITEM
            cls.endsWith("TextView") -> Role.TEXT
            node.isClickable -> Role.BUTTON
            else -> Role.OTHER
        }
    }

    companion object {
        private const val MAX_DEPTH = 30
        private const val MAX_CHILDREN = 100
        private const val MAX_NODES_READ = 600
        @Volatile private var instance: WeakReference<PipAccessibilityService>? = null

        fun isOn(): Boolean = instance?.get() != null

        /** Called only from the owner's turn on S2. */
        fun capture(): Capture = instance?.get()?.captureNow() ?: Capture.ServiceOff
    }
}
