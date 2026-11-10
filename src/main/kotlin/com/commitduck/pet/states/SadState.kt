package com.commitduck.pet.states

import com.commitduck.pet.Event
import com.commitduck.pet.PetStateType

class SadState : PetBaseState() {
    override val displayName = "Sad"
    override val type = PetStateType.SAD

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.Sleep -> SleepState()
        else -> this
    }
}