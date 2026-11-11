package ru.itmo.listener

import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestStatusListener
import ru.itmo.pet.Event
import ru.itmo.service.DuckService

/**
 * Reports a test run in which every test passed to [DuckService].
 * Registered in plugin.xml via the `com.intellij.testStatusListener` extension point.
 */
class DuckTestListener : TestStatusListener() {
    override fun testSuiteFinished(root: AbstractTestProxy?) {
        if (root != null && allTestsPassed(root)) {
            DuckService.getInstance().handle(Event.TestsPassed)
        }
    }

    /** A run "passed" when it contained at least one leaf test and none of them failed, errored, or was interrupted. */
    private fun allTestsPassed(root: AbstractTestProxy): Boolean {
        var leafCount = 0
        var failed = false
        fun walk(node: AbstractTestProxy) {
            if (node.isLeaf) {
                leafCount++
                if (node.isDefect || node.isInterrupted) failed = true
            } else {
                node.children.forEach { walk(it) }
            }
        }
        walk(root)
        return leafCount > 0 && !failed
    }
}