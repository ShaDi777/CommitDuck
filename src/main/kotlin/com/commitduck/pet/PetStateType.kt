package com.commitduck.pet

import com.commitduck.pet.states.HappyState
import com.commitduck.pet.states.IdleState
import com.commitduck.pet.states.PetState
import com.commitduck.pet.states.SadState
import com.commitduck.pet.states.SleepState
import com.commitduck.pet.states.WorkingState

/**
 * The discrete states a duck can be in.
 *
 * Designed to be easily extended: to add a new state, add an enum constant
 * (and optionally a corresponding image + phrase pool) without touching the
 * rest of the pet logic.
 */
enum class PetStateType(val displayName: String) {
    IDLE("Idle"),
    WORKING("Working"),
    HAPPY("Happy"),
    SAD("Sad"),
    SLEEPING("Sleeping"),
}

fun PetStateType.createState(): PetState = when (this) {
    PetStateType.IDLE -> IdleState()
    PetStateType.WORKING -> WorkingState()
    PetStateType.HAPPY -> HappyState()
    PetStateType.SAD -> SadState()
    PetStateType.SLEEPING -> SleepState()
}