package com.commitduck.pet

/**
 * The shared event contract between the IDE/UI layer and the pet logic.
 *
 * New events can be added without changing the state machine's `handle` logic
 * beyond the `when` mapping, keeping the components loosely coupled.
 */
sealed interface PetEvent {

    /** The user performed some activity; [typedChars] is the number of characters typed. */
    data class Activity(val typedChars: Int = 0) : PetEvent

    /** The user went idle for the first threshold; the duck gets bored. */
    data object IdleStarted : PetEvent

    /** The user stayed idle past the "bored" threshold; the duck grows sad. */
    data object IdleExtended : PetEvent

    /** The user stayed idle long enough to fall asleep. */
    data object Sleep : PetEvent

    /** The user came back after idle; the duck wakes up. */
    data object WakeUp : PetEvent

    /** The user clicked / interacted with the duck. */
    data object DuckClicked : PetEvent

    /** A commit was created or a task finished. */
    data object Commit : PetEvent

    /** An activity milestone (e.g. 1 000 / 10 000 characters) was reached. */
    data class ThresholdReached(val chars: Long) : PetEvent
}