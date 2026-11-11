package ru.itmo.debugduck

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import ru.itmo.config.DuckSettings
import ru.itmo.config.MyMessageBundle
import ru.itmo.pet.Event
import ru.itmo.service.DuckService
import kotlin.random.Random

data class DebugDuckState(
    val messages: List<ChatMessage>,
    /** The duck is preparing an answer; the user can't send another message until it is ready. */
    val thinking: Boolean = false,
    /** Changes with every new conversation, so a late answer can't end up in the next conversation. */
    val conversationId: Int = 0,
    /** Code and errors picked with "Ask Debug Duck", sent with the next message. */
    val pending: PendingContext? = null,
    /** The part of the language model's answer received so far, while [thinking]. */
    val partialAnswer: String? = null,
    /** The user said the problem is solved; counted once per conversation. */
    val solved: Boolean = false,
)

/**
 * One Debug Duck conversation per project. The duck answers either with the built-in questions ([LocalDebugDuck]) or,
 * if enabled in the settings, with a language model ([OpenAiCompatibleClient]). If the model fails, the user sees why,
 * and the conversation continues with a built-in question.
 */
@Service(Service.Level.PROJECT)
class DebugDuckService(project: Project, private val scope: CoroutineScope) {
    private val localDuck = LocalDebugDuck(language = DuckSettings.getInstance().initialDuckLanguage)
    private val llmClient = OpenAiCompatibleClient()
    private val history = DebugDuckHistory.getInstance(project)
    private val mutableState = MutableStateFlow(restoreOrGreet())
    private var answerJob: Job? = null

    val state: StateFlow<DebugDuckState> = mutableState.asStateFlow()

    init {
        // keep the conversation between IDE restarts; the streamed part of an answer is not saved, only messages
        scope.launch {
            state.map { StoredConversation(it.messages, it.solved) }.distinctUntilChanged().drop(1).collect { history.save(it) }
        }
    }

    /** The conversation from the last session, or a new one with a greeting. */
    private fun restoreOrGreet(): DebugDuckState {
        val stored = history.load() ?: return DebugDuckState(listOf(greeting()))
        synchronized(localDuck) {
            localDuck.fixedLanguage = DuckSettings.getInstance().fixedDuckLanguage
            localDuck.restore(stored.messages)
        }
        return DebugDuckState(stored.messages, solved = stored.solved)
    }

    /** Calls [listener] on the EDT with the current state and every change, until [disposable] is disposed. */
    fun subscribe(disposable: Disposable, listener: (DebugDuckState) -> Unit) = subscribe(state, disposable, listener)

