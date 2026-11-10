package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

class SleepState : PetBaseState() {
    override val displayName = "Sleeping"
    override val type = PetStateType.SLEEPING

    override fun handleSpecific(event: Event): PetState = when (event) {
        // the duck only wakes up on activity (handled by [PetBaseState])
        else -> this
    }
}