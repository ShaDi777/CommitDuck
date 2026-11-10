package com.commitduck.pet.states

import com.commitduck.pet.Event
import com.commitduck.pet.PetStateType

class IdleState : PetBaseState() {
    override val displayName = "Idle"
    override val type = PetStateType.IDLE

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.IdleExtended -> SadState()
        Event.Sleep -> SleepState()
        else -> this
    }
}