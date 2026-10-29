package ca.michael.pip.core.snapshot

enum class Role(val label: String) {
    TEXT("text"), BUTTON("button"), FIELD("field"), IMAGE("image"),
    LIST("list"), ITEM("item"), TOGGLE("toggle"), OTHER("other"),
}

/** A platform-neutral view of one on-screen node, built by the accessibility service. */
data class SnapshotNode(
    val role: Role,
    val text: String?,
    val visible: Boolean = true,
    val password: Boolean = false,
    val checked: Boolean = false,
    val selected: Boolean = false,
    val disabled: Boolean = false,
    val focused: Boolean = false,
    val children: List<SnapshotNode> = emptyList(),
)

/**
 * Writes the Pip Screen Snapshot text format (SPEC 6.2).
 *
 * Empty nodes are not written but their children are, one level up.
 * Invisible and password nodes are skipped together with everything below them.
 */
object SnapshotFormatter {
    const val MAX_NODES = 200
    const val MAX_CHARS = 6_000
    const val MAX_NODE_TEXT = 200

    fun format(
        appPackage: String,
        timeIso: String,
        root: SnapshotNode,
        maxNodes: Int = MAX_NODES,
        maxChars: Int = MAX_CHARS,
    ): String {
        val body = StringBuilder()
        var count = 0
        var truncated = false

        fun visit(node: SnapshotNode, depth: Int) {
            if (truncated) return
            if (!node.visible || node.password) return
            val text = clean(node.text)
            var childDepth = depth
            if (text.isNotEmpty()) {
                val flags = buildList {
                    if (node.checked) add("checked")
                    if (node.selected) add("selected")
                    if (node.disabled) add("disabled")
                    if (node.focused) add("focused")
                }
                val line = buildString {
                    append("  ".repeat(depth)).append(node.role.label).append(" \"").append(text).append('"')
                    if (flags.isNotEmpty()) append(" [").append(flags.joinToString(" ")).append(']')
                    append('\n')
                }
                if (count >= maxNodes || body.length + line.length > maxChars) {
                    truncated = true
                    return
                }
                body.append(line)
                count++
                childDepth = depth + 1
            }
            for (child in node.children) visit(child, childDepth)
        }

        visit(root, 0)
        val header = "[pip-snapshot v1] app=$appPackage time=$timeIso nodes=$count truncated=${if (truncated) "yes" else "no"}\n"
        return header + body.toString().trimEnd('\n')
    }

    private fun clean(raw: String?): String {
        if (raw == null) return ""
        val oneLine = raw.replace(Regex("\\s+"), " ").trim().replace("\"", "\\\"")
        return if (oneLine.length > MAX_NODE_TEXT) oneLine.take(MAX_NODE_TEXT - 1) + "…" else oneLine
    }
}
