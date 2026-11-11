package ru.itmo.pet

/**
 * The discrete states a duck can be in.
 *
 * Designed to be easily extended: to add a state, add an enum constant (and a matching
 * sprite) without touching the rest of the pet logic.
 */
enum class PetStateType(val displayName: String) {
    IDLE("Idle"),
    WORKING("Working"),
    SLEEPING("Sleeping"),
    DELETED("Deleted"),
    HAPPY("Happy"),
    SAD("Sad"),
    ;

    /** Sprite name in `/duck/<size>/`, e.g. `idle` for [IDLE]. */
    val animationName: String
        get() = name.lowercase()
}