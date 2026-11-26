package icu.xingguangcuican.meowcode.data

import icu.xingguangcuican.meowcode.model.Account
import icu.xingguangcuican.meowcode.model.AccountSource
import icu.xingguangcuican.meowcode.model.ApprovalBlock
import icu.xingguangcuican.meowcode.model.ApprovalState
import icu.xingguangcuican.meowcode.model.AssistantMessage
import icu.xingguangcuican.meowcode.model.ChatBlock
import icu.xingguangcuican.meowcode.model.Conversation
import icu.xingguangcuican.meowcode.model.DiffLine
import icu.xingguangcuican.meowcode.model.DiffType
import icu.xingguangcuican.meowcode.model.EnvironmentItem
import icu.xingguangcuican.meowcode.model.EnvState
import icu.xingguangcuican.meowcode.model.FileTreeNode
import icu.xingguangcuican.meowcode.model.NodeKind
import icu.xingguangcuican.meowcode.model.NoticeTone
import icu.xingguangcuican.meowcode.model.PlanBlock
import icu.xingguangcuican.meowcode.model.PlanStep
import icu.xingguangcuican.meowcode.model.RepoFile
import icu.xingguangcuican.meowcode.model.SessionSummary
import icu.xingguangcuican.meowcode.model.StepState
import icu.xingguangcuican.meowcode.model.SystemNotice
import icu.xingguangcuican.meowcode.model.TerminalCommand
import icu.xingguangcuican.meowcode.model.ToolCall
import icu.xingguangcuican.meowcode.model.ToolKind
import icu.xingguangcuican.meowcode.model.ToolState
import icu.xingguangcuican.meowcode.model.UserMessage
import icu.xingguangcuican.meowcode.model.Workspace
import icu.xingguangcuican.meowcode.model.WorkspaceOrigin

/**
 * Deterministic sample data for the stub. Nothing here touches a device, a network
 * or a shell; every surface that renders it must label it as 示例.
 */
object MockData {
    const val completionReply: String =
        "可以。`refreshRelayToken()` 已经是单飞（single-flight），但它在 `finally` 里清空 " +
            "`refreshInFlight` 之前没有把新的 refresh token 落盘就返回了。断网重试时会用旧的 " +
            "token 再刷一次，服务端的轮换重放检测会撤销整族令牌。\n\n" +
            "建议把保存动作移动到返回值之前，并在 401 分支里重新读取一次凭据。要我直接改吗？"

    val workspaces: List<Workspace> = listOf(
        Workspace("ws-meowcode", "MeowCode", "~/meowcode", "feat/oauth-refresh", 2, WorkspaceOrigin.Demo),
        Workspace("ws-newapi", "new-api", "~/projects/new-api", "main", 0, WorkspaceOrigin.Demo),
        Workspace("ws-notes", "notes", "~/notes", "main", 1, WorkspaceOrigin.Imported),
    )

    val sessions: List<SessionSummary> = listOf(
        SessionSummary("s-1", "修复 OAuth 刷新竞态", "refreshRelayToken 在断网重试时会…", "刚刚", "ws-meowcode", running = true),
        SessionSummary("s-2", "给 WebUI 加键盘导航", "把 picker 的键盘解码抽成…", "2 小时前", "ws-meowcode"),
        SessionSummary("s-3", "核对 new-api 的 scope 文档", "models.invoke 与 apikeys.manage…", "昨天", "ws-newapi"),
        SessionSummary("s-4", "整理 Termux 引导清单", "需要 pkg install nodejs git…", "3 天前", "ws-notes"),
    )

    val conversation: List<ChatBlock> = listOf(
        UserMessage("m-1", "帮我看看 src/lib/oauth.ts 里的刷新逻辑，断网重试会不会把整族令牌撤销？", "10:24"),
        AssistantMessage("m-2", "我先读一下当前的实现和它的调用点。", "10:24", activity = "正在读取 2 个文件"),
        ToolCall(
            id = "t-1", tool = ToolKind.Read, title = "读取 src/lib/oauth.ts",
            argument = "src/lib/oauth.ts", state = ToolState.Succeeded,
            result = "共 326 行。refreshRelayToken 使用模块级 refreshInFlight 做单飞…",
            durationMs = 31,
        ),
        ToolCall(
            id = "t-2", tool = ToolKind.Grep, title = "搜索 refreshRelayToken 的调用点",
            argument = "refreshRelayToken", state = ToolState.Succeeded,
            result = "3 处：src/providers/index.ts:118、src/app.tsx:742、src/components/LoginPanel.tsx:96",
            durationMs = 44,
        ),
        AssistantMessage(
            id = "m-3",
            text = "目前 `refreshRelayToken()` 在 `finally` 中清空单飞锁，但新的 refresh token 在返回后" +
                "才由调用方保存。并发请求会各自发起一次刷新；服务端轮换后旧 token 立即失效，" +
                "重放会触发整族撤销。",
            at = "10:25",
        ),
        PlanBlock(
            id = "p-1", title = "建议的修复",
            steps = listOf(
                PlanStep(1, "把 saveCredentials 移到 finally 之前", StepState.Current),
                PlanStep(2, "401 分支重新读取凭据再刷新", StepState.Pending),
                PlanStep(3, "补一个覆盖并发刷新的单测", StepState.Pending),
            ),
        ),
        ApprovalBlock(
            id = "a-1", tool = ToolKind.Bash, title = "运行测试",
            detail = "这是模拟的权限确认卡片，不会真的执行命令。",
            command = "npm run test -- oauth", state = ApprovalState.Pending,
        ),
        SystemNotice(
            id = "n-1", title = "示例会话",
            detail = "以上消息与工具调用均为本地样例，未连接模型或终端。",
            tone = NoticeTone.Info,
        ),
    )

