package ru.itmo.pet

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.itmo.pet.states.IdleState

class PetModelTest {
    // the behaviour below starts from an idle duck; the greeting has its own tests
    private val model = PetModel(IdleState())

    @Test
    fun `starts idle`() {
        assertEquals(PetStateType.IDLE, model.state.type)
    }

    @Test
    fun `a new duck greets the user`() {
        assertEquals(PetStateType.GREETING, PetModel().state.type)
    }

    @Test
    fun `the greeting is over on wake up`() {
        val greeting = PetModel()
        greeting.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, greeting.state.type)
    }

    @Test
    fun `typing during the greeting puts the duck to work`() {
        val greeting = PetModel()
        greeting.handle(Event.Activity())
        assertEquals(PetStateType.WORKING, greeting.state.type)
    }

    @Test
    fun `a duck that keeps greeting can still fall asleep`() {
        val greeting = PetModel()
        greeting.handle(Event.Sleep)
        assertEquals(PetStateType.SLEEPING, greeting.state.type)
    }

    @Test
    fun `the first launch welcome is over on wake up`() {
        val firstLaunch = PetModel()
        firstLaunch.handle(Event.FirstLaunch)
        assertEquals(PetStateType.FIRST_LAUNCH, firstLaunch.state.type)
        firstLaunch.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, firstLaunch.state.type)
    }

    @Test
    fun `a reaction during the greeting returns to the greeting, which then ends`() {
        val greeting = PetModel()
        greeting.handle(Event.Deleted)
        greeting.handle(Event.WakeUp)
        assertEquals(PetStateType.GREETING, greeting.state.type)
        greeting.handle(Event.WakeUp)
        assertEquals(PetStateType.IDLE, greeting.state.type)
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
    fun `a problem solved with the debug duck makes the duck happy`() {
        model.handle(Event.Activity())
        model.handle(Event.ProblemSolved)
        assertEquals(PetStateType.HAPPY, model.state.type)
        model.handle(Event.WakeUp)
        assertEquals(PetStateType.WORKING, model.state.type)
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