package ru.itmo.pet.states

import ru.itmo.pet.PetStateType

class GreetingState : PetBaseState() {
    override val displayName = PetStateType.GREETING.displayName
    override val type = PetStateType.GREETING
}