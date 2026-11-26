package icu.xingguangcuican.meowcode.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import icu.xingguangcuican.meowcode.data.MockData
import androidx.compose.runtime.mutableStateMapOf
import icu.xingguangcuican.meowcode.data.oauth.ApiKey
import icu.xingguangcuican.meowcode.data.oauth.AuthorizationRequest
import icu.xingguangcuican.meowcode.data.oauth.BrowserOAuthTransport
import icu.xingguangcuican.meowcode.data.oauth.MeowApiClient
import icu.xingguangcuican.meowcode.data.oauth.MeowOAuthClient
import icu.xingguangcuican.meowcode.data.oauth.OAuthConfig
import icu.xingguangcuican.meowcode.data.oauth.OAuthSession
import icu.xingguangcuican.meowcode.data.oauth.SimulatedOAuthTransport
import icu.xingguangcuican.meowcode.data.oauth.SimulatedOutcome
import icu.xingguangcuican.meowcode.data.oauth.UserInfo
import icu.xingguangcuican.meowcode.data.storage.SessionStore
import icu.xingguangcuican.meowcode.model.Account
import icu.xingguangcuican.meowcode.model.AccountSource
import icu.xingguangcuican.meowcode.model.AppPreferences
import icu.xingguangcuican.meowcode.model.ApprovalBlock
import icu.xingguangcuican.meowcode.model.ApprovalState
import icu.xingguangcuican.meowcode.model.AssistantMessage
import icu.xingguangcuican.meowcode.model.ChatBlock
import icu.xingguangcuican.meowcode.model.EnvState
import icu.xingguangcuican.meowcode.model.TerminalCommand
import icu.xingguangcuican.meowcode.model.ThemeSource
import icu.xingguangcuican.meowcode.model.ToolCall
import icu.xingguangcuican.meowcode.model.ToolState
import icu.xingguangcuican.meowcode.model.UserMessage
import icu.xingguangcuican.meowcode.model.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * Login has two modes: [startLogin] is the real MeowAPI round-trip through
 * [BrowserOAuthTransport], and [startSimulatedLogin] drives the same PKCE contract
 * through [SimulatedOAuthTransport]. The account card labels which one is in force.
 */
