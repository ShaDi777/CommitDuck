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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

/**
 * One Debug Duck conversation per project. The duck answers either with the built-in questions ([LocalDebugDuck]) or,
 * if enabled in the settings, with a language model ([OpenAiCompatibleClient]). If the model fails, the user sees why,
 * and the conversation continues with a built-in question.
 */
@Service(Service.Level.PROJECT)
class DebugDuckService(@Suppress("unused") private val project: Project, private val scope: CoroutineScope) {
    private val localDuck = LocalDebugDuck()
    private val llmClient = OpenAiCompatibleClient()
    private val mutableState = MutableStateFlow(DebugDuckState(listOf(greeting())))
    private var answerJob: Job? = null

    val state: StateFlow<DebugDuckState> = mutableState.asStateFlow()

    /** Calls [listener] on the EDT with the current state and every change, until [disposable] is disposed. */
    fun subscribe(disposable: Disposable, listener: (DebugDuckState) -> Unit) {
        val job = scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            state.collect { listener(it) }
        }
        Disposer.register(disposable) { job.cancel() }
    }

    /** Sends the user's message. Ignored while the duck is still answering the previous one. */
    fun send(text: String, attachment: CodeAttachment?) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() && attachment?.code == null) return
        if (mutableState.value.thinking) return

        // talking to the duck is activity too: the duck in the status bar wakes up and works along
        DuckService.getInstance().handle(Event.Activity())

        val message = ChatMessage(ChatRole.USER, trimmed, attachment)
        val newState = mutableState.updateAndGet { it.copy(messages = it.messages + message, thinking = true) }
        answerJob = scope.launch {
            val answer = try {
                answer(newState.messages, message)
            }
            catch (e: CancellationException) {
                throw e
            }
            catch (e: Exception) {
                LOG.warn("Debug Duck failed to answer", e)
                listOf(ChatMessage(ChatRole.NOTICE, MyMessageBundle.message("debugduck.error.unexpected")), localAnswer(message))
            }
            mutableState.update {
                if (it.conversationId != newState.conversationId) it
                else it.copy(messages = it.messages + answer, thinking = false)
            }
        }
    }

    /** Forgets the conversation (cancelling an answer in progress) and greets the user again. */
    fun newConversation() {
        answerJob?.cancel()
        synchronized(localDuck) { localDuck.reset() }
        mutableState.update { DebugDuckState(listOf(greeting()), thinking = false, conversationId = it.conversationId + 1) }
    }

    /** E.g. "Mode: language model (gpt-5-mini)", shown in the tool window. */
    val modeDescription: String
        get() {
            val settings = DuckSettings.getInstance()
            return if (settings.isLlmEnabled) {
                MyMessageBundle.message("debugduck.mode.llm", settings.llmConfig(null).model.ifBlank { "?" })
            }
            else {
                MyMessageBundle.message("debugduck.mode.local")
            }
        }

    private suspend fun answer(history: List<ChatMessage>, message: ChatMessage): List<ChatMessage> {
        val settings = DuckSettings.getInstance()
        if (!settings.isLlmEnabled) {
            // a short pause, so the duck seems to think about the question instead of firing it back instantly
            delay(Random.nextLong(400, 900))
            return listOf(localAnswer(message))
        }

        val config = settings.llmConfig(DebugDuckCredentials.getInstance().apiKey())
        return try {
            val text = runInterruptible(Dispatchers.IO) {
                llmClient.complete(config, DebugDuckPrompt.buildMessages(history))
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
        val reply = synchronized(localDuck) { localDuck.reply(message.text, message.attachment) }
        return ChatMessage(ChatRole.DUCK, reply.text)
    }

    private fun greeting() = ChatMessage(ChatRole.DUCK, synchronized(localDuck) { localDuck.greeting().text })

    companion object {
        private val LOG = logger<DebugDuckService>()

        fun getInstance(project: Project): DebugDuckService = project.service()
    }
}
