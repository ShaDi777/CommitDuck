package ru.itmo

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** The one duck shared by all project windows. */
@Service(Service.Level.APP)
class DuckService(scope: CoroutineScope) {
    private val machine = DuckStateMachine(System.currentTimeMillis())
    private val mutableState = MutableStateFlow(machine.state)
    val state: StateFlow<PetState> = mutableState.asStateFlow()

    init {
        scope.launch {
            while (true) {
                delay(1.seconds)
                handle(PetEvent.Tick)
            }
        }
    }

    fun handle(event: PetEvent) {
        val timeouts = DuckSettings.getInstance().timeouts()
        synchronized(machine) {
            mutableState.value = machine.handle(event, System.currentTimeMillis(), timeouts)
        }
    }

    companion object {
        fun getInstance(): DuckService = service()
    }
}
