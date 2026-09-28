package com.tlhf.shared.data

/**
 * E*TRADE OAuth 1.0a signing helpers. Pure Kotlin (no javax.crypto) so the
 * exact same code runs on Android, iOS and JVM unit tests.
 *
 * Flow (unchanged from the web app): request token -> authorize URL ->
 * access token. Every signed request needs an Authorization header built by
 * [authorizationHeader]. Consumer key/secret and tokens stay on-device;
 * they are parameters here, never stored.
 */

private fun Int.rotl(n: Int): Int = (this shl n) or (this ushr (32 - n))

/** SHA-1 digest (FIPS 180-4), pure Kotlin. */
fun sha1(message: ByteArray): ByteArray {
    var h0 = 0x67452301.toInt()
    var h1 = 0xEFCDAB89.toInt()
    var h2 = 0x98BADCFE.toInt()
    var h3 = 0x10325476.toInt()
    var h4 = 0xC3D2E1F0.toInt()

    val ml = message.size.toLong() * 8
    val padded = mutableListOf<Byte>()
    padded.addAll(message.toList())
    padded.add(0x80.toByte())
    while (padded.size % 64 != 56) padded.add(0)
    for (i in 7 downTo 0) padded.add(((ml ushr (i * 8)) and 0xFF).toByte())

    val w = IntArray(80)
    var pos = 0
    while (pos < padded.size) {
        for (i in 0 until 16) {
            w[i] = ((padded[pos + i * 4].toInt() and 0xFF) shl 24) or
                ((padded[pos + i * 4 + 1].toInt() and 0xFF) shl 16) or
                ((padded[pos + i * 4 + 2].toInt() and 0xFF) shl 8) or
                (padded[pos + i * 4 + 3].toInt() and 0xFF)
        }
        for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotl(1)
        var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
        for (i in 0 until 80) {
            val (f, k) = when (i) {
                in 0 until 20 -> ((b and c) or ((b.inv()) and d)) to 0x5A827999.toInt()
                in 20 until 40 -> (b xor c xor d) to 0x6ED9EBA1.toInt()
                in 40 until 60 -> ((b and c) or (b and d) or (c and d)) to 0x8F1BBCDC.toInt()
                else -> (b xor c xor d) to 0xCA62C1D6.toInt()
            }
            val temp = a.rotl(5) + f + e + k + w[i]
            e = d; d = c; c = b.rotl(30); b = a; a = temp
        }
        h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        pos += 64
    }
    fun Int.bytes() = byteArrayOf(
        ((this ushr 24) and 0xFF).toByte(), ((this ushr 16) and 0xFF).toByte(),
        ((this ushr 8) and 0xFF).toByte(), (this and 0xFF).toByte()
    )
    return h0.bytes() + h1.bytes() + h2.bytes() + h3.bytes() + h4.bytes()
}

/** HMAC-SHA1 per RFC 2104. */
fun hmacSha1(key: ByteArray, data: ByteArray): ByteArray {
    val block = 64
    val k = if (key.size > block) sha1(key) else key.copyOf()
    val kp = k + ByteArray(block - k.size)
    val ipad = ByteArray(block) { i -> (kp[i].toInt() xor 0x36).toByte() }
    val opad = ByteArray(block) { i -> (kp[i].toInt() xor 0x5C).toByte() }
    return sha1(opad + sha1(ipad + data))
}

private val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
fun base64Encode(bytes: ByteArray): String {
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        sb.append(B64[(b0 shr 2) and 0x3F])
        sb.append(B64[((b0 shl 4) or (b1 shr 4)) and 0x3F])
        sb.append(if (i + 1 < bytes.size) B64[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
        sb.append(if (i + 2 < bytes.size) B64[b2 and 0x3F] else '=')
        i += 3
    }
    return sb.toString()
}

/** OAuth percent-encoding (RFC 5849 §3.6). */
fun oauthEncode(s: String): String {
    val sb = StringBuilder()
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        if (c in 48..57 || c in 65..90 || c in 97..122 || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code) {
            sb.append(c.toChar())
        } else {
            sb.append('%')
            sb.append("0123456789ABCDEF"[(c shr 4) and 0xF])
            sb.append("0123456789ABCDEF"[c and 0xF])
        }
    }
    return sb.toString()
}

data class OAuthSecrets(
    val consumerKey: String,
    val consumerSecret: String,
    val token: String = "",
    val tokenSecret: String = ""
)

/** Builds the OAuth 1.0a signature base string (RFC 5849 §3.4.1). */
fun signatureBaseString(
    method: String,
    url: String,
    params: Map<String, String>
): String {
    val normalized = params.toSortedMap()
        .map { (k, v) -> "${oauthEncode(k)}=${oauthEncode(v)}" }
        .joinToString("&")
    return "${method.uppercase()}&${oauthEncode(url)}&${oauthEncode(normalized)}"
}

/**
 * Builds the full `Authorization: OAuth ...` header value for one request.
 * [extraParams] are query/body params that must be signed.
 */
fun authorizationHeader(
    secrets: OAuthSecrets,
    method: String,
    url: String,
    extraParams: Map<String, String> = emptyMap(),
    nonce: String,
    timestampSeconds: Long
): String {
    val oauth = mutableMapOf(
        "oauth_consumer_key" to secrets.consumerKey,
        "oauth_nonce" to nonce,
        "oauth_signature_method" to "HMAC-SHA1",
        "oauth_timestamp" to timestampSeconds.toString(),
        "oauth_version" to "1.0"
    )
    if (secrets.token.isNotEmpty()) oauth["oauth_token"] = secrets.token
    val base = signatureBaseString(method, url, oauth + extraParams)
    val signingKey = "${oauthEncode(secrets.consumerSecret)}&${oauthEncode(secrets.tokenSecret)}"
    val sig = base64Encode(hmacSha1(signingKey.encodeToByteArray(), base.encodeToByteArray()))
    val headerParams = (oauth + ("oauth_signature" to sig)).toSortedMap()
        .map { (k, v) -> "${oauthEncode(k)}=\"${oauthEncode(v)}\"" }
        .joinToString(", ")
    return "OAuth $headerParams"
}
