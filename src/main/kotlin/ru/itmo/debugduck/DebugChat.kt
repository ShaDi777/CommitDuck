package ru.itmo.debugduck

/** Who wrote a message in the Debug Duck chat. */
enum class ChatRole {
    USER,
    DUCK,

    /** A notice from the plugin itself (e.g. the language model is unreachable). Never sent to the model. */
    NOTICE,
}

/** Code the user attached to a message: the editor selection, or nothing but the file name if nothing is selected. */
data class CodeAttachment(
    val fileName: String,
    /** File type name as shown by the IDE, e.g. `Kotlin`; `null` if unknown. */
    val language: String?,
    /** Selected code, already truncated to [MAX_CODE_LENGTH]; `null` if nothing was selected. */
    val code: String?,
) {
    val lineCount: Int
        get() = code?.lines()?.size ?: 0

    companion object {
        /** Keeps prompts small: a selection longer than this is cut off. */
        const val MAX_CODE_LENGTH = 6_000

        fun of(fileName: String, language: String?, selectedText: String?): CodeAttachment {
            val code = selectedText?.takeIf { it.isNotBlank() }?.let {
                if (it.length > MAX_CODE_LENGTH) it.take(MAX_CODE_LENGTH) + "\n… (truncated)" else it
            }
            return CodeAttachment(fileName, language, code)
        }
    }
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val attachment: CodeAttachment? = null,
    /** For [ChatRole.DUCK]: the model that wrote the answer, `null` for built-in questions. */
    val model: String? = null,
)
