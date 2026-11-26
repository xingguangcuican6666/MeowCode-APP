package icu.xingguangcuican.meowcode.data.oauth

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The real half of the login: opens the MeowAPI authorization page in the system
 * browser and waits for Android to hand the custom-scheme callback back to
 * [icu.xingguangcuican.meowcode.MainActivity].
 *
 * [deliver] is called from the activity's `onNewIntent` / launch `intent` for any URI
 * starting with the registered redirect URI; it resumes the suspended [authorize].
 * Routing goes through [CallbackSlot], so one instance can serve every login in the
 * process: a fresh login replaces the previous attempt instead of inheriting its
 * (already completed) signal.
 *
 * HTTP goes through [HttpTransports].
 */
class BrowserOAuthTransport(
    context: Context,
    private val timeoutMs: Long = 10 * 60_000,
) : OAuthTransport {
    private val appContext = context.applicationContext
    private val slot = CallbackSlot()

    override val label: String = "系统浏览器"

    override suspend fun authorize(config: OAuthConfig, authorizationUrl: String): AuthorizationResult {
        // Take the slot before leaving the app, so a callback that beats the
        // browser launch still has an attempt to land in.
        val waiting = slot.begin(queryParameters(authorizationUrl)["state"])
        val intent = Intent(Intent.ACTION_VIEW, authorizationUrl.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            appContext.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            slot.cancel(waiting)
            return AuthorizationResult(null, null, "browser_unavailable", "无法打开浏览器，请检查默认浏览器设置")
        }
        // On timeout clear only this attempt: a newer login may own the slot by now.
        return withTimeoutOrNull(timeoutMs) { waiting.await() }
            ?: run {
                slot.cancel(waiting)
                AuthorizationResult(null, null, "timeout", "等待授权超时")
            }
    }

    /**
     * Feeds a callback URI back into the suspended [authorize]. Safe to call twice and
     * from an activity that has no login in flight (returns false, nothing happens).
     */
    fun deliver(rawUri: String): Boolean {
        if (!isCallbackUri(rawUri)) return false
        return slot.deliver(parseAuthorizationRedirect(rawUri))
    }

    override fun cancel() {
        slot.cancel()
    }

    override fun postForm(url: String, form: Map<String, String>): String = HttpTransports.postForm(url, form)

    override fun getBearer(url: String, accessToken: String): String = HttpTransports.getBearer(url, accessToken)

    override fun postJson(url: String, accessToken: String, body: String): String =
        HttpTransports.postJson(url, accessToken, body)

    override fun deleteBearer(url: String, accessToken: String): String =
        HttpTransports.deleteBearer(url, accessToken)
}
