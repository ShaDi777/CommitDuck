package ru.itmo.pet

import ru.itmo.pet.states.GreetingState
import ru.itmo.pet.states.PetState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The duck's state machine and observable state holder.
 *
 * - [handle] feeds incoming signals to the current [PetState], which decides the next one.
 * - [addListener] lets the UI subscribe and re-render whenever the state changes.
 */
class PetModel(initialState: PetState = GreetingState()) {

    var state: PetState = initialState
        private set

    private val listeners = CopyOnWriteArrayList<(PetState) -> Unit>()

    fun addListener(listener: (PetState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (PetState) -> Unit) {
        listeners.remove(listener)
    }

    /** Feeds a signal into the current state and, if the state actually changed, notifies listeners. */
    fun handle(event: Event) {
        val target = state.handle(event)
        if (target.type != state.type) {
            transitionTo(target)
        }
    }

    private fun transitionTo(newState: PetState) {
        state = newState
        listeners.forEach { it(newState) }
    }
}