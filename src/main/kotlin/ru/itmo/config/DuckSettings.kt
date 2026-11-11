package ru.itmo.config

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import ru.itmo.statistics.DuckStatistics
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Cell
import ru.itmo.debugduck.DebugDuckCredentials
import ru.itmo.debugduck.DebugDuckPrompt
import ru.itmo.debugduck.DuckLanguage
import ru.itmo.debugduck.LlmConfig
import ru.itmo.debugduck.LlmException
import ru.itmo.debugduck.OpenAiCompatibleClient
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

data class DuckTimeouts(
    /** No activity for this long: [PetStateType.WORKING] -> [PetStateType.IDLE]. */
    val idleAfter: Duration,
    /** No activity for this long: any state -> [PetStateType.SAD]. */
    val sadAfter: Duration,
    /** No activity for this long: any state -> [PetStateType.SLEEPING]. */
    val sleepAfter: Duration,
    /** How long the duck shows [PetStateType.DELETED] after the user deletes files. */
    val deletedFor: Duration,
    /** How long the duck shows [PetStateType.HAPPY] after a successful build or test run. */
    val happyFor: Duration,
)

/** Which language the Debug Duck speaks. Stored by name, so don't override `toString` (the XML serializer uses it). */
enum class DuckLanguageSetting(private val titleKey: String) {
    AUTO("settings.duck.language.auto"),
    ENGLISH("settings.duck.language.english"),
    RUSSIAN("settings.duck.language.russian");

    val title: String
        get() = MyMessageBundle.message(titleKey)
}

@Service(Service.Level.APP)
@State(name = "CommitDuckSettings", storages = [Storage("commitDuck.xml")])
class DuckSettings : SimplePersistentStateComponent<DuckSettings.Options>(Options()) {
    class Options : BaseState() {
        var idleAfterSeconds by property(30)
        var sadAfterSeconds by property(60)
        var sleepAfterMinutes by property(10)
        var deletedForSeconds by property(5)
        var happyForSeconds by property(5)
        var isFirstLaunch by property(true)
        var quackOnClick by property(true)
        var typedChars by property(0)

        /** Debug Duck: answer with a language model instead of the built-in questions. */
        var llmEnabled by property(false)
        /** Base URL of an OpenAI-compatible API. The API key is kept in the IDE password safe, see [DebugDuckCredentials]. */
        var llmApiUrl by string(DEFAULT_LLM_API_URL)
        var llmModel by string(DEFAULT_LLM_MODEL)
        var llmTimeoutSeconds by property(60)
        /** Debug Duck: send errors from the editor, the last build and the last test run along with messages. */
        var shareIdeProblems by property(true)
        var duckLanguage by enum(DuckLanguageSetting.AUTO)
        /** The user agreed that messages, code and errors are sent to a remote language model. */
        var llmPrivacyAccepted by property(false)
    }

    fun timeouts() = DuckTimeouts(
        idleAfter = state.idleAfterSeconds.seconds,
        sadAfter = state.sadAfterSeconds.seconds,
        sleepAfter = state.sleepAfterMinutes.minutes,
        deletedFor = state.deletedForSeconds.seconds,
        happyFor = state.happyForSeconds.seconds,
    )

    fun isFirstLaunch() = state.isFirstLaunch

    fun markLaunched() {
        state.isFirstLaunch = false
    }

    val isLlmEnabled: Boolean
        get() = state.llmEnabled

    /** The language chosen in the settings; `null` for "the language of the user's message". */
    val fixedDuckLanguage: DuckLanguage?
        get() = when (state.duckLanguage) {
            DuckLanguageSetting.AUTO -> null
            DuckLanguageSetting.ENGLISH -> DuckLanguage.ENGLISH
            DuckLanguageSetting.RUSSIAN -> DuckLanguage.RUSSIAN
        }

    /** The language of the greeting: the chosen one, or the system language. */
    val initialDuckLanguage: DuckLanguage
        get() = fixedDuckLanguage ?: DuckLanguage.forLocale(Locale.getDefault().toLanguageTag())

    fun llmConfig(apiKey: String?) = LlmConfig(
        apiUrl = state.llmApiUrl.orEmpty(),
        model = state.llmModel.orEmpty(),
        apiKey = apiKey,
        timeoutSeconds = state.llmTimeoutSeconds,
    )

    companion object {
        const val DEFAULT_LLM_API_URL = "https://api.openai.com/v1"
        const val DEFAULT_LLM_MODEL = "gpt-5-mini"

        fun getInstance(): DuckSettings = service()
    }
}

/** Settings | Tools | Commit Duck */
class DuckConfigurable : BoundConfigurable(MyMessageBundle.message("settings.duck.name")) {
    // the LLM fields, so "Test Connection" can try the values before they are applied
    private lateinit var urlField: Cell<JBTextField>
    private lateinit var modelField: Cell<JBTextField>
    private lateinit var apiKeyField: Cell<JBPasswordField>
    private lateinit var timeoutField: Cell<JBIntSpinner>