    /** Calls [listener] on the EDT with every value of [flow], until [disposable] is disposed. */
    fun <T> subscribe(flow: Flow<T>, disposable: Disposable, listener: (T) -> Unit) {
        val job = scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            flow.collect { listener(it) }
        }
        Disposer.register(disposable) { job.cancel() }
    }

    /** The user says the problem is solved: the duck congratulates, and the pet in the status bar is happy. */
    fun markSolved() {
        val congratulation = synchronized(localDuck) {
            localDuck.fixedLanguage = DuckSettings.getInstance().fixedDuckLanguage
            localDuck.congratulate().text
        }
        recordSolved()
        mutableState.update { it.copy(messages = it.messages + ChatMessage(ChatRole.DUCK, congratulation)) }
    }

    private fun recordSolved() {
        val before = mutableState.getAndUpdate { it.copy(solved = true) }
        if (!before.solved) DebugDuckStats.getInstance().problemSolved()
        DuckService.getInstance().handle(Event.ProblemSolved)
    }

    /** Attaches code and errors from the editor to the next message, replacing what was attached before. */
    fun attach(context: PendingContext) {
        mutableState.update { it.copy(pending = context.takeUnless { context.isEmpty }) }
    }

    fun clearPending() {
        mutableState.update { it.copy(pending = null) }
    }

    /**
     * Sends the user's message with the pending context and the IDE problems found automatically ([autoProblems]).
     * Ignored while the duck is still answering the previous message.
     * @return whether a message was sent
     */
    fun send(text: String, autoProblems: List<IdeProblem> = emptyList()): Boolean {
        val current = mutableState.value
        if (current.thinking) return false
        val message = composeUserMessage(text, current.pending, autoProblems, current.messages) ?: return false

        // talking to the duck is activity too: the duck in the status bar wakes up and works along
        DuckService.getInstance().handle(Event.Activity())
        if (LocalDebugDuck.isSolved(message.text)) recordSolved()

        val newState = mutableState.updateAndGet {
            it.copy(messages = it.messages + message, thinking = true, pending = null)
        }
        val conversationId = newState.conversationId
        answerJob = scope.launch {
            val answer = try {
                answer(newState.messages, message, conversationId)
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Exception) {
                LOG.warn("Debug Duck failed to answer", e)
                listOf(ChatMessage(ChatRole.NOTICE, MyMessageBundle.message("debugduck.error.unexpected")), localAnswer(message))
            }
            mutableState.update {
                // a stopped answer or one from an older conversation is dropped
                if (it.conversationId != conversationId || !it.thinking) it
                else it.copy(messages = it.messages + answer, thinking = false, partialAnswer = null)
            }
        }
        return true
    }

    /** Stops the answer in progress; what the language model has written so far stays in the chat. */
    fun stop() {
        val before = mutableState.getAndUpdate { state ->
            if (!state.thinking) return@getAndUpdate state
            val partial = state.partialAnswer?.takeIf { it.isNotBlank() }?.let {
                ChatMessage(ChatRole.DUCK, it, model = DuckSettings.getInstance().llmConfig(null).model.trim())
            }
            val notice = ChatMessage(ChatRole.NOTICE, MyMessageBundle.message("debugduck.stopped"))
            state.copy(messages = state.messages + listOfNotNull(partial, notice), thinking = false, partialAnswer = null)
        }
        if (before.thinking) answerJob?.cancel()
    }

    /** Forgets the conversation (cancelling an answer in progress) and greets the user again. */
    fun newConversation() {
        answerJob?.cancel()
        synchronized(localDuck) { localDuck.reset() }
        // what the user just attached stays: they probably want to ask about it in the new conversation
        mutableState.update { DebugDuckState(listOf(greeting()), conversationId = it.conversationId + 1, pending = it.pending) }
        // the old conversation is forgotten for good: the saved copy is replaced by the collector in init
    }

    /** E.g. "Mode: language model (gpt-5-mini) · Solved with the duck: 3", shown in the tool window. */
    val modeDescription: String
        get() {
            val settings = DuckSettings.getInstance()
            val mode = if (settings.isLlmEnabled) {
                MyMessageBundle.message("debugduck.mode.llm", settings.llmConfig(null).model.ifBlank { "?" })
            }
            else {
                MyMessageBundle.message("debugduck.mode.local")
            }
            val solved = DebugDuckStats.getInstance().solvedProblems
            return if (solved == 0) mode else mode + " · " + MyMessageBundle.message("debugduck.solved.count", solved)
        }

    private suspend fun answer(history: List<ChatMessage>, message: ChatMessage, conversationId: Int): List<ChatMessage> {
        val settings = DuckSettings.getInstance()
        if (!settings.isLlmEnabled) {
            // a short pause, so the duck seems to think about the question instead of firing it back instantly
            delay(Random.nextLong(400, 900))
            return listOf(localAnswer(message))
        }

        val config = settings.llmConfig(DebugDuckCredentials.getInstance().apiKey())
        return try {
            var lastShown = 0L
            val text = runInterruptible(Dispatchers.IO) {
                llmClient.complete(config, DebugDuckPrompt.buildMessages(history, settings.fixedDuckLanguage)) { partial ->
                    // the chat is re-rendered on every update, a few times a second is plenty
                    val now = System.currentTimeMillis()
                    if (now - lastShown >= PARTIAL_UPDATE_MILLIS) {
                        lastShown = now
                        mutableState.update {
                            if (it.conversationId != conversationId || !it.thinking) it else it.copy(partialAnswer = partial)
                        }
                    }
                }
            }
            listOf(ChatMessage(ChatRole.DUCK, text, model = config.model.trim()))
        }
        catch (e: LlmException) {
            LOG.info("Debug Duck LLM request failed: ${e.message}", e.cause)
            listOf(
                ChatMessage(ChatRole.NOTICE, MyMessageBundle.message("debugduck.llm.error", e.message.orEmpty())),
                localAnswer(message),
            )
        }
    }

    private fun localAnswer(message: ChatMessage): ChatMessage {
        val reply = synchronized(localDuck) {
            localDuck.fixedLanguage = DuckSettings.getInstance().fixedDuckLanguage
            localDuck.reply(message.text, message.attachment, message.problems)
        }
        return ChatMessage(ChatRole.DUCK, reply.text)
    }

    private fun greeting() = ChatMessage(ChatRole.DUCK, synchronized(localDuck) {
        // a new conversation starts in the chosen language, or in the one the user spoke last
        localDuck.fixedLanguage = DuckSettings.getInstance().fixedDuckLanguage
        localDuck.greeting().text
    })

    companion object {
        private val LOG = logger<DebugDuckService>()
        private const val PARTIAL_UPDATE_MILLIS = 120L

        fun getInstance(project: Project): DebugDuckService = project.service()
    }
}
