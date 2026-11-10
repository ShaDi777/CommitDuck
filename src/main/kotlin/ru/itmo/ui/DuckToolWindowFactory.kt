package ru.itmo.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/** A resizable panel showing a large animated duck that follows the duck's current state. */
class DuckToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.contentManager.addContent(ContentFactory.getInstance().createContent(DuckView(), null, false))
    }
}