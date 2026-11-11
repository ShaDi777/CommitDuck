package ru.itmo.debugduck

/**
 * How a Debug Duck conversation is presented to the language model: the system prompt that makes the model act as a
 * rubber duck asking leading questions, and the conversion of the chat history into chat-completion messages.
 */
object DebugDuckPrompt {
    /** Only the latest messages are sent, so long conversations don't blow up the prompt. */
    const val MAX_HISTORY_MESSAGES = 30

    val SYSTEM_PROMPT = """
        You are Debug Duck, a friendly rubber duck that lives in the developer's JetBrains IDE (the Commit Duck plugin)
        and helps them debug by asking questions.

        How you work:
        - Your goal is to help the developer find the cause of the problem themselves. Rubber duck debugging works
          because explaining the problem forces careful thinking.
        - Ask one or two short, specific leading questions per reply, based on what the developer has told you.
          Refer to the details they mentioned: error messages, names, code, steps they tried.
        - Do not hand over a complete solution or rewritten code right away. Guide them instead: what to check,
          where to look, what to log, which assumption to verify, what small experiment to run.
        - If the developer explicitly asks for the answer, or is clearly stuck after several exchanges, give a concrete
          hint or name the most likely cause and briefly explain why. Even then prefer a small snippet over a rewrite.
        - When the developer shares code, read it carefully and ask about the specific lines that look suspicious.
        - When the problem is solved, congratulate them briefly and ask what the root cause was, or whether a test
          would keep the bug from coming back.
        - Keep replies short: about 80 words at most. Plain text; use Markdown only for code (`inline` or fenced blocks).
        - Be warm and encouraging. An occasional "quack" is welcome, but don't overdo it.
        - Reply in the language the developer writes in.
        - If the message is not about software development, gently steer the conversation back to the problem.
    """.trimIndent()

    fun buildMessages(history: List<ChatMessage>): List<LlmMessage> {
        val conversation = history
            .filter { it.role != ChatRole.NOTICE }
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { message ->
                when (message.role) {
                    ChatRole.USER -> LlmMessage(LlmMessage.USER, userContent(message))
                    else -> LlmMessage(LlmMessage.ASSISTANT, message.text)
                }
            }
        return listOf(LlmMessage(LlmMessage.SYSTEM, SYSTEM_PROMPT)) + conversation
    }

    /** The user's text plus the attached code, formatted as Markdown so the model sees the file and language. */
    fun userContent(message: ChatMessage): String {
        val attachment = message.attachment ?: return message.text
        val code = attachment.code
        val context = if (code == null) {
            "(The file currently open in my editor is ${attachment.fileName}.)"
        }
        else {
            val language = attachment.language?.takeUnless { it.equals("Plain text", ignoreCase = true) }
            val fence = fenceFor(code)
            val tag = language?.lowercase()?.filter { it.isLetterOrDigit() || it == '+' || it == '#' }.orEmpty()
            val title = "Code from ${attachment.fileName}" + (language?.let { " ($it)" } ?: "") + ":"
            "$title\n$fence$tag\n$code\n$fence"
        }
        return if (message.text.isBlank()) context else "${message.text}\n\n$context"
    }

    /** A Markdown fence longer than any run of backticks inside [code], so the code can't close it early. */
    private fun fenceFor(code: String): String {
        val longestRun = Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat(maxOf(3, longestRun + 1))
    }
}
