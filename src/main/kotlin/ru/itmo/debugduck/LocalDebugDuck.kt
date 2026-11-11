package ru.itmo.debugduck

import kotlin.random.Random

/** Why the local duck picked a reply; useful for tests and for tuning the question pool. */
enum class ReplyKind { GREETING, SOLVED, THANKS, PROBLEM, CODE, FILE, STUCK, TOPIC, PHASE, GENERIC }

data class LocalReply(val text: String, val kind: ReplyKind, val topic: DebugTopic? = null)

/**
 * Offline Debug Duck: rubber-duck debugging with a local pool of generic leading questions.
 *
 * Without an LLM the duck can't understand the problem, so it follows a classic debugging checklist
 * ([DebugPhase]: expected vs. actual -> reproduce -> what changed -> locate -> assumptions -> experiment -> explain)
 * and adds light, topic-specific hints when the user's message mentions a well-known kind of bug ([DebugTopic]),
 * e.g. a null value, a failing test or a slow query. Errors reported by the IDE ([IdeProblem]) are quoted back and
 * pick the topic too, so "I have a CE" plus the compiler message gets compile-error hints right away.
 * Questions are not repeated within one conversation.
 *
 * The duck speaks English or Russian ([RussianPhrases]): the language of the user's last message, unless
 * [fixedLanguage] is set.
 *
 * Pure Kotlin without IDE dependencies, so it can be unit-tested with a seeded [random].
 */
