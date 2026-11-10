package com.commitduck.pet.states

import com.commitduck.pet.Event
import com.commitduck.pet.PetStateType

class SleepState : PetBaseState() {
    override val displayName = "Sleeping"
    override val type = PetStateType.SLEEPING

    override fun handleSpecific(event: Event): PetState = when (event) {
        else -> this
    }
}