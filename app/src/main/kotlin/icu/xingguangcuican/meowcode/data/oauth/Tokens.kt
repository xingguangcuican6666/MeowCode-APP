package icu.xingguangcuican.meowcode.data.oauth

import org.json.JSONObject

/** OAuthSession is the persisted part of a login; the stub keeps it in memory only. */
data class OAuthSession(
    val issuer: String,
    val clientId: String,
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val scope: String?,
    /** Epoch millis, aligned with the CLI's `expiresAt`. */
    val expiresAt: Long?,
) {
    /** Refreshes a minute early to cover clock skew, same as the CLI. */
    fun isFresh(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt == null || expiresAt - EXPIRY_SKEW_MS > now

    companion object { const val EXPIRY_SKEW_MS: Long = 60_000 }
}

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String?,
    val idToken: String?,
    val scope: String?,
    val expiresIn: Long?,
    val tokenType: String?,
) {
    /** Parses a token endpoint body. Returns null when it does not carry an access token. */
    companion object {
        fun fromJson(raw: String): TokenResponse? {
            val json = JSONObject(raw)
            val access = json.optString("access_token").ifBlank { return null }
            val expiresIn = if (json.has("expires_in") && !json.isNull("expires_in")) json.optLong("expires_in") else null
            return TokenResponse(
                accessToken = access,
                refreshToken = json.optStringOrNull("refresh_token"),
                idToken = json.optStringOrNull("id_token"),
                scope = json.optStringOrNull("scope"),
                expiresIn = expiresIn,
                tokenType = json.optStringOrNull("token_type"),
            )
        }

        private fun JSONObject.optStringOrNull(key: String): String? =
            if (has(key) && !isNull(key)) getString(key).ifBlank { null } else null
    }

    fun toSession(config: OAuthConfig, now: Long = System.currentTimeMillis()): OAuthSession = OAuthSession(
        issuer = config.issuer,
        clientId = config.clientId,
        accessToken = accessToken,
        refreshToken = refreshToken,
        idToken = idToken,
        scope = scope,
        expiresAt = expiresIn?.let { now + it * 1000 },
    )
}

/** Form body for the token endpoint. A public client sends only its client_id. */
fun authorizationCodeForm(config: OAuthConfig, code: String, verifier: String): Map<String, String> = mapOf(
    "grant_type" to "authorization_code",
    "code" to code,
    "redirect_uri" to config.redirectUri,
    "code_verifier" to verifier,
    "client_id" to config.clientId,
)

/**
 * Rotation means the returned refresh token replaces the old one immediately.
 * Replaying a rotated token revokes the whole family, so callers must persist this.
 */
fun refreshForm(config: OAuthConfig, refreshToken: String): Map<String, String> = mapOf(
    "grant_type" to "refresh_token",
    "refresh_token" to refreshToken,
    "client_id" to config.clientId,
)

data class UserInfo(
    val subject: String,
    val preferredUsername: String?,
    val name: String?,
    val email: String?,
    val emailVerified: Boolean?,
    /** `groups` scope: the account's group(s) and role, as the server reports them. */
    val groups: List<String> = emptyList(),
) {
    companion object {
        fun fromJson(raw: String): UserInfo {
            val json = JSONObject(raw)
            return UserInfo(
                subject = json.optString("sub"),
                preferredUsername = json.optString("preferred_username").ifBlank { null },
                name = json.optString("name").ifBlank { null },
                email = json.optString("email").ifBlank { null },
                emailVerified = if (json.has("email_verified")) json.optBoolean("email_verified") else null,
                groups = groupList(json.opt("groups")),
            )
        }

        /** `groups` may be a JSON array or a space-delimited string depending on the claim. */
        private fun groupList(value: Any?): List<String> = when (value) {
            is org.json.JSONArray -> (0 until value.length()).mapNotNull { value.optString(it).ifBlank { null } }
            is String -> value.split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }
}
