package ru.itmo.listener

import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import ru.itmo.pet.Event
import ru.itmo.service.DuckService

/**
 * Reports a successful project build to [DuckService].
 * Registered in plugin.xml as a project listener.
 */
class DuckBuildListener : CompilationStatusListener {
    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        if (!aborted && errors == 0) {
            DuckService.getInstance().handle(Event.BuildSucceeded)
        }
    }
}