class LocalDebugDuck(
    private val random: Random = Random.Default,
    /** The language before the user wrote anything, i.e. of the greeting. */
    var language: DuckLanguage = DuckLanguage.ENGLISH,
) {
    /** Always answer in this language; `null` to follow the language of the user's messages. */
    var fixedLanguage: DuckLanguage? = null
        set(value) {
            field = value
            if (value != null) language = value
        }

    private val asked = mutableSetOf<String>()
    private var phaseIndex = 0
    /** Checklist steps the user already talked about without being asked, e.g. "it fails every time". */
    private val coveredPhases = mutableSetOf<DebugPhase>()
    /** Topics of the first message that had any: that's usually where the user describes the actual problem. */
    private val mainTopics = mutableListOf<DebugTopic>()
    private var lastKind: ReplyKind? = null
    private var lastAcknowledgement = ""
    /** Error messages the duck has seen (from the IDE or pasted by the user), lowercase; picks the fitting hints. */
    private val errorText = StringBuilder()

    fun greeting(): LocalReply = LocalReply(t(GREETINGS.random(random)), ReplyKind.GREETING)

    fun reply(userText: String, attachment: CodeAttachment? = null, problems: List<IdeProblem> = emptyList()): LocalReply {
        val mentionedTopics = observe(userText, problems)
        val text = userText.lowercase()
        val reply = solvedReply(text)
            ?: thanksReply(text)
            ?: problemReply(problems, mentionedTopics)
            ?: attachmentReply(attachment)
            ?: stuckReply(text)
            ?: topicReply(mentionedTopics, text)
            ?: phaseReply()
            ?: genericReply()
        lastKind = reply.kind
        return reply
    }

    /**
     * Picks up a conversation restored after an IDE restart: what the user said and which questions were asked,
     * so the duck neither repeats itself nor forgets the main problem.
     */
    fun restore(history: List<ChatMessage>) {
        reset()
        for (message in history) {
            when (message.role) {
                ChatRole.USER -> observe(message.text, message.problems)
                ChatRole.DUCK -> allPhrases().filterTo(asked) { phrase ->
                    phrase in message.text || RussianPhrases.byEnglish[phrase]?.let { it in message.text } == true
                }
                ChatRole.NOTICE -> {}
            }
        }
        phaseIndex = DebugPhase.entries.indexOfLast { phase -> phase.questions.any { it in asked } } + 1
    }

    /** Takes in what the user said: language, checklist steps already answered, topics. Returns the topics. */
    private fun observe(userText: String, problems: List<IdeProblem>): List<DebugTopic> {
        if (fixedLanguage == null) DuckLanguage.detect(userText)?.let { language = it }
        val text = userText.lowercase()
        DebugPhase.entries.filterTo(coveredPhases) { it.isAnsweredBy(text) }
        val problemTopics = topicsOf(problems)
        val mentionedTopics = DebugTopic.entries.filter { it in problemTopics || it.matches(text) }
        if (mainTopics.isEmpty()) mainTopics += mentionedTopics

        // once the duck has the error message, asking "what does the compiler say?" makes no sense
        val errors = problems.map { (it.message + "\n" + it.details.orEmpty()).lowercase() } +
            listOfNotNull(text.takeIf { QUOTED_ERROR.matches(it) })
        if (errors.isNotEmpty()) {
            errors.forEach { errorText.append(it).append('\n') }
            asked += READ_THE_MESSAGE_HINTS
        }
        return mentionedTopics
    }

    /** A hint for [topic]: one that fits the error text best (e.g. about for-each for "foreach not applicable"), or any. */
    private fun hintFor(topic: DebugTopic, text: String): String? {
        val context = "$text\n$errorText"
        val specific = topic.specificHints.firstOrNull { (matcher, hint) -> hint !in asked && matcher.matches(context) }
        return specific?.second?.also { asked += it } ?: pickUnasked(topic.hints)
    }

    /** A congratulation in the current language, e.g. when the user marks the problem as solved. */
    fun congratulate(): LocalReply = LocalReply(t(pickCycling(SOLVED_REPLIES)), ReplyKind.SOLVED)

    fun reset() {
        asked.clear()
        phaseIndex = 0
        coveredPhases.clear()
        mainTopics.clear()
        lastKind = null
        lastAcknowledgement = ""
        errorText.clear()
    }

    private fun solvedReply(text: String): LocalReply? = if (isSolved(text)) congratulate() else null

    private fun thanksReply(text: String): LocalReply? {
        // "thanks, but the test still fails" is not the end of the conversation
        if (!THANKS.matches(text) || text.split(WHITESPACE).size > 6) return null
        return LocalReply(t(pickCycling(THANKS_REPLIES)), ReplyKind.THANKS)
    }

    /** Quotes the first new IDE problem and follows up with a hint for its topic. */
    private fun problemReply(problems: List<IdeProblem>, mentionedTopics: List<DebugTopic>): LocalReply? {
        val problem = problems.firstOrNull() ?: return null
        val message = problem.message.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().let {
            if (it.length > MAX_QUOTE_LENGTH) it.take(MAX_QUOTE_LENGTH) + "..." else it
        }
        val location = problem.location
        val test = problem.kind == ProblemKind.TEST
        val quote = when (language) {
            DuckLanguage.ENGLISH -> when {
                test && location != null -> "The test $location failed: \"$message\"."
                test -> "A test failed: \"$message\"."
                location != null -> "The IDE reports an error at $location: \"$message\"."
                else -> "The IDE reports an error: \"$message\"."
            }
            DuckLanguage.RUSSIAN -> when {
                test && location != null -> "Тест $location упал: «$message»."
                test -> "Упал тест: «$message»."
                location != null -> "IDE сообщает об ошибке в $location: «$message»."
                else -> "IDE сообщает об ошибке: «$message»."
            }
        }
        val others = problems.size - 1
        val more = when {
            others == 0 -> ""
            language == DuckLanguage.RUSSIAN -> " (и ещё $others)"
            else -> " (and $others more)"
        }
        // new information from the IDE beats the alternation of hints and checklist questions
        for (topic in mentionedTopics) {
            val hint = hintFor(topic, problem.message.lowercase()) ?: continue
            return LocalReply("$quote$more ${t(hint)}", ReplyKind.PROBLEM, topic)
        }
        val question = phaseReply(acknowledge = false)?.text ?: genericReply(acknowledge = false).text
        return LocalReply("$quote$more $question", ReplyKind.PROBLEM)
    }

    private fun attachmentReply(attachment: CodeAttachment?): LocalReply? {
        attachment ?: return null
        val code = attachment.code
        if (code == null) {
            val template = pickUnasked(FILE_QUESTIONS) ?: return null
            return LocalReply(t(template).replace("{file}", attachment.fileName), ReplyKind.FILE)
        }
        // for a compile error, questions about types and calls fit better than "which value is wrong?"
        val template = (if (DebugTopic.COMPILE_ERROR in mainTopics) pickUnasked(COMPILE_CODE_QUESTIONS) else null)
            ?: pickUnasked(CODE_QUESTIONS)
            ?: return null
        val text = t(template)
            .replace("{file}", attachment.fileName)
            .replace("{lines}", linesText(attachment.lineCount, language))
        return LocalReply(text, ReplyKind.CODE)
    }

    private fun stuckReply(text: String): LocalReply? {
        if (!STUCK.matches(text)) return null
        return LocalReply(t(pickCycling(STUCK_REPLIES)), ReplyKind.STUCK)
    }

    private fun topicReply(mentionedTopics: List<DebugTopic>, text: String): LocalReply? {
        // alternate topic hints with checklist questions, so keywords don't take over the conversation;
        // a pasted error message is new information though, and deserves a hint of its own
        if (lastKind == ReplyKind.TOPIC && !QUOTED_ERROR.matches(text)) return null
        // without keywords in this message, come back to the main problem: "yes, they behave" after "I have a compile
        // error" is still about the compile error, not about "the loop in line 13" mentioned in passing
        for (topic in mentionedTopics.ifEmpty { mainTopics }) {
            val hint = hintFor(topic, text) ?: continue
            return LocalReply(acknowledge() + t(hint), ReplyKind.TOPIC, topic)
        }
        return null
    }

    private fun phaseReply(acknowledge: Boolean = true): LocalReply? {
        val phases = DebugPhase.entries
        // the checklist follows the main problem: no "Can you reproduce it?" for a compile error
        val irrelevant = mainTopics.flatMapTo(HashSet()) { it.irrelevantPhases }
        while (phaseIndex < phases.size) {
            val phase = phases[phaseIndex++]
            if (phase in coveredPhases || phase in irrelevant) continue
            val question = pickUnasked(phase.questions)
            if (question != null) return LocalReply((if (acknowledge) acknowledge() else "") + t(question), ReplyKind.PHASE)
        }
        return null
    }

    private fun genericReply(acknowledge: Boolean = true) =
        LocalReply((if (acknowledge) acknowledge() else "") + t(pickCycling(GENERIC_QUESTIONS)), ReplyKind.GENERIC)

    private fun acknowledge(): String {
        val acknowledgement = ACKNOWLEDGEMENTS.filter { it.isEmpty() || it != lastAcknowledgement }.random(random)
        lastAcknowledgement = acknowledgement
        return t(acknowledgement)
    }

    /** [phrase] in the current [language]; phrases are kept in English and translated when they are said. */
    private fun t(phrase: String): String =
        if (language == DuckLanguage.RUSSIAN && phrase.isNotEmpty()) RussianPhrases.byEnglish[phrase] ?: phrase else phrase

    /** A random phrase from [pool] that wasn't used in this conversation yet, or `null` if all were used. */
    private fun pickUnasked(pool: List<String>): String? =
        pool.filter { it !in asked }.randomOrNull(random)?.also { asked += it }

    /** Like [pickUnasked], but starts over once the whole [pool] was used. */
    private fun pickCycling(pool: List<String>): String =
        pickUnasked(pool) ?: run {
            asked -= pool.toSet()
            pickUnasked(pool)!!
        }

    internal companion object {
        /** Whether the user says the problem is solved: "fixed it!", "заработало", but not "still not fixed". */
        fun isSolved(userText: String): Boolean {
            val text = userText.lowercase()
            return SOLVED.matches(text) && !NOT_SOLVED.matches(text)
        }

        /** Every phrase the duck can say, in English; used to check the translations. */
        fun allPhrases(): List<String> =
            GREETINGS + ACKNOWLEDGEMENTS.filter { it.isNotEmpty() } + SOLVED_REPLIES + THANKS_REPLIES + CODE_QUESTIONS +
                COMPILE_CODE_QUESTIONS + DebugTopic.entries.flatMap { topic -> topic.specificHints.map { it.second } } +
                FILE_QUESTIONS + STUCK_REPLIES + GENERIC_QUESTIONS +
                DebugPhase.entries.flatMap { it.questions } + DebugTopic.entries.flatMap { it.hints }

        val WHITESPACE = Regex("\\s+")
        const val MAX_QUOTE_LENGTH = 160

        /** Text that looks like a pasted error message rather than the user's own words. */
        val QUOTED_ERROR = KeywordMatcher(
            prefixes = listOf(
                "not applicable to", "incompatible types", "cannot find symbol", "cannot resolve", "unresolved reference",
                "type mismatch", "cannot be applied", "cannot be converted", "' expected", "expected:<", "exception in thread",
                "caused by:", "error:",
            ),
        )

        /** Hints that ask the user to read the error message out: pointless once the duck has the message. */
        val READ_THE_MESSAGE_HINTS = setOf(
            "What exactly does the compiler say? Read the message word by word: which symbol, type or token does it complain about?",
            "What does the error message say, word by word? Find the first stack trace frame from your own code - which line is it?",
        )

        val COMPILE_CODE_QUESTIONS = listOf(
            "Here are {lines} from {file}. Go through the line the compiler points to: what type does each expression there have, and what type does the code around it expect?",
            "I see {lines} from {file}. Which call or expression here are you least sure about: does it return what you think, and does that fit where it is used?",
        )

        /** Topics suggested by IDE problems: compile errors for code and build errors, tests for test failures, plus
         *  whatever the error text itself mentions (a `NullPointerException` in a failed test is a null problem). */
        fun topicsOf(problems: List<IdeProblem>): Set<DebugTopic> = buildSet {
            for (problem in problems) {
                add(if (problem.kind == ProblemKind.TEST) DebugTopic.TEST else DebugTopic.COMPILE_ERROR)
                val text = (problem.message + "\n" + problem.details.orEmpty()).lowercase()
                DebugTopic.entries.filterTo(this) { it != DebugTopic.COMPILE_ERROR && it.matches(text) }
            }
        }

        val GREETINGS = listOf(
            "Quack! I'm your debug duck. Tell me what's going wrong - explaining a problem out loud is often half of the fix.",
            "Quack! Something broke? Describe the problem to me step by step. I'm all ears (well, feathers).",
            "Hi, I'm the debug duck. What are you stuck on? Tell me in your own words and I'll ask questions.",
        )

        val ACKNOWLEDGEMENTS = listOf("", "", "", "Hmm. ", "Got it. ", "I see. ", "Interesting. ", "Quack. ", "Okay. ")

        val SOLVED = KeywordMatcher(
            prefixes = listOf(
                "solved", "fixed", "found it", "it works", "works now", "working now", "figured it out", "nailed it",
                "resolved",
                "решил", "решено", "нашел", "нашёл", "заработал", "работает", "понял", "починил", "исправил",
            ),
        )

        val NOT_SOLVED = KeywordMatcher(
            prefixes = listOf(
                "not work", "doesn't work", "doesnt work", "does not work", "isn't working", "is not working",
                "not fixed", "not solved", "still", "but now",
                "не работает", "не заработал", "не решил", "не нашел", "не нашёл", "не понял", "не починил",
                "не исправил", "всё ещё", "все еще", "до сих пор",
            ),
        )

        val THANKS = KeywordMatcher(prefixes = listOf("thank", "thx", "спасибо", "благодар"), words = listOf("ty"))

        val STUCK = KeywordMatcher(
            prefixes = listOf(
                "don't know", "dont know", "do not know", "no idea", "not sure", "stuck", "no clue", "give up",
                "help me", "i'm lost", "im lost",
                "не знаю", "без понятия", "застрял", "не понимаю", "помоги", "сдаюсь",
            ),
            words = listOf("idk", "dunno", "хз"),
        )

        val SOLVED_REPLIES = listOf(
            "Quack! Great job! What was the root cause in the end? Is it worth adding a test so it never comes back?",
            "Nice, the bug is gone! Is there any other place in the code with the same pattern that could break the same way?",
            "Well done! Can you explain the fix in one sentence? That sentence might be a perfect commit message.",
            "Quack-tastic! Was the cause where you looked first, or somewhere completely different?",
        )

        val THANKS_REPLIES = listOf(
            "Any time! Quack.",
            "You did the hard part - I just listened. Quack!",
            "Happy to help. I'll be right here if the bug comes back.",
        )

        val CODE_QUESTIONS = listOf(
            "Thanks for the code ({lines} from {file}). Read it to me line by line: what should each line do? Which one are you least sure about?",
            "I see {lines} from {file}. Which value in this code do you expect to be different from what it actually is?",
            "Let's look at these {lines} from {file}. What goes into this code, and what should come out?",
            "Here are {lines} from {file}. Which branch or call here do you think is executed - and have you checked that it really is?",
        )

        val FILE_QUESTIONS = listOf(
            "You're in {file}. Is the problem in this file, or in something it calls? Select the suspicious lines and send them with \"Attach from editor\" or \"Ask Debug Duck\" in the editor's context menu.",
        )

        val STUCK_REPLIES = listOf(
            "That's fine - let's make it smaller. Comment out or skip half of the suspicious code. Does the problem stay or go away?",
            "Let's collect facts instead of guesses: put a breakpoint or a log line before and after the suspicious part and compare the values.",
            "Try to describe the problem in one sentence: \"The program should ..., but instead it ...\".",
            "Go back to the last version that worked (git stash or an older commit) and make sure it really works. Then bring the changes back in small steps.",
            "Write down what you know for sure about the bug, and separately what you only suspect. Which list is longer?",
        )

        val GENERIC_QUESTIONS = listOf(
            "What have you tried so far, and what did each attempt tell you?",
            "If a colleague looked at this, what would they ask first?",
            "Is there a simpler version of the problem you could solve first?",
            "What would you search for in the docs or the issue tracker? Has anyone hit this before?",
            "Could you write a small test that fails because of this bug? Then you'll know for sure when it's fixed.",
            "Take a short break and come back. With fresh eyes, what would you check first?",
        )
    }
}

