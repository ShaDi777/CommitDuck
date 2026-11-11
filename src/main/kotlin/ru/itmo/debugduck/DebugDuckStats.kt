package ru.itmo.debugduck

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/** How many problems the user solved with the Debug Duck, over all projects; kept between IDE restarts. */
@Service(Service.Level.APP)
@State(name = "CommitDuckDebugStats", storages = [Storage("commitDuck.xml")])
class DebugDuckStats : SimplePersistentStateComponent<DebugDuckStats.Counters>(Counters()) {
    class Counters : BaseState() {
        var solvedProblems by property(0)
    }

    val solvedProblems: Int
        get() = state.solvedProblems

    fun problemSolved() {
        state.solvedProblems++
    }

    companion object {
        fun getInstance(): DebugDuckStats = service()
    }
}
