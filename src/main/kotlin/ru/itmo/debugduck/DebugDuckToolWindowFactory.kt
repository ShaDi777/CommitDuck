package ru.itmo.debugduck

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import ru.itmo.config.DuckConfigurable
import ru.itmo.config.DuckSettings
import ru.itmo.config.MyMessageBundle
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultEditorKit

/** The "Debug Duck" tool window: a chat with the duck about the problem the user is stuck on. */
class DebugDuckToolWindowFactory : ToolWindowFactory, DumbAware {
    companion object {
        /** The tool window id from plugin.xml. */
        const val ID = "DebugDuck"
    }

    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // starts loading the API key from the password safe, so it's ready for the first question
        DebugDuckCredentials.getInstance()

        val panel = DebugDuckPanel(project, toolWindow.disposable)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        content.preferredFocusableComponent = panel.preferredFocusedComponent
        toolWindow.contentManager.addContent(content)
        toolWindow.setTitleActions(listOf(NewConversationAction(project), OpenSettingsAction()))
    }
}

private class DebugDuckPanel(private val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()) {
    private val service = DebugDuckService.getInstance(project)

    private val history = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        // use the IDE font instead of the default HTML font
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        font = JBFont.label()
        border = JBUI.Borders.empty(4)
        // scrolling is done explicitly after each update
        (caret as? DefaultCaret)?.updatePolicy = DefaultCaret.NEVER_UPDATE
    }
    private val historyScrollPane = JBScrollPane(history).apply {
        border = JBUI.Borders.empty()
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
    }

    private val input = JBTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.setText(MyMessageBundle.message("debugduck.input.placeholder"))
    }
    /** What "Ask Debug Duck" or "Attach from editor" picked, with a link to drop it. Hidden when nothing is attached. */
    private val pendingLabel = JBLabel(AllIcons.FileTypes.Any_type).apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }
    private val pendingPanel = JPanel(BorderLayout()).apply {
        isVisible = false
        border = JBUI.Borders.emptyBottom(4)
        add(pendingLabel, BorderLayout.CENTER)
        add(ActionLink(MyMessageBundle.message("debugduck.pending.remove")) { service.clearPending() }, BorderLayout.EAST)
    }
    private val attachLink = ActionLink(MyMessageBundle.message("debugduck.attach")) { attachFromEditor() }.apply {
        toolTipText = MyMessageBundle.message("debugduck.attach.tooltip")
    }
    private val sendButton = JButton(MyMessageBundle.message("debugduck.send"))
    private val modeLabel = JBLabel().apply {
        font = JBFont.small()
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    }

    val preferredFocusedComponent: JComponent
        get() = input

    init {
        val controls = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.emptyTop(4)
            add(attachLink, BorderLayout.WEST)
            add(sendButton, BorderLayout.EAST)
            add(modeLabel, BorderLayout.SOUTH)
        }
        val inputPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createCompoundBorder(JBUI.Borders.customLineTop(JBColor.border()), JBUI.Borders.empty(6, 8))
            add(pendingPanel, BorderLayout.NORTH)
            add(JBScrollPane(input), BorderLayout.CENTER)
            add(controls, BorderLayout.SOUTH)
        }
        add(historyScrollPane, BorderLayout.CENTER)
        add(inputPanel, BorderLayout.SOUTH)

        // the same button sends a message or, while the duck is answering, stops the answer
        sendButton.addActionListener { if (service.state.value.thinking) service.stop() else send() }
        // Enter sends the message, Shift+Enter starts a new line
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), SEND_ACTION)
        input.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), DefaultEditorKit.insertBreakAction)
        input.actionMap.put(SEND_ACTION, object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = send()
        })
        // the mode may have been changed in the settings in the meantime
        input.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) = updateMode()
        })

        service.subscribe(parentDisposable, ::render)
        // the chat colors come from the theme, so re-render when the user switches it
        ApplicationManager.getApplication().messageBus.connect(parentDisposable)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { render(service.state.value) })
    }

    private fun send() {
        if (service.state.value.thinking || !confirmSendingToLlm()) return
        if (service.send(input.text, autoProblems())) {
            input.text = ""
        }
        input.requestFocusInWindow()
    }

    /**
     * Before the first message to a remote language model, makes sure the user knows that their messages, code and
     * errors leave the computer. Local servers such as Ollama don't ask.
     */
    private fun confirmSendingToLlm(): Boolean {
        val settings = DuckSettings.getInstance()
        if (!settings.isLlmEnabled || settings.state.llmPrivacyAccepted) return true
        val apiUrl = settings.llmConfig(null).apiUrl
        if (OpenAiCompatibleClient.isLocal(apiUrl)) return true
        val answer = Messages.showOkCancelDialog(
            project,
            MyMessageBundle.message("debugduck.privacy.message", apiUrl),
            MyMessageBundle.message("debugduck.privacy.title"),
            MyMessageBundle.message("debugduck.privacy.ok"),
            MyMessageBundle.message("debugduck.privacy.cancel"),
            Messages.getQuestionIcon(),
        )
        if (answer != Messages.OK) return false
        settings.state.llmPrivacyAccepted = true
        return true
    }

    /** Errors from the editor, the last build and the last test run; the service drops the ones already sent. */
    private fun autoProblems(): List<IdeProblem> {
        if (!DuckSettings.getInstance().state.shareIdeProblems) return emptyList()
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        return DebugDuckContext.getInstance(project).currentProblems(editor)
    }

    /** The selection (or the lines around the caret) of the active editor with its errors, like "Ask Debug Duck". */
    private fun attachFromEditor() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        service.attach(DebugDuckContext.getInstance(project).editorContext(editor))
        input.requestFocusInWindow()
    }

    private fun render(state: DebugDuckState) {
        val labels = ChatLabels(
            you = MyMessageBundle.message("debugduck.you"),
            duck = MyMessageBundle.message("debugduck.duck"),
            notice = MyMessageBundle.message("debugduck.notice"),
            thinking = MyMessageBundle.message("debugduck.thinking"),
        )
        history.background = UIUtil.getPanelBackground()
        val model = DuckSettings.getInstance().llmConfig(null).model.trim()
        history.text = ChatHtml.render(state.messages, state.thinking, palette(), labels, state.partialAnswer, model)
        sendButton.text = MyMessageBundle.message(if (state.thinking) "debugduck.stop" else "debugduck.send")
        sendButton.icon = if (state.thinking) AllIcons.Actions.Suspend else null
        renderPending(state.pending)
        updateMode()
        SwingUtilities.invokeLater {
            val scrollBar = historyScrollPane.verticalScrollBar
            scrollBar.value = scrollBar.maximum
        }
    }

    private fun renderPending(pending: PendingContext?) {
        pendingPanel.isVisible = pending != null
        if (pending == null) return
        val what = pending.attachment?.location ?: MyMessageBundle.message("debugduck.pending.errors.only")
        pendingLabel.text =
            if (pending.problems.isEmpty()) MyMessageBundle.message("debugduck.pending", what)
            else MyMessageBundle.message("debugduck.pending.with.errors", what, pending.problems.size)
        pendingLabel.toolTipText = pending.problems.joinToString("\n") { it.summary }.ifEmpty { null }
        pendingPanel.revalidate()
    }

    private fun updateMode() {
        modeLabel.text = service.modeDescription
    }

    /** Colors of the current IDE theme. */
    private fun palette() = ChatPalette(
        text = UIUtil.getLabelForeground().hex,
        secondaryText = JBUI.CurrentTheme.ContextHelp.FOREGROUND.hex,
        userBackground = USER_BACKGROUND.hex,
        duckBackground = DUCK_BACKGROUND.hex,
        noticeBackground = NOTICE_BACKGROUND.hex,
        noticeText = NOTICE_FOREGROUND.hex,
        codeBackground = CODE_BACKGROUND.hex,
        codeFont = codeFontFamily(),
    )

    /** The editor font if AWT knows it (it falls back to "Dialog" otherwise), so code looks like in the editor. */
    private fun codeFontFamily(): String {
        val editorFont = EditorColorsManager.getInstance().globalScheme.editorFontName
        return if (Font(editorFont, Font.PLAIN, 12).family.equals(editorFont, ignoreCase = true)) editorFont else Font.MONOSPACED
    }

    private val Color.hex: String
        get() = "#%06x".format(rgb and 0xFFFFFF)

    private companion object {
        const val SEND_ACTION = "debugDuck.send"

        val USER_BACKGROUND = JBColor(0xE6EEFF, 0x2E3A50)
        val DUCK_BACKGROUND = JBColor(0xFFF5D1, 0x3B3826)
        val NOTICE_BACKGROUND = JBColor(0xFDE8E8, 0x4A2D2D)
        val NOTICE_FOREGROUND = JBColor(0x9E2A2A, 0xF2A7A7)
        val CODE_BACKGROUND = JBColor(0xF2F3F5, 0x1E1F22)
    }
}

private class NewConversationAction(private val project: Project) : DumbAwareAction(
    MyMessageBundle.message("debugduck.new.conversation"),
    MyMessageBundle.message("debugduck.new.conversation.description"),
    AllIcons.General.Add,
) {
    override fun actionPerformed(e: AnActionEvent) = DebugDuckService.getInstance(project).newConversation()
}

private class OpenSettingsAction : DumbAwareAction(
    MyMessageBundle.message("debugduck.settings"),
    MyMessageBundle.message("debugduck.settings.description"),
    AllIcons.General.Settings,
) {
    override fun actionPerformed(e: AnActionEvent) =
        ShowSettingsUtil.getInstance().showSettingsDialog(e.project, DuckConfigurable::class.java)
}
