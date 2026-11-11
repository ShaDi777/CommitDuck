package ru.itmo.statistics

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
@State(
    name = "CommitDuckStatistics",
    storages = [Storage("commitDuckStatistics.xml")]
)
class DuckStatistics :
    SimplePersistentStateComponent<DuckStatistics.StatisticsState>(StatisticsState()) {

    class StatisticsState : BaseState() {
        var typedChars by property(0)
        var commits by property(0)
        var successfulBuilds by property(0)
        var passedTests by property(0)
    }

    private val mutableTypedChars = MutableStateFlow(state.typedChars)
    val typedChars: StateFlow<Int> = mutableTypedChars.asStateFlow()

    fun addTypedChars(count: Int) {
        if (count <= 0) return

        state.typedChars += count
        mutableTypedChars.value = state.typedChars
    }

    fun addCommit() {
        state.commits++
    }

    fun addSuccessfulBuild() {
        state.successfulBuilds++
    }

    fun addPassedTests() {
        state.passedTests++
    }

    companion object {
        fun getInstance(): DuckStatistics = service()
    }
}