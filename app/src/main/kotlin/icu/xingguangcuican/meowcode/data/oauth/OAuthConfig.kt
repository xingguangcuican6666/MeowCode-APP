package icu.xingguangcuican.meowcode.data.oauth

/**
 * Endpoints and client identity for MeowAPI's OAuth 2.0 server (new-api).
 *
 * Contract source of truth: new-api `controller/oauth_server.go` and
 * `oauth-server-docs/`; the CLI client lives in MeowCode `src/lib/oauth.ts`.
 * The client is a public client: no secret, PKCE S256 is mandatory.
 */
data class OAuthConfig(
    val issuer: String = DEFAULT_ISSUER,
    val clientId: String = DEFAULT_CLIENT_ID,
    val scope: String = DEFAULT_SCOPE,
    /**
     * Must match a redirect URI registered for the client *exactly* (RFC 6749 §3.1.2).
     * The CLI's loopback URI cannot work from an Android browser, so this client was
     * registered with the custom scheme below by the MeowAPI administrator.
     */
    val redirectUri: String = DEFAULT_REDIRECT_URI,
    /** The scope actually requested for this login (overrides [scope] when set). */
    val requestedScope: String? = null,
) {
    val authorizationEndpoint: String get() = "${issuer.trimEnd('/')}/oauth2/authorize"
    val tokenEndpoint: String get() = "${issuer.trimEnd('/')}/oauth2/token"
    val userInfoEndpoint: String get() = "${issuer.trimEnd('/')}/oauth2/userinfo"
    val revocationEndpoint: String get() = "${issuer.trimEnd('/')}/oauth2/revoke"
    val discoveryEndpoint: String get() = "${issuer.trimEnd('/')}/.well-known/openid-configuration"

    init {
        require(issuer.startsWith("https://")) { "OAuth issuer must use HTTPS" }
    }

    /** What goes into the `scope` query parameter. */
    val effectiveScope: String get() = requestedScope ?: scope

    companion object {
        const val DEFAULT_ISSUER: String = "https://api.xingguangcuican.icu"
        const val DEFAULT_CLIENT_ID: String = "cli_44cf45ec8ef543e49edede2e329836fe"
        /**
         * What the app asks for: identity, the key manager and the model catalog all
         * come from here. `models.invoke` authorises /v1/messages directly with the at_
         * token; `apikeys.manage` is sensitive, so the MeowAPI client must be marked
         * trusted or `/oauth2/api/tokens` answers 403 insufficient_scope.
         */
        const val DEFAULT_SCOPE: String =
            "openid profile email groups wallet.read apikeys.manage models.read models.invoke"
        /**
         * Custom scheme owned by the app's applicationId, not a generic `meowcode://`:
         * any installed app can claim a generic scheme, so the callback namespace must be
         * the package name (RFC 8252 §7.1: "reverse domain name notation"). Android delivers
         * it as a normal VIEW intent once the activity declares the matching intent-filter.
         */
        const val DEFAULT_REDIRECT_URI: String = "icu.xingguangcuican.meowcode://oauth/callback"
        const val CALLBACK_PREFIX: String = "icu.xingguangcuican.meowcode://oauth/callback"
        /** Registered but unused: the former generic scheme, kept for comparison. */
        const val LEGACY_REDIRECT_URI: String = "meowcode://oauth/callback"
    }
}

/** Parsed authorization redirect: either `code`, or `error` + description. */
data class AuthorizationResult(val code: String?, val state: String?, val error: String?, val errorDescription: String?) {
    override fun toString(): String =
        if (error != null) "error=$error (${errorDescription.orEmpty()})" else "code=…(len=${code?.length ?: 0})"
}
