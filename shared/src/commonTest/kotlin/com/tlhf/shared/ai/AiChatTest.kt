package com.tlhf.shared.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Shared across the ai test files in this package.
internal class FakeStorage : KeyValueStorage {
    val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun remove(key: String) { map.remove(key) }
}

class AiChatTest {

    @Test
    fun newSessionStartsEmptyWithUntitled() {
        val store = AiChatStore(FakeStorage())
        val s = store.newSession()
        assertTrue(s.id.startsWith("s"))
        assertNull(s.title)
        assertTrue(s.messages.isEmpty())
        assertEquals(1, store.load().size)
    }

    @Test
    fun firstUserMessageSetsTitleTruncated() {
        val store = AiChatStore(FakeStorage())
        val s = store.newSession()
        val updated = store.addMessage(s.id, "user", "What is a covered call and when should I use one instead of just holding stock?")
        // 40 chars + ellipsis
        assertEquals("What is a covered call and when should I…", updated!!.title)
        assertEquals(1, updated.messages.size)
        assertEquals("user", updated.messages[0].role)
    }

    @Test
    fun messagesTrimmedToFortyNewestKept() {
        val store = AiChatStore(FakeStorage())
        val s = store.newSession()
        repeat(50) { store.addMessage(s.id, "user", "q$it") }
        val got = store.get(s.id)!!
        assertEquals(AiChatStore.MAX_MESSAGES, got.messages.size)
        assertEquals("q49", got.messages.last().text)
        assertEquals("q10", got.messages.first().text)
    }

    @Test
    fun sessionsCappedAtTenOldestEvicted() {
        val store = AiChatStore(FakeStorage())
        val first = store.newSession()
        repeat(10) { store.newSession() }
        val all = store.load()
        assertEquals(AiChatStore.MAX_SESSIONS, all.size)
        assertNull(all.firstOrNull { it.id == first.id })
    }

    @Test
    fun deleteRemovesSession() {
        val store = AiChatStore(FakeStorage())
        val s = store.newSession()
        store.delete(s.id)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun corruptJsonLoadsEmpty() {
        val storage = FakeStorage()
        storage.put(AiChatStore.KEY, "not json {{{")
        assertTrue(AiChatStore(storage).load().isEmpty())
    }

    @Test
    fun unknownSessionReturnsNull() {
        val store = AiChatStore(FakeStorage())
        assertNull(store.addMessage("nope", "user", "hi"))
        assertNull(store.get("nope"))
    }
}
