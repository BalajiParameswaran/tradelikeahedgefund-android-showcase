package com.tlhf.shared.ai

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * On-device AI chat history. Mirrors the web app's `tlhf_ai_sessions_v1`
 * semantics exactly:
 * - one JSON blob: { sessions: [...] }, newest session first
 * - max 10 sessions (oldest evicted)
 * - max 40 messages per session (oldest trimmed)
 * - session id: "s" + base36 timestamp + base36 random
 * - title: first user message, truncated to 40 chars
 *
 * The platform provides the [KeyValueStorage]; Android uses
 * EncryptedSharedPreferences and iOS uses the Keychain, so no plaintext
 * chat log ever sits on disk — same guarantee as the web app's SecureStore.
 */
@Serializable
data class ChatMessage(val role: String, val text: String, val ts: Long)

@Serializable
data class ChatSession(
    val id: String,
    val title: String? = null,
    val startedAt: Long,
    val messages: List<ChatMessage> = emptyList()
)

@Serializable
private data class SessionFile(val sessions: List<ChatSession> = emptyList())

/** Minimal key-value store, implemented per platform (encrypted). */
interface KeyValueStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class AiChatStore(private val storage: KeyValueStorage) {

    companion object {
        const val KEY = "tlhf_ai_sessions_v1"
        const val MAX_SESSIONS = 10
        const val MAX_MESSAGES = 40
        const val TITLE_MAX = 40
        internal val json = Json { ignoreUnknownKeys = true }
    }

    fun load(): List<ChatSession> = try {
        val raw = storage.get(KEY) ?: return emptyList()
        json.decodeFromString(SessionFile.serializer(), raw).sessions
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(sessions: List<ChatSession>) {
        val trimmed = sessions
            .take(MAX_SESSIONS)
            .map { it.copy(messages = it.messages.takeLast(MAX_MESSAGES)) }
        storage.put(KEY, json.encodeToString(SessionFile.serializer(), SessionFile(trimmed)))
    }

    fun newSession(): ChatSession {
        val now = Clock.System.now().toEpochMilliseconds()
        val session = ChatSession(
            id = "s" + now.toString(36) + (0..999_999).random().toString(36),
            startedAt = now
        )
        save(listOf(session) + load())
        return session
    }

    fun get(sessionId: String): ChatSession? = load().firstOrNull { it.id == sessionId }

    /**
     * Appends a message. The session title is set from the first user message.
     * Returns the updated session, or null if the id is unknown.
     */
    fun addMessage(sessionId: String, role: String, text: String): ChatSession? {
        val sessions = load().toMutableList()
        val i = sessions.indexOfFirst { it.id == sessionId }
        if (i < 0) return null
        val s = sessions[i]
        val title = s.title ?: sessionTitle(text)
        val updated = s.copy(
            title = title,
            messages = (s.messages + ChatMessage(role, text, Clock.System.now().toEpochMilliseconds()))
                .takeLast(MAX_MESSAGES)
        )
        sessions[i] = updated
        // keep the touched session newest-first
        sessions.removeAt(i)
        sessions.add(0, updated)
        save(sessions)
        return updated
    }

    fun delete(sessionId: String) {
        save(load().filter { it.id != sessionId })
    }

    fun clear() {
        storage.remove(KEY)
    }

    internal fun sessionTitle(firstText: String): String {
        val t = firstText.replace(Regex("\\s+"), " ").trim()
        if (t.isEmpty()) return "New chat"
        return if (t.length > TITLE_MAX) t.take(TITLE_MAX).trimEnd() + "…" else t
    }
}
