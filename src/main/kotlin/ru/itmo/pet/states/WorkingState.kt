package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

class WorkingState : PetBaseState() {
    override val displayName = "Working"
    override val type = PetStateType.WORKING

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.IdleStarted -> IdleState()
        else -> this
    }
}