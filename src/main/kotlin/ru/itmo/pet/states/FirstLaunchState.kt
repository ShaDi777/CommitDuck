package ru.itmo.pet.states

import ru.itmo.pet.PetStateType

class FirstLaunchState : PetBaseState() {
    override val displayName = PetStateType.FIRST_LAUNCH.displayName
    override val type = PetStateType.FIRST_LAUNCH
}