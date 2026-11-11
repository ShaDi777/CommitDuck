package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiniJsonTest {
    @Test
    fun `writes nested values`() {
        val json = MiniJson.stringify(
            mapOf("model" to "gpt", "stream" to false, "n" to 2, "t" to 0.5, "none" to null, "list" to listOf(1, "a"))
        )
        assertEquals("""{"model":"gpt","stream":false,"n":2,"t":0.5,"none":null,"list":[1,"a"]}""", json)
    }

    @Test
    fun `escapes strings`() {
        assertEquals("\"a\\\"b\\\\c\\nd\\te\\u0001\"", MiniJson.stringify("a\"b\\c\nd\te\u0001"))
    }

    @Test
    fun `round-trips text with quotes, newlines and unicode`() {
        val text = "fun main() {\n\tprintln(\"Привет, утка! 🦆\")\n}"
        assertEquals(text, MiniJson.parse(MiniJson.stringify(text)))
    }

    @Test
    fun `parses a chat completion response`() {
        val json = MiniJson.parse(
            """
            {
              "id": "chatcmpl-1", "object": "chat.completion", "created": 1700000000,
              "choices": [{"index": 0, "message": {"role": "assistant", "content": "Quack?\nWhat did you expect?"},
                           "finish_reason": "stop", "logprobs": null}],
              "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15, "cost": 1.5e-5}
            }
            """
        )
        val message = json.jsonField("choices").jsonIndex(0).jsonField("message")
        assertEquals("Quack?\nWhat did you expect?", message.jsonField("content"))
        assertEquals(1700000000L, json.jsonField("created"))
        assertEquals(1.5e-5, json.jsonField("usage").jsonField("cost"))
        assertNull(json.jsonField("choices").jsonIndex(0).jsonField("logprobs"))
    }

    @Test
    fun `decodes escapes`() {
        assertEquals("a/bé\n", MiniJson.parse("\"a\\/b\\u00e9\\n\""))
    }

    @Test
    fun `parses empty containers and literals`() {
        assertEquals(emptyMap<String, Any?>(), MiniJson.parse(" { } "))
        assertEquals(emptyList<Any?>(), MiniJson.parse("[]"))
        assertEquals(listOf(true, false, null, -3L, 2.5), MiniJson.parse("[true, false, null, -3, 2.5]"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated json`() {
        MiniJson.parse("""{"choices": [""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects html`() {
        MiniJson.parse("<html><body>Bad gateway</body></html>")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects trailing garbage`() {
        MiniJson.parse("{} {}")
    }
}
