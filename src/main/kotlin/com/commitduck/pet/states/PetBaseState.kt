package com.commitduck.pet.states

import com.commitduck.pet.Event

/**
 * Базовая реализация состояния питомца и его переходов
 */
abstract class PetBaseState : PetState {
    override fun handle(event: Event): PetState = when (event) {
        is Event.Activity -> WorkingState()
        Event.DuckClicked -> HappyState()
        Event.Commit -> HappyState()
        is Event.ThresholdReached -> HappyState()
        else -> handleSpecific(event)
    }

    protected open fun handleSpecific(event: Event): PetState = this
}