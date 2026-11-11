package ru.itmo.listener

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import ru.itmo.pet.Event
import ru.itmo.service.DuckService

/** Edits that don't go through [afterEditorTyping] because they are actions, not typed characters. */
private val EDITING_ACTIONS = setOf(
    IdeActions.ACTION_EDITOR_ENTER,
    IdeActions.ACTION_EDITOR_BACKSPACE,
    IdeActions.ACTION_EDITOR_DELETE,
    IdeActions.ACTION_EDITOR_TAB,
    IdeActions.ACTION_EDITOR_PASTE,
    IdeActions.ACTION_EDITOR_CUT,
    IdeActions.ACTION_PASTE,
    IdeActions.ACTION_CUT,
)

/** Reports typing in code editors to [DuckService]. Registered in plugin.xml as an application listener. */
class DuckActivityListener : AnActionListener {
    override fun afterEditorTyping(c: Char, dataContext: DataContext) {
        if (isCodeEditor(CommonDataKeys.EDITOR.getData(dataContext))) {
            DuckService.getInstance().handle(Event.Activity())
        }
    }

    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        if (result.isPerformed &&
            event.actionManager.getId(action) in EDITING_ACTIONS &&
            isCodeEditor(event.getData(CommonDataKeys.EDITOR))
        ) {
            DuckService.getInstance().handle(Event.Activity())
        }
    }

    // skips consoles, the commit message field, diff viewers, etc.
    private fun isCodeEditor(editor: Editor?) = editor != null && editor.editorKind == EditorKind.MAIN_EDITOR
}
