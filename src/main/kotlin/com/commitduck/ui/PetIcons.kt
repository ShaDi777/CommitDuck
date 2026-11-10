package com.commitduck.ui

import com.commitduck.pet.PetState
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Maps each pet state to its image resource. */
object PetIcons {

    private val icons: Map<PetState, Icon> = PetState.entries.associateWith { state ->
        IconLoader.getIcon("/icons/duck_${state.name.lowercase()}.svg", PetIcons::class.java)
    }

    fun iconFor(state: PetState): Icon = icons.getValue(state)
}