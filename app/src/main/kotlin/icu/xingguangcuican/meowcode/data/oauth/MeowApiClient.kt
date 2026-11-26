package icu.xingguangcuican.meowcode.data.oauth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * The MeowAPI calls the app makes, each authorised by the at_ token itself (new-api
 * `setOAuthScopedAPIRouter`):
 *
 *  GET  /oauth2/userinfo          → identity claims (openid/profile/email/groups)
 *  GET  /oauth2/api/wallet        → remaining + used quota (wallet.read)
 *  GET  /oauth2/api/models        → the catalog this account can call (models.read)
 *  GET  /oauth2/api/tokens        → the account's relay keys, masked (apikeys.manage)
 *  POST /oauth2/api/tokens        → create a key
 *  POST /oauth2/api/tokens/:id/key→ reveal a key (the only call that returns plaintext)
 *  DELETE /oauth2/api/tokens/:id  → delete a key
 *
 * Responses are the dashboard envelope `{success, message, data}`; `success:false`
 * arrives with HTTP 200 and is still an error.
 */
class MeowApiClient(
    private val config: OAuthConfig = OAuthConfig(),
    @Volatile var transport: OAuthTransport,
) {
    private val base: String get() = config.issuer.trimEnd('/')

    suspend fun loadAccount(accessToken: String): Result<LiveAccount> = withContext(Dispatchers.IO) {
        runCatching {
            val info = UserInfo.fromJson(transport.getBearer(config.userInfoEndpoint, accessToken))
            val wallet = runCatching { wallet(accessToken) }.getOrNull()
            val models = runCatching { modelIds(accessToken) }.getOrDefault(emptyList())
            LiveAccount(info = info, quota = wallet?.first, usedQuota = wallet?.second, models = models)
        }
    }

    // ---- wallet --------------------------------------------------------------

    /** Returns (remaining, used) in new-api quota units (QuotaPerUnit = 500_000 per ￥1). */
    private fun wallet(accessToken: String): Pair<Double, Double> {
        val data = envelope(transport.getBearer("$base/oauth2/api/wallet", accessToken))?.optJSONObject("data")
            ?: return 0.0 to 0.0
        return data.optDouble("quota", 0.0) to data.optDouble("used_quota", 0.0)
    }

    // ---- model catalog -------------------------------------------------------

    /** Model ids only; the pricing payload is large and the stub does not render it yet. */
    private fun modelIds(accessToken: String): List<String> {
        val data = envelope(transport.getBearer("$base/oauth2/api/models", accessToken))?.opt("data")
        return when (data) {
            is JSONArray -> (0 until data.length()).mapNotNull { index ->
                val item = data.opt(index)
                when (item) {
                    is String -> item.ifBlank { null }
                    is JSONObject -> item.optString("id").ifBlank { null }
                    else -> null
                }
            }
            is JSONObject -> (data.optJSONArray("models") ?: data.optJSONArray("data"))
                ?.let { array -> (0 until array.length()).mapNotNull { array.optString(it).ifBlank { null } } }
                ?: emptyList()
            else -> emptyList()
        }
    }

    // ---- API keys (apikeys.manage) ------------------------------------------

    suspend fun loadKeys(accessToken: String): Result<List<ApiKey>> = withContext(Dispatchers.IO) {
        runCatching { keys(accessToken) }
    }

    fun keys(accessToken: String): List<ApiKey> {
        val root = envelope(transport.getBearer("$base/oauth2/api/tokens", accessToken)) ?: return emptyList()
        // GetAllTokens answers a PageInfo: the list lives in data.items.
        val items = root.optJSONObject("data")?.optJSONArray("items")
            ?: root.optJSONArray("data")
            ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let(::apiKey)
        }
    }

    fun createKey(accessToken: String, name: String, unlimitedQuota: Boolean = true): ApiKey? {
        val body = JSONObject().apply {
            put("name", name)
            put("expired_time", -1)
            put("unlimited_quota", unlimitedQuota)
            put("remain_quota", 0)
            put("model_limits_enabled", false)
        }.toString()
        val root = envelope(transport.postJson("$base/oauth2/api/tokens", accessToken, body)) ?: return null
        return root.optJSONObject("data")?.let(::apiKey)
    }

    fun deleteKey(accessToken: String, id: Int): Boolean {
        val root = envelope(transport.deleteBearer("$base/oauth2/api/tokens/$id", accessToken)) ?: return false
        return root.optBoolean("success", false)
    }

    /** The one call that yields the full `sk-` key; the dashboard logs it as an audit event. */
    fun revealKey(accessToken: String, id: Int): String? {
        val root = envelope(transport.postJson("$base/oauth2/api/tokens/$id/key", accessToken, "{}")) ?: return null
        return root.optJSONObject("data")?.optString("key")?.ifBlank { null }
    }

    private fun apiKey(json: JSONObject): ApiKey {
        val rawKey = json.optString("key")
        // Masked responses carry a prefix and asterisks; the full key always starts sk-.
        val full = rawKey.takeIf { !it.contains('*') && it.isNotBlank() }
        return ApiKey(
            id = json.optInt("id"),
            name = json.optString("name"),
            maskedKey = rawKey.ifBlank { "sk-…" },
            fullKey = full,
            enabled = json.optInt("status", 1) == 1,
            unlimitedQuota = json.optBoolean("unlimited_quota"),
            remainQuota = json.optInt("remain_quota"),
            usedQuota = json.optInt("used_quota"),
            group = json.optString("group").ifBlank { null },
            expiredTime = json.optLong("expired_time", -1),
            accessedTime = json.optLong("accessed_time"),
            createdTime = json.optLong("created_time"),
        )
    }

    /** Unwraps `{success, message, data}`; a false `success` throws with the server message. */
    private fun envelope(raw: String?): JSONObject? {
        val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        check(json.optBoolean("success", true)) { json.optString("message").ifBlank { "MeowAPI 请求失败" } }
        return json
    }
}

data class LiveAccount(
    val info: UserInfo,
    val quota: Double?,
    val usedQuota: Double?,
    val models: List<String> = emptyList(),
) {
    /** new-api stores quota in 1/500000 of a currency unit. */
    fun quotaLabel(value: Double?): String? =
        value?.let { String.format(java.util.Locale.US, "￥ %.2f", it / 500_000.0) }
}

/** One relay key as the dashboard shows it. [fullKey] is null unless just revealed. */
data class ApiKey(
    val id: Int,
    val name: String,
    val maskedKey: String,
    val fullKey: String?,
    val enabled: Boolean,
    val unlimitedQuota: Boolean,
    val remainQuota: Int,
    val usedQuota: Int,
    val group: String?,
    val expiredTime: Long,
    val accessedTime: Long,
    val createdTime: Long,
)
