package ru.itmo.pet.states

import ru.itmo.pet.Event
import ru.itmo.pet.PetStateType

/** Contract of a duck state in the state pattern. */
interface PetState {
    val displayName: String
    val type: PetStateType

    /** Feeds [event] into this state and returns the next state. */
    fun handle(event: Event): PetState
}