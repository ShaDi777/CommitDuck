package ru.itmo.debugduck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuckLanguageTest {
    private val placeholder = Regex("\\{\\w+}")

    @Test
    fun `detects the language of a message`() {
        assertEquals(DuckLanguage.RUSSIAN, DuckLanguage.detect("у меня NPE в Parser.kt"))
        assertEquals(DuckLanguage.ENGLISH, DuckLanguage.detect("my test fails"))
        assertNull(DuckLanguage.detect("?? 13"))
        assertEquals(DuckLanguage.RUSSIAN, DuckLanguage.forLocale("ru-RU"))
        assertEquals(DuckLanguage.ENGLISH, DuckLanguage.forLocale("de"))
    }

    @Test
    fun `every phrase has a Russian version with the same placeholders`() {
        for (phrase in LocalDebugDuck.allPhrases()) {
            val russian = RussianPhrases.byEnglish[phrase]
            assertTrue("No Russian version of: $phrase", russian != null)
            assertNotEquals(phrase, russian)
            assertEquals(phrase, placeholder.findAll(phrase).map { it.value }.toList().sorted(), placeholder.findAll(russian!!).map { it.value }.toList().sorted())
        }
    }

    @Test
    fun `answers in Russian to a Russian message`() {
        val duck = LocalDebugDuck(Random(7))
        val reply = duck.reply("у меня ошибка компиляции в строке 13")
        assertEquals(DebugTopic.COMPILE_ERROR, reply.topic)
        assertTrue(reply.text, DebugTopic.COMPILE_ERROR.hints.any { reply.text.endsWith(RussianPhrases.byEnglish.getValue(it)) })
        assertEquals(DuckLanguage.RUSSIAN, DuckLanguage.detect(duck.reply("ну и что").text))
    }

    @Test
    fun `switches the language with the user`() {
        val duck = LocalDebugDuck(Random(7))
        assertEquals(DuckLanguage.RUSSIAN, DuckLanguage.detect(duck.reply("всё сломалось").text))
        assertEquals(DuckLanguage.ENGLISH, DuckLanguage.detect(duck.reply("sorry, let me write in English").text))
        // no letters: keeps the language
        assertEquals(DuckLanguage.ENGLISH, DuckLanguage.detect(duck.reply("???").text))
    }

    @Test
    fun `a fixed language wins over the language of the message`() {
        val duck = LocalDebugDuck(Random(7)).apply { fixedLanguage = DuckLanguage.ENGLISH }
        assertEquals(DuckLanguage.ENGLISH, DuckLanguage.detect(duck.reply("у меня всё сломалось").text))
    }

    @Test
    fun `greets in the default language`() {
        val greeting = LocalDebugDuck(Random(7), language = DuckLanguage.RUSSIAN).greeting().text
        assertEquals(DuckLanguage.RUSSIAN, DuckLanguage.detect(greeting))
    }

    @Test
    fun `quotes IDE errors and counts lines in Russian`() {
        val duck = LocalDebugDuck(Random(7))
        val problem = duck.reply("почему?", problems = listOf(IdeProblem(ProblemKind.CODE, "';' expected", "Main.java:13")))
        assertTrue(problem.text, problem.text.startsWith("IDE сообщает об ошибке в Main.java:13: «';' expected»."))
        val code = duck.reply("вот код", CodeAttachment.of("A.kt", "Kotlin", "a\nb\nc"))
        assertTrue(code.text, "3 строки" in code.text)
    }

    @Test
    fun `russian plurals`() {
        assertEquals(
            listOf("1 строка", "2 строки", "5 строк", "11 строк", "21 строка", "22 строки", "112 строк"),
            listOf(1, 2, 5, 11, 21, 22, 112).map { linesText(it, DuckLanguage.RUSSIAN) },
        )
        assertEquals("1 line", linesText(1, DuckLanguage.ENGLISH))
    }
}