/**
 * Questions of a classic debugging checklist, asked in this order. A step is skipped if the user already talked about
 * it on their own, which the duck recognises by [markers]: there is no point in asking "Does it happen every time?"
 * after "it fails every time I run it".
 */
enum class DebugPhase(private val markers: KeywordMatcher, val questions: List<String>) {
    EXPECTED_VS_ACTUAL(
        KeywordMatcher(
            prefixes = listOf("expect", "should", "instead", "supposed to", "ожида", "должн", "вместо"),
        ),
        listOf(
            "What exactly did you expect to happen, and what happens instead?",
            "Let's pin it down: what is the expected behaviour, and what do you actually observe?",
            "If everything worked, what would you see? And what do you see now?",
        )
    ),
    REPRODUCE(
        KeywordMatcher(
            prefixes = listOf(
                "every time", "each time", "always", "sometimes", "reproduc", "only when", "only if", "randomly",
                "каждый раз", "всегда", "иногда", "воспроизв", "только когда", "только если",
            ),
        ),
        listOf(
            "Can you reproduce it every time? What are the exact steps?",
            "Does it happen always, or only sometimes? What is special about the times it happens?",
            "What is the smallest input or the shortest sequence of steps that still shows the problem?",
        )
    ),
    CHANGES(
        KeywordMatcher(
            prefixes = listOf(
                "worked before", "used to work", "was working", "worked yesterday", "since i", "since the", "after i",
                "after the", "changed", "refactor", "updated", "upgraded", "never work", "ever work", "first time",
                "раньше", "работало вчера", "вчера работал", "после того", "после обнов", "изменил", "обновил", "рефактор",
                "никогда не работал", "ни разу не работал",
            ),
        ),
        listOf(
            "Did this ever work? What has changed since then - code, dependencies, data, environment?",
            "When did you first notice it? What were you working on right before that?",
            "What was the last change before things broke? What happens if you undo it?",
        )
    ),
    LOCATE(
        KeywordMatcher(
            prefixes = listOf(
                "in the method", "in the function", "in method", "in function", "logged", "logging",
                "в методе", "в функции", "в строке", "логи", "логах",
            ),
            words = listOf("line", "lines", "log", "logs"),
        ),
        listOf(
            "Where does the program go wrong first? Can you narrow it down to a function or a line?",
            "What do the logs say right before the problem? If there are no logs there, what would you log?",
            "If you split the way from the input to the output in half, is the data still correct in the middle?",
        )
    ),
    ASSUMPTIONS(
        KeywordMatcher(),
        listOf(
            "What are you assuming to be true here? Which of those assumptions have you actually checked?",
            "Which part of the code are you most sure about? Let's double-check exactly that part.",
            "Is the code that runs really the code you are reading - the right file, the right branch, a fresh build?",
        )
    ),
    EXPERIMENT(
        KeywordMatcher(),
        listOf(
            "What is your best guess about the cause right now? What small experiment would prove or disprove it?",
            "If your hypothesis is right, what else should be true? Can you check that?",
            "What is the cheapest thing you can try in the next five minutes to learn something new about the bug?",
        )
    ),
    EXPLAIN(
        KeywordMatcher(),
        listOf(
            "Explain the relevant code to me line by line, as if I were a duck. Which line surprises you?",
            "Walk me through what happens step by step, from the input to the wrong result.",
            "Say out loud what each variable holds at the point where it fails. Does any value look odd?",
        )
    );

