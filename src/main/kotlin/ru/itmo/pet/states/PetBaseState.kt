package ru.itmo.pet.states

import ru.itmo.pet.Event

/**
 * Base state holding the transitions shared by every state. Concrete states only override
 * [handleSpecific] with the transitions specific to them.
 */
abstract class PetBaseState : PetState {
    override fun handle(event: Event): PetState = when (event) {
        Event.FirstLaunch -> FirstLaunchState()
        // any activity puts the duck (back) to work
        is Event.Activity -> WorkingState()
        // deleting files is activity too; the deleted reaction returns to the previous state
        Event.Deleted -> DeletedState(this)
        // a successful build or test run cheers the duck up for a moment
        Event.BuildSucceeded -> HappyState(this)
        Event.TestsPassed -> HappyState(this)
        // so does a problem solved with the Debug Duck
        Event.ProblemSolved -> HappyState(this)
        // States that need sprites the duck doesn't have yet are commented out:
        // Event.DuckClicked -> HappyState()
        Event.Commit -> HappyState(this)
        // is Event.ThresholdReached -> HappyState()
        else -> handleSpecific(event)
    }

    protected open fun handleSpecific(event: Event): PetState = this
}