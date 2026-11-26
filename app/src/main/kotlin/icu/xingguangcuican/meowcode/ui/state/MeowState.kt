package icu.xingguangcuican.meowcode.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import icu.xingguangcuican.meowcode.data.oauth.ApiKey
import icu.xingguangcuican.meowcode.data.oauth.AuthorizationRequest
import icu.xingguangcuican.meowcode.data.oauth.BrowserOAuthTransport
import icu.xingguangcuican.meowcode.data.oauth.MeowApiClient
import icu.xingguangcuican.meowcode.data.oauth.MeowOAuthClient
import icu.xingguangcuican.meowcode.data.oauth.OAuthConfig
import icu.xingguangcuican.meowcode.data.oauth.OAuthSession
import icu.xingguangcuican.meowcode.data.oauth.UnavailableTransport
import icu.xingguangcuican.meowcode.data.oauth.isCallbackUri
import icu.xingguangcuican.meowcode.data.oauth.parseAuthorizationRedirect
import icu.xingguangcuican.meowcode.data.storage.SessionStore
import icu.xingguangcuican.meowcode.model.Account
import icu.xingguangcuican.meowcode.model.AppPreferences
import icu.xingguangcuican.meowcode.model.ApprovalBlock
import icu.xingguangcuican.meowcode.model.ApprovalState
import icu.xingguangcuican.meowcode.model.ChatBlock
import icu.xingguangcuican.meowcode.model.EnvironmentItem
import icu.xingguangcuican.meowcode.model.NoticeTone
import icu.xingguangcuican.meowcode.model.SessionSummary
import icu.xingguangcuican.meowcode.model.SystemNotice
import icu.xingguangcuican.meowcode.model.TerminalCommand
import icu.xingguangcuican.meowcode.model.ThemeSource
import icu.xingguangcuican.meowcode.model.ToolCall
import icu.xingguangcuican.meowcode.model.ToolState
import icu.xingguangcuican.meowcode.model.UserMessage
import icu.xingguangcuican.meowcode.model.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Which destination the shell shows. Constants keep saveable state simple. */
object Destinations {
    const val Chat = "chat"
    const val Terminal = "terminal"
    const val Files = "files"
    const val Workspace = "workspace"
    const val Me = "me"
    val order = listOf(Chat, Terminal, Files, Workspace, Me)
}

/**
 * Shown to the user on the login card. Subset of the client's granted scopes: every
 * entry here has a visible effect, so consent never asks for something invisible.
 */
val RequestedScopes: List<Pair<String, String>> = listOf(
    "openid" to "确认你的身份",
    "profile" to "用户名与显示名",
    "email" to "邮箱地址",
    "groups" to "分组与角色",
    "wallet.read" to "查看余额（不扣费）",
    "apikeys.manage" to "管理你的 API 密钥（新建、查看、删除）",
    "models.read" to "查看可用模型与价格",
    "models.invoke" to "以你的身份调用模型（消耗额度）",
)

enum class LoginPhase(val label: String) {
    SignedOut("未登录"),
    Preparing("准备授权"),
    AwaitingBrowser("等待授权"),
    Exchanging("正在交换令牌"),
    SignedIn("已登录"),
    Failed("登录失败"),
}

/**
 * Everything the screens read and mutate. One owner keeps state coherent.
 *
 * There is one login: [startLogin] runs the real MeowAPI PKCE round-trip through
 * [BrowserOAuthTransport]. Nothing here can fabricate a session.
 */
@Stable
class MeowState(
    private val scope: CoroutineScope,
    val oauthConfig: OAuthConfig = OAuthConfig(),
    /** Null in plain-JVM contexts (tests); the browser login then reports itself unavailable. */
    val browserTransport: BrowserOAuthTransport? = null,
    private val store: SessionStore? = null,
) {
    var destination by mutableStateOf(Destinations.Chat)
    var preferences by mutableStateOf(AppPreferences())

    // ---- Workspaces / sessions ------------------------------------------------
    // Populated by the agent transport when it lands. The app ships no sample rows,
    // so every list here starts empty and the screens show an empty state instead.
    var workspaces by mutableStateOf<List<Workspace>>(emptyList())
    var activeWorkspaceId by mutableStateOf("")
    val activeWorkspace: Workspace?
        get() = workspaces.firstOrNull { it.id == activeWorkspaceId } ?: workspaces.firstOrNull()

    var sessions by mutableStateOf<List<SessionSummary>>(emptyList())
    var activeSessionId by mutableStateOf("")

    // ---- Chat -----------------------------------------------------------------
    val messages = mutableStateListOf<ChatBlock>()
    var composerText by mutableStateOf("")

    // ---- Terminal -------------------------------------------------------------
    val terminal = mutableStateListOf<TerminalCommand>()
    var terminalDraft by mutableStateOf("")

    // ---- Environment ----------------------------------------------------------
    var environment by mutableStateOf<List<EnvironmentItem>>(emptyList())

    // ---- MeowAPI keys (apikeys.manage) ----------------------------------------
    var keys by mutableStateOf<List<ApiKey>>(emptyList())
        private set
    var keysLoading by mutableStateOf(false)
        private set
    var keysError by mutableStateOf<String?>(null)
        private set
    /** Full keys revealed in this session only; the reveal is an audited call. */
    val revealedKeys = mutableStateMapOf<Int, String>()
    var keyDraftName by mutableStateOf("")
    var keyBusy by mutableStateOf(false)
        private set

    // ---- Account / login ------------------------------------------------------
    var phase by mutableStateOf(LoginPhase.SignedOut)
        private set
    var account by mutableStateOf(Account(signedIn = false))
        private set
    var session by mutableStateOf<OAuthSession?>(null)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    var pendingAuthorization by mutableStateOf<AuthorizationRequest?>(null)
        private set
    private var loginJob: Job? = null

    // Non-null from construction so the clients never need a nullable transport; the
    // stand-in throws instead of answering, so no session can come out of it.
    private val transport = browserTransport ?: UnavailableTransport
    private val oauth = MeowOAuthClient(oauthConfig, transport)
    private val api = MeowApiClient(oauthConfig, transport)

    init {
        // Restore a persisted login before the first frame so the card opens signed in.
        // Only trust a token for the issuer that minted it; anything else is dropped.
        store?.readSession()?.takeIf { it.issuer.trimEnd('/') == oauthConfig.issuer.trimEnd('/') }?.let { restored ->
            oauth.restore(restored)
            session = restored
            account = accountFor(restored)
            phase = LoginPhase.SignedIn
            scope.launch { refreshAccountDetails(refreshIfStale = true) }
            loadKeys()
        }
        // Persist every rotation at the moment it happens, never later.
        oauth.onSessionChanged = { updated ->
            session = updated
            store?.writeSession(updated)
        }
    }

    // ---- Chat actions ---------------------------------------------------------

    /** No agent transport yet: this reports the truth instead of staging a fake reply. */
    fun sendMessage() {
        val text = composerText.trim()
        if (text.isEmpty()) return
        messages += UserMessage("u-${messages.size}", text, "现在")
        composerText = ""
        messages += SystemNotice(
            id = "n-${messages.size}",
            title = "尚未连接智能体",
            detail = "消息已保留在本地会话里，但没有发送出去——MeowCode agent 传输层还没有接入。",
            tone = NoticeTone.Info,
        )
    }

    fun toggleToolCard(id: String) {
        val index = messages.indexOfFirst { it.id == id }
        val block = messages.getOrNull(index) as? ToolCall ?: return
        messages[index] = block.copy(expanded = !block.expanded)
    }

    fun respondToApproval(id: String, state: ApprovalState) {
        val index = messages.indexOfFirst { it.id == id }
        val block = messages.getOrNull(index) as? ApprovalBlock ?: return
        messages[index] = block.copy(state = state)
    }

    // ---- Terminal actions -----------------------------------------------------

    /**
     * Records the command as a card but runs nothing: there is no PTY or Termux bridge
     * yet, so the card says so rather than printing invented output.
     */
    fun submitCommand(raw: String = terminalDraft) {
        val command = raw.trim()
        if (command.isEmpty()) return
        terminalDraft = ""
        terminal += TerminalCommand(
            id = "cmd-${terminal.size}-${command.hashCode()}",
            command = command,
            state = ToolState.Pending,
            startedAt = "现在",
            durationMs = null,
            exitCode = null,
            output = "未执行：终端桥接尚未接入，命令没有提交到任何进程。\n",
        )
    }

    fun toggleCommandCard(id: String) {
        val index = terminal.indexOfFirst { it.id == id }
        if (index >= 0) terminal[index] = terminal[index].copy(expanded = !terminal[index].expanded)
    }

    fun clearTerminal() { terminal.clear() }

    // ---- Files / environment --------------------------------------------------

    fun selectWorkspace(id: String) { activeWorkspaceId = id }

    // ---- Preferences ----------------------------------------------------------

    /** [systemDark] is the platform's current setting, used when following the system. */
    fun toggleTheme(systemDark: Boolean) {
        val current = preferences.darkTheme ?: systemDark
        setTheme(!current)
    }

    fun setTheme(dark: Boolean?) {
        preferences = preferences.copy(darkTheme = dark)
        store?.writeDarkTheme(dark)
    }
    fun setColorSource(source: ThemeSource) { preferences = preferences.copy(colorSource = source) }
    fun setReduceMotion(enabled: Boolean) { preferences = preferences.copy(reduceMotion = enabled) }
    fun setFontScale(scale: Float) { preferences = preferences.copy(fontSizeScale = scale) }

    // ---- Login ----------------------------------------------------------------

    /**
     * Real login: generate PKCE + state locally, open MeowAPI in the system browser,
     * and let the custom-scheme callback (delivered by `MeowApp`) feed the code back.
     * The state check and token exchange run in [MeowOAuthClient]. Whether an incoming
     * callback belongs to this attempt is decided there by the `state` echo, so a tab
     * left over from an earlier attempt can neither finish nor kill this login.
     */
    fun startLogin() {
        // Preparing/Exchanging hold no callback slot, so starting again would strand
        // the login that is talking to the server. AwaitingBrowser may start again:
        // that re-opens the tab for the attempt already waiting.
        if (phase == LoginPhase.Preparing || phase == LoginPhase.Exchanging) return
        val browser = browserTransport
        if (browser == null) {
            fail("当前环境无法打开浏览器")
            return
        }
        loginError = null
        oauth.transport = browser
        api.transport = browser
        phase = LoginPhase.Preparing
        loginJob = scope.launch {
            val request = oauth.beginAuthorization()
            pendingAuthorization = request
            phase = LoginPhase.AwaitingBrowser
            val result = browser.authorize(oauthConfig, request.url)
            if (result.error != null) {
                when (result.error) {
                    // A newer attempt (or "取消授权") owns the flow now: leave its UI alone.
                    "superseded", "cancelled" -> Unit
                    else -> fail(result.errorDescription ?: result.error)
                }
                return@launch
            }
            phase = LoginPhase.Exchanging
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { oauth.completeAuthorization(result) }.fold(
                onSuccess = { created ->
                    session = created
                    account = accountFor(created)
                    phase = LoginPhase.SignedIn
                    pendingAuthorization = null
                    refreshAccountDetails()
                    loadKeys()
                },
                onFailure = { error -> fail(oauth.errorMessage(error)) },
            )
        }
    }

    /**
     * Called from the activity when Android delivers the custom-scheme callback.
     *
     * Whether a callback belongs to the login in progress is decided by [CallbackSlot]
     * inside the transport: [BrowserOAuthTransport.deliver] only resumes the attempt
     * whose `state` the redirect echoes, so a code from a cancelled or otherwise older
     * tab is dropped here instead of failing or finishing the login that is running.
     *
     * One case must not go through the suspended waiter: the activity can be recreated
     * (Android process death in the background is the common one) since the tab was
     * opened. Then nothing waits for the callback, but `oauth.inFlight` survives in the
     * process and carries the verifier the code needs — so it is exchanged directly.
     */
    fun handleOAuthCallback(rawUri: String): Boolean {
        val browser = browserTransport ?: return false
        if (!isCallbackUri(rawUri)) return false
        val callback = parseAuthorizationRedirect(rawUri)
        if (browser.deliver(rawUri)) return true
        val pending = oauth.pendingRequest()
        if (pending != null && callback.state == pending.state) {
            loginJob = scope.launch {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { oauth.completeAuthorization(callback) }.fold(
                    onSuccess = { created ->
                        session = created
                        account = accountFor(created)
                        phase = LoginPhase.SignedIn
                        pendingAuthorization = null
                        refreshAccountDetails()
                        loadKeys()
                    },
                    onFailure = { error -> fail(oauth.errorMessage(error)) },
                )
            }
            return true
        }
        if (callback.error != null || callback.code == null) {
            loginError = callback.errorDescription ?: callback.error
            phase = LoginPhase.Failed
            return true
        }
        // A code arrived for a request this process no longer holds (it was killed with
        // the browser in front, or the tab outlived the 10-minute wait). The verifier
        // died with it, so this code cannot be exchanged and the user has to approve
        // once more — say exactly that.
        loginError = "授权回调已收到，但登录状态已丢失，请重新登录"
        phase = LoginPhase.Failed
        return true
    }

    fun cancelLogin() {
        loginJob?.cancel()
        loginJob = null
        oauth.cancel()
        pendingAuthorization = null
        if (phase != LoginPhase.SignedIn) phase = LoginPhase.SignedOut
    }

    fun refreshTokenNow() {
        scope.launch {
            val refreshed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { oauth.refresh() } ?: return@launch
            session = refreshed
            account = account.copy(tokenExpiresAt = expiryLabel(refreshed))
        }
    }

    fun signOut() {
        keys = emptyList()
        revealedKeys.clear()
        keysError = null
        scope.launch { runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { oauth.signOut() } } }
        session = null
        store?.writeSession(null)
        account = Account(signedIn = false)
        phase = LoginPhase.SignedOut
        loginError = null
    }

    // ---- Key manager ----------------------------------------------------------

    fun loadKeys() {
        val current = session ?: return
        if (keysLoading) return
        keysLoading = true
        keysError = null
        scope.launch(Dispatchers.IO) {
            api.loadKeys(current.accessToken).fold(
                onSuccess = { list ->
                    keys = list
                    keysLoading = false
                },
                onFailure = { error ->
                    keysError = error.message ?: "读取密钥失败"
                    keysLoading = false
                },
            )
        }
    }

    fun createKey() {
        val current = session ?: return
        val name = keyDraftName.trim().ifBlank { "手机创建的密钥" }
        if (keyBusy) return
        keyBusy = true
        scope.launch(Dispatchers.IO) {
            val created = runCatching { api.createKey(current.accessToken, name) }.getOrNull()
            if (created != null) {
                revealedKeys[created.id] = api.revealKey(current.accessToken, created.id) ?: created.fullKey ?: ""
                keyDraftName = ""
                loadKeys()
            } else {
                keysError = "创建密钥失败"
            }
            keyBusy = false
        }
    }

    fun revealKey(id: Int) {
        val current = session ?: return
        scope.launch(Dispatchers.IO) {
            api.revealKey(current.accessToken, id)?.let { revealedKeys[id] = it }
                ?: run { keysError = "无法显示该密钥" }
        }
    }

    fun deleteKey(id: Int) {
        val current = session ?: return
        scope.launch(Dispatchers.IO) {
            if (api.deleteKey(current.accessToken, id)) {
                revealedKeys.remove(id)
                keys = keys.filterNot { it.id == id }
            } else {
                keysError = "删除失败"
            }
        }
    }

    fun clearKeyError() { keysError = null }

    /** Identity + wallet for the signed-in card. Silently keeps what is known on failure. */
    private fun refreshAccountDetails(refreshIfStale: Boolean = false) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (refreshIfStale) oauth.bearerForApi()
            val current = session ?: return@launch
            api.loadAccount(current.accessToken).fold(
                onSuccess = { live ->
                    account = account.copy(
                        username = live.info.preferredUsername ?: account.username,
                        displayName = live.info.name ?: live.info.preferredUsername ?: account.displayName,
                        avatarInitials = (live.info.name ?: live.info.preferredUsername)?.take(1),
                        quotaLabel = live.quotaLabel(live.quota) ?: account.quotaLabel,
                        usedLabel = live.quotaLabel(live.usedQuota)?.let { "已用 $it" } ?: account.usedLabel,
                        tokenExpiresAt = expiryLabel(current),
                    )
                },
                onFailure = {
                    account = account.copy(tokenExpiresAt = expiryLabel(current))
                },
            )
        }
    }

    private fun accountFor(created: OAuthSession): Account = Account(
        signedIn = true,
        username = null,
        displayName = "MeowAPI 用户",
        avatarInitials = "喵",
        issuer = created.issuer,
        scope = created.scope,
        tokenExpiresAt = expiryLabel(created),
    )


    private fun expiryLabel(session: OAuthSession): String {
        val expiresAt = session.expiresAt ?: return "未知"
        val minutes = ((expiresAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        return "$minutes 分钟后"
    }

    private fun fail(message: String?) {
        oauth.cancel()
        loginJob = null
        pendingAuthorization = null
        loginError = message ?: "登录失败"
        // A login that already succeeded is never undone by a late attempt's failure.
        // (Its UI is the signed-in card, which would otherwise turn into the error card.)
        if (phase != LoginPhase.SignedIn) phase = LoginPhase.Failed
    }
}

val LocalMeow = staticCompositionLocalOf<MeowState> { error("MeowState not provided") }
