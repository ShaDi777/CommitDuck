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
    /** 1-based line of the editor where the selection starts; `null` if unknown. */
    val startLine: Int? = null,
) {
    val lineCount: Int
        get() = code?.lines()?.size ?: 0

    /** `Main.java:13-15`, `Main.java:13` or just `Main.java`. */
    val location: String
        get() = when {
            startLine == null || code == null -> fileName
            lineCount <= 1 -> "$fileName:$startLine"
            else -> "$fileName:$startLine-${startLine + lineCount - 1}"
        }

    companion object {
        /** Keeps prompts small: a selection longer than this is cut off. */
        const val MAX_CODE_LENGTH = 6_000

        fun of(fileName: String, language: String?, selectedText: String?, startLine: Int? = null): CodeAttachment {
            val code = selectedText?.takeIf { it.isNotBlank() }?.let {
                if (it.length > MAX_CODE_LENGTH) it.take(MAX_CODE_LENGTH) + "\n… (truncated)" else it
            }
            return CodeAttachment(fileName, language, code, startLine)
        }
    }
}

/** Where the IDE noticed a problem. */
enum class ProblemKind {
    /** An error highlighted in the editor (usually a compile error). */
    CODE,

    /** An error from the last project build. */
    BUILD,

    /** A failed test from the last test run. */
    TEST,
}

/** A problem the IDE already knows about, so the user doesn't have to retype it for the duck. */
data class IdeProblem(
    val kind: ProblemKind,
    /** The error text, e.g. `';' expected` or `expected:<2> but was:<3>`. */
    val message: String,
    /** `Main.java:13` for code and build errors, the test name for tests; `null` if unknown. */
    val location: String? = null,
    /** Extra text such as the top of a stack trace; `null` if none. */
    val details: String? = null,
) {
    /** `Main.java:13: ';' expected`. */
    val summary: String
        get() = if (location == null) message else "$location: $message"

    companion object {
        /** Keeps prompts small: at most this many problems are attached to one message. */
        const val MAX_PER_MESSAGE = 8

        /** Stack traces are cut to this many lines; the top is what matters. */
        const val MAX_DETAIL_LINES = 15

        fun details(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }?.lines()?.let { lines ->
            if (lines.size <= MAX_DETAIL_LINES) lines.joinToString("\n")
            else (lines.take(MAX_DETAIL_LINES) + "\t... (${lines.size - MAX_DETAIL_LINES} more lines)").joinToString("\n")
        }
    }
}

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val attachment: CodeAttachment? = null,
    /** For [ChatRole.DUCK]: the model that wrote the answer, `null` for built-in questions. */
    val model: String? = null,
    /** For [ChatRole.USER]: errors from the IDE sent along with the message. */
    val problems: List<IdeProblem> = emptyList(),
)
