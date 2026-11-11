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
import ru.itmo.pet.states.IdleState
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The one duck shared by all project windows. */
@Service(Service.Level.APP)
class DuckService(scope: CoroutineScope) {
    private val model = PetModel(IdleState())
    private val mutableState = MutableStateFlow(model.state.type)
    val state: StateFlow<PetStateType> = mutableState.asStateFlow()

    private var lastActivityMillis = System.currentTimeMillis()
    private var deletedUntilMillis = Long.MIN_VALUE
    private var happyUntilMillis = Long.MIN_VALUE

    init {
        scope.launch {
            while (true) {
                delay(1.seconds)
                val now = System.currentTimeMillis()
                val timeouts = DuckSettings.getInstance().timeouts()
                val quietFor = (now - lastActivityMillis).milliseconds
                synchronized(model) {
                    when {
                        // a short reaction is over
                        model.state.type == PetStateType.DELETED && now >= deletedUntilMillis ->
                            model.handle(Event.WakeUp)
                        // keep showing the reaction until it is over
                        model.state.type == PetStateType.DELETED -> {}
                        model.state.type == PetStateType.HAPPY && now >= happyUntilMillis ->
                            model.handle(Event.WakeUp)
                        // keep showing the reaction until it is over
                        model.state.type == PetStateType.HAPPY -> {}
                        // sleep takes priority over idling
                        model.state.type != PetStateType.SAD && quietFor >= timeouts.sadAfter ->
                            model.handle(Event.IdleExtended)
                        model.state.type != PetStateType.SLEEPING && quietFor >= timeouts.sleepAfter ->
                            model.handle(Event.Sleep)
                        model.state.type == PetStateType.WORKING && quietFor >= timeouts.idleAfter ->
                            model.handle(Event.IdleStarted)
                        else -> {}
                    }
                    mutableState.value = model.state.type
                }
            }
        }
    }

    fun handle(event: Event) {
        val now = System.currentTimeMillis()
        val timeouts = DuckSettings.getInstance().timeouts()
        synchronized(model) {
            when (event) {
                is Event.Activity -> lastActivityMillis = now
                Event.Deleted -> {
                    lastActivityMillis = now
                    deletedUntilMillis = now + timeouts.deletedFor.inWholeMilliseconds
                }
                Event.BuildSucceeded, Event.TestsPassed -> {
                    lastActivityMillis = now
                    happyUntilMillis = now + timeouts.happyFor.inWholeMilliseconds
                }
                else -> {}
            }
            model.handle(event)
            mutableState.value = model.state.type
        }
    }

    companion object {
        fun getInstance(): DuckService = service()
    }
}