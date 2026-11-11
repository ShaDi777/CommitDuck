package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ConversationCodecTest {
    private val conversation = StoredConversation(
        listOf(
            ChatMessage(ChatRole.DUCK, "Quack! What's wrong?"),
            ChatMessage(
                ChatRole.USER, "My \"test\" fails\nhelp",
                CodeAttachment.of("A.kt", "Kotlin", "val a = 1", startLine = 3),
                problems = listOf(IdeProblem(ProblemKind.TEST, "expected:<2> but was:<3>", "ATest.adds", "at ATest.adds(ATest.kt:7)")),
            ),
            ChatMessage(ChatRole.DUCK, "What does the assertion say?", model = "llama3"),
            ChatMessage(ChatRole.NOTICE, "Stopped."),
        ),
        solved = true,
    )

    @Test
    fun `round-trips a conversation`() {
        assertEquals(conversation, ConversationCodec.decode(ConversationCodec.encode(conversation)))
    }

    @Test
    fun `stores the conversation on one line`() {
        assertFalse(ConversationCodec.encode(conversation).contains('\n'))
    }

    @Test
    fun `keeps only the latest messages`() {
        val long = StoredConversation(List(500) { ChatMessage(ChatRole.USER, "message $it") }, solved = false)
        val decoded = ConversationCodec.decode(ConversationCodec.encode(long))!!
        assertEquals(ConversationCodec.MAX_STORED_MESSAGES, decoded.messages.size)
        assertEquals("message 499", decoded.messages.last().text)
    }

    @Test
    fun `ignores broken or foreign data`() {
        assertNull(ConversationCodec.decode(null))
        assertNull(ConversationCodec.decode(""))
        assertNull(ConversationCodec.decode("{not json"))
        assertNull(ConversationCodec.decode("""{"version":99,"messages":[{"role":"USER","text":"hi"}]}"""))
        val partlyBroken = """{"version":1,"messages":[{"role":"ALIEN","text":"?"},{"role":"USER","text":"hi"}]}"""
        assertEquals(listOf(ChatMessage(ChatRole.USER, "hi")), ConversationCodec.decode(partlyBroken)!!.messages)
    }

    @Test
    fun `a restored duck does not repeat its questions and remembers the main problem`() {
        val first = LocalDebugDuck(Random(3))
        val history = mutableListOf(ChatMessage(ChatRole.DUCK, first.greeting().text))
        for (text in listOf("i have CE in line 13", "hmm", "ok", "hmm")) {
            history += ChatMessage(ChatRole.USER, text)
            history += ChatMessage(ChatRole.DUCK, first.reply(text).text)
        }
        val restored = LocalDebugDuck(Random(4)).apply { restore(history) }
        val askedBefore = history.filter { it.role == ChatRole.DUCK }.map { it.text }
        repeat(6) {
            val reply = restored.reply("hmm")
            assertFalse(reply.text, askedBefore.any { asked -> reply.text.substringAfter(". ").let { it.length > 20 && asked.endsWith(it) } })
            assertTrue(reply.text, DebugPhase.REPRODUCE.questions.none { reply.text.endsWith(it) })
        }
    }

    @Test
    fun `a restored duck keeps speaking the user's language`() {
        val restored = LocalDebugDuck(Random(4)).apply { restore(listOf(ChatMessage(ChatRole.USER, "у меня всё сломалось"))) }
        assertEquals(DuckLanguage.RUSSIAN, restored.language)
    }
}
