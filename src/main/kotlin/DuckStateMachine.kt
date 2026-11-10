package ru.itmo

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class PetState {
    IDLE,
    WORKING,
    SLEEPING,
    DELETED;

    /** Sprite name in `/duck/<size>/`, e.g. `idle` for [IDLE]. */
    val animationName: String
        get() = name.lowercase()
}

sealed interface PetEvent {
    /** The user typed or edited code in an editor. */
    data object Typing : PetEvent

    /** The user deleted files or directories in the IDE. */
    data object Deleted : PetEvent

    /** Periodic clock tick that drives time-based transitions. */
    data object Tick : PetEvent
}

data class DuckTimeouts(
    /** No activity for this long: [PetState.WORKING] -> [PetState.IDLE]. */
    val idleAfter: Duration,
    /** No activity for this long: any state -> [PetState.SLEEPING]. */
    val sleepAfter: Duration,
    /** How long the duck shows [PetState.DELETED] after the user deletes files. */
    val deletedFor: Duration,
)

/** Pure state machine without IDE dependencies, so it can be unit-tested with a fake clock. */
class DuckStateMachine(startMillis: Long) {
    var state: PetState = PetState.IDLE
        private set

    /** The state without short reactions such as [PetState.DELETED] that are shown on top of it. */
    private var baseState = PetState.IDLE
    private var lastActivityMillis = startMillis
    private var deletedUntilMillis = Long.MIN_VALUE

    fun handle(event: PetEvent, nowMillis: Long, timeouts: DuckTimeouts): PetState {
        when (event) {
            PetEvent.Typing -> {
                lastActivityMillis = nowMillis
                baseState = PetState.WORKING
            }
            PetEvent.Deleted -> {
                // deleting files is activity too, so a sleeping duck wakes up
                lastActivityMillis = nowMillis
                if (baseState == PetState.SLEEPING) baseState = PetState.IDLE
                deletedUntilMillis = nowMillis + timeouts.deletedFor.inWholeMilliseconds
            }
            PetEvent.Tick -> {
                val quietFor = (nowMillis - lastActivityMillis).milliseconds
                baseState = when {
                    quietFor >= timeouts.sleepAfter -> PetState.SLEEPING
                    baseState == PetState.WORKING && quietFor >= timeouts.idleAfter -> PetState.IDLE
                    else -> baseState
                }
            }
        }
        state = if (nowMillis < deletedUntilMillis) PetState.DELETED else baseState
        return state
    }
}
