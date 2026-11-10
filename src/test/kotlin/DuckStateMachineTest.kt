package ru.itmo

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DuckStateMachineTest {
    private val timeouts = DuckTimeouts(idleAfter = 30.seconds, sleepAfter = 10.minutes, deletedFor = 5.seconds)
    private val machine = DuckStateMachine(startMillis = 0)

    private fun typeAt(seconds: Long) = machine.handle(PetEvent.Typing, seconds * 1000, timeouts)
    private fun deleteAt(seconds: Long) = machine.handle(PetEvent.Deleted, seconds * 1000, timeouts)
    private fun tickAt(seconds: Long) = machine.handle(PetEvent.Tick, seconds * 1000, timeouts)

    @Test
    fun `starts idle`() {
        assertEquals(PetState.IDLE, machine.state)
        assertEquals(PetState.IDLE, tickAt(5))
    }

    @Test
    fun `typing makes the duck work`() {
        assertEquals(PetState.WORKING, typeAt(1))
    }

    @Test
    fun `duck stays working while the user keeps typing`() {
        typeAt(0)
        typeAt(20)
        assertEquals(PetState.WORKING, tickAt(45))
    }

    @Test
    fun `duck returns to idle after the idle timeout`() {
        typeAt(0)
        assertEquals(PetState.WORKING, tickAt(29))
        assertEquals(PetState.IDLE, tickAt(30))
    }

    @Test
    fun `duck falls asleep after the sleep timeout and wakes up on typing`() {
        typeAt(0)
        assertEquals(PetState.IDLE, tickAt(599))
        assertEquals(PetState.SLEEPING, tickAt(600))
        assertEquals(PetState.SLEEPING, tickAt(700))
        assertEquals(PetState.WORKING, typeAt(701))
    }

    @Test
    fun `duck falls asleep even if the user never typed`() {
        assertEquals(PetState.SLEEPING, tickAt(600))
    }

    @Test
    fun `deleting files shows the deleted reaction and then returns to the previous state`() {
        assertEquals(PetState.DELETED, deleteAt(1))
        assertEquals(PetState.DELETED, tickAt(5))
        assertEquals(PetState.IDLE, tickAt(6))
    }

    @Test
    fun `deleted reaction returns to working while the user keeps typing`() {
        typeAt(0)
        deleteAt(1)
        assertEquals(PetState.DELETED, typeAt(2))
        assertEquals(PetState.WORKING, tickAt(6))
    }

    @Test
    fun `deleting again extends the deleted reaction`() {
        deleteAt(0)
        deleteAt(4)
        assertEquals(PetState.DELETED, tickAt(8))
        assertEquals(PetState.IDLE, tickAt(9))
    }

    @Test
    fun `deleting files wakes the duck up`() {
        assertEquals(PetState.SLEEPING, tickAt(600))
        assertEquals(PetState.DELETED, deleteAt(601))
        assertEquals(PetState.IDLE, tickAt(606))
    }

    @Test
    fun `deleting files counts as activity`() {
        typeAt(0)
        deleteAt(20)
        assertEquals(PetState.WORKING, tickAt(45))
        assertEquals(PetState.IDLE, tickAt(50))
    }
}
