package com.commitduck.phrases

import com.commitduck.pet.PetState
import kotlin.random.Random

/**
 * A prepared pool of motivational messages for interacting with the duck.
 * Provides random picks from a general set and from a per-state set.
 */
class MotivationPhrases(private val random: Random = Random.Default) {

    private val general = listOf(
        "You're doing great — keep going!",
        "One commit at a time. You've got this.",
        "Even ducks take small steps. Well done!",
        "Quack! That looks like solid progress.",
        "Rest is part of the plan too. Proud of you.",
        "Another line closer to done. Nice work!",
        "Remember: half of shipping is showing up. You did.",
    )

    private val byState = mapOf(
        PetState.WORKING to listOf(
            "Look at you go — deep in flow!",
            "Type on, champion!",
        ),
        PetState.HAPPY to listOf(
            "Yay! That deserves a happy quack!",
            "Commit! I'm so proud of you!",
        ),
        PetState.SAD to listOf(
            "Hey, take a breath. I'm right here.",
            "Even the best days have slow parts. Coffee break?",
        ),
        PetState.SLEEPING to listOf(
            "Zzz... I'll keep watch while you're away.",
        ),
        PetState.IDLE to listOf(
            "Whenever you're ready, I'm here.",
        ),
    )

    /** Returns a random message from the general pool. */
    fun random(): String = general.random(random)

    /** Returns a random message suitable for the given state. */
    fun forState(state: PetState): String {
        val pool = byState[state]
        return if (pool.isNullOrEmpty()) random() else pool.random(random)
    }
}