package com.commitduck.pet.states

import com.commitduck.pet.Event
import com.commitduck.pet.PetStateType

/**
 * Контракт состояний утки
 */
interface PetState {
    val displayName: String
    val type: PetStateType
    fun handle(event: Event): PetState
}