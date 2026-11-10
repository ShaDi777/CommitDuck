package com.commitduck.pet.states

import com.commitduck.pet.Event
import com.commitduck.pet.PetStateType

class HappyState : PetBaseState() {
    override val displayName = "Happy"
    override val type = PetStateType.IDLE

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.IdleStarted -> IdleState()
        else -> this
    }
}