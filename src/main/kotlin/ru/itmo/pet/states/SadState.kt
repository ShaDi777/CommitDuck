package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

/**
 * Not implemented yet: the duck needs a `sad` sprite before this state can be used.
 * Transferred from pattern_state, kept commented out so the plugin still runs.
 */
class SadState : PetBaseState() {
    override val displayName = "Sad"
    override val type = PetStateType.SAD

    override fun handleSpecific(event: Event): PetState = when (event) {
        Event.Sleep -> SleepState()
        else -> this
    }
}
