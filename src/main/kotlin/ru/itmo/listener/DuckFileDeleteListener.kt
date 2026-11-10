package ru.itmo.listener

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import ru.itmo.pet.Event
import ru.itmo.service.DuckService

/**
 * Reports files and directories deleted from the IDE to [DuckService].
 * Registered in plugin.xml as an application listener.
 */
class DuckFileDeleteListener : BulkFileListener {
    // `before`, not `after`: deleted files are already invalid in `after` and can't be looked up in the file index
    override fun before(events: List<VFileEvent>) {
        val projects = ProjectManager.getInstance().openProjects
        val deletedInProject = events.any { event ->
            // refresh events are external changes (git checkout, rm in a terminal, ...), not the user deleting files in the IDE
            event is VFileDeleteEvent && !event.isFromRefresh &&
                projects.any { !it.isDisposed && ProjectFileIndex.getInstance(it).isInContent(event.file) }
        }
        if (deletedInProject) {
            DuckService.getInstance().handle(Event.Deleted)
        }
    }
}
