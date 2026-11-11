package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugDuckPromptTest {
    @Test
    fun `starts with the system prompt and keeps the conversation order`() {
        val messages = DebugDuckPrompt.buildMessages(
            listOf(
                ChatMessage(ChatRole.DUCK, "Quack! What's wrong?"),
                ChatMessage(ChatRole.USER, "My test fails"),
                ChatMessage(ChatRole.DUCK, "What does the assertion say?", model = "gpt"),
                ChatMessage(ChatRole.USER, "expected 2 but was 3"),
            )
        )
        assertEquals(listOf("system", "assistant", "user", "assistant", "user"), messages.map { it.role })
        assertEquals(DebugDuckPrompt.SYSTEM_PROMPT, messages.first().content)
        assertEquals("expected 2 but was 3", messages.last().content)
    }

    @Test
    fun `system prompt asks for leading questions instead of ready solutions`() {
        val prompt = DebugDuckPrompt.SYSTEM_PROMPT
        assertTrue("question" in prompt)
        assertTrue("Do not hand over a complete solution" in prompt)
        assertTrue("language the developer writes in" in prompt)
    }

    @Test
    fun `does not send plugin notices to the model`() {
        val messages = DebugDuckPrompt.buildMessages(
            listOf(
                ChatMessage(ChatRole.USER, "hi"),
                ChatMessage(ChatRole.NOTICE, "The API key was rejected"),
                ChatMessage(ChatRole.DUCK, "What did you expect?"),
            )
        )
        assertEquals(listOf("system", "user", "assistant"), messages.map { it.role })
    }

    @Test
    fun `sends only the latest messages`() {
        val history = (1..100).map { ChatMessage(if (it % 2 == 0) ChatRole.DUCK else ChatRole.USER, "message $it") }
        val messages = DebugDuckPrompt.buildMessages(history)
        assertEquals(DebugDuckPrompt.MAX_HISTORY_MESSAGES + 1, messages.size)
        assertEquals("message 100", messages.last().content)
    }

    @Test
    fun `includes attached code as a fenced block`() {
        val attachment = CodeAttachment.of("Parser.kt", "Kotlin", "val x = y!!")
        val content = DebugDuckPrompt.userContent(ChatMessage(ChatRole.USER, "Why does this crash?", attachment))
        assertEquals("Why does this crash?\n\nCode from Parser.kt (Kotlin):\n```kotlin\nval x = y!!\n```", content)
    }

    @Test
    fun `uses a longer fence when the code contains backticks`() {
        val attachment = CodeAttachment.of("README.md", "Markdown", "```\ncode\n```")
        val content = DebugDuckPrompt.userContent(ChatMessage(ChatRole.USER, "", attachment))
        assertTrue(content, content.startsWith("Code from README.md (Markdown):\n````markdown\n"))
        assertTrue(content, content.endsWith("\n````"))
    }

    @Test
    fun `mentions the open file when nothing is selected`() {
        val content = DebugDuckPrompt.userContent(
            ChatMessage(ChatRole.USER, "It hangs", CodeAttachment.of("Main.java", "JAVA", "   "))
        )
        assertEquals("It hangs\n\n(The file currently open in my editor is Main.java.)", content)
    }

    @Test
    fun `truncates long selections`() {
        val attachment = CodeAttachment.of("Big.kt", "Kotlin", "x".repeat(CodeAttachment.MAX_CODE_LENGTH + 100))
        assertTrue(attachment.code!!.length < CodeAttachment.MAX_CODE_LENGTH + 50)
        assertTrue(attachment.code!!.endsWith("(truncated)"))
    }
}
