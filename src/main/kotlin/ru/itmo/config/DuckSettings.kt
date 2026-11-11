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
import ru.itmo.debugduck.DebugDuckCredentials
import ru.itmo.debugduck.LlmConfig
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

        /** Debug Duck: answer with a language model instead of the built-in questions. */
        var llmEnabled by property(false)
        /** Base URL of an OpenAI-compatible API. The API key is kept in the IDE password safe, see [DebugDuckCredentials]. */
        var llmApiUrl by string(DEFAULT_LLM_API_URL)
        var llmModel by string(DEFAULT_LLM_MODEL)
        var llmTimeoutSeconds by property(60)
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
    override fun createPanel(): DialogPanel {
        val options = DuckSettings.getInstance().state
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

            group(MyMessageBundle.message("settings.duck.llm.group")) {
                row {
                    checkBox(MyMessageBundle.message("settings.duck.llm.enabled"))
                        .bindSelected(options::llmEnabled)
                        .comment(MyMessageBundle.message("settings.duck.llm.enabled.comment"))
                }
                row(MyMessageBundle.message("settings.duck.llm.url")) {
                    textField()
                        .align(AlignX.FILL)
                        .bindText({ options.llmApiUrl.orEmpty() }, { options.llmApiUrl = it.trim() })
                        .comment(MyMessageBundle.message("settings.duck.llm.url.comment"))
                }
                row(MyMessageBundle.message("settings.duck.llm.model")) {
                    textField()
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
                }
                row(MyMessageBundle.message("settings.duck.llm.timeout")) {
                    spinner(5..600).bindIntValue(options::llmTimeoutSeconds)
                    label(MyMessageBundle.message("settings.duck.seconds"))
                }
            }
        }
    }
}
