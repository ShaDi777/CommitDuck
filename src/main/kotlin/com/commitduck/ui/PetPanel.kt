package com.commitduck.ui

import com.commitduck.persistence.PetPersistence
import com.commitduck.pet.PetEvent
import com.commitduck.pet.PetModel
import com.commitduck.pet.PetState
import com.commitduck.phrases.MotivationPhrases
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * The main duck UI. Shows the image for the current pet state, a status line,
 * and a message bubble. Clicking the duck triggers an interaction.
 */
class PetPanel(
    private val model: PetModel,
    private val persistence: PetPersistence,
) : JPanel(BorderLayout()) {

    private val phrases = MotivationPhrases()

    private val imageLabel = JBLabel(PetIcons.iconFor(model.state), SwingConstants.CENTER)
    private val statusLabel = JBLabel("", SwingConstants.CENTER)
    private val messageLabel = JBLabel("", SwingConstants.CENTER)

    init {
        border = JBUI.Borders.empty(16, 12, 12, 12)

        statusLabel.font = JBFont.label().asBold()
        statusLabel.foreground = JBColor.namedColor("Label.foreground", JBColor.GRAY)

        messageLabel.font = JBFont.label().deriveFont(JBFont.label().size - 2f)
        messageLabel.foreground = JBColor.namedColor("Label.infoForeground", JBColor.GRAY)
        messageLabel.isOpaque = true
        messageLabel.background = JBColor.namedColor("Panel.infoBackground", JBColor.WHITE)
        messageLabel.border = JBUI.Borders.empty(8, 10)

        imageLabel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        imageLabel.toolTipText = "Click me for a pep talk!"
        imageLabel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                model.handle(PetEvent.DuckClicked)
                showMessage(phrases.random())
            }
        })

        add(imageLabel, BorderLayout.CENTER)
        add(statusLabel, BorderLayout.NORTH)
        add(messageLabel, BorderLayout.SOUTH)

        // Subscribe to state changes after the initial render.
        model.addListener(::onPetStateChanged)
        render(model.state)
    }

    private fun onPetStateChanged(state: PetState) {
        render(state)
        if (state == PetState.HAPPY || state == PetState.SLEEPING) {
            showMessage(phrases.forState(state))
        }
    }

    private fun render(state: PetState) {
        imageLabel.icon = PetIcons.iconFor(state)
        statusLabel.text = "Duck is ${state.displayName.lowercase()}"
        persistence.setPetState(state)
    }

    private fun showMessage(text: String) {
        messageLabel.text = text
    }
}