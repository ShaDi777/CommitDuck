package ru.itmo.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import ru.itmo.config.MyMessageBundle
import ru.itmo.pet.PetStateType
import ru.itmo.service.DuckService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent
import javax.swing.Timer

private const val WIDGET_ID = "CommitDuck"

class DuckStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId() = WIDGET_ID

    override fun getDisplayName() = MyMessageBundle.message("statusbar.duck.name")

    override fun createWidget(project: Project, scope: CoroutineScope): StatusBarWidget = DuckStatusBarWidget(scope)
}

class DuckStatusBarWidget(private val scope: CoroutineScope) : CustomStatusBarWidget {
    // status bar widgets may be created on a background thread, so the Swing component is created lazily
    private val component by lazy {
        DuckComponent().also { component ->
            // any() modality: keep the duck alive while a modal dialog (e.g. Settings) is open
            scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
                DuckService.getInstance().state.collect { component.state = it }
            }
        }
    }

    override fun ID() = WIDGET_ID

    override fun getComponent(): JComponent = component
}

private class DuckComponent : JComponent() {
    private val animations = PetStateType.entries.associateWith { DuckAnimation.load(it.animationName, "status") }

    // all states share one slot so the status bar doesn't shift when the duck changes state
    private val slotWidth = animations.values.maxOf { it.frameWidth }
    private val slotHeight = animations.values.maxOf { it.frameHeight }

    private var animation = animations.getValue(PetStateType.IDLE)
    private var frame = 0
    private val timer = Timer(1000 / animation.fps) {
        frame = (frame + 1) % animation.frameCount
        repaint()
    }

    var state: PetStateType = PetStateType.IDLE
        set(value) {
            if (field == value) return
            field = value
            animation = animations.getValue(value)
            frame = 0
            timer.delay = 1000 / animation.fps
            repaint()
        }

    init {
        isOpaque = false
        border = JBUI.Borders.empty(0, 4)
        toolTipText = MyMessageBundle.message("statusbar.duck.name")
    }

    /** Animate only while the widget is actually on screen. */
    override fun addNotify() {
        super.addNotify()
        timer.start()
    }

    override fun removeNotify() {
        timer.stop()
        super.removeNotify()
    }

    override fun getPreferredSize(): Dimension {
        val insets = insets
        return Dimension(
            JBUIScale.scale(slotWidth) + insets.left + insets.right,
            JBUIScale.scale(slotHeight) + insets.top + insets.bottom,
        )
    }

    override fun getMinimumSize() = preferredSize

    override fun getMaximumSize() = preferredSize

    override fun paintComponent(g: Graphics) {
        val x = insets.left + (JBUIScale.scale(slotWidth) - JBUIScale.scale(animation.frameWidth)) / 2
        val y = (height - JBUIScale.scale(animation.frameHeight)) / 2
        animation.paintFrame(g as Graphics2D, frame, x, y)
    }
}
