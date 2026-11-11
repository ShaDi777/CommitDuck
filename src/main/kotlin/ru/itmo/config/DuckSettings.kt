package ru.itmo.config

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
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

    companion object {
        fun getInstance(): DuckSettings = service()
    }
}

/** Settings | Tools | Commit Duck */
class DuckConfigurable : BoundConfigurable(MyMessageBundle.message("settings.duck.name")) {
    override fun createPanel(): DialogPanel {
        val options = DuckSettings.getInstance().state
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
        }
    }
}
