package ru.itmo.debugduck

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The LLM API key. It is kept in the IDE password safe (the OS keychain by default), never in settings files or in
 * the source code. For development, the `COMMIT_DUCK_LLM_API_KEY` environment variable is used if no key is saved.
 *
 * The password safe is blocking and must not be used on the EDT, so the key is loaded once in the background and
 * cached; the settings page shows the cached value.
 */
@Service(Service.Level.APP)
class DebugDuckCredentials(private val scope: CoroutineScope) {
    private val attributes = CredentialAttributes(generateServiceName("Commit Duck", "LLM API key"))
    private val lock = Any()
    private val writeMutex = Mutex()
    private var key: String? = null
    private var changedByUser = false

    private val loading = scope.launch(Dispatchers.IO) {
        val stored = PasswordSafe.instance.getPassword(attributes)
        synchronized(lock) {
            if (!changedByUser) key = stored
        }
    }

    /** The saved key, or `null` if it isn't loaded yet or there is none. Safe to call on the EDT. */
    val cachedApiKey: String?
        get() = synchronized(lock) { key }

    /** The key to send to the LLM API: the saved one or the environment variable; `null` if there is none. */
    suspend fun apiKey(): String? {
        loading.join()
        return cachedApiKey?.takeIf { it.isNotBlank() } ?: System.getenv(ENV_VARIABLE)?.takeIf { it.isNotBlank() }
    }

    /** Runs [action] on the EDT once the saved key is loaded, also while a modal dialog such as Settings is open. */
    fun whenLoaded(action: (String?) -> Unit) {
        scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            loading.join()
            action(cachedApiKey)
        }
    }

    /** Saves [newKey] (blank removes the key). Returns immediately, the password safe is updated in the background. */
    fun saveApiKey(newKey: String) {
        val normalized = newKey.trim().ifEmpty { null }
        synchronized(lock) {
            if (key == normalized) return
            key = normalized
            changedByUser = true
        }
        scope.launch(Dispatchers.IO) {
            loading.join()
            // always writes the latest key, so the order of concurrent saves doesn't matter
            writeMutex.withLock { PasswordSafe.instance.setPassword(attributes, cachedApiKey) }
        }
    }

    companion object {
        const val ENV_VARIABLE = "COMMIT_DUCK_LLM_API_KEY"

        fun getInstance(): DebugDuckCredentials = service()
    }
}
