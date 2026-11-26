package icu.xingguangcuican.meowcode.data.oauth

import java.io.IOException

/**
 * The browser round-trip. There is exactly one real implementation,
 * [BrowserOAuthTransport]; nothing may fabricate tokens outside it.
 */
interface OAuthTransport {
    val label: String

    /**
     * Opens [authorizationUrl] and suspends until the redirect comes back.
     * Implementations must not throw; failures are reported as [AuthorizationResult.error].
     */
    suspend fun authorize(config: OAuthConfig, authorizationUrl: String): AuthorizationResult

    /** POSTs an `application/x-www-form-urlencoded` body and returns the raw JSON. */
    @Throws(IOException::class)
    fun postForm(url: String, form: Map<String, String>): String

    /** GETs with a Bearer token and returns the raw JSON. */
    @Throws(IOException::class)
    fun getBearer(url: String, accessToken: String): String

    /** POSTs a JSON body with a Bearer token; used by the key manager. */
    @Throws(IOException::class)
    fun postJson(url: String, accessToken: String, body: String): String

    /** DELETEs with a Bearer token. */
    @Throws(IOException::class)
    fun deleteBearer(url: String, accessToken: String): String

    fun cancel()
}

/**
 * Stands in for the browser before a login starts, so [MeowOAuthClient] and
 * [MeowApiClient] always hold a transport without anyone faking a session. It never
 * opens a browser and never answers with a token.
 */
object UnavailableTransport : OAuthTransport {
    override val label: String = "未开始登录"

    override suspend fun authorize(config: OAuthConfig, authorizationUrl: String): AuthorizationResult =
        AuthorizationResult(null, null, "browser_unavailable", "当前环境无法打开浏览器")

    override fun postForm(url: String, form: Map<String, String>): String = notSignedIn()

    override fun getBearer(url: String, accessToken: String): String = notSignedIn()

    override fun postJson(url: String, accessToken: String, body: String): String = notSignedIn()

    override fun deleteBearer(url: String, accessToken: String): String = notSignedIn()

    override fun cancel() = Unit

    private fun notSignedIn(): Nothing = throw IOException("尚未登录，无法访问 MeowAPI")
}