    override fun createPanel(): DialogPanel {
        val options = DuckSettings.getInstance().state
        val statistics = DuckStatistics.getInstance()
        val credentials = DebugDuckCredentials.getInstance()
        return panel {
            row(MyMessageBundle.message("settings.duck.idle.after")) {
                spinner(1..3600).bindIntValue(options::idleAfterSeconds)
                label(MyMessageBundle.message("settings.duck.seconds"))
            }
            row(MyMessageBundle.message("settings.duck.sad.after")) {
                spinner(1..3600).bindIntValue(options::sadAfterSeconds)
                label(MyMessageBundle.message("settings.duck.seconds"))
            }
            row(MyMessageBundle.message("settings.duck.sleep.after")) {
                spinner(1..24 * 60).bindIntValue(options::sleepAfterMinutes)
                label(MyMessageBundle.message("settings.duck.minutes"))
            }
            row(MyMessageBundle.message("settings.duck.deleted.for")) {
                spinner(1..60).bindIntValue(options::deletedForSeconds)
                label(MyMessageBundle.message("settings.duck.seconds"))
            }
            row(MyMessageBundle.message("settings.duck.happy.for")) {
                spinner(1..60).bindIntValue(options::happyForSeconds)
                label(MyMessageBundle.message("settings.duck.seconds"))
            }
            row {
                checkBox(MyMessageBundle.message("settings.duck.quack.on.click")).bindSelected(options::quackOnClick)
            }

            row("Statistics") {
                label("Characters entered: ${statistics.typedChars.value}")
            }

            group(MyMessageBundle.message("settings.duck.llm.group")) {
                row(MyMessageBundle.message("settings.duck.language")) {
                    comboBox(DuckLanguageSetting.entries, SimpleListCellRenderer.create<DuckLanguageSetting?>("") { it?.title ?: "" })
                        .bindItem({ options.duckLanguage }, { options.duckLanguage = it ?: DuckLanguageSetting.AUTO })
                }
                row {
                    checkBox(MyMessageBundle.message("settings.duck.share.problems"))
                        .bindSelected(options::shareIdeProblems)
                        .comment(MyMessageBundle.message("settings.duck.share.problems.comment"))
                }
                row {
                    checkBox(MyMessageBundle.message("settings.duck.llm.enabled"))
                        .bindSelected(options::llmEnabled)
                        .comment(MyMessageBundle.message("settings.duck.llm.enabled.comment"))
                }
                row(MyMessageBundle.message("settings.duck.llm.url")) {
                    urlField = textField()
                        .align(AlignX.FILL)
                        .bindText({ options.llmApiUrl.orEmpty() }, { options.llmApiUrl = it.trim() })
                        .comment(MyMessageBundle.message("settings.duck.llm.url.comment"))
                }
                row(MyMessageBundle.message("settings.duck.llm.model")) {
                    modelField = textField()
                        .columns(COLUMNS_MEDIUM)
                        .bindText({ options.llmModel.orEmpty() }, { options.llmModel = it.trim() })
                }
                row(MyMessageBundle.message("settings.duck.llm.key")) {
                    val keyField = passwordField()
                        .columns(COLUMNS_MEDIUM)
                        // the password safe must not be read on the EDT, so the field shows the cached key
                        .bindText({ credentials.cachedApiKey.orEmpty() }, { credentials.saveApiKey(it) })
                        .comment(MyMessageBundle.message("settings.duck.llm.key.comment"))
                    credentials.whenLoaded { key ->
                        if (keyField.component.password.isEmpty() && key != null) keyField.component.text = key
                    }
                    apiKeyField = keyField
                }
                row(MyMessageBundle.message("settings.duck.llm.timeout")) {
                    timeoutField = spinner(5..600).bindIntValue(options::llmTimeoutSeconds)
                    label(MyMessageBundle.message("settings.duck.seconds"))
                }
                row {
                    button(MyMessageBundle.message("settings.duck.llm.test")) { testConnection() }
                        .comment(MyMessageBundle.message("settings.duck.llm.test.comment"))
                }
            }
        }
    }

    /** Sends a tiny request with the values currently in the form and shows whether the model answered. */
    private fun testConnection() {
        val component = urlField.component
        val config = LlmConfig(
            apiUrl = urlField.component.text,
            model = modelField.component.text,
            apiKey = String(apiKeyField.component.password).ifBlank { System.getenv(DebugDuckCredentials.ENV_VARIABLE) },
            // a connection test shouldn't block the settings for minutes
            timeoutSeconds = timeoutField.component.number.coerceAtMost(30),
        )
        val title = MyMessageBundle.message("settings.duck.llm.test.title")
        try {
            val answer = ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable<String, LlmException> {
                    OpenAiCompatibleClient().complete(config, DebugDuckPrompt.CONNECTION_TEST)
                },
                MyMessageBundle.message("settings.duck.llm.test.progress"),
                false,
                null,
            )
            Messages.showInfoMessage(component, MyMessageBundle.message("settings.duck.llm.test.success", answer.take(200)), title)
        }
        catch (e: LlmException) {
            Messages.showErrorDialog(component, e.message, title)
        }
    }
}
