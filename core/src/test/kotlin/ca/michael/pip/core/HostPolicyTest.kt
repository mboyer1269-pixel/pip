package ca.michael.pip.core

import ca.michael.pip.core.acp.AcpHostPolicy
import ca.michael.pip.core.brain.BrainUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** T-11: non-allowlisted ACP host and non-https brain URL are refused. */
class HostPolicyTest {
    private val allow = listOf("orchestrator.example.org")

    @Test fun onlyListedHostsAreAllowed() {
        assertTrue(AcpHostPolicy.isAllowed("orchestrator.example.org", allow))
        assertFalse(AcpHostPolicy.isAllowed("other.example.org", allow))
        assertFalse(AcpHostPolicy.isAllowed("orchestrator.example.org:8080", allow))
        assertFalse(AcpHostPolicy.isAllowed("orchestrator.example.org", emptyList()))
        assertEquals("wss://orchestrator.example.org/acp", AcpHostPolicy.urlFor("orchestrator.example.org", allow))
        assertNull(AcpHostPolicy.urlFor("evil.example.net", allow))
    }

    @Test fun brainUrlMustBeHttps() {
        assertEquals("https://api.example.org/v1", BrainUrl.normalize("https://api.example.org/v1/"))
        assertNull(BrainUrl.normalize("http://api.example.org/v1"))
        assertNull(BrainUrl.normalize("ftp://api.example.org"))
        assertNull(BrainUrl.normalize("https://user:pw@api.example.org"))
        assertNull(BrainUrl.normalize("api.example.org"))
    }
}
