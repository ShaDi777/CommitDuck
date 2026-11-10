package com.commitduck.ui

import com.commitduck.pet.PetStateType
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Maps each pet state to its image resource. */
object PetIcons {

    private val icons: Map<PetStateType, Icon> = PetStateType.entries.associateWith { state ->
        IconLoader.getIcon("/icons/duck_${state.name.lowercase()}.svg", PetIcons::class.java)
    }

    fun iconFor(state: PetStateType): Icon = icons.getValue(state)
}