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

    /** @param language always answer in this language; `null` to answer in the language the user writes in */
    fun buildMessages(history: List<ChatMessage>, language: DuckLanguage? = null): List<LlmMessage> {
        val conversation = history
            .filter { it.role != ChatRole.NOTICE }
            .takeLast(MAX_HISTORY_MESSAGES)
            .map { message ->
                when (message.role) {
                    ChatRole.USER -> LlmMessage(LlmMessage.USER, userContent(message))
                    else -> LlmMessage(LlmMessage.ASSISTANT, message.text)
                }
            }
        val system = when (language) {
            null -> SYSTEM_PROMPT
            DuckLanguage.ENGLISH -> "$SYSTEM_PROMPT\n- Always reply in English, whatever language the developer writes in."
            DuckLanguage.RUSSIAN -> "$SYSTEM_PROMPT\n- Always reply in Russian, whatever language the developer writes in."
        }
        return listOf(LlmMessage(LlmMessage.SYSTEM, system)) + conversation
    }

    /**
     * The user's text plus the attached code and the errors reported by the IDE, formatted as Markdown so the model
     * sees the file, the language and the exact error messages.
     */
    fun userContent(message: ChatMessage): String {
        val parts = mutableListOf<String>()
        if (message.text.isNotBlank()) parts += message.text
        message.attachment?.let { parts += attachmentContent(it) }
        if (message.problems.isNotEmpty()) parts += problemsContent(message.problems)
        return parts.joinToString("\n\n")
    }

    private fun attachmentContent(attachment: CodeAttachment): String {
        val code = attachment.code ?: return "(The file currently open in my editor is ${attachment.fileName}.)"
        val language = attachment.language?.takeUnless { it.equals("Plain text", ignoreCase = true) }
        val fence = fenceFor(code)
        val tag = language?.lowercase()?.filter { it.isLetterOrDigit() || it == '+' || it == '#' }.orEmpty()
        val title = "Code from ${attachment.location}" + (language?.let { " ($it)" } ?: "") + ":"
        return "$title\n$fence$tag\n$code\n$fence"
    }

    private fun problemsContent(problems: List<IdeProblem>): String = buildString {
        append("Problems reported by the IDE:")
        for (problem in problems) {
            val prefix = when (problem.kind) {
                ProblemKind.CODE -> "Error"
                ProblemKind.BUILD -> "Build error"
                ProblemKind.TEST -> "Failed test"
            }
            append("\n- ").append(prefix).append(' ').append(problem.summary)
            problem.details?.let { details ->
                val fence = fenceFor(details)
                append("\n").append(fence).append('\n').append(details).append('\n').append(fence)
            }
        }
    }

    /** A Markdown fence longer than any run of backticks inside [code], so the code can't close it early. */
    private fun fenceFor(code: String): String {
        val longestRun = Regex("`+").findAll(code).maxOfOrNull { it.value.length } ?: 0
        return "`".repeat(maxOf(3, longestRun + 1))
    }
}
