package ru.itmo.pet.states

import ru.itmo.pet.Event

/**
 * Base state holding the transitions shared by every state. Concrete states only override
 * [handleSpecific] with the transitions specific to them.
 */
abstract class PetBaseState : PetState {
    override fun handle(event: Event): PetState = when (event) {
        // any activity puts the duck (back) to work
        is Event.Activity -> WorkingState()
        // deleting files is activity too; the deleted reaction returns to the previous state
        Event.Deleted -> DeletedState(this)
        // States that need sprites the duck doesn't have yet are commented out:
        // Event.DuckClicked -> HappyState()
        // Event.Commit -> HappyState()
        // is Event.ThresholdReached -> HappyState()
        else -> handleSpecific(event)
    }

    protected open fun handleSpecific(event: Event): PetState = this
}