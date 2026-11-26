package icu.xingguangcuican.meowcode.data.oauth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * RFC 7636 verifier/challenge pair. The server only accepts S256
 * (`ValidChallengeMethod` in new-api), so the plain method is not offered.
 */
data class PkcePair(val verifier: String, val challenge: String) {
    companion object {
        const val VERIFIER_BYTES: Int = 48 // base64url → 64 chars, inside the 43..128 range

        fun generate(random: SecureRandom = SecureRandom()): PkcePair {
            val bytes = ByteArray(VERIFIER_BYTES).also(random::nextBytes)
            val verifier = base64Url(bytes)
            return PkcePair(verifier, challengeFor(verifier))
        }

        /** BASE64URL(SHA256(ASCII(verifier))), unpadded (RFC 7636 §A). */
        fun challengeFor(verifier: String): String =
            base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        fun base64Url(bytes: ByteArray): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun randomToken(random: SecureRandom = SecureRandom(), bytes: Int = 16): String =
            base64Url(ByteArray(bytes).also(random::nextBytes))
    }
}

/**
 * Builds the authorization request. Parameter order follows the CLI so the two
 * clients stay easy to compare in logs.
 */
fun buildAuthorizationUrl(config: OAuthConfig, pair: PkcePair, state: String, nonce: String): String {
    val query = listOf(
        "response_type" to "code",
        "client_id" to config.clientId,
        "redirect_uri" to config.redirectUri,
        "scope" to config.effectiveScope,
        "state" to state,
        "nonce" to nonce,
        "code_challenge" to pair.challenge,
        "code_challenge_method" to "S256",
    ).joinToString("&") { (key, value) -> "$key=${percentEncode(value)}" }
    return "${config.authorizationEndpoint}?$query"
}

/** Reads `code` / `state` / `error` out of the redirect the browser comes back with. */
fun parseAuthorizationRedirect(redirect: String): AuthorizationResult {
    val params = queryParameters(redirect)
    return AuthorizationResult(
        code = params["code"],
        state = params["state"],
        error = params["error"],
        errorDescription = params["error_description"],
    )
}

/**
 * Whether an incoming VIEW intent is this app's OAuth callback. Matches the scheme
 * and host only, so a query appended by the server (or by a malicious page) cannot
 * change the decision — the state check downstream is what rejects forged codes.
 */
fun isCallbackUri(rawUri: String): Boolean = rawUri.startsWith(OAuthConfig.CALLBACK_PREFIX, ignoreCase = true)

/**
 * RFC 3986 unreserved characters stay literal, everything else is percent-encoded
 * as UTF-8. Hand-rolled instead of `Uri.encode` / `URLEncoder` so the same code runs
 * unchanged in JVM unit tests (both are Android-framework / form-encoding specific).
 */
internal fun percentEncode(value: String): String = buildString {
    value.toByteArray(Charsets.UTF_8).forEach { byte ->
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' || ch in "-._~") {
            append(ch)
        } else {
            append('%').append(HEX[c shr 4]).append(HEX[c and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"

/** Parses the query of any URI-ish string into a map, later keys winning. */
internal fun queryParameters(uri: String): Map<String, String> {
    val query = uri.substringAfter('?', "").substringBefore('#')
    if (query.isEmpty()) return emptyMap()
    return query.split('&').mapNotNull { pair ->
        val key = pair.substringBefore("=")
        if (key.isEmpty()) return@mapNotNull null
        key to percentDecode(pair.substringAfter('=', ""))
    }.toMap()
}

internal fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val out = java.io.ByteArrayOutputStream(value.length)
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '%' && index + 2 <= value.lastIndex) {
            out.write(value.substring(index + 1, index + 3).toInt(16))
            index += 3
        } else {
            out.write(ch.toString().toByteArray(Charsets.UTF_8))
            index++
        }
    }
    return out.toString(Charsets.UTF_8.name())
}
