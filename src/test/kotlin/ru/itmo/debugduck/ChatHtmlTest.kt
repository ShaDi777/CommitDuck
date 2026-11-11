package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHtmlTest {
    private val palette = ChatPalette("#000000", "#666666", "#e0e0ff", "#fff8d0", "#ffe0e0", "#aa0000", "#eeeeee", "JetBrains Mono")
    private val labels = ChatLabels("You", "Duck", "Commit Duck", "The duck is thinking...")

    @Test
    fun `escapes html in messages`() {
        val html = ChatHtml.render(listOf(ChatMessage(ChatRole.USER, "<script>alert(1)</script> & co")), false, palette, labels)
        assertTrue(html, "&lt;script&gt;alert(1)&lt;/script&gt; &amp; co" in html)
        assertFalse(html, "<script>" in html)
    }

    @Test
    fun `renders markdown code, bold and bullets`() {
        assertEquals(
            "<div>Check <code>a &lt; b</code> and <b>really</b> look:</div><pre>if (a &lt; b) {\n}</pre><div>• one<br>• two</div>",
            ChatHtml.formatText("Check `a < b` and **really** look:\n```kotlin\nif (a < b) {\n}\n```\n- one\n* two"),
        )
    }

    @Test
    fun `shows IDE problems and the selected lines`() {
        val html = ChatHtml.render(
            listOf(
                ChatMessage(
                    ChatRole.USER, "why?", CodeAttachment.of("A.kt", "Kotlin", "a\nb", startLine = 5),
                    problems = listOf(IdeProblem(ProblemKind.CODE, "Unresolved reference: <foo>", "A.kt:6")),
                )
            ),
            thinking = false, palette, labels,
        )
        assertTrue(html, "<div class=\"file\">A.kt:5-6</div>" in html)
        assertTrue(html, "<div class=\"problem\">A.kt:6: Unresolved reference: &lt;foo&gt;</div>" in html)
    }

    @Test
    fun `turns newlines into line breaks`() {
        assertEquals("<div>a<br>b</div>", ChatHtml.formatText("a\nb"))
    }

    @Test
    fun `shows the model name, attachments and the thinking indicator`() {
        val html = ChatHtml.render(
            listOf(
                ChatMessage(ChatRole.USER, "why?", CodeAttachment.of("A.kt", "Kotlin", "val a = 1")),
                ChatMessage(ChatRole.DUCK, "Quack?", model = "gpt-5-mini"),
            ),
            thinking = true, palette, labels,
        )
        assertTrue(html, "Duck · gpt-5-mini" in html)
        assertTrue(html, "<div class=\"file\">A.kt</div><pre>val a = 1</pre>" in html)
        assertTrue(html, "The duck is thinking..." in html)
    }
}
