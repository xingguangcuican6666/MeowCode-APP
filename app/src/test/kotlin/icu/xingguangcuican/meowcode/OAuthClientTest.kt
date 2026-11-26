package icu.xingguangcuican.meowcode

import icu.xingguangcuican.meowcode.data.oauth.AuthorizationRequest
import icu.xingguangcuican.meowcode.data.oauth.LiveAccount
import icu.xingguangcuican.meowcode.data.oauth.UserInfo
import icu.xingguangcuican.meowcode.data.oauth.isCallbackUri
import icu.xingguangcuican.meowcode.data.oauth.percentEncode
import icu.xingguangcuican.meowcode.data.oauth.queryParameters
import icu.xingguangcuican.meowcode.data.oauth.MeowOAuthClient
import icu.xingguangcuican.meowcode.data.oauth.OAuthConfig
import icu.xingguangcuican.meowcode.data.oauth.PkcePair
import icu.xingguangcuican.meowcode.data.oauth.AuthorizationResult
import icu.xingguangcuican.meowcode.data.oauth.OAuthTransport
import icu.xingguangcuican.meowcode.data.oauth.TokenResponse
import icu.xingguangcuican.meowcode.data.oauth.buildAuthorizationUrl
import icu.xingguangcuican.meowcode.data.oauth.parseAuthorizationRedirect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * Contract checks for the OAuth client. These run on the JVM without Android: the
 * pure parts of the flow (PKCE, URL shape, state validation, token lifecycle) are
 * exercised against a recording fake, never against the real browser transport.
 */
class OAuthClientTest {
    private val config = OAuthConfig(issuer = "https://example.test")

