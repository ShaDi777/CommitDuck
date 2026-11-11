package ru.itmo.debugduck

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

/**
 * "Ask Debug Duck" in the editor's context menu: attaches the selection (or the lines around the caret) together with
 * the errors highlighted there, and opens the Debug Duck so the user only has to type the question.
 * Registered in plugin.xml; its text comes from `action.CommitDuck.AskDebugDuck.text` in MyMessageBundle.
 */
class AskDebugDuckAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        DebugDuckService.getInstance(project).attach(DebugDuckContext.getInstance(project).editorContext(editor))
        // activating moves the focus into the tool window, i.e. to the message field
        ToolWindowManager.getInstance(project).getToolWindow(DebugDuckToolWindowFactory.ID)?.activate(null)
    }
}
