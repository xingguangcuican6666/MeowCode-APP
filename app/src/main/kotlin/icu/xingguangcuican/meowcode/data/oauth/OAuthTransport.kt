package icu.xingguangcuican.meowcode.data.oauth

import java.io.IOException

/**
 * The browser round-trip. Real transport (Custom Tabs + app link) is a later
 * milestone; the stub wires [SimulatedOAuthTransport] so every state of the flow is
 * reachable in the UI today.
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

/** Shapes the stub simulation can be driven into, from the login screen. */
enum class SimulatedOutcome(val label: String) {
    Success("模拟成功"),
    Denied("模拟用户拒绝"),
    NetworkFailure("模拟网络中断"),
    Expired("模拟授权超时"),
}

/**
 * Deterministic, offline transport. It produces tokens that are obviously fake
 * (`sim_at_…`) so nothing can mistake them for real credentials, and it never opens
 * a browser, a socket or a URL.
 */
class SimulatedOAuthTransport(
    var outcome: SimulatedOutcome = SimulatedOutcome.Success,
    private val delayMs: Long = 1_200,
    private val sleep: suspend (Long) -> Unit = { kotlinx.coroutines.delay(it) },
) : OAuthTransport {
    override val label: String = "本地模拟"

    override suspend fun authorize(config: OAuthConfig, authorizationUrl: String): AuthorizationResult {
        sleep(delayMs)
        val state = queryParameters(authorizationUrl)["state"]
        return when (outcome) {
            SimulatedOutcome.Success -> AuthorizationResult("sim_code", state, null, null)
            SimulatedOutcome.Denied -> AuthorizationResult(null, state, "access_denied", "用户拒绝了本次授权")
            SimulatedOutcome.Expired -> AuthorizationResult(null, null, "timeout", "等待授权超时（5 分钟）")
            SimulatedOutcome.NetworkFailure -> AuthorizationResult(null, state, "server_error", "无法连接授权服务器")
        }
    }

    override fun postForm(url: String, form: Map<String, String>): String {
        if (outcome == SimulatedOutcome.NetworkFailure) throw IOException("无法连接授权服务器")
        val grant = form["grant_type"]
        return when (grant) {
            "authorization_code" -> """
                {"access_token":"sim_at_${PkcePair.randomToken(bytes = 18)}",
                 "token_type":"Bearer","expires_in":3600,"scope":"openid profile email groups wallet.read apikeys.manage models.read models.invoke",
                 "refresh_token":"sim_rt_${PkcePair.randomToken(bytes = 18)}"}
            """.trimIndent()
            "refresh_token" -> """
                {"access_token":"sim_at_${PkcePair.randomToken(bytes = 18)}",
                 "token_type":"Bearer","expires_in":3600,"scope":"openid profile email groups wallet.read apikeys.manage models.read models.invoke",
                 "refresh_token":"sim_rt_${PkcePair.randomToken(bytes = 18)}"}
            """.trimIndent()
            else -> throw IOException("不支持的 grant_type: $grant")
        }
    }

    override fun getBearer(url: String, accessToken: String): String = when {
        url.endsWith("/oauth2/userinfo") ->
            """{"sub":"1024","preferred_username":"xingguangcuican","name":"星光璀璨",
                "email":"meow@example.com","email_verified":true,"groups":["default"]}"""
        url.endsWith("/oauth2/api/wallet") -> """{"success":true,"message":"","data":{"quota":21000000,"used_quota":2900000}}"""
        url.endsWith("/oauth2/api/models") -> """{"success":true,"message":"","data":["claude-sonnet-5-5","gpt-5.1","deepseek-v4"]}"""
        url.endsWith("/oauth2/api/tokens") ->
            """{"success":true,"message":"","data":{"page":1,"page_size":10,"total":1,"items":[
                {"id":1,"name":"MeowCode 桌面端","key":"sk-abcd****wxyz","status":1,
                 "unlimited_quota":true,"remain_quota":0,"used_quota":1200000,"group":"default",
                 "expired_time":-1,"accessed_time":0,"created_time":0}]}}"""
        else -> "{}"
    }

    override fun postJson(url: String, accessToken: String, body: String): String = when {
        url.endsWith("/key") -> """{"success":true,"message":"","data":{"key":"sk-simmock0123456789abcdef"}}"""
        url.contains("/oauth2/api/tokens") ->
            """{"success":true,"message":"","data":{"id":2,"name":"模拟新建","key":"sk-newmock****abcd",
                "status":1,"unlimited_quota":true,"remain_quota":0,"used_quota":0,"group":"default",
                "expired_time":-1,"accessed_time":0,"created_time":0}}"""
        else -> "{}"
    }

    override fun deleteBearer(url: String, accessToken: String): String = """{"success":true,"message":""}"""

    override fun cancel() { /* nothing to cancel offline */ }
}
