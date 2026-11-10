package com.commitduck.persistence

import com.commitduck.pet.PetState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-scoped persistence: the duck's last known state and the total
 * number of characters typed survive IDE restarts.
 *
 * Registered in plugin.xml as an `<applicationService>`.
 */
@Service
@State(name = "CommitDuckPetPersistence", storages = [Storage("commitduck.xml")])
class PetPersistence : PersistentStateComponent<PetPersistence.State> {

    data class State(
        var typedCharacters: Long = 0L,
        var lastPetState: String = PetState.IDLE.name,
        var isAwake: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    /** Adds [chars] to the persisted character counter and returns the new total. */
    fun addTypedCharacters(chars: Int): Long {
        myState.typedCharacters += chars
        return myState.typedCharacters
    }

    fun setPetState(state: PetState) {
        myState.lastPetState = state.name
    }

    companion object {
        fun getInstance(): PetPersistence =
            ApplicationManager.getApplication().getService(PetPersistence::class.java)
    }
}