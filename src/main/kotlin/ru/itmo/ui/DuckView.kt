package ru.itmo.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.ui.scale.JBUIScale
import ru.itmo.config.MyMessageBundle
import ru.itmo.pet.PetStateType
import ru.itmo.service.DuckService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.Timer

/** A large animated duck that follows [DuckService]'s current state. Used by the Duck tool window. */
class DuckView : JComponent() {
    private val animations = PetStateType.entries.associateWith { DuckAnimation.load(it.animationName, "status") }
    private var animation = animations.getValue(PetStateType.IDLE)
    private var frame = 0
    private val timer = Timer(1000 / animation.fps) {
        frame = (frame + 1) % animation.frameCount
        repaint()
    }

    /** Displayed duck height in logical pixels. */
    private val targetHeight = JBUIScale.scale(160)

    /** Clicking the duck shows its state name for this long (ms). */
    private val stateLabelMillis = 800

    var state: PetStateType = PetStateType.IDLE
        set(value) {
            if (field == value) return
            field = value
            animation = animations.getValue(value)
            frame = 0
            timer.delay = 1000 / animation.fps
            repaint()
        }

    private var collector: Job? = null
    private var stateLabelVisibleUntil = 0L

    init {
        isOpaque = false
        toolTipText = MyMessageBundle.message("statusbar.duck.name")
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                // the duck is repainted continuously by its animation timer, so the label just appears and goes away
                stateLabelVisibleUntil = System.currentTimeMillis() + stateLabelMillis
                repaint()
            }
        })
    }

    // a tool window panel may be created on a background thread, so collection starts only once it's on screen
    override fun addNotify() {
        super.addNotify()
        // any() modality: keep the duck animating while a modal dialog (e.g. Settings) is open
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.EDT + ModalityState.any().asContextElement())
        collector = scope.launch { DuckService.getInstance().state.collect { state = it } }
        timer.start()
    }

    override fun removeNotify() {
        timer.stop()
        collector?.cancel()
        collector = null
        super.removeNotify()
    }

    override fun getPreferredSize(): Dimension {
        val scale = targetHeight.toDouble() / animation.frameHeight
        return Dimension((animation.frameWidth * scale).toInt(), targetHeight)
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            val scale = targetHeight.toDouble() / animation.frameHeight
            val duckWidth = (animation.frameWidth * scale).toInt()
            val duckHeight = (animation.frameHeight * scale).toInt()
            val duckX = (this.width - duckWidth) / 2
            val duckY = (this.height - duckHeight) / 2
            animation.paintFrame(g2, frame, duckX, duckY, duckWidth, duckHeight)
            if (System.currentTimeMillis() < stateLabelVisibleUntil) {
                drawStateLabel(g2, duckY)
            }
        } finally {
            g2.dispose()
        }
    }

    /** Draws the current state name in a small pill centered above the duck. */
    private fun drawStateLabel(g: Graphics2D, duckTop: Int) {
        val text = DuckService.getInstance().state.value.displayName
        val font = g.font //.deriveFont(Font.BOLD)
        val fm = g.getFontMetrics(font)
        val padX = 10
        val padY = 5
        val pillWidth = fm.stringWidth(text) + padX * 2
        val pillHeight = fm.height + padY * 2
        val x = (this.width - pillWidth) / 2
        val y = duckTop - pillHeight - 8

        g.font = font
        g.color = Color(0, 0, 0, 150)
        g.fillRoundRect(x, y, pillWidth, pillHeight, pillHeight, pillHeight)
        g.color = Color.WHITE
        g.drawString(text, x + padX, y + padY + fm.ascent)
    }
}