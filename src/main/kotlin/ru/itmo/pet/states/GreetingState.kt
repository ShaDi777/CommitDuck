package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

/**
 * Short reaction: a greeting when the IDE starts. When it's over ([Event.WakeUp], sent by `DuckService`), the duck goes idle;
 * activity puts it to work right away (handled by [PetBaseState]).
 */
class GreetingState : PetBaseState() {
    override val displayName = PetStateType.GREETING.displayName
    override val type = PetStateType.GREETING

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.WakeUp, Event.IdleStarted -> IdleState()
        Event.Sleep -> SleepState()
        else -> this
    }
}
