package ru.itmo.debugduck

/** A conversation as it is kept between IDE restarts. */
data class StoredConversation(val messages: List<ChatMessage>, val solved: Boolean)

/**
 * Turns a Debug Duck conversation into a single JSON string and back, so it can be kept in the project's workspace
 * file. Reading is lenient: anything unexpected yields `null` (start a new conversation) or skips the broken message.
 */
object ConversationCodec {
    /** Older messages are dropped, so the workspace file doesn't grow forever. */
    const val MAX_STORED_MESSAGES = 200
    private const val VERSION = 1L

    fun encode(conversation: StoredConversation): String = MiniJson.stringify(
        mapOf(
            "version" to VERSION,
            "solved" to conversation.solved,
            "messages" to conversation.messages.takeLast(MAX_STORED_MESSAGES).map(::encodeMessage),
        )
    )

    fun decode(json: String?): StoredConversation? {
        if (json.isNullOrBlank()) return null
        val root = try {
            MiniJson.parse(json)
        }
        catch (e: IllegalArgumentException) {
            return null
        }
        if (root.jsonField("version") != VERSION) return null
        val messages = (root.jsonField("messages") as? List<*>)?.mapNotNull(::decodeMessage) ?: return null
        if (messages.isEmpty()) return null
        return StoredConversation(messages, root.jsonField("solved") == true)
    }

    private fun encodeMessage(message: ChatMessage): Map<String, Any?> = buildMap {
        put("role", message.role.name)
        put("text", message.text)
        message.model?.let { put("model", it) }
        message.attachment?.let { attachment ->
            put(
                "attachment",
                mapOf(
                    "fileName" to attachment.fileName,
                    "language" to attachment.language,
                    "code" to attachment.code,
                    "startLine" to attachment.startLine,
                )
            )
        }
        if (message.problems.isNotEmpty()) {
            put("problems", message.problems.map {
                mapOf("kind" to it.kind.name, "message" to it.message, "location" to it.location, "details" to it.details)
            })
        }
    }

    private fun decodeMessage(json: Any?): ChatMessage? {
        val role = enumValue<ChatRole>(json.jsonField("role")) ?: return null
        val text = json.jsonField("text") as? String ?: return null
        val attachment = json.jsonField("attachment")?.let { attachment ->
            val fileName = attachment.jsonField("fileName") as? String ?: return@let null
            CodeAttachment(
                fileName = fileName,
                language = attachment.jsonField("language") as? String,
                code = attachment.jsonField("code") as? String,
                startLine = (attachment.jsonField("startLine") as? Number)?.toInt(),
            )
        }
        val problems = (json.jsonField("problems") as? List<*>).orEmpty().mapNotNull { problem ->
            val kind = enumValue<ProblemKind>(problem.jsonField("kind")) ?: return@mapNotNull null
            val message = problem.jsonField("message") as? String ?: return@mapNotNull null
            IdeProblem(kind, message, problem.jsonField("location") as? String, problem.jsonField("details") as? String)
        }
        return ChatMessage(role, text, attachment, json.jsonField("model") as? String, problems)
    }

    private inline fun <reified E : Enum<E>> enumValue(name: Any?): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
