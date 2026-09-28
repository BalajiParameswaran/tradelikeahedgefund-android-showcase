package com.tlhf.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EtradeAuthTest {

    @Test
    fun hmacSha1KnownVector() {
        // RFC 2202-style check: HMAC-SHA1("key", "The quick brown fox jumps over the lazy dog")
        val mac = hmacSha1("key".encodeToByteArray(), "The quick brown fox jumps over the lazy dog".encodeToByteArray())
        assertEquals("de7c9b85b8b78aa6bc8a7a36f70a90701c9db4d9", mac.toHex())
    }

    @Test
    fun sha1KnownVector() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc".encodeToByteArray()).toHex())
    }

    @Test
    fun authorizationHeaderShape() {
        val h = authorizationHeader(
            OAuthSecrets("ck", "cs", "tok", "ts"),
            "GET", "https://example.com/api",
            mapOf("symbol" to "AAPL"),
            nonce = "abc123",
            timestampSeconds = 1_700_000_000L
        )
        assertTrue(h.startsWith("OAuth "), h)
        assertTrue(h.contains("oauth_signature_method=\"HMAC-SHA1\""), h)
        assertTrue(h.contains("oauth_signature=\""), h)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { ((it.toInt() and 0xFF).toString(16)).padStart(2, '0') }
}
