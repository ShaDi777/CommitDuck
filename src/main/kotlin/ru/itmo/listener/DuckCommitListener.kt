package ru.itmo.listener

import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import ru.itmo.pet.Event
import ru.itmo.service.DuckService

class DuckCommitListener : CheckinHandlerFactory() {
    override fun createHandler(
        panel: CheckinProjectPanel,
        commitContext: CommitContext
    ): CheckinHandler {
        return object : CheckinHandler() {
            override fun checkinSuccessful() {
                DuckService.getInstance().handle(Event.Commit)
            }
        }
    }
}