package ru.itmo

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntValue
import com.intellij.ui.dsl.builder.panel
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.APP)
@State(name = "CommitDuckSettings", storages = [Storage("commitDuck.xml")])
class DuckSettings : SimplePersistentStateComponent<DuckSettings.Options>(Options()) {
    class Options : BaseState() {
        var idleAfterSeconds by property(30)
        var sleepAfterMinutes by property(10)
        var deletedForSeconds by property(5)
    }

    fun timeouts() = DuckTimeouts(
        idleAfter = state.idleAfterSeconds.seconds,
        sleepAfter = state.sleepAfterMinutes.minutes,
        deletedFor = state.deletedForSeconds.seconds,
    )

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
            row(MyMessageBundle.message("settings.duck.sleep.after")) {
                spinner(1..24 * 60).bindIntValue(options::sleepAfterMinutes)
                label(MyMessageBundle.message("settings.duck.minutes"))
            }
            row(MyMessageBundle.message("settings.duck.deleted.for")) {
                spinner(1..60).bindIntValue(options::deletedForSeconds)
                label(MyMessageBundle.message("settings.duck.seconds"))
            }
        }
    }
}