    fun isAnsweredBy(lowercaseText: String): Boolean = markers.matches(lowercaseText)
}

/**
 * Well-known kinds of bugs with light hints. Recognised by keywords (English and Russian) in the user's message.
 * The order matters: when a message matches several topics, the more specific one comes first.
 */
enum class DebugTopic(
    private val matcher: KeywordMatcher,
    val hints: List<String>,
    /** Checklist steps that make no sense for this kind of problem, e.g. "Can you reproduce it?" for a compile error. */
    val irrelevantPhases: Set<DebugPhase> = emptySet(),
    /** Hints that fit a particular error text, tried before [hints], most specific first. */
    val specificHints: List<Pair<KeywordMatcher, String>> = emptyList(),
) {
    COMPILE_ERROR(
        KeywordMatcher(
            prefixes = listOf(
                "compile error", "compilation error", "compiler", "compile-time", "doesn't compile", "does not compile",
                "won't compile", "not compil", "syntax error", "cannot find symbol", "cannot resolve symbol",
                "unresolved reference", "incompatible types", "type mismatch",
                "ошибка компиляции", "ошибку компиляции", "не компилир", "компилятор", "синтаксическ",
            ),
            words = listOf("ce"),
        ),
        listOf(
            "What exactly does the compiler say? Read the message word by word: which symbol, type or token does it complain about?",
            "Look at the line just before the one the compiler points to. A missing semicolon, bracket or quote often shows up a line later.",
            "Do the types fit? Check what each expression on that line returns and what is expected there, and whether everything it uses is declared and imported.",
        ),
        irrelevantPhases = setOf(DebugPhase.EXPECTED_VS_ACTUAL, DebugPhase.REPRODUCE),
        specificHints = listOf(
            KeywordMatcher(prefixes = listOf("foreach not applicable", "for-each not applicable", "for-each loop", "not iterable")) to
                "A for-each loop only works with arrays and Iterable. Is the expression after the colon one of them? A stream, for example, is neither.",
            KeywordMatcher(prefixes = listOf("cannot find symbol", "cannot resolve", "unresolved reference", "is not defined")) to
                "Is everything on that line declared and visible at that point: spelled the same way, imported, and in scope?",
            KeywordMatcher(prefixes = listOf("' expected", "illegal start", "unclosed", "reached end of file", "expecting")) to
                "Look at the line just before the one the compiler points to. A missing semicolon, bracket or quote often shows up a line later.",
            KeywordMatcher(
                prefixes = listOf(
                    "not applicable", "incompatible types", "type mismatch", "cannot be applied", "cannot be converted",
                    "required:", "inferred type",
                ),
            ) to
                "Do the types fit? Check what each expression on that line returns and what is expected there, and whether everything it uses is declared and imported.",
        ),
    ),
    NULL_VALUE(
        KeywordMatcher(
            prefixes = listOf("null", "npe", "nil", "undefined", "lateinit", "uninitiali", "not initiali", "kotlinnullpointer"),
        ),
        listOf(
            "Which exact value turns out to be null? Trace it back: where should it be assigned, and is that code actually reached before the place where it fails?",
            "Can that value legitimately be missing - an empty lookup, an optional field, an object that isn't initialised yet? What should the code do then?",
            "Could it be an ordering issue - something read before it is set, like a lateinit property, a callback or an async result?",
        )
    ),
    CONCURRENCY(
        KeywordMatcher(
            prefixes = listOf(
                "thread", "race", "deadlock", "concurren", "async", "coroutine", "parallel", "synchroni", "mutex",
                "поток", "гонк", "корутин", "асинхрон", "многопоточ", "блокировк", "дедлок",
            ),
        ),
        listOf(
            "Does the bug appear only sometimes? Which threads or coroutines touch the same data at the same time?",
            "What happens if you make it sequential and run everything on one thread? If the bug disappears, it is about ordering or shared state.",
            "Who waits for whom? If two pieces of code wait for each other's locks or results, that's a deadlock.",
        )
    ),
    DATABASE(
        KeywordMatcher(
            prefixes = listOf(
                "database", "sql", "query", "queries", "migration", "transaction", "postgres", "mysql", "sqlite",
                "mongo", "hibernate", "база данных", "базе данных", "бд", "миграц", "транзакц",
            ),
            words = listOf("db", "orm", "jdbc", "jpa"),
        ),
        listOf(
            "What SQL actually reaches the database? Log the final query with its parameters and run it by hand.",
            "Is the data what you think it is? Look at the actual rows instead of assuming.",
            "Could a transaction be involved - uncommitted changes, a rollback, or two transactions seeing different data?",
        )
    ),
    NETWORK(
        KeywordMatcher(
            prefixes = listOf(
                "http", "request", "response", "timeout", "timed out", "network", "socket", "cors", "endpoint",
                "ssl", "certificat", "запрос", "сеть", "сети", "сетев", "таймаут", "эндпоинт",
            ),
            words = listOf("api", "url", "dns", "rest", "grpc", "401", "403", "404", "500", "502", "503"),
        ),
        listOf(
            "What exactly is sent and what comes back? Compare the real request (URL, headers, body) with what the server expects.",
            "Does the same request work outside your code, for example with curl or Postman? That cuts the problem in half.",
            "What is the status code and the response body? 401, 404 and 500 each point in a different direction.",
        )
    ),
    TEST(
        KeywordMatcher(prefixes = listOf("test", "assert", "junit", "pytest", "mock", "flaky", "тест")),
        listOf(
            "Is the code wrong, or does the test expect the wrong thing? Read the assertion message: what was expected and what was actual?",
            "Does the test fail when you run it alone, or only together with other tests? Shared state and test order are classic suspects.",
            "Is the test deterministic? Time, randomness, collection ordering or network calls can make it flaky.",
        )
    ),
    BUILD(
        KeywordMatcher(
            prefixes = listOf(
                "build", "gradle", "maven", "dependenc", "classpath", "сборк", "собира", "зависимост",
            ),
            words = listOf("npm", "pip", "cmake", "webpack"),
        ),
        listOf(
            "What is the very first error in the build output? Later errors are often just consequences of the first one.",
            "Did dependencies or their versions change recently? Does a clean build behave the same way?",
            "Does it build from the command line but not in the IDE, or the other way around? That points to configuration rather than code.",
        ),
        irrelevantPhases = setOf(DebugPhase.EXPECTED_VS_ACTUAL),
    ),
    PERFORMANCE(
        KeywordMatcher(
            prefixes = listOf(
                "slow", "performance", "lag", "freez", "hang", "memory", "leak", "outofmemory", "latency",
                "медлен", "тормоз", "памят", "завис", "утечк", "долго",
            ),
            words = listOf("oom", "cpu"),
        ),
        listOf(
            "Have you measured where the time actually goes? A profiler or a few timestamps in the log usually beat guessing.",
            "Does it get slower with more data? How does the time change when the input doubles?",
            "Is something running more often than you think - a loop inside a loop, a query per item, a repeated recalculation?",
        )
    ),
    USER_INTERFACE(
        KeywordMatcher(
            prefixes = listOf(
                "layout", "button", "render", "display", "screen", "click", "css", "widget", "repaint",
                "кнопк", "верстк", "отображ", "экран", "интерфейс",
            ),
            words = listOf("ui", "ux", "html", "dom", "view", "swing"),
        ),
        listOf(
            "Is the data wrong, or is it only shown wrong? Check the value right before it is rendered.",
            "Is the UI updated on the right thread and at the right moment - after the data has actually changed?",
            "Can you reproduce it with the simplest possible screen or component, without the rest of the app?",
        )
    ),
    ENVIRONMENT(
        KeywordMatcher(
            prefixes = listOf(
                "works on my machine", "environment", "config", "deploy", "docker", "production", "staging",
                "merge", "conflict", "rebase", "branch", "окружени", "конфиг", "деплой", "ветк", "мерж",
            ),
            words = listOf("env", "git", "ci", "prod", "version", "версия", "версии"),
        ),
        listOf(
            "What is different between where it works and where it doesn't - versions, config, environment variables, data?",
            "When did it last work? If you know a good commit, git bisect can find the change that broke it.",
            "Is the configuration you are looking at the one that is actually loaded at runtime? Print it from inside the program.",
        )
    ),
    LOGIC(
        KeywordMatcher(
            prefixes = listOf(
                "wrong", "incorrect", "unexpected", "off by one", "off-by-one", "loop", "index", "calculat",
                "неправильн", "неверн", "не то", "не тот", "цикл", "индекс", "считает",
            ),
        ),
        listOf(
            "Take one concrete input and compute the expected result by hand. At which step does the program start to disagree?",
            "Check the boundaries: empty input, a single element, the first and the last iteration of the loop. Do they behave?",
            "Which condition decides between the right and the wrong branch? Is it evaluated the way you think - negation, precedence, < versus <=?",
        )
    ),
    EXCEPTION(
        KeywordMatcher(
            prefixes = listOf(
                "exception", "stack trace", "stacktrace", "traceback", "crash", "throw", "panic", "segfault",
                "исключени", "падает", "упал", "краш", "ошибк",
            ),
            words = listOf("error", "errors"),
        ),
        listOf(
            "What does the error message say, word by word? Find the first stack trace frame from your own code - which line is it?",
            "Is that exception the root cause, or is it wrapped? Look for the last \"Caused by\" in the stack trace - what does it say?",
            "What input or state reaches that line when it throws? Could you log it or set a breakpoint right before it?",
        )
    );

    fun matches(lowercaseText: String): Boolean = matcher.matches(lowercaseText)
}

/**
 * Matches lowercase text against keywords. [prefixes] match at the start of a word (`test` matches `tests`, but not
 * `latest`), [words] only match whole words (`ui` matches `ui`, but not `build`).
 */
class KeywordMatcher(prefixes: List<String> = emptyList(), words: List<String> = emptyList()) {
    private val regex: Regex? = buildList {
        prefixes.forEach { add("$WORD_START${Regex.escape(it)}") }
        words.forEach { add("$WORD_START${Regex.escape(it)}$WORD_END") }
    }.takeIf { it.isNotEmpty() }?.let { Regex(it.joinToString("|")) }

    fun matches(lowercaseText: String): Boolean = regex?.containsMatchIn(lowercaseText) ?: false

    private companion object {
        const val WORD_START = "(?<![\\p{L}\\p{N}_])"
        const val WORD_END = "(?![\\p{L}\\p{N}_])"
    }
}
