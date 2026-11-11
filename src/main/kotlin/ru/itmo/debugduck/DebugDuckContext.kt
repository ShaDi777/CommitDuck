package ru.itmo.debugduck

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestStatusListener
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import kotlin.math.abs

/**
 * What the IDE already knows about the user's problem: errors highlighted in the editor, errors of the last build and
 * the failed tests of the last test run. Sent along with Debug Duck messages, so the user doesn't have to retype them.
 */
@Service(Service.Level.PROJECT)
class DebugDuckContext(private val project: Project) {
    @Volatile
    var buildProblems: List<IdeProblem> = emptyList()
        private set

    @Volatile
    var testProblems: List<IdeProblem> = emptyList()
        private set

    /** Called by [DebugDuckBuildListener]; a successful build clears the errors. */
    fun buildFinished(problems: List<IdeProblem>) {
        buildProblems = problems
    }

    /** Called by [DebugDuckTestListener]; a run without failures clears them. */
    fun testsFinished(problems: List<IdeProblem>) {
        testProblems = problems
    }

    /** Problems in [editor] (if any), the last build and the last test run, at most [IdeProblem.MAX_PER_MESSAGE]. */
    fun currentProblems(editor: Editor?): List<IdeProblem> =
        ((editor?.let(::editorProblems) ?: emptyList()) + buildProblems + testProblems)
            .distinct()
            .take(IdeProblem.MAX_PER_MESSAGE)

    /**
     * Errors highlighted in [editor]: inside the selection if there is one, otherwise in the whole file, the ones
     * nearest to the caret first. `runReadAction` is deprecated in the newest platform versions in favour of
     * `runReadActionBlocking`, which older versions don't have; this is a short read on the EDT either way.
     */
    @Suppress("DEPRECATION")
    fun editorProblems(editor: Editor): List<IdeProblem> = runReadAction {
        val document = editor.document
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name ?: "untitled"
        val selection = editor.selectionModel
        val start = if (selection.hasSelection()) selection.selectionStart else 0
        val end = if (selection.hasSelection()) selection.selectionEnd else document.textLength
        val caret = editor.caretModel.offset

        val found = mutableListOf<Pair<Int, String>>()
        DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, start, end) { info ->
            info.description?.takeIf { it.isNotBlank() }?.let { found += info.startOffset to it }
            true
        }
        found.distinct()
            .sortedBy { (offset, _) -> abs(offset - caret) }
            .take(IdeProblem.MAX_PER_MESSAGE)
            .map { (offset, description) ->
                val line = document.getLineNumber(offset.coerceIn(0, document.textLength)) + 1
                IdeProblem(ProblemKind.CODE, description, "$fileName:$line")
            }
    }

    companion object {
        fun getInstance(project: Project): DebugDuckContext = project.service()
    }
}

/**
 * Remembers the errors of the last build (builds by the IntelliJ build system; Gradle-delegated builds report
 * through the Build tool window instead). Registered in plugin.xml as a project listener.
 */
class DebugDuckBuildListener(private val project: Project) : CompilationStatusListener {
    override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
        if (aborted || project.isDisposed) return
        val problems = compileContext.getMessages(CompilerMessageCategory.ERROR)
            .take(IdeProblem.MAX_PER_MESSAGE)
            .map { message ->
                val line = (message.navigatable as? OpenFileDescriptor)?.line?.takeIf { it >= 0 }
                val location = message.virtualFile?.name?.let { name -> if (line == null) name else "$name:${line + 1}" }
                IdeProblem(ProblemKind.BUILD, message.message.orEmpty().trim(), location)
            }
        DebugDuckContext.getInstance(project).buildFinished(problems)
    }
}

/** Remembers the failed tests of the last test run. Registered in plugin.xml via `com.intellij.testStatusListener`. */
class DebugDuckTestListener : TestStatusListener() {
    // the project-aware overload below does the work
    override fun testSuiteFinished(root: AbstractTestProxy?) {}

    override fun testSuiteFinished(root: AbstractTestProxy?, project: Project?) {
        if (root == null || project == null || project.isDisposed) return
        val problems = root.allTests
            .filter { it.isLeaf && it.isDefect }
            .take(MAX_FAILED_TESTS)
            .map { test ->
                IdeProblem(
                    ProblemKind.TEST,
                    message = test.errorMessage?.trim()?.takeIf { it.isNotEmpty() } ?: "failed",
                    location = test.name,
                    details = IdeProblem.details(test.stacktrace),
                )
            }
        DebugDuckContext.getInstance(project).testsFinished(problems)
    }

    private companion object {
        const val MAX_FAILED_TESTS = 3
    }
}
