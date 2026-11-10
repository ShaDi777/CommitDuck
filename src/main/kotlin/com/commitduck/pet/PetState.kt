package com.commitduck.pet

/**
 * The discrete states a duck can be in.
 *
 * Designed to be easily extended: to add a new state, add an enum constant
 * (and optionally a corresponding image + phrase pool) without touching the
 * rest of the pet logic.
 */
enum class PetState(val displayName: String) {
    IDLE("Idle"),
    WORKING("Working"),
    HAPPY("Happy"),
    SAD("Sad"),
    SLEEPING("Sleeping"),
}