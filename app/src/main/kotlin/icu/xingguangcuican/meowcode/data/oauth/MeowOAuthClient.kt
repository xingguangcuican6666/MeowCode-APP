package icu.xingguangcuican.meowcode.data.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Authorization Code + PKCE(S256) against MeowAPI's OAuth server, mirroring
 * MeowCode's `src/lib/oauth.ts`:
 *
 *  authorize → code → POST /oauth2/token (client_id + code_verifier) → at_/rt_
 *  tokens; `at_` itself is the /v1/messages credential (scope models.invoke).
 *
 * Refresh tokens rotate, so refresh is single-flight and the rotated token must be
 * persisted before the next call can start — the exact race the CLI fights.
 */
class MeowOAuthClient(
    val config: OAuthConfig = OAuthConfig(),
    @Volatile var transport: OAuthTransport,
    private val now: () -> Long = System::currentTimeMillis,
    private val random: java.security.SecureRandom = java.security.SecureRandom(),
) {
    private val refreshMutex = Mutex()
    @Volatile private var session: OAuthSession? = null
    @Volatile private var inFlight: AuthorizationRequest? = null

    /**
     * Called with the new session whenever the client rotates or drops it, on the
     * caller's thread. The host persists from here so a refresh can never leave disk
     * holding a token the server has already invalidated.
     */
    var onSessionChanged: ((OAuthSession?) -> Unit)? = null

    /** The in-memory session; also the value the host persists. */
    fun currentSession(): OAuthSession? = session

    /** Restores a previously persisted login (call once at startup). */
    fun restore(session: OAuthSession?) {
        if (session == null) return
        this.session = session
        inFlight = null
    }

    /** The pending authorization, so the UI can show what is being asked for. */
    fun pendingRequest(): AuthorizationRequest? = inFlight

    /**
     * Starts the flow. Returns the URL that must be opened in a browser; the caller
     * then feeds the redirect back through [completeAuthorization].
     */
    fun beginAuthorization(): AuthorizationRequest {
        inFlight?.let { return it }
        val pair = PkcePair.generate(random)
        val request = AuthorizationRequest(
            config = config,
            pair = pair,
            state = PkcePair.randomToken(random),
            nonce = PkcePair.randomToken(random),
        )
        inFlight = request
        return request
    }

    /**
     * Validates `state` (CSRF) before anything else, then exchanges the code.
     * Never throws: the result carries a human message for the login screen.
     */
    fun completeAuthorization(redirect: String): Result<OAuthSession> = completeAuthorization(parseAuthorizationRedirect(redirect))

    /** Same contract as the string overload, for transports that already parsed the redirect. */
    fun completeAuthorization(result: AuthorizationResult): Result<OAuthSession> {
        val request = inFlight ?: return failure("没有进行中的授权请求，请重新开始")
        return try {
            if (result.error != null) return failure(result.errorDescription ?: result.error)
            if (result.state != request.state) return failure("state 不匹配，已中止（可能的 CSRF）")
            val code = result.code ?: return failure("回调缺少授权码")
            val raw = transport.postForm(config.tokenEndpoint, authorizationCodeForm(config, code, request.pair.verifier))
            val tokens = TokenResponse.fromJson(raw) ?: return failure("令牌响应缺少 access_token")
            val created = tokens.toSession(config, now())
            session = created
            inFlight = null
            onSessionChanged?.invoke(created)
            Result.success(created)
        } catch (e: Exception) {
            failure(e.message ?: "令牌交换失败")
        }
    }

    /** Abandons the pending authorization (back / cancel in the login sheet). */
    fun cancel() {
        inFlight = null
        transport.cancel()
    }

    fun signOut(revoke: Boolean = true) {
        val current = session ?: return
        session = null
        inFlight = null
        onSessionChanged?.invoke(null)
        if (!revoke) return
        val token = current.refreshToken ?: current.accessToken
        runCatching {
            transport.postForm(config.revocationEndpoint, mapOf("token" to token, "client_id" to config.clientId))
        }
    }

    /**
     * Returns a bearer to call the /v1 relay with, refreshing once when the access
     * token is stale. Concurrent callers share one refresh; the rotated token is
     * committed before the lock is released so a retry can never replay the old one.
     */
    suspend fun bearerForApi(): String? {
        val current = session ?: return null
        if (current.isFresh(now())) return current.accessToken
        return refresh()?.accessToken ?: current.accessToken
    }

    /** Forced rotation (401 path, or "刷新令牌" in settings). Single-flight. */
    suspend fun refresh(): OAuthSession? = refreshMutex.withLock {
        val current = session ?: return@withLock null
        val refreshToken = current.refreshToken ?: return@withLock null
        val rotated = runCatching {
            val raw = transport.postForm(config.tokenEndpoint, refreshForm(config, refreshToken))
            TokenResponse.fromJson(raw)?.toSession(config, now())
        }.getOrNull() ?: return@withLock null
        // Keep the rotated refresh token even if the server omitted a new one.
        val committed = rotated.copy(refreshToken = rotated.refreshToken ?: refreshToken)
        session = committed
        // Persist inside the lock: a second caller must never read the old token.
        onSessionChanged?.invoke(committed)
        committed
    }

    /** Fetches identity claims for the account card. */
    suspend fun userInfo(): Result<UserInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val current = session ?: error("尚未登录")
            UserInfo.fromJson(transport.getBearer(config.userInfoEndpoint, current.accessToken))
        }
    }

    /** Best-effort sanity check on an ID token's `nonce` claim, without trusting it. */
    fun idTokenNonceMatches(): Boolean {
        val idToken = session?.idToken ?: return false
        return runCatching {
            val payload = idToken.split('.').getOrNull(1)?.let {
                String(java.util.Base64.getUrlDecoder().decode(it))
            } ?: return false
            JSONObject(payload).optString("nonce").isNotBlank()
        }.getOrDefault(false)
    }

    private fun failure(message: String): Result<OAuthSession> = Result.failure(IllegalStateException(message))

    fun errorMessage(error: Throwable?): String = error?.message ?: "未知错误"
}

/** The PKCE material for one authorization attempt. */
data class AuthorizationRequest(
    val config: OAuthConfig,
    val pair: PkcePair,
    val state: String,
    val nonce: String,
) {
    val url: String get() = buildAuthorizationUrl(config, pair, state, nonce)
}
