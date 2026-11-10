package com.commitduck.ui

import com.commitduck.persistence.PetPersistence
import com.commitduck.pet.PetModel
import com.commitduck.pet.PetState
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Creates the CommitDuck tool window and its content. Restores the duck's last
 * saved state from [PetPersistence] before showing the UI.
 */
class PetToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val persistence = PetPersistence.getInstance()
        val initialState = runCatching { PetState.valueOf(persistence.getState().lastPetState) }
            .getOrDefault(PetState.IDLE)

        val model = PetModel(initialState)
        val panel = PetPanel(model, persistence)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}