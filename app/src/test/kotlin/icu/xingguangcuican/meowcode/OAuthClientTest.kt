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
import icu.xingguangcuican.meowcode.data.oauth.SimulatedOAuthTransport
import icu.xingguangcuican.meowcode.data.oauth.SimulatedOutcome
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
 * exercised against the same simulated transport the stub UI uses.
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
    fun `successful simulated login produces a session bound to the config`() {
        val transport = SimulatedOAuthTransport(delayMs = 0, sleep = {})
        val client = MeowOAuthClient(config, transport)
        val request: AuthorizationRequest = client.beginAuthorization()
        val redirect = "${config.redirectUri}?code=abc&state=${request.state}"
        val session = client.completeAuthorization(redirect).getOrThrow()
        assertEquals(config.issuer, session.issuer)
        assertTrue(session.accessToken.startsWith("sim_at_"))
        assertNotNull(session.refreshToken)
        assertTrue(session.isFresh())
        // A second completion after success has no pending request.
        assertTrue(client.completeAuthorization(redirect).isFailure)
    }

    @Test
    fun `state mismatch is rejected before any token exchange`() {
        var exchangeCalls = 0
        val transport = SimulatedOAuthTransport(delayMs = 0, sleep = {})
        val client = MeowOAuthClient(config, object : icu.xingguangcuican.meowcode.data.oauth.OAuthTransport by transport {
            override fun postForm(url: String, form: Map<String, String>): String {
                exchangeCalls++
                return transport.postForm(url, form)
            }
        })
        val request = client.beginAuthorization()
        val result = client.completeAuthorization("${config.redirectUri}?code=abc&state=forged")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("state"))
        assertEquals(0, exchangeCalls)
        assertNull(client.currentSession())
        assertNotNull(request)
    }

    @Test
    fun `denied authorization surfaces the server description`() {
        val transport = SimulatedOAuthTransport(SimulatedOutcome.Denied, delayMs = 0, sleep = {})
        val client = MeowOAuthClient(config, transport)
        val request = client.beginAuthorization()
        val result = transport.authorizeSync(request.url)
        assertEquals("access_denied", result.error)
        assertFalse(result.errorDescription.isNullOrBlank())
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

    @Test
    fun `simulated transport only echoes the state it was given`() {
        // The simulator must not invent a code for a URL it does not understand.
        val transport = SimulatedOAuthTransport(delayMs = 0, sleep = {})
        val result = kotlinx.coroutines.runBlocking { transport.authorize(config, "https://example.test/oauth2/authorize?state=abc") }
        assertEquals("abc", result.state)
        assertEquals("sim_code", result.code)
        val stateless = kotlinx.coroutines.runBlocking { transport.authorize(config, "not a url") }
        assertNull(stateless.state)
    }

    private fun SimulatedOAuthTransport.authorizeSync(url: String): icu.xingguangcuican.meowcode.data.oauth.AuthorizationResult =
        kotlinx.coroutines.runBlocking { authorize(config, url) }
}
