package ru.itmo.debugduck

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.http.HttpClient
import java.time.Duration

/** Runs the client against a fake OpenAI-compatible server on localhost. */
class OpenAiCompatibleClientTest {
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0).apply { start() }
    private val baseUrl = "http://127.0.0.1:${server.address.port}/v1"
    private val client = OpenAiCompatibleClient(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build())
    private val messages = listOf(LlmMessage("system", "be a duck"), LlmMessage("user", "my \"test\" fails"))

    private var requestPath: String? = null
    private var requestBody: String? = null
    private var authorization: String? = null

    @After
    fun stopServer() = server.stop(0)

    private fun respond(status: Int, body: String, delayMillis: Long = 0) {
        server.createContext("/") { exchange: HttpExchange ->
            requestPath = exchange.requestURI.path
            authorization = exchange.requestHeaders.getFirst("Authorization")
            requestBody = exchange.requestBody.readBytes().decodeToString()
            Thread.sleep(delayMillis)
            val bytes = body.encodeToByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    /** Sends [events] as server-sent events, flushing each one, with [pauseMillis] between them. */
    private fun stream(events: List<String>, pauseMillis: Long = 0, contentType: String = "text/event-stream") {
        server.createContext("/") { exchange: HttpExchange ->
            requestBody = exchange.requestBody.readBytes().decodeToString()
            exchange.responseHeaders.add("Content-Type", contentType)
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.use { out ->
                for (event in events) {
                    out.write("$event\n\n".encodeToByteArray())
                    out.flush()
                    Thread.sleep(pauseMillis)
                }
            }
        }
    }

    private fun delta(text: String) =
        "data: " + MiniJson.stringify(mapOf("choices" to listOf(mapOf("index" to 0, "delta" to mapOf("content" to text)))))

    private fun config(url: String = baseUrl, key: String? = "sk-test", timeoutSeconds: Int = 5) =
        LlmConfig(apiUrl = url, model = "duck-model", apiKey = key, timeoutSeconds = timeoutSeconds)

    private fun completionBody(content: String) =
        """{"choices":[{"index":0,"message":{"role":"assistant","content":${MiniJson.stringify(content)}},"finish_reason":"stop"}]}"""

    private fun expectError(config: LlmConfig = config(), check: (String) -> Unit) {
        try {
            client.complete(config, messages)
            fail("Expected an LlmException")
        }
        catch (e: LlmException) {
            check(e.message!!)
        }
    }

    @Test
    fun `sends the conversation and returns the answer`() {
        respond(200, completionBody("What did you expect to happen?"))

        assertEquals("What did you expect to happen?", client.complete(config(), messages))
        assertEquals("/v1/chat/completions", requestPath)
        assertEquals("Bearer sk-test", authorization)
        val body = MiniJson.parse(requestBody!!)
        assertEquals("duck-model", body.jsonField("model"))
        assertEquals(false, body.jsonField("stream"))
        assertEquals("my \"test\" fails", body.jsonField("messages").jsonIndex(1).jsonField("content"))
        assertEquals("system", body.jsonField("messages").jsonIndex(0).jsonField("role"))
    }

    @Test
    fun `works without an api key`() {
        respond(200, completionBody("Quack"))
        assertEquals("Quack", client.complete(config(key = " "), messages))
        assertNull(authorization)
    }

    @Test
    fun `accepts the full endpoint url`() {
        respond(200, completionBody("Quack"))
        client.complete(config(url = "$baseUrl/chat/completions/"), messages)
        assertEquals("/v1/chat/completions", requestPath)
    }

    @Test
    fun `strips the thinking of reasoning models`() {
        respond(200, completionBody("<think>\nThe user is sad.\n</think>\n\nWhat changed recently?"))
        assertEquals("What changed recently?", client.complete(config(), messages))
    }

    @Test
    fun `explains a rejected api key`() {
        respond(401, """{"error":{"message":"Incorrect API key provided: sk-test","type":"invalid_request_error"}}""")
        expectError { message ->
            assertTrue(message, "API key was rejected" in message)
            assertTrue(message, "Incorrect API key provided" in message)
        }
    }

    @Test
    fun `explains an unknown model in ollama format`() {
        respond(404, """{"error":"model \"duck-model\" not found, try pulling it first"}""")
        expectError { message ->
            assertTrue(message, "HTTP 404" in message)
            assertTrue(message, "try pulling it first" in message)
        }
    }

    @Test
    fun `explains rate limits`() {
        respond(429, "Too Many Requests")
        expectError { assertTrue(it, "HTTP 429" in it && "Too Many Requests" in it) }
    }

    @Test
    fun `does not show html error pages`() {
        respond(502, "<html><body><h1>502 Bad Gateway</h1></body></html>")
        expectError { assertEquals("The LLM server failed (HTTP 502). Try again later.", it) }
    }

    @Test
    fun `explains a response that is not json`() {
        respond(200, "<html>login page</html>")
        expectError { assertTrue(it, "did not return JSON" in it) }
    }

    @Test
    fun `explains an empty answer`() {
        respond(200, """{"choices":[{"message":{"role":"assistant","content":""},"finish_reason":"length"}]}""")
        expectError { assertTrue(it, "empty answer" in it && "length" in it) }
    }

    @Test
    fun `reports an error inside a successful response`() {
        respond(200, """{"error":{"message":"Provider returned error"}}""")
        expectError { assertTrue(it, "Provider returned error" in it) }
    }

    @Test
    fun `times out instead of hanging`() {
        respond(200, completionBody("too late"), delayMillis = 3_000)
        val started = System.nanoTime()
        expectError(config(timeoutSeconds = 1)) { assertTrue(it, "did not answer within 1 seconds" in it) }
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(2_500))
    }

    @Test
    fun `explains that the server is not running`() {
        val freePort = ServerSocket(0, 0, InetAddress.getLoopbackAddress()).use { it.localPort }
        expectError(config(url = "http://127.0.0.1:$freePort/v1")) {
            assertTrue(it, "Could not connect to 127.0.0.1:$freePort" in it)
        }
    }

    @Test
    fun `validates the url and the model`() {
        expectError(config(url = "")) { assertTrue(it, "API URL is empty" in it) }
        expectError(config(url = "api.openai.com/v1")) { assertTrue(it, "must start with http" in it) }
        expectError(config().copy(model = " ")) { assertTrue(it, "No model" in it) }
    }

    @Test
    fun `builds the endpoint from the base url`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleClient.chatCompletionsUri("https://api.openai.com/v1/").toString(),
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleClient.chatCompletionsUri(" http://localhost:11434/v1 ").toString(),
        )
    }

    @Test
    fun `streams the answer piece by piece`() {
        stream(listOf(": OPENROUTER PROCESSING", delta("What "), delta("did you "), delta("expect?"), "data: [DONE]"))
        val partials = mutableListOf<String>()
        assertEquals("What did you expect?", client.complete(config(), messages) { partials += it })
        assertEquals(listOf("What ", "What did you ", "What did you expect?"), partials)
        assertEquals(true, MiniJson.parse(requestBody!!).jsonField("stream"))
    }

    @Test
    fun `hides the thinking of reasoning models while streaming`() {
        stream(listOf(delta("<think>The user"), delta(" is sad</think>"), delta("\n\nWhy?"), "data: [DONE]"))
        val partials = mutableListOf<String>()
        assertEquals("Why?", client.complete(config(), messages) { partials += it })
        assertEquals(listOf("Why?"), partials)
    }

    @Test
    fun `handles servers that ignore the stream flag`() {
        respond(200, completionBody("Quack"))
        val partials = mutableListOf<String>()
        assertEquals("Quack", client.complete(config(), messages) { partials += it })
        assertTrue(partials.isEmpty())
    }

    @Test
    fun `reports an error sent in the middle of a stream`() {
        stream(listOf(delta("Wh"), "data: {\"error\":{\"message\":\"Rate limit exceeded\"}}"))
        try {
            client.complete(config(), messages) {}
            fail("Expected an LlmException")
        }
        catch (e: LlmException) {
            assertTrue(e.message, "Rate limit exceeded" in e.message!!)
        }
    }

    @Test
    fun `an empty stream is an error`() {
        stream(listOf("data: [DONE]"))
        try {
            client.complete(config(), messages) {}
            fail("Expected an LlmException")
        }
        catch (e: LlmException) {
            assertTrue(e.message, "empty answer" in e.message!!)
        }
    }

    @Test
    fun `stops streaming when the thread is interrupted`() {
        stream(listOf(delta("one"), delta(" two"), delta(" three")), pauseMillis = 3_000)
        var outcome: Throwable? = null
        val firstPiece = java.util.concurrent.CountDownLatch(1)
        val worker = Thread {
            try {
                client.complete(config(timeoutSeconds = 30), messages) { firstPiece.countDown() }
            }
            catch (e: Throwable) {
                outcome = e
            }
        }
        worker.start()
        assertTrue(firstPiece.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val started = System.nanoTime()
        worker.interrupt()
        worker.join(5_000)
        assertTrue("still running", !worker.isAlive)
        assertTrue(outcome.toString(), outcome is InterruptedException)
        assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofMillis(2_000))
    }

    @Test
    fun `recognises local servers`() {
        assertTrue(OpenAiCompatibleClient.isLocal("http://localhost:11434/v1"))
        assertTrue(OpenAiCompatibleClient.isLocal("http://127.0.0.1:1234/v1"))
        assertTrue(!OpenAiCompatibleClient.isLocal("https://api.openai.com/v1"))
        assertTrue(!OpenAiCompatibleClient.isLocal("not a url"))
    }

    @Test
    fun `accepts content split into parts`() {
        assertEquals(
            "Quack? Why?",
            OpenAiCompatibleClient.parseAnswer("""{"choices":[{"message":{"content":[{"type":"text","text":"Quack? "},{"type":"text","text":"Why?"}]}}]}"""),
        )
    }
}
