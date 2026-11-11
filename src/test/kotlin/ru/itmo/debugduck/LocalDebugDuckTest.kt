package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LocalDebugDuckTest {
    private val duck = LocalDebugDuck(Random(42))

    @Test
    fun `greets the user`() {
        assertEquals(ReplyKind.GREETING, duck.greeting().kind)
    }

    @Test
    fun `follows the debugging checklist when no topic is recognised`() {
        val kinds = (1..DebugPhase.entries.size).map { duck.reply("it behaves strangely").kind }
        assertEquals(List(DebugPhase.entries.size) { ReplyKind.PHASE }, kinds)
        assertEquals(ReplyKind.GENERIC, duck.reply("still strange").kind)
    }

    @Test
    fun `starts with expected versus actual behaviour`() {
        val reply = duck.reply("my program behaves strangely")
        assertTrue(reply.text, DebugPhase.EXPECTED_VS_ACTUAL.questions.any { reply.text.endsWith(it) })
    }

    @Test
    fun `skips checklist questions the user already answered`() {
        val reply = duck.reply("It should return a list but returns an empty one instead, every time I call it")
        assertTrue(reply.text, DebugPhase.CHANGES.questions.any { reply.text.endsWith(it) })
    }

    @Test
    fun `recognises topics in English and Russian`() {
        assertEquals(DebugTopic.NULL_VALUE, duck.reply("I get a NullPointerException in the parser").topic)
        assertEquals(DebugTopic.TEST, LocalDebugDuck(Random(1)).reply("тесты падают на CI").topic)
        assertEquals(DebugTopic.BUILD, LocalDebugDuck(Random(1)).reply("Gradle sync fails").topic)
        assertEquals(DebugTopic.PERFORMANCE, LocalDebugDuck(Random(1)).reply("всё очень медленно").topic)
    }

    @Test
    fun `recognises compile errors`() {
        assertEquals(DebugTopic.COMPILE_ERROR, duck.reply("i have CE in line 13").topic)
        assertEquals(DebugTopic.COMPILE_ERROR, LocalDebugDuck(Random(1)).reply("ошибка компиляции в Main.java").topic)
        assertEquals(DebugTopic.COMPILE_ERROR, LocalDebugDuck(Random(1)).reply("it doesn't compile").topic)
    }

    @Test
    fun `keeps coming back to the main problem`() {
        assertEquals(DebugTopic.COMPILE_ERROR, duck.reply("i have CE in line 13").topic)
        // "loop" is mentioned in passing; a checklist question comes in between the hints
        assertEquals(ReplyKind.PHASE, duck.reply("i expect that loop in lines 13-15 do print values from range").kind)
        assertEquals(DebugTopic.COMPILE_ERROR, duck.reply("yes they behave").topic)
    }

    @Test
    fun `does not switch to a topic mentioned in passing`() {
        duck.reply("i have CE in line 13")
        duck.reply("i expect that loop in lines 13-15 do print values from range")
        val topics = List(8) { duck.reply(if (it % 2 == 0) "yes" else "the compiler says ';' expected").topic }
        assertFalse(topics.toString(), DebugTopic.LOGIC in topics)
    }

    @Test
    fun `quotes an IDE error and gives a hint for it`() {
        val reply = duck.reply("why?", problems = listOf(IdeProblem(ProblemKind.CODE, "';' expected", "Main.java:13")))
        assertEquals(ReplyKind.PROBLEM, reply.kind)
        assertEquals(DebugTopic.COMPILE_ERROR, reply.topic)
        assertTrue(reply.text, "Main.java:13" in reply.text && "';' expected" in reply.text)
        assertTrue(reply.text, DebugTopic.COMPILE_ERROR.hints.any { reply.text.endsWith(it) })
    }

    @Test
    fun `picks the topic from the text of a failed test`() {
        val failure = IdeProblem(
            ProblemKind.TEST, "Cannot invoke \"String.length()\" because \"s\" is null", "ParserTest.parsesEmptyLine",
            details = "java.lang.NullPointerException: Cannot invoke ...\n\tat Parser.parse(Parser.java:12)",
        )
        val reply = duck.reply("the test is red", problems = listOf(failure))
        assertEquals(ReplyKind.PROBLEM, reply.kind)
        assertEquals(DebugTopic.NULL_VALUE, reply.topic)
        assertTrue(reply.text, reply.text.startsWith("The test ParserTest.parsesEmptyLine failed"))
    }

    @Test
    fun `new IDE errors are answered even right after a hint`() {
        assertEquals(ReplyKind.TOPIC, duck.reply("my test fails").kind)
        val reply = duck.reply("and now this", problems = listOf(IdeProblem(ProblemKind.BUILD, "cannot find symbol", "A.java:3")))
        assertEquals(ReplyKind.PROBLEM, reply.kind)
        assertTrue(reply.text, "A.java:3" in reply.text)
    }

    @Test
    fun `counts the other IDE errors`() {
        val problems = List(3) { IdeProblem(ProblemKind.CODE, "error $it", "A.kt:${it + 1}") }
        assertTrue(duck.reply("help", problems = problems).text.contains("(and 2 more)"))
    }

    @Test
    fun `skips checklist questions that make no sense for the main problem`() {
        val replies = listOf("i have CE in line 13") + List(10) { "hmm" }
        val texts = replies.map { duck.reply(it).text }
        val irrelevant = DebugPhase.EXPECTED_VS_ACTUAL.questions + DebugPhase.REPRODUCE.questions
        assertFalse(texts.joinToString("\n"), texts.any { text -> irrelevant.any { text.endsWith(it) } })
        // "line 13" already answers "where", the rest of the checklist is still asked
        assertTrue(texts.joinToString("\n"), texts.any { text -> DebugPhase.CHANGES.questions.any { text.endsWith(it) } })
    }

    @Test
    fun `helps with a pasted compiler message instead of asking for it`() {
        val foreachHint = DebugTopic.COMPILE_ERROR.specificHints.first().second
        assertEquals(DebugTopic.COMPILE_ERROR, duck.reply("i have compilation errors in selected lines").topic)
        val reply = duck.reply("Foreach not applicable to type 'java.util.stream.IntStream'")
        assertTrue(reply.text, reply.text.endsWith(foreachHint))
        val code = CodeAttachment.of("Main.java", "JAVA", "for (var i : IntStream.range(1, 2).sorted()) {\n    System.out.println(i);\n}", 7)
        val codeReply = duck.reply("i have select problem code, please view it", code)
        assertEquals(ReplyKind.CODE, codeReply.kind)
        assertTrue(codeReply.text, "compiler" in codeReply.text || "call or expression" in codeReply.text)
    }

    @Test
    fun `picks the hint that fits the IDE error`() {
        val reply = duck.reply("why?", problems = listOf(IdeProblem(ProblemKind.CODE, "Cannot resolve symbol 'foo'", "A.java:3")))
        assertTrue(reply.text, reply.text.endsWith("Is everything on that line declared and visible at that point: spelled the same way, imported, and in scope?"))
        val next = List(6) { duck.reply("hmm").text }
        assertTrue(next.joinToString("\n"), next.none { it.endsWith(LocalDebugDuck.READ_THE_MESSAGE_HINTS.first()) })
    }

    @Test
    fun `short keywords match whole words only`() {
        assertTrue(DebugTopic.USER_INTERFACE.matches("the ui is broken"))
        assertFalse(DebugTopic.USER_INTERFACE.matches("the build is broken"))
        assertTrue(DebugTopic.TEST.matches("my tests fail"))
        assertFalse(DebugTopic.TEST.matches("the latest version"))
    }

    @Test
    fun `alternates topic hints with checklist questions`() {
        val first = duck.reply("the test fails")
        val second = duck.reply("the test still fails")
        val third = duck.reply("the test fails again")
        assertEquals(ReplyKind.TOPIC, first.kind)
        assertEquals(ReplyKind.PHASE, second.kind)
        assertEquals(ReplyKind.TOPIC, third.kind)
    }

    @Test
    fun `runs out of topic hints and falls back to the checklist`() {
        val kinds = (1..10).map { duck.reply("null again").kind }
        assertEquals(DebugTopic.NULL_VALUE.hints.size, kinds.count { it == ReplyKind.TOPIC })
    }

    @Test
    fun `never repeats a question within a conversation`() {
        val messages = listOf(
            "the test fails with a NullPointerException", "it happens every time", "it worked yesterday",
            "the query is slow", "I don't know", "the request returns 500", "in the loop", "the build is fine",
        )
        val replies = (messages + List(10) { "hmm" }).map { duck.reply(it).text.substringAfter(". ").trim() }
        assertEquals(replies.size, replies.toSet().size)
    }

    @Test
    fun `congratulates when the problem is solved`() {
        assertEquals(ReplyKind.SOLVED, duck.reply("Found it! It works now").kind)
        assertEquals(ReplyKind.SOLVED, duck.reply("Ура, заработало").kind)
    }

    @Test
    fun `recognises a solved problem`() {
        assertTrue(LocalDebugDuck.isSolved("Fixed it!"))
        assertTrue(LocalDebugDuck.isSolved("ура, заработало"))
        assertFalse(LocalDebugDuck.isSolved("still not fixed"))
        assertFalse(LocalDebugDuck.isSolved("what does the compiler say?"))
    }

    @Test
    fun `congratulates on request without repeating itself`() {
        val first = duck.congratulate()
        val second = duck.congratulate()
        assertEquals(ReplyKind.SOLVED, first.kind)
        assertNotEquals(first.text, second.text)
    }

    @Test
    fun `does not congratulate when it still does not work`() {
        assertNotEquals(ReplyKind.SOLVED, duck.reply("it still does not work").kind)
        assertNotEquals(ReplyKind.SOLVED, duck.reply("всё ещё не работает").kind)
    }

    @Test
    fun `helps when the user is stuck`() {
        assertEquals(ReplyKind.STUCK, duck.reply("I have no idea").kind)
        assertEquals(ReplyKind.STUCK, duck.reply("не знаю").kind)
    }

    @Test
    fun `answers short thanks but not thanks followed by a new problem`() {
        assertEquals(ReplyKind.THANKS, duck.reply("thanks!").kind)
        assertNotEquals(ReplyKind.THANKS, duck.reply("thanks, but now the build fails with a strange error message").kind)
    }

    @Test
    fun `asks about attached code`() {
        val attachment = CodeAttachment.of("Parser.kt", "Kotlin", "val a = 1\nval b = a / 0")
        val reply = duck.reply("look at this", attachment)
        assertEquals(ReplyKind.CODE, reply.kind)
        assertTrue(reply.text, "Parser.kt" in reply.text && "2 lines" in reply.text)
    }

    @Test
    fun `mentions the open file if nothing is selected`() {
        val reply = duck.reply("something is off", CodeAttachment.of("Parser.kt", "Kotlin", null))
        assertEquals(ReplyKind.FILE, reply.kind)
        assertTrue(reply.text, "Parser.kt" in reply.text)
    }

    @Test
    fun `reset starts the checklist over`() {
        val first = duck.reply("strange")
        repeat(5) { duck.reply("strange") }
        duck.reset()
        val afterReset = duck.reply("strange")
        assertEquals(ReplyKind.PHASE, afterReset.kind)
        assertTrue(DebugPhase.EXPECTED_VS_ACTUAL.questions.any { afterReset.text.endsWith(it) })
        assertTrue(DebugPhase.EXPECTED_VS_ACTUAL.questions.any { first.text.endsWith(it) })
    }

    @Test
    fun `cycles generic questions without crashing in a long conversation`() {
        repeat(100) { assertTrue(duck.reply("hmm").text.isNotBlank()) }
    }
}
