package ru.itmo.debugduck

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * The project's Debug Duck conversation, kept between IDE restarts in the workspace file (`.idea/workspace.xml`,
 * which is not committed), as one JSON string made by [ConversationCodec].
 */
@Service(Service.Level.PROJECT)
@State(name = "DebugDuckHistory", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class DebugDuckHistory : SimplePersistentStateComponent<DebugDuckHistory.Stored>(Stored()) {
    class Stored : BaseState() {
        var conversation by string()
    }

    fun load(): StoredConversation? = ConversationCodec.decode(state.conversation)

    fun save(conversation: StoredConversation) {
        state.conversation = ConversationCodec.encode(conversation)
    }

    companion object {
        fun getInstance(project: Project): DebugDuckHistory = project.service()
    }
}