    fun newConversation(): Conversation = Conversation("s-1", conversation)

    val terminalCommands: List<TerminalCommand> = listOf(
        TerminalCommand(
            "c-1", "git status --short", ToolState.Succeeded, "10:12", 320L, 0,
            " M src/lib/oauth.ts\n?? src/lib/oauth.refresh.test.ts\n",
        ),
        TerminalCommand(
            "c-2", "npm run typecheck", ToolState.Succeeded, "10:13", 8_420L, 0,
            "> meowcode@0.4.2 typecheck\n> tsc --noEmit\n\n✓ 无类型错误\n",
        ),
        TerminalCommand(
            "c-3", "npm run test -- oauth", ToolState.Failed, "10:15", 2_180L, 1,
            "FAIL src/lib/oauth.test.ts > refreshRelayToken\n" +
                "  AssertionError: expected the rotated token to be persisted\n" +
                "    at oauth.test.ts:88:29\n\nTests 1 failed, 12 passed\n",
        ),
        TerminalCommand(
            "c-4", "npm run dev -- --entry tui", ToolState.Running, "10:16", null, null,
            "meowcode 0.4.2 · mock provider · bypassPermissions off\n等待输入…\n",
        ),
    )

    val files: List<RepoFile> = listOf(
        RepoFile("src/lib/oauth.ts", "9.8 KB", changed = true, language = "TypeScript"),
        RepoFile("src/lib/newapi.ts", "5.1 KB", language = "TypeScript"),
        RepoFile("src/lib/credentials.ts", "2.3 KB", language = "TypeScript"),
        RepoFile("src/providers/anthropic.ts", "12.4 KB", language = "TypeScript"),
        RepoFile("src/components/LoginPanel.tsx", "11.7 KB", changed = true, language = "TSX"),
        RepoFile("MEOWCODE.md", "18.2 KB", language = "Markdown"),
        RepoFile("package.json", "1.9 KB", language = "JSON"),
    )

    val fileTree: List<FileTreeNode> = listOf(
        FileTreeNode("src", "src", NodeKind.Directory, listOf(
            FileTreeNode("lib", "src/lib", NodeKind.Directory, listOf(
                FileTreeNode("oauth.ts", "src/lib/oauth.ts", NodeKind.File),
                FileTreeNode("newapi.ts", "src/lib/newapi.ts", NodeKind.File),
                FileTreeNode("credentials.ts", "src/lib/credentials.ts", NodeKind.File),
            )),
            FileTreeNode("providers", "src/providers", NodeKind.Directory, listOf(
                FileTreeNode("anthropic.ts", "src/providers/anthropic.ts", NodeKind.File),
            )),
        )),
        FileTreeNode("MEOWCODE.md", "MEOWCODE.md", NodeKind.File),
        FileTreeNode("package.json", "package.json", NodeKind.File),
    )

    val oauthFilePreview: List<DiffLine> = listOf(
        DiffLine(DiffType.Context, "export async function refreshRelayToken(): Promise<string | undefined> {"),
        DiffLine(DiffType.Context, "  if (refreshInFlight) return refreshInFlight"),
        DiffLine(DiffType.Context, "  refreshInFlight = (async () => {"),
        DiffLine(DiffType.Remove, "    try {"),
        DiffLine(DiffType.Remove, "      const cur = loadCredentials()"),
        DiffLine(DiffType.Add, "    try {"),
        DiffLine(DiffType.Add, "      const cur = loadCredentials()"),
        DiffLine(DiffType.Add, "      if (!cur?.oauth?.refreshToken) return undefined"),
        DiffLine(DiffType.Add, "      // 旋转后立即落盘，避免重试时重放旧 token"),
        DiffLine(DiffType.Context, "      const r = await refreshTokens(cur.oauth)"),
        DiffLine(DiffType.Context, "      if (!r.ok || !r.oauth) return undefined"),
    )

    val environment: List<EnvironmentItem> = listOf(
        EnvironmentItem("termux", "Termux 应用", "提供 Android 上的 POSIX 运行时", EnvState.Ready, "com.termux 0.118.3"),
        EnvironmentItem("exec", "外部命令授权", "允许本应用向 Termux 提交命令", EnvState.Blocked, "需要在 Termux 中允许 external apps"),
        EnvironmentItem("node", "Node.js 运行时", "运行 MeowCode CLI", EnvState.Ready, "v22.9.0"),
        EnvironmentItem("git", "git", "拉取与查看工作区", EnvState.Ready, "2.47.1"),
        EnvironmentItem("cli", "MeowCode CLI", "全局安装的 meowcode 命令", EnvState.Missing, "未找到"),
    )

    /** Simulated account shown after the local OAuth simulation completes. */
    val signedInAccount: Account = Account(
        signedIn = true, username = "xingguangcuican", displayName = "星光璀璨",
        avatarInitials = "星", issuer = "https://api.xingguangcuican.icu",
        scope = "openid profile email groups wallet.read apikeys.manage models.read models.invoke",
        quotaLabel = "￥ 42.00", usedLabel = "本月已用 ￥ 5.80",
        tokenExpiresAt = "59 分钟后", source = AccountSource.Mock,
    )
}
