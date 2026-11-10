package com.commitduck.pet.states

import com.commitduck.pet.Event
import com.commitduck.pet.PetStateType

class WorkingState : PetBaseState() {
    override val displayName = "Working"
    override val type = PetStateType.WORKING

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.IdleStarted -> IdleState()
        else -> this
    }
}