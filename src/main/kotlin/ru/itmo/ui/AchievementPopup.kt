package ru.itmo.ui

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import ru.itmo.achievement.AchievementType
import ru.itmo.config.MyMessageBundle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer

class AchievementPopup(
    private val achievement: AchievementType
) {
    private var animationTimer: Timer? = null
    private var dismissTimer: Timer? = null
    private var popup: JBPopup? = null

    fun show() {
        val frame = WindowManager.getInstance()
            .mostRecentFocusedWindow ?: return

        val content = createContent()

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, null)
            .setRequestFocus(false)
            .setFocusable(false)
            .setResizable(false)
            .setMovable(false)
            .setShowShadow(true)
            .setCancelOnClickOutside(false)
            .createPopup()

        popup?.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                animationTimer?.stop()
                dismissTimer?.stop()

                animationTimer = null
                dismissTimer = null
                popup = null
            }
        })

        val margin = JBUI.scale(16)
        val popupSize = content.preferredSize

        val x = (frame.width - popupSize.width - margin)
            .coerceAtLeast(margin)

        popup?.show(RelativePoint(frame, Point(x, margin)))

        dismissTimer = Timer(5000) {
            popup?.cancel()
        }.apply {
            isRepeats = false
            start()
        }
    }

    private fun close() {
        popup?.cancel()
    }

    private fun createContent(): JPanel {
        val notificationBackground =
            JBUI.CurrentTheme.Notification.BACKGROUND

        val notificationForeground =
            JBUI.CurrentTheme.Notification.FOREGROUND

        val borderColor = JBColor.namedColor(
            "Notification.borderColor",
            JBColor.border()
        )

        val accentColor = JBUI.CurrentTheme.IconBadge.SUCCESS

        val horizontalPadding = JBUI.scale(16)
        val verticalPadding = JBUI.scale(14)
        val closeSize = JBUI.scale(24)

        val panel = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = notificationBackground

            border = BorderFactory.createCompoundBorder(
                JBUI.Borders.customLine(borderColor, 1),
                JBUI.Borders.empty(
                    verticalPadding,
                    horizontalPadding,
                    verticalPadding,
                    horizontalPadding
                )
            )
        }

        // Header
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
        }

        val heading = JLabel(MyMessageBundle.message("achievement.new_achievement_title")).apply {
            font = JBFont.label().deriveFont(
                Font.BOLD,
                JBUI.scale(10).toFloat()
            )
            foreground = accentColor
        }

        val closeButton = JButton("×").apply {
            font = JBFont.label().deriveFont(18f)
            foreground = notificationForeground

            isBorderPainted = false
            isContentAreaFilled = false
            isFocusPainted = false
            isOpaque = false
            isRolloverEnabled = true

            border = JBUI.Borders.empty(0)
            margin = JBUI.emptyInsets()
            toolTipText = "Закрыть"

            cursor = Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR
            )

            addActionListener { close() }
        }

        val closeContainer = JPanel(BorderLayout()).apply {
            isOpaque = false
            preferredSize = JBUI.size(closeSize, closeSize)
            minimumSize = preferredSize
            maximumSize = preferredSize

            add(closeButton, BorderLayout.CENTER)
        }

        header.add(heading, BorderLayout.CENTER)
        header.add(closeContainer, BorderLayout.EAST)

        panel.add(header, BorderLayout.NORTH)

        // Main content
        val body = JPanel(
            BorderLayout(JBUI.scale(14), 0)
        ).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(JBUI.scale(12))
        }

        // Animation remains unchanged
        body.add(AnimatedDuckPanel(), BorderLayout.WEST)

        // Text
        val textPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        val titleLabel = JLabel(
            "<html><div style='width: 220px;'>${escapeHtml(achievement.title)}</div></html>"
        ).apply {
            font = JBFont.label().deriveFont(
                Font.BOLD,
                14f
            )
            foreground = notificationForeground
            alignmentX = Component.LEFT_ALIGNMENT
        }

        val descriptionLabel = JLabel(
            "<html><div style='width: 220px;'>${
                escapeHtml(achievement.description).replace("\n", "<br>")
            }</div></html>"
        ).apply {
            font = JBFont.label().deriveFont(12f)
            foreground = notificationForeground
            alignmentX = Component.LEFT_ALIGNMENT
        }

        textPanel.add(titleLabel)
        textPanel.add(Box.createVerticalStrut(JBUI.scale(7)))
        textPanel.add(descriptionLabel)

        body.add(textPanel, BorderLayout.CENTER)

        panel.add(body, BorderLayout.CENTER)

        // Calculate the actual preferred size.
        val calculatedSize = panel.preferredSize

        panel.preferredSize = Dimension(
            JBUI.scale(390),
            calculatedSize.height
        )

        return panel
    }

    private fun escapeHtml(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private inner class AnimatedDuckPanel : JPanel() {
        private val animation = DuckAnimation.load(achievement.animation, "popup")
        private var currentFrame = 0

        init {
            isOpaque = false
            preferredSize = JBUI.size(72, 72)

            animationTimer = Timer(1000 / animation.fps) {
                currentFrame = (currentFrame + 1) % animation.frameCount
                repaint()
            }.apply {
                start()
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val g2 = g.create() as Graphics2D

            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
                )

                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )

                val scale = minOf(
                    width.toFloat() / animation.frameWidth,
                    height.toFloat() / animation.frameHeight
                )

                val frameWidth =
                    (animation.frameWidth * scale).toInt()

                val frameHeight =
                    (animation.frameHeight * scale).toInt()

                val x = (width - frameWidth) / 2
                val y = (height - frameHeight) / 2

                animation.paintFrame(
                    g2,
                    currentFrame,
                    x,
                    y,
                    frameWidth,
                    frameHeight
                )
            } finally {
                g2.dispose()
            }
        }
    }
}
