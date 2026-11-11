package ru.itmo.pet

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.itmo.pet.states.GreetingState

class PetModelTest {
    private val model = PetModel(GreetingState())

    @Test
    fun `starts idle`() {
        assertEquals(PetStateType.GREETING, model.state.type)
    }

    @Test
    fun `activity makes the duck work`() {
        model.handle(Event.Activity())
        assertEquals(PetStateType.WORKING, model.state.type)
    }

    @Test
    fun `duck stays working while the user keeps typing`() {
        model.handle(Event.Activity())
        model.handle(Event.Activity())
        assertEquals(PetStateType.WORKING, model.state.type)
    }

    @Test
    fun `idle started after a pause returns to idle`() {
        model.handle(Event.Activity())
        model.handle(Event.IdleStarted)
        assertEquals(PetStateType.IDLE, model.state.type)
    }

    @Test
    fun `duck falls asleep on the sleep signal and wakes up on activity`() {
        model.handle(Event.Sleep)
        assertEquals(PetStateType.SLEEPING, model.state.type)
        model.handle(Event.Activity())
        assertEquals(PetStateType.WORKING, model.state.type)
    }

    @Test
    fun `deleting files shows the deleted reaction then returns to the previous state`() {
        model.handle(Event.Deleted)
        assertEquals(PetStateType.DELETED, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, model.state.type)
    }

    @Test
    fun `deleted reaction returns to working if the user kept typing`() {
        model.handle(Event.Activity())
        model.handle(Event.Deleted)
        assertEquals(PetStateType.DELETED, model.state.type)
        model.handle(Event.Activity())
        assertEquals(PetStateType.DELETED, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.WORKING, model.state.type)
    }

    @Test
    fun `deleting while sleeping wakes the duck up`() {
        model.handle(Event.Sleep)
        model.handle(Event.Deleted)
        assertEquals(PetStateType.DELETED, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, model.state.type)
    }

    @Test
    fun `successful build makes the duck happy`() {
        model.handle(Event.BuildSucceeded)
        assertEquals(PetStateType.HAPPY, model.state.type)
    }

    @Test
    fun `passing tests make the duck happy`() {
        model.handle(Event.TestsPassed)
        assertEquals(PetStateType.HAPPY, model.state.type)
    }

    @Test
    fun `happy reaction returns to the previous state on wake up`() {
        model.handle(Event.BuildSucceeded)
        assertEquals(PetStateType.HAPPY, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, model.state.type)
    }

    @Test
    fun `typing during the happy reaction keeps it happy and returns to working after`() {
        model.handle(Event.TestsPassed)
        model.handle(Event.Activity())
        assertEquals(PetStateType.HAPPY, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.WORKING, model.state.type)
    }

    @Test
    fun `a successful build wakes a sleeping duck`() {
        model.handle(Event.Sleep)
        model.handle(Event.BuildSucceeded)
        assertEquals(PetStateType.HAPPY, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, model.state.type)
    }
}