    @Test
    fun `pkce challenge is base64url sha256 of the verifier without padding`() {
        val pair = PkcePair.generate()
        assertTrue(pair.verifier.length in 43..128)
        val expected = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(pair.verifier.toByteArray(Charsets.US_ASCII)))
        assertEquals(expected, pair.challenge)
        assertFalse(pair.challenge.contains('='))
        assertFalse(pair.challenge.contains('+'))
        assertFalse(pair.challenge.contains('/'))
    }

    @Test
    fun `authorization url carries S256 PKCE and the exact redirect uri`() {
        val pair = PkcePair.generate()
        val url = buildAuthorizationUrl(config, pair, state = "s1", nonce = "n1")
        assertTrue(url.startsWith("https://example.test/oauth2/authorize?"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=${config.clientId}"))
        // The redirect URI is percent-encoded; decoding it must return the registered value.
        val params = icu.xingguangcuican.meowcode.data.oauth.queryParameters(url)
        assertEquals(config.redirectUri, params["redirect_uri"])
        assertEquals(config.scope, params["scope"])
        assertEquals(pair.challenge, params["code_challenge"])
    }

    @Test
    fun `successful login produces a session bound to the config`() {
        val transport = FakeTransport()
        val client = MeowOAuthClient(config, transport)
        val request: AuthorizationRequest = client.beginAuthorization()
        val redirect = "${config.redirectUri}?code=abc&state=${request.state}"
        val session = client.completeAuthorization(redirect).getOrThrow()
        assertEquals(config.issuer, session.issuer)
        assertEquals("at_test", session.accessToken)
        assertEquals("rt_test", session.refreshToken)
        assertTrue(session.isFresh())
        // The verifier sent to the token endpoint is the one behind the URL's challenge.
        assertEquals(request.pair.verifier, transport.lastForm?.get("code_verifier"))
        assertEquals("abc", transport.lastForm?.get("code"))
        // A second completion after success has no pending request.
        assertTrue(client.completeAuthorization(redirect).isFailure)
    }

    @Test
    fun `state mismatch is rejected before any token exchange`() {
        val transport = FakeTransport()
        val client = MeowOAuthClient(config, transport)
        val request = client.beginAuthorization()
        val result = client.completeAuthorization("${config.redirectUri}?code=abc&state=forged")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("state"))
        assertEquals(0, transport.postFormCalls)
        assertNull(client.currentSession())
        assertNotNull(request)
    }

    @Test
    fun `denied authorization surfaces the server description`() {
        val transport = FakeTransport(
            authorizeResult = AuthorizationResult(null, "s", "access_denied", "用户拒绝了授权"),
        )
        val client = MeowOAuthClient(config, transport)
        val request = client.beginAuthorization()
        val result = kotlinx.coroutines.runBlocking { transport.authorize(config, request.url) }
        assertEquals("access_denied", result.error)
        assertFalse(result.errorDescription.isNullOrBlank())
        // A redirect that only carries the error must not reach the token endpoint either.
        val completed = client.completeAuthorization(
            "${config.redirectUri}?error=access_denied&error_description=用户拒绝了授权&state=${request.state}",
        )
        assertTrue(completed.isFailure)
        assertEquals("用户拒绝了授权", completed.exceptionOrNull()!!.message)
        assertEquals(0, transport.postFormCalls)
    }

    @Test
    fun `token parsing tolerates missing optional fields`() {
        val parsed = TokenResponse.fromJson("""{"access_token":"at_x","expires_in":3600}""")
        assertNotNull(parsed)
        assertEquals("at_x", parsed!!.accessToken)
        assertNull(parsed.refreshToken)
        assertEquals(3600L, parsed.expiresIn)
        assertNull(TokenResponse.fromJson("""{"error":"invalid_grant"}"""))
    }

    @Test
    fun `redirect parsing reads error and description`() {
        val result = parseAuthorizationRedirect("meowcode://oauth/callback?error=access_denied&error_description=%E6%8B%92%E7%BB%9D")
        assertEquals("access_denied", result.error)
        assertEquals("拒绝", result.errorDescription)
        assertNull(result.code)
    }

    @Test
    fun `callback detection accepts only the registered scheme and host`() {
        assertTrue(isCallbackUri("icu.xingguangcuican.meowcode://oauth/callback?code=abc&state=s"))
        assertTrue(isCallbackUri("ICU.XINGGUANGCUICAN.meowcode://oauth/callback"))
        // A generic scheme any installed app could claim must never resume the login.
        assertFalse(isCallbackUri("meowcode://oauth/callback?code=abc"))
        assertFalse(isCallbackUri("https://evil.example/oauth/callback"))
    }

    @Test
    fun `percent coding round-trips reserved characters`() {
        val samples = listOf("icu.xingguangcuican.meowcode://oauth/callback", "a b&c=d/e?f#g", "中文与空格", "~-._")
        samples.forEach { value ->
            assertEquals(value, queryParameters("x://y/?v=${percentEncode(value)}")["v"])
        }
    }

    @Test
    fun `userinfo groups accept array and space-delimited claims`() {
        val array = UserInfo.fromJson("""{"sub":"7","preferred_username":"meow","groups":["default","vip"]}""")
        assertEquals(listOf("default", "vip"), array.groups)
        val spaced = UserInfo.fromJson("""{"sub":"7","groups":"default vip"}""")
        assertEquals(listOf("default", "vip"), spaced.groups)
        assertTrue(UserInfo.fromJson("""{"sub":"7"}""").groups.isEmpty())
    }

    @Test
    fun `quota is rendered from new-api units`() {
        val account = LiveAccount(
            UserInfo(subject = "7", preferredUsername = null, name = null, email = null, emailVerified = null),
            quota = 21_000_000.0,
            usedQuota = 2_900_000.0,
        )
        assertEquals("￥ 42.00", account.quotaLabel(account.quota))
        assertEquals("￥ 5.80", account.quotaLabel(account.usedQuota))
    }

    @Test
    fun `session from config carries the requested scope`() {
        val parsed = TokenResponse.fromJson("""{"access_token":"at_x","expires_in":60}""")!!.toSession(config, now = 1_000)
        assertEquals("https://example.test", parsed.issuer)
        assertEquals(61_000L, parsed.expiresAt)
        assertTrue(config.authorizationEndpoint.startsWith("https://"))
    }

    /** Records what the client sends and answers with a fixed token payload. */
    private class FakeTransport(
        private val authorizeResult: AuthorizationResult = AuthorizationResult("code", "s", null, null),
    ) : OAuthTransport {
        override val label: String = "测试"
        var postFormCalls = 0
            private set
        var lastForm: Map<String, String>? = null
            private set

        override suspend fun authorize(config: OAuthConfig, authorizationUrl: String): AuthorizationResult = authorizeResult

        override fun postForm(url: String, form: Map<String, String>): String {
            postFormCalls++
            lastForm = form
            return if (form["grant_type"] == "refresh_token") {
                """{"access_token":"at_rotated","refresh_token":"rt_rotated","expires_in":3600}"""
            } else {
                """{"access_token":"at_test","refresh_token":"rt_test","token_type":"Bearer","expires_in":3600}"""
            }
        }

        override fun getBearer(url: String, accessToken: String): String =
            """{"sub":"7","preferred_username":"meow"}"""

        override fun postJson(url: String, accessToken: String, body: String): String = error("unused")

        override fun deleteBearer(url: String, accessToken: String): String = error("unused")

        override fun cancel() = Unit
    }
}
