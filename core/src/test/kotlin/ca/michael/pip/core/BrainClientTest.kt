package ca.michael.pip.core

import ca.michael.pip.core.brain.BrainClient
import ca.michael.pip.core.brain.BrainConfig
import ca.michael.pip.core.brain.BrainResult
import ca.michael.pip.core.brain.ChatMessage
import ca.michael.pip.core.brain.HttpResponse
import ca.michael.pip.core.brain.HttpTransport
import ca.michael.pip.core.brain.TurnBuilder
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrainClientTest {
    private class Recorder(var reply: () -> HttpResponse) : HttpTransport {
        var calls = 0
        override fun postJson(url: String, headers: Map<String, String>, body: String): HttpResponse { calls++; return reply() }
        override fun cancel() {}
    }

    private val good = BrainConfig("https://api.example.org/v1", "m", "k")
    private val msgs = listOf(ChatMessage("user", "salut"))

    /** T-2: empty key gives a clear "no key" result and makes no request. */
    @Test fun emptyKeyMakesNoRequest() {
        val t = Recorder { HttpResponse(200, "") }
        assertEquals(BrainResult.NoKey, BrainClient(t) { true }.ask(good.copy(key = ""), msgs))
        assertEquals(0, t.calls)
    }

    @Test fun errorsAreToldApart() {
        fun run(net: Boolean = true, reply: () -> HttpResponse) = BrainClient(Recorder(reply)) { net }.ask(good, msgs)
        assertEquals(BrainResult.NoNetwork, run(net = false) { HttpResponse(200, "") })
        assertEquals(BrainResult.KeyRefused(401), run { HttpResponse(401, "") })
        assertEquals(BrainResult.ServerError(503), run { HttpResponse(503, "") })
        assertEquals(BrainResult.Timeout, run { throw SocketTimeoutException() })
        assertEquals(BrainResult.Unreachable, run { throw IOException() })
        assertEquals(BrainResult.Malformed, run { HttpResponse(200, "{}") })
        assertEquals(
            BrainResult.Answer("Allô !"),
            run { HttpResponse(200, """{"choices":[{"message":{"role":"assistant","content":" Allô ! "}}]}""") },
        )
    }

    @Test fun turnSendsOnlyWhatWasAttached() {
        val plain = TurnBuilder.messages(emptyList(), "salut", null, null).last().content
        assertEquals("salut", plain)
        val withBoth = TurnBuilder.messages(emptyList(), "salut", "[pip-snapshot v1] x", "- note: y").last().content
        assertTrue("[écran]" in withBoth && "[carnet]" in withBoth)
        assertFalse("[carnet]" in TurnBuilder.messages(emptyList(), "salut", "snap", null).last().content)
    }
}
