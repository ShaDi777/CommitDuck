package ru.itmo.debugduck

/** Colors as `#rrggbb` strings and an installed monospaced font family, taken from the current IDE theme by the UI. */
data class ChatPalette(
    val text: String,
    val secondaryText: String,
    val userBackground: String,
    val duckBackground: String,
    val noticeBackground: String,
    val noticeText: String,
    val codeBackground: String,
    val codeFont: String,
)

data class ChatLabels(val you: String, val duck: String, val notice: String, val thinking: String)

/**
 * Renders the chat history as HTML for a Swing `JEditorPane`, which supports only HTML 3.2 and a small CSS subset:
 * one class per element, no border-radius, no white-space handling outside `pre`.
 *
 * Message text is escaped, and a small Markdown subset that language models like to use is rendered:
 * fenced code blocks, `inline code`, **bold** and `-`/`*` bullet points.
 */
object ChatHtml {
    private val FENCED_CODE = Regex("```[^\\n`]*\\n(.*?)(?:\\n)?```", RegexOption.DOT_MATCHES_ALL)
    private val INLINE_CODE = Regex("`([^`\\n]+)`")
    private val BOLD = Regex("\\*\\*([^*\\n]+)\\*\\*")
    private val BULLET = Regex("^[ \\t]*[-*][ \\t]+", RegexOption.MULTILINE)

    /**
     * @param partialAnswer the part of the answer streamed so far, shown instead of the "thinking" line
     * @param partialModel the model writing [partialAnswer]
     */
    fun render(
        messages: List<ChatMessage>,
        thinking: Boolean,
        palette: ChatPalette,
        labels: ChatLabels,
        partialAnswer: String? = null,
        partialModel: String? = null,
    ): String = buildString {
        append("<html><head><style>")
        append(css(palette))
        append("</style></head><body>")
        for (message in messages) {
            appendMessage(message, labels)
        }
        if (thinking && !partialAnswer.isNullOrBlank()) {
            appendMessage(ChatMessage(ChatRole.DUCK, partialAnswer, model = partialModel), labels)
        }
        else if (thinking) {
            append("<div class=\"duck\"><div class=\"who\">").append(escape(labels.duck)).append("</div>")
            append("<i>").append(escape(labels.thinking)).append("</i></div>")
        }
        append("</body></html>")
    }

    private fun StringBuilder.appendMessage(message: ChatMessage, labels: ChatLabels) {
        val (cssClass, title) = when (message.role) {
            ChatRole.USER -> "user" to labels.you
            ChatRole.DUCK -> "duck" to (message.model?.let { "${labels.duck} · $it" } ?: labels.duck)
            ChatRole.NOTICE -> "notice" to labels.notice
        }
        append("<div class=\"").append(cssClass).append("\">")
        append("<div class=\"who\">").append(escape(title)).append("</div>")
        if (message.text.isNotBlank()) {
            append(formatText(message.text))
        }
        message.attachment?.let { attachment ->
            append("<div class=\"file\">").append(escape(attachment.location)).append("</div>")
            attachment.code?.let { append("<pre>").append(escape(it)).append("</pre>") }
        }
        for (problem in message.problems) {
            append("<div class=\"problem\">").append(escape(problem.summary)).append("</div>")
        }
        append("</div>")
    }

    /** Escapes [text] and turns the supported Markdown subset into HTML. */
    fun formatText(text: String): String = buildString {
        var position = 0
        for (match in FENCED_CODE.findAll(text)) {
            append(formatInline(text.substring(position, match.range.first)))
            append("<pre>").append(escape(match.groupValues[1])).append("</pre>")
            position = match.range.last + 1
        }
        append(formatInline(text.substring(position)))
    }

    private fun formatInline(text: String): String {
        val trimmed = text.trim('\n')
        if (trimmed.isBlank()) return ""
        val withBullets = trimmed.replace(BULLET, "• ")
        val result = StringBuilder()
        var position = 0
        for (match in INLINE_CODE.findAll(withBullets)) {
            result.append(formatPlain(withBullets.substring(position, match.range.first)))
            result.append("<code>").append(escape(match.groupValues[1])).append("</code>")
            position = match.range.last + 1
        }
        result.append(formatPlain(withBullets.substring(position)))
        return "<div>$result</div>"
    }

    private fun formatPlain(text: String): String =
        escape(text).replace(BOLD, "<b>$1</b>").replace("\n", "<br>")

    fun escape(text: String): String = buildString(text.length) {
        for (c in text) {
            when (c) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(c)
            }
        }
    }

    private fun css(palette: ChatPalette): String {
        // Swing only uses the first font family of a list, so the palette must name an installed font
        val codeFont = palette.codeFont.replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "Monospaced" }
        return """
            body { color: ${palette.text}; margin: 4px; }
            div.user { background-color: ${palette.userBackground}; padding: 6px 8px; margin: 0 0 8px 24px; }
            div.duck { background-color: ${palette.duckBackground}; padding: 6px 8px; margin: 0 24px 8px 0; }
            div.notice { background-color: ${palette.noticeBackground}; color: ${palette.noticeText}; padding: 6px 8px; margin: 0 0 8px 0; }
            div.who { color: ${palette.secondaryText}; font-weight: bold; margin-bottom: 2px; }
            div.file { color: ${palette.secondaryText}; margin-top: 4px; }
            div.problem { color: ${palette.noticeText}; margin-top: 4px; }
            pre { font-family: $codeFont; background-color: ${palette.codeBackground}; padding: 4px; margin: 4px 0 0 0; }
            code { font-family: $codeFont; background-color: ${palette.codeBackground}; }
        """.trimIndent()
    }
}
