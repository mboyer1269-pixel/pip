package ca.michael.pip.core

import ca.michael.pip.core.actions.ActionCatalog
import ca.michael.pip.core.actions.ActionParse
import ca.michael.pip.core.actions.DeviceAction
import ca.michael.pip.core.actions.Recipient
import ca.michael.pip.core.brain.BrainClient
import ca.michael.pip.core.brain.BrainConfig
import ca.michael.pip.core.brain.BrainResult
import ca.michael.pip.core.brain.ChatMessage
import ca.michael.pip.core.brain.HttpResponse
import ca.michael.pip.core.brain.HttpTransport
import ca.michael.pip.core.brain.TurnBuilder
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** SPEC 13: T-A2 (closed list, argument limits), T-A5 (no tools when off), tool-call protocol. */
class ActionsTest {
    private fun ok(name: String, args: String) = (ActionCatalog.parse(name, args) as ActionParse.Ok).action
    private fun rejected(name: String, args: String) = ActionCatalog.parse(name, args) is ActionParse.Rejected

    @Test fun closedListMatchesTheToolsOffered() {
        val offered = ActionCatalog.tools().map { it.jsonObject.getValue("function").jsonObject.getValue("name").jsonPrimitive.content }
        assertEquals(ActionCatalog.NAMES, offered)
        assertTrue(rejected("delete_contact", "{}"))
        assertTrue(rejected("shell", """{"cmd":"ls"}"""))
    }

    @Test fun recipientsAreNumbersOrNames() {
        assertEquals(Recipient.Number("+18195551234"), ActionCatalog.recipientOf("+1 (819) 555-1234"))
        assertEquals(Recipient.Number("5551234"), ActionCatalog.recipientOf("555-1234"))
        assertEquals(Recipient.Name("Maman"), ActionCatalog.recipientOf(" Maman "))
        assertEquals(DeviceAction.Call(Recipient.Name("Marc")), ok("call", """{"to":"Marc"}"""))
    }

    @Test fun argumentsAreValidated() {
        assertTrue(rejected("send_sms", """{"to":"Marc"}"""), "text is required")
        assertTrue(rejected("send_sms", """{"to":"Marc","text":"${"x".repeat(1001)}"}"""))
        assertTrue(rejected("call", """{"to":"Marc","video":true}"""), "unknown arguments")
        assertTrue(rejected("read_sms", """{"limit":50}"""))
        assertTrue(rejected("read_calendar", """{"from_date":"demain"}"""))
        assertTrue(rejected("add_calendar_event", """{"title":"Dentiste","start":"tomorrow 3pm"}"""))
        assertTrue(rejected("open_app", "not json"))
        assertEquals(DeviceAction.ReadSms(null, 5), ok("read_sms", """{"from":null}"""))
        assertEquals(
            DeviceAction.AddCalendarEvent("Dentiste", LocalDateTime.of(2026, 11, 3, 14, 30), 60, null),
            ok("add_calendar_event", """{"title":"Dentiste","start":"2026-11-03T14:30"}"""),
        )
        assertEquals(DeviceAction.GetSteps, ok("get_steps", ""))
    }

    @Test fun readsAreMarkedAsSendingData() {
        assertTrue(ok("read_call_log", "{}").sendsDataToBrain)
        assertTrue(DeviceAction.GetLocation.sendsDataToBrain)
        assertFalse(ok("call", """{"to":"Marc"}""").sendsDataToBrain)
    }

    private class Recorder(val reply: String) : HttpTransport {
        val bodies = mutableListOf<String>()
        override fun postJson(url: String, headers: Map<String, String>, body: String): HttpResponse { bodies += body; return HttpResponse(200, reply) }
        override fun cancel() {}
    }
    private val cfg = BrainConfig("https://api.example.org/v1", "m", "k")

    @Test fun noToolsAreSentWhenActionsAreOff() {
        val t = Recorder("""{"choices":[{"message":{"content":"ok"}}]}""")
        BrainClient(t) { true }.ask(cfg, listOf(ChatMessage("user", "salut")))
        assertFalse("\"tools\"" in t.bodies.single())
        BrainClient(t) { true }.ask(cfg, listOf(ChatMessage("user", "salut")), ActionCatalog.tools())
        assertTrue("\"tools\"" in t.bodies.last())
    }

    @Test fun toolCallsAreParsedAndReplayedInTheFollowUp() {
        val reply = """{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[
            {"id":"c1","type":"function","function":{"name":"call","arguments":"{\"to\":\"Marc\"}"}}]}}]}"""
        val t = Recorder(reply)
        val turn = TurnBuilder.messages(emptyList(), "appelle Marc", null, null, actionsEnabled = true, localNow = "2026-10-30 10:00")
        val r = BrainClient(t) { true }.ask(cfg, turn, ActionCatalog.tools())
        assertIs<BrainResult.ActionRequest>(r)
        assertEquals("call", r.calls.single().name)

        val follow = TurnBuilder.followUp(turn, r.assistant, listOf("c1" to "refused by the owner"))
        BrainClient(t) { true }.ask(cfg, follow, ActionCatalog.tools())
        val sent = Json.parseToJsonElement(t.bodies.last()).jsonObject.getValue("messages").jsonArray
        val assistant = sent[sent.size - 2].jsonObject
        assertEquals("c1", assistant.getValue("tool_calls").jsonArray[0].jsonObject.getValue("id").jsonPrimitive.content)
        val tool = sent.last().jsonObject
        assertEquals("tool", tool.getValue("role").jsonPrimitive.content)
        assertEquals("c1", tool.getValue("tool_call_id").jsonPrimitive.content)
        assertTrue("[maintenant] 2026-10-30 10:00" in sent[1].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test fun instructionsSayWhetherActionsExist() {
        val on = TurnBuilder.messages(emptyList(), "x", null, null, actionsEnabled = true).first().content
        val off = TurnBuilder.messages(emptyList(), "x", null, null).first().content
        assertTrue("outils" in on && "aucune action" !in on)
        assertTrue("aucune action" in off)
        assertEquals(JsonObject(emptyMap()), JsonObject(emptyMap()))
    }
}
