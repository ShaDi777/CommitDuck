package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

/**
 * Short reaction shown for a while after the user deletes files, on top of the state the
 * duck was in. [previous] is the state to return to once the reaction is over.
 */
class DeletedState(private var previous: PetState) : PetBaseState() {
    override val displayName = "Deleted"
    override val type = PetStateType.DELETED

    override fun handle(event: Event): PetState = when (event) {
        // typing during the reaction targets the state shown once it's over
        is Event.Activity -> {
            previous = WorkingState()
            this
        }
        // the reaction is over: go back, waking a sleeping duck
        Event.WakeUp -> if (previous.type == PetStateType.SLEEPING) IdleState() else previous
        else -> this
    }

    override fun handleSpecific(event: Event): PetState = this
}