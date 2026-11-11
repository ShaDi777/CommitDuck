package ru.itmo.debugduck

import java.io.IOException
import java.net.Authenticator
import java.net.ConnectException
import java.net.ProxySelector
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration

data class LlmMessage(val role: String, val content: String) {
    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"
    }
}

data class LlmConfig(
    /** Base URL of an OpenAI-compatible API, e.g. `https://api.openai.com/v1` or `http://localhost:11434/v1`. */
    val apiUrl: String,
    val model: String,
    /** `null` or blank for servers that don't need a key, e.g. a local Ollama. */
    val apiKey: String?,
    val timeoutSeconds: Int,
)

/** A request to the language model failed. [message] is meant to be shown to the user as is. */
class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Client for the OpenAI Chat Completions API (`POST {apiUrl}/chat/completions`). The same format is served by
 * OpenAI, OpenRouter, DeepSeek, Mistral, Groq, and local servers such as Ollama and LM Studio, so switching providers
 * only needs a different URL, model and key.
 *
 * [complete] is blocking: call it from a background thread. It reacts to thread interruption.
 */
class OpenAiCompatibleClient(private val httpClient: HttpClient = sharedHttpClient) {

    /**
     * Sends the conversation and returns the model's answer.
     * @throws LlmException with a user-readable message if the request fails for any reason
     */
    fun complete(config: LlmConfig, messages: List<LlmMessage>): String {
        val uri = chatCompletionsUri(config.apiUrl)
        val model = config.model.trim()
        if (model.isEmpty()) throw LlmException("No model is configured. Set one in Settings | Tools | Commit Duck.")

        val body = MiniJson.stringify(
            mapOf(
                "model" to model,
                "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
                "stream" to false,
            )
        )
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(config.timeoutSeconds.coerceAtLeast(1).toLong()))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .apply { config.apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let { header("Authorization", "Bearer $it") } }
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }
        catch (e: HttpConnectTimeoutException) {
            throw LlmException("Could not connect to ${uri.authority} in time. Check the API URL and your network or proxy.", e)
        }
        catch (e: HttpTimeoutException) {
            throw LlmException(
                "The model did not answer within ${config.timeoutSeconds} seconds. Try again or increase the timeout in the settings.", e
            )
        }
        catch (e: ConnectException) {
            throw LlmException("Could not connect to ${uri.authority}. Check the API URL and that the server is running.", e)
        }
        catch (e: IOException) {
            throw LlmException("Network error while contacting ${uri.authority}: ${e.message ?: e.javaClass.simpleName}", e)
        }

        val status = response.statusCode()
        if (status !in 200..299) {
            throw LlmException(describeHttpError(status, response.body(), response.headers().firstValue("Location").orElse(null)))
        }
        return parseAnswer(response.body())
    }

    companion object {
        private val sharedHttpClient: HttpClient by lazy {
            HttpClient.newBuilder()
                // plain HTTP/1.1: local servers (Ollama, LM Studio) don't like the h2c upgrade
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                // inside the IDE these are the IDE's proxy settings
                .apply { ProxySelector.getDefault()?.let { proxy(it) } }
                .apply { Authenticator.getDefault()?.let { authenticator(it) } }
                .build()
        }

        private const val CHAT_COMPLETIONS = "/chat/completions"
        private val THINKING = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

        /** `https://api.openai.com/v1` -> `https://api.openai.com/v1/chat/completions`; a full endpoint URL is kept. */
        fun chatCompletionsUri(apiUrl: String): URI {
            val trimmed = apiUrl.trim().trimEnd('/')
            if (trimmed.isEmpty()) throw LlmException("The API URL is empty. Set it in Settings | Tools | Commit Duck.")
            val full = if (trimmed.endsWith(CHAT_COMPLETIONS)) trimmed else trimmed + CHAT_COMPLETIONS
            val uri = try {
                URI(full)
            }
            catch (e: URISyntaxException) {
                throw LlmException("The API URL is not valid: $apiUrl", e)
            }
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrEmpty()) {
                throw LlmException("The API URL must start with http:// or https://, e.g. https://api.openai.com/v1")
            }
            return uri
        }

        /** Extracts the answer text from a chat-completion response body. */
        fun parseAnswer(body: String): String {
            val json = try {
                MiniJson.parse(body)
            }
            catch (e: IllegalArgumentException) {
                throw LlmException("The server did not return JSON. Does the API URL point to an OpenAI-compatible API?", e)
            }
            errorDetail(json)?.let { throw LlmException("The LLM server returned an error: $it") }

            val choice = json.jsonField("choices").jsonIndex(0)
            val text = when (val content = choice.jsonField("message").jsonField("content")) {
                is String -> content
                // some servers return content as a list of parts: [{"type": "text", "text": "..."}]
                is List<*> -> content.joinToString("") { part -> part.jsonField("text") as? String ?: "" }
                else -> null
            }
            if (text == null && choice == null) {
                throw LlmException("Unexpected response from the server: no answer in it. Is it an OpenAI-compatible API?")
            }
            val answer = text.orEmpty().replace(THINKING, "").trim()
            if (answer.isEmpty()) {
                val reason = choice.jsonField("finish_reason") as? String
                throw LlmException("The model returned an empty answer" + (reason?.let { " (finish reason: $it)" } ?: "") + ". Try again.")
            }
            return answer
        }

        fun describeHttpError(status: Int, body: String?, location: String? = null): String {
            val reason = when (status) {
                in 300..399 -> "The server redirected the request (HTTP $status)" +
                    (location?.let { " to $it. Use that address as the API URL." } ?: ". Check the API URL.")
                400 -> "The server rejected the request (HTTP 400). Check the model name."
                401 -> "The API key was rejected (HTTP 401). Check the key in Settings | Tools | Commit Duck."
                402 -> "Payment required (HTTP 402). Check the balance of your LLM account."
                403 -> "Access denied (HTTP 403). The key may not have access to this model or region."
                404 -> "Not found (HTTP 404). Check the API URL and the model name."
                408, 504 -> "The server timed out (HTTP $status). Try again."
                429 -> "Too many requests or the quota is used up (HTTP 429). Wait a bit and try again."
                in 500..599 -> "The LLM server failed (HTTP $status). Try again later."
                else -> "Unexpected response from the LLM server (HTTP $status)."
            }
            val detail = body?.let { text ->
                val json = try {
                    MiniJson.parse(text)
                }
                catch (e: IllegalArgumentException) {
                    // short plain-text bodies are usually readable error messages, HTML pages are not
                    return@let text.trim().takeIf { it.isNotEmpty() && it.length <= 200 && !it.startsWith("<") }
                }
                errorDetail(json)
            }
            return if (detail == null) reason else "$reason Server says: ${detail.take(300)}"
        }

        /** `{"error": {"message": "..."}}` (OpenAI), `{"error": "..."}` (Ollama) or `{"message": "..."}`. */
        private fun errorDetail(json: Any?): String? {
            val error = json.jsonField("error")
            return when {
                error is String -> error
                error is Map<*, *> -> error["message"] as? String ?: error.toString()
                json.jsonField("choices") == null -> json.jsonField("message") as? String ?: json.jsonField("detail") as? String
                else -> null
            }
        }
    }
}
