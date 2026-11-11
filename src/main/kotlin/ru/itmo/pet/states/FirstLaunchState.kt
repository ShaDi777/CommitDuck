package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

/**
 * Short reaction: a welcome on the very first launch. When it's over ([Event.WakeUp], sent by `DuckService`), the duck goes idle;
 * activity puts it to work right away (handled by [PetBaseState]).
 */
class FirstLaunchState : PetBaseState() {
    override val displayName = PetStateType.FIRST_LAUNCH.displayName
    override val type = PetStateType.FIRST_LAUNCH

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.WakeUp, Event.IdleStarted -> IdleState()
        Event.Sleep -> SleepState()
        else -> this
    }
}
