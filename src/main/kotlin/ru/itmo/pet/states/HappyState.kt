package ru.itmo.pet.states

/**
 * Not implemented yet: the duck needs a `happy` sprite before this state can be used.
 * Transferred from pattern_state, kept commented out so the plugin still runs.
 *
 * class HappyState : PetBaseState() {
 *     override val displayName = "Happy"
 *     override val type = PetStateType.HAPPY
 *
 *     override fun handleSpecific(event: Event): PetState = when (event) {
 *         Event.IdleStarted -> IdleState()
 *         else -> this
 *     }
 * }
 */