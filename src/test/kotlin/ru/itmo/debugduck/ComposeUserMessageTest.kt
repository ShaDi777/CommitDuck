package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComposeUserMessageTest {
    private val error = IdeProblem(ProblemKind.CODE, "';' expected", "Main.java:13")
    private val failedTest = IdeProblem(ProblemKind.TEST, "expected:<2> but was:<3>", "MainTest.adds")
    private val code = CodeAttachment.of("Main.java", "JAVA", "int x = 1", startLine = 13)

    @Test
    fun `sends the text with the pending context and the errors found automatically`() {
        val message = composeUserMessage(" why? ", PendingContext(code, listOf(error)), listOf(failedTest, error), emptyList())!!
        assertEquals("why?", message.text)
        assertEquals(code, message.attachment)
        assertEquals(listOf(error, failedTest), message.problems)
    }

    @Test
    fun `does not send errors the duck has already seen`() {
        val history = listOf(ChatMessage(ChatRole.USER, "first", problems = listOf(error)))
        assertEquals(listOf(failedTest), composeUserMessage("again", null, listOf(error, failedTest), history)!!.problems)
    }

    @Test
    fun `errors found automatically are not a message on their own`() {
        assertNull(composeUserMessage("  ", null, listOf(error), emptyList()))
    }

    @Test
    fun `the pending context is a message on its own`() {
        assertEquals(code, composeUserMessage("", PendingContext(code, emptyList()), emptyList(), emptyList())!!.attachment)
        assertEquals(listOf(error), composeUserMessage("", PendingContext(null, listOf(error)), emptyList(), emptyList())!!.problems)
    }

    @Test
    fun `limits the number of errors`() {
        val many = List(20) { IdeProblem(ProblemKind.CODE, "error $it", "A.kt:$it") }
        assertEquals(IdeProblem.MAX_PER_MESSAGE, composeUserMessage("x", null, many, emptyList())!!.problems.size)
    }

    @Test
    fun `shows the selected lines`() {
        assertEquals("Main.java:13", code.location)
        assertEquals("A.kt:5-7", CodeAttachment.of("A.kt", null, "a\nb\nc", startLine = 5).location)
        assertEquals("A.kt", CodeAttachment.of("A.kt", null, null, startLine = 5).location)
    }
}
