package ca.michael.pip.core.acp

/**
 * ACP-2/ACP-3: Pip connects only over wss:// and only to a host listed at
 * build time. Anything else is refused before any connection is attempted.
 */
object AcpHostPolicy {
    private val HOSTNAME = Regex("^(?=.{1,253}$)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")

    /** Path on the orchestrator. Not set by SPEC; chosen for Pip and listed in README. */
    const val PATH = "/acp"

    fun isAllowed(host: String, allowlist: List<String>): Boolean {
        val h = host.trim().lowercase()
        if (!HOSTNAME.matches(h)) return false
        return allowlist.any { it.trim().lowercase() == h }
    }

    /** The only URL shape Pip ever opens for ACP, or null when the host is refused. */
    fun urlFor(host: String, allowlist: List<String>): String? =
        if (isAllowed(host, allowlist)) "wss://${host.trim().lowercase()}$PATH" else null
}
