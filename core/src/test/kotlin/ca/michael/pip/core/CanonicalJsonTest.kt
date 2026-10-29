package ca.michael.pip.core

import ca.michael.pip.core.acp.CanonicalJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class CanonicalJsonTest {
    @Test
    fun sortsKeysAtEveryLevelWithoutWhitespace() {
        val input = Json.parseToJsonElement("""{ "b": 1, "a": { "z": true, "m": [ {"y":null,"x":"é"} ] } }""")
        assertEquals("""{"a":{"m":[{"x":"é","y":null}],"z":true},"b":1}""", CanonicalJson.encode(input))
    }

    @Test
    fun escapesQuotesBackslashesAndControls() {
        val input = Json.parseToJsonElement("{\"k\":\"a\\\"b\\\\c\\n\\u0001\"}")
        assertEquals("{\"k\":\"a\\\"b\\\\c\\n\\u0001\"}", CanonicalJson.encode(input))
    }
}
