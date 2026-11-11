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
    fun currentProblems(editor: Editor?): List<IdeProblem> {
        val inEditor = editor?.let {
            val selection = it.selectionModel
            if (selection.hasSelection()) editorProblems(it, selection.selectionStart, selection.selectionEnd)
            else editorProblems(it, 0, it.document.textLength)
        }
        return ((inEditor ?: emptyList()) + buildProblems + testProblems).distinct().take(IdeProblem.MAX_PER_MESSAGE)
    }

    /**
     * What "Ask Debug Duck" sends: the selection, or a few lines around the caret if nothing is selected, with the
     * errors highlighted there.
     */
    @Suppress("DEPRECATION")
    fun editorContext(editor: Editor): PendingContext = runReadAction {
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document)
        val selection = editor.selectionModel
        val (start, end) = if (selection.hasSelection()) {
            selection.selectionStart to selection.selectionEnd
        }
        else {
            val caretLine = document.getLineNumber(editor.caretModel.offset)
            val first = (caretLine - CONTEXT_LINES).coerceAtLeast(0)
            val last = (caretLine + CONTEXT_LINES).coerceAtMost((document.lineCount - 1).coerceAtLeast(0))
            document.getLineStartOffset(first) to document.getLineEndOffset(last)
        }
        val code = document.charsSequence.subSequence(start, end).toString()
        val attachment = CodeAttachment.of(
            fileName = file?.name ?: "untitled",
            language = file?.fileType?.name,
            selectedText = code,
            startLine = document.getLineNumber(start) + 1,
        )
        PendingContext(attachment, editorProblems(editor, start, end))
    }

    /**
     * Errors highlighted in [editor] between [start] and [end], the ones nearest to the caret first.
     * `runReadAction` is deprecated in the newest platform versions in favour of `runReadActionBlocking`, which older
     * versions don't have; this is a short read on the EDT either way.
     */
    @Suppress("DEPRECATION")
    fun editorProblems(editor: Editor, start: Int, end: Int): List<IdeProblem> = runReadAction {
        val document = editor.document
        val fileName = FileDocumentManager.getInstance().getFile(document)?.name ?: "untitled"
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
        /** Lines above and below the caret sent by "Ask Debug Duck" when nothing is selected. */
        private const val CONTEXT_LINES = 3

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
