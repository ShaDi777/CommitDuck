package ru.itmo.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.itmo.config.DuckSettings
import ru.itmo.pet.Event
import ru.itmo.pet.PetModel
import ru.itmo.pet.PetStateType
import ru.itmo.pet.states.GreetingState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The one duck shared by all project windows. */
@Service(Service.Level.APP)
class DuckService(scope: CoroutineScope) {

    private val model = PetModel(GreetingState())
    private val settings = DuckSettings.getInstance()

    private val mutableState = MutableStateFlow(model.state.type)
    val state: StateFlow<PetStateType> = mutableState.asStateFlow()

    private var lastActivityMillis = System.currentTimeMillis()
    private var deletedUntilMillis = Long.MIN_VALUE
    private var happyUntilMillis = Long.MIN_VALUE

    init {
        if(settings.isFirstLaunch()) {
            model.handle(Event.FirstLaunch)
            settings.markLaunched()
        }

        scope.launch {
            while (true) {
                delay(CHECK_INTERVAL)
                checkTimeouts()
            }
        }
    }

    fun handle(event: Event) {
        synchronized(model) {
            updateTimestamps(event)

            model.handle(event)
            publishState()
        }
    }

    private fun checkTimeouts() {
        val now = System.currentTimeMillis()
        val timeouts = DuckSettings.getInstance().timeouts()

        synchronized(model) {
            val quietFor = (now - lastActivityMillis).milliseconds

            when (model.state.type) {
                PetStateType.DELETED -> {
                    if (now >= deletedUntilMillis) {
                        model.handle(Event.WakeUp)
                    }
                }

                PetStateType.HAPPY -> {
                    if (now >= happyUntilMillis) {
                        model.handle(Event.WakeUp)
                    }
                }

                PetStateType.SLEEPING -> Unit

                // the greeting is a short reaction too: without it the duck would greet until the user types
                PetStateType.GREETING, PetStateType.FIRST_LAUNCH -> {
                    if (quietFor >= GREETING_DURATION) {
                        model.handle(Event.WakeUp)
                    }
                }

                PetStateType.SAD -> {
                    if (quietFor >= timeouts.sleepAfter) {
                        model.handle(Event.Sleep)
                    }
                }

                PetStateType.IDLE -> {
                    when {
                        quietFor >= timeouts.sleepAfter ->
                            model.handle(Event.Sleep)

                        quietFor >= timeouts.sadAfter ->
                            model.handle(Event.IdleExtended)
                    }
                }

                PetStateType.WORKING -> {
                    when {
                        quietFor >= timeouts.sleepAfter ->
                            model.handle(Event.Sleep)

                        quietFor >= timeouts.idleAfter ->
                            model.handle(Event.IdleStarted)
                    }
                }

                else -> Unit
            }

            publishState()
        }
    }

    private fun updateTimestamps(event: Event) {
        val now = System.currentTimeMillis()
        val timeouts = DuckSettings.getInstance().timeouts()

        when (event) {
            is Event.Activity -> lastActivityMillis = now

            Event.Deleted -> {
                lastActivityMillis = now
                deletedUntilMillis =
                    now + timeouts.deletedFor.inWholeMilliseconds
            }

            Event.BuildSucceeded, Event.TestsPassed, Event.Commit, Event.ProblemSolved -> {
                lastActivityMillis = now
                happyUntilMillis =
                    now + timeouts.happyFor.inWholeMilliseconds
            }

            else -> Unit
        }
    }

    private fun publishState() {
        mutableState.value = model.state.type
    }

    companion object {
        /** How long the greeting (and the first-launch welcome) is shown if the user doesn't start typing. */
        private val GREETING_DURATION = 4.seconds

        private val CHECK_INTERVAL = 1.seconds

        fun getInstance(): DuckService = service()
    }
}
