package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

class IdleState : PetBaseState() {
    override val displayName = "Idle"
    override val type = PetStateType.IDLE

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.Sleep -> SleepState()
        Event.IdleExtended -> SadState()
        else -> this
    }
}