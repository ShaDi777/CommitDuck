package com.commitduck.pet

import java.util.concurrent.CopyOnWriteArrayList

/**
 * The duck's state machine and observable state holder.
 *
 * - [handle] maps incoming [PetEvent]s to [PetState] transitions.
 * - [addListener] lets the UI subscribe and re-render whenever the state changes.
 *
 * The mapping is intentionally a pure, simple `when` so that adding new states
 * or events does not require changing the notification/persistence plumbing.
 */
class PetModel(initialState: PetState = PetState.IDLE) {

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
    fun handle(event: PetEvent) {
        val target = transitionFor(event)
        if (target != state) {
            transitionTo(target)
        }
    }

    private fun transitionFor(event: PetEvent): PetState = when (event) {
        is PetEvent.Activity -> PetState.WORKING
        PetEvent.IdleStarted -> PetState.IDLE
        PetEvent.IdleExtended -> PetState.SAD
        PetEvent.Sleep -> PetState.SLEEPING
        PetEvent.WakeUp -> PetState.WORKING
        PetEvent.DuckClicked -> PetState.HAPPY
        PetEvent.Commit -> PetState.HAPPY
        is PetEvent.ThresholdReached -> PetState.HAPPY
    }

    private fun transitionTo(newState: PetState) {
        state = newState
        listeners.forEach { it(newState) }
    }
}