@Stable
class MeowState(
    private val scope: CoroutineScope,
    val oauthConfig: OAuthConfig = OAuthConfig(),
    val simulatedTransport: SimulatedOAuthTransport = SimulatedOAuthTransport(),
    /** Null in plain-JVM contexts (tests); the browser login then reports itself unavailable. */
    val browserTransport: BrowserOAuthTransport? = null,
    private val store: SessionStore? = null,
) {
    var destination by mutableStateOf(Destinations.Chat)
    var preferences by mutableStateOf(AppPreferences())
    var workspaces by mutableStateOf(MockData.workspaces)
    var activeWorkspaceId by mutableStateOf("ws-meowcode")
    val activeWorkspace: Workspace get() = workspaces.firstOrNull { it.id == activeWorkspaceId } ?: workspaces.first()

    var sessions by mutableStateOf(MockData.sessions)
    var activeSessionId by mutableStateOf("s-1")

    // ---- Chat -----------------------------------------------------------------
    val messages = mutableStateListOf<ChatBlock>().apply { addAll(MockData.conversation) }
    var composerText by mutableStateOf("")
    var isThinking by mutableStateOf(false)
    private var replyJob: Job? = null

    // ---- Terminal -------------------------------------------------------------
    val terminal = mutableStateListOf<TerminalCommand>().apply { addAll(MockData.terminalCommands) }
    var terminalDraft by mutableStateOf("")
    var terminalNotice by mutableStateOf<String?>(null)

    // ---- Files ----------------------------------------------------------------
    var selectedFilePath by mutableStateOf("src/lib/oauth.ts")
    var fileSearch by mutableStateOf("")

    // ---- Environment ----------------------------------------------------------
    var environment by mutableStateOf(MockData.environment)
    var checkingEnvironment by mutableStateOf(false)

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
    var simulatedOutcome by mutableStateOf(SimulatedOutcome.Success)
        private set
    private var loginJob: Job? = null

    private val oauth = MeowOAuthClient(oauthConfig, simulatedTransport)
    private val api = MeowApiClient(oauthConfig, simulatedTransport)

    /** Mirrors which transport began the login, so the card never calls a real session "模拟". */
    private var simulatedAccount: AccountSource = AccountSource.Live

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
        // The terminal tab opens with one running command; complete it after a beat so
        // the "进行中" state is visible but not permanent.
        scope.launch {
            delay(4_000)
            val index = terminal.indexOfFirst { it.state == ToolState.Running }
            if (index >= 0) {
                val running = terminal[index]
                terminal[index] = running.copy(
                    state = ToolState.Succeeded, durationMs = 4_120L, exitCode = 0,
                    output = running.output + "✓ 会话已就绪（模拟）\n",
                )
            }
        }
    }

    // ---- Chat actions ---------------------------------------------------------

    fun sendMessage() {
        val text = composerText.trim()
        if (text.isEmpty() || isThinking) return
        messages += UserMessage("u-${messages.size}", text, "现在")
        composerText = ""
        isThinking = true
        val replyId = "reply-${messages.size}"
        messages += AssistantMessage(id = replyId, text = "", at = "现在", streaming = true, activity = "正在思考（模拟）")
        replyJob = scope.launch {
            delay(700)
            val chunks = MockData.completionReply.chunked(16)
            chunks.forEachIndexed { index, chunk ->
                val position = messages.indexOfFirst { it.id == replyId }
                if (position >= 0) {
                    val current = messages[position] as AssistantMessage
                    messages[position] = current.copy(text = current.text + chunk, streaming = index < chunks.lastIndex)
                }
                delay(40)
            }
            isThinking = false
        }
    }

    fun stopReply() {
        replyJob?.cancel()
        val index = messages.indexOfFirst { (it as? AssistantMessage)?.streaming == true }
        if (index >= 0) {
            val current = messages[index] as AssistantMessage
            messages[index] = current.copy(streaming = false, activity = "已停止")
        }
        isThinking = false
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

    /** Runs a command against [MockShell] and appends its card. Never executes anything. */
    fun submitCommand(raw: String = terminalDraft) {
        val command = raw.trim()
        if (command.isEmpty()) return
        terminalDraft = ""
        val id = "cmd-${terminal.size}-${command.hashCode()}"
        val card = TerminalCommand(
            id = id, command = command, state = ToolState.Running, startedAt = "现在",
            durationMs = null, exitCode = null, output = "$ mock-shell · 输出为本地模拟\n",
        )
        terminal += card
        scope.launch {
            delay(500)
            val result = MockShell.simulate(command)
            val index = terminal.indexOfFirst { it.id == id }
            if (index >= 0) {
                terminal[index] = card.copy(
                    state = if (result.exitCode == 0) ToolState.Succeeded else ToolState.Failed,
                    exitCode = result.exitCode, durationMs = result.durationMs,
                    output = card.output + result.output,
                )
            }
        }
    }

    fun toggleCommandCard(id: String) {
        val index = terminal.indexOfFirst { it.id == id }
        if (index >= 0) terminal[index] = terminal[index].copy(expanded = !terminal[index].expanded)
    }

    fun clearTerminal() { terminal.clear() }

    // ---- Files / environment --------------------------------------------------

    fun selectFile(path: String) { selectedFilePath = path }

    fun selectWorkspace(id: String) { activeWorkspaceId = id }

    fun checkEnvironment() {
        if (checkingEnvironment) return
        checkingEnvironment = true
        environment = environment.map { it.copy(state = EnvState.Checking) }
        scope.launch {
            delay(1_400)
            environment = MockData.environment
            checkingEnvironment = false
        }
    }

    fun installEnvironmentItem(id: String) {
        val index = environment.indexOfFirst { it.id == id }
        if (index < 0) return
        val item = environment[index]
        if (item.state == EnvState.Ready) return
        environment = environment.toMutableList().also { it[index] = item.copy(state = EnvState.Checking, detail = "模拟安装中…") }
        scope.launch {
            delay(1_800)
            val now = environment.indexOfFirst { it.id == id }
            if (now >= 0) {
                environment = environment.toMutableList().also {
                    it[now] = item.copy(state = EnvState.Ready, detail = "模拟完成 · 未修改设备")
                }
            }
        }
    }

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

    fun chooseSimulatedOutcome(outcome: SimulatedOutcome) {
        simulatedOutcome = outcome
        simulatedTransport.outcome = outcome
    }

    /**
     * Real login: generate PKCE + state locally, open MeowAPI in the system browser,
     * and let the custom-scheme callback (delivered by `MeowApp`) feed the code back.
     * The state check and token exchange run in [MeowOAuthClient].
     */
    fun startLogin() {
        if (loginJob?.isActive == true) return
        val browser = browserTransport
        if (browser == null) {
            fail("当前环境无法打开浏览器")
            return
        }
        loginError = null
        oauth.transport = browser
        api.transport = browser
        simulatedAccount = AccountSource.Live
        phase = LoginPhase.Preparing
        loginJob = scope.launch {
            val request = oauth.beginAuthorization()
            pendingAuthorization = request
            phase = LoginPhase.AwaitingBrowser
            val result = browser.authorize(oauthConfig, request.url)
            if (result.error != null) {
                if (result.error == "cancelled") cancelLogin() else fail(result.errorDescription ?: result.error)
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
     * Offline variant for the stub: a real state nonce, a real verifier/challenge pair,
     * a real `state` comparison and token parsing — only the browser and HTTP are faked.
     */
    fun startSimulatedLogin() {
        if (loginJob?.isActive == true) return
        loginError = null
        oauth.transport = simulatedTransport
        api.transport = simulatedTransport
        simulatedAccount = AccountSource.Mock
        phase = LoginPhase.Preparing
        loginJob = scope.launch {
            val request = oauth.beginAuthorization()
            pendingAuthorization = request
            phase = LoginPhase.AwaitingBrowser
            val result = simulatedTransport.authorize(oauthConfig, request.url)
            if (result.error != null) {
                fail(result.errorDescription ?: result.error)
                return@launch
            }
            phase = LoginPhase.Exchanging
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { oauth.completeAuthorization(result) }.fold(
                onSuccess = { created ->
                    session = created
                    account = accountFor(created)
                    phase = LoginPhase.SignedIn
                    pendingAuthorization = null
                },
                onFailure = { error -> fail(oauth.errorMessage(error)) },
            )
        }
    }

    /** Called from the activity when Android delivers the custom-scheme callback. */
    fun handleOAuthCallback(rawUri: String): Boolean {
        val browser = browserTransport ?: return false
        if (!browser.deliver(rawUri)) return false
        if (phase == LoginPhase.SignedOut || phase == LoginPhase.Failed) {
            // Delivered with no pending request (app was killed): nothing to exchange.
            loginError = "授权回调已收到，但登录状态已丢失，请重新登录"
            phase = LoginPhase.Failed
        }
        return true
    }

    fun cancelLogin() {
        loginJob?.cancel()
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
        source = simulatedAccount,
    )


    private fun expiryLabel(session: OAuthSession): String {
        val expiresAt = session.expiresAt ?: return "未知"
        val minutes = ((expiresAt - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
        return "$minutes 分钟后"
    }

    private fun fail(message: String?) {
        oauth.cancel()
        pendingAuthorization = null
        loginError = message ?: "登录失败"
        phase = LoginPhase.Failed
    }
}

/** Deliberately tiny shell used by the terminal stub. */
object MockShell {
    data class Output(val exitCode: Int, val durationMs: Long, val output: String)

    fun simulate(command: String): Output {
        val normalized = command.trim()
        return when {
            normalized.startsWith("git status") ->
                Output(0, 310L, " M src/lib/oauth.ts\n?? src/lib/oauth.refresh.test.ts\n")
            normalized.startsWith("git diff") ->
                Output(0, 420L, "diff --git a/src/lib/oauth.ts b/src/lib/oauth.ts\n" +
                    "   const cur = loadCredentials()\n+  if (!cur?.oauth?.refreshToken) return undefined\n" +
                    "+  // 旋转后立即落盘，避免重试时重放旧 token\n")
            normalized.startsWith("npm run typecheck") ->
                Output(0, 7_600L, "> meowcode@0.4.2 typecheck\n> tsc --noEmit\n\n✓ 无类型错误（模拟输出）\n")
            normalized.startsWith("npm test") || normalized.startsWith("npm run test") ->
                Output(0, 2_100L, "✓ oauth.test.ts (13 tests, 模拟输出)\n")
            normalized.startsWith("npm") ->
                Output(0, 1_100L, "> meowcode@0.4.2\n模拟执行完成，未真正运行 npm。\n")
            normalized.startsWith("ls") ->
                Output(0, 60L, "MEOWCODE.md\npackage.json\nsrc\n")
            normalized.startsWith("pwd") ->
                Output(0, 40L, "/home/meowcode\n")
            normalized == "help" ->
                Output(0, 10L, "演示命令：git status / git diff / npm run typecheck / npm test / ls / pwd / help\n" +
                    "其他命令返回退出码 127。\n")
            else ->
                Output(127, 25L, "$normalized: command not found（模拟环境不执行真实命令）\n")
        }
    }
}

val LocalMeow = staticCompositionLocalOf<MeowState> { error("MeowState not provided") }
