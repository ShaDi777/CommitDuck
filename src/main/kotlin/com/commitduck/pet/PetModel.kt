package com.commitduck.pet

import com.commitduck.pet.states.IdleState
import com.commitduck.pet.states.PetState
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The duck's state machine and observable state holder.
 *
 * - [handle] delegates incoming events to the current [PetState].
 * - [addListener] lets the UI subscribe and re-render whenever the state changes.
 */
class PetModel(initialState: PetState = IdleState()) {

    var state: PetState = initialState
        private set

    private val listeners = CopyOnWriteArrayList<(PetState) -> Unit>()

    fun addListener(listener: (PetState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (PetState) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Feeds an event into the state machine and, if the state actually changed,
     * notifies all listeners.
     */
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