package ru.itmo.pet

/**
 * Signals emitted by widgets/listeners (typing, deleting files, clicking the duck, ...)
 * and by the controller's clock (idle/sleep/wake-up). The pet reacts to them and changes
 * state.
 *
 * New signals can be added without touching the states' `handle` logic beyond the `when`
 * mapping, keeping widgets and the pet loosely coupled.
 */
sealed interface Event {

    data object FirstLaunch : Event

    /** The user performed some activity; [typedChars] is the number of characters typed. */
    data class Activity(val typedChars: Int = 0) : Event

    /** The user went idle for the first threshold; the duck gets bored. */
    data object IdleStarted : Event

    /** The user stayed idle past the "bored" threshold; the duck grows sad. */
    data object IdleExtended : Event

    /** The user stayed idle long enough to fall asleep. */
    data object Sleep : Event

    /** The user came back after idle, or a short reaction such as [Deleted] is over. */
    data object WakeUp : Event

    /** The user clicked / interacted with the duck. */
    data object DuckClicked : Event

    /** A commit was created or a task finished. */
    data object Commit : Event

    /** A project build finished without errors. */
    data object BuildSucceeded : Event

    /** A test run finished with all tests passing. */
    data object TestsPassed : Event

    /** An activity milestone (e.g. 1 000 / 10 000 characters) was reached. */
    data class ThresholdReached(val chars: Long) : Event

    /** The user deleted files or directories in the IDE. */
    data object Deleted : Event
}