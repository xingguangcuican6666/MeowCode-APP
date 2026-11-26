package icu.xingguangcuican.meowcode.model

/** Shapes the agent transport will emit. Nothing here is populated until it is wired. */

enum class WorkspaceOrigin(val label: String) {
    Imported("已导入"),
    Termux("Termux 目录"),
}

data class Workspace(
    val id: String,
    val name: String,
    val path: String,
    val branch: String,
    val dirtyFiles: Int,
    val origin: WorkspaceOrigin,
)

/**
 * One turn-visible block of a conversation. Kept as a sealed hierarchy so screens
 * can render types explicitly instead of sniffing roles.
 */
sealed interface ChatBlock {
    val id: String
}

data class UserMessage(override val id: String, val text: String, val at: String) : ChatBlock

data class AssistantMessage(
    override val id: String,
    val text: String,
    val at: String,
    val streaming: Boolean = false,
    /** Short line above the text, e.g. "正在查看 src/providers"。 */
    val activity: String? = null,
) : ChatBlock

data class ToolCall(
    override val id: String,
    val tool: ToolKind,
    /** Human title, e.g. "读取 src/lib/oauth.ts"。 */
    val title: String,
    /** The significant argument, shown in a code block (path, command, pattern). */
    val argument: String?,
    val state: ToolState,
    val result: String,
    val durationMs: Long? = null,
    val diff: List<DiffLine> = emptyList(),
    val expanded: Boolean = false,
) : ChatBlock

data class PlanBlock(
    override val id: String,
    val title: String,
    val steps: List<PlanStep>,
) : ChatBlock

data class ApprovalBlock(
    override val id: String,
    val tool: ToolKind,
    val title: String,
    val detail: String,
    val command: String?,
    val state: ApprovalState,
) : ChatBlock

data class SystemNotice(
    override val id: String,
    val title: String,
    val detail: String,
    val tone: NoticeTone,
) : ChatBlock

enum class NoticeTone { Info, Success, Warning, Danger }

enum class ApprovalState(val label: String) {
    Pending("待确认"), Allowed("已允许"), AlwaysAllowed("总是允许"), Denied("已拒绝"),
}

enum class ToolKind(val label: String) {
    Read("读取"), Edit("编辑"), Write("写入"), Bash("命令"), Glob("查找"),
    Grep("搜索"), Agent("子代理"), Web("网络"), Todo("待办"),
}

enum class ToolState(val label: String) {
    Running("进行中"), Succeeded("完成"), Failed("失败"), Cancelled("已取消"), Pending("等待"),
}

data class DiffLine(val type: DiffType, val text: String)
enum class DiffType { Add, Remove, Context }

data class PlanStep(val index: Int, val title: String, val state: StepState)
enum class StepState { Done, Current, Pending }

/** One conversation row in a session list. */
data class SessionSummary(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: String,
    val workspaceId: String,
    val running: Boolean = false,
)

/** Terminal destination model: one submitted command per card. */
data class TerminalCommand(
    val id: String,
    val command: String,
    val state: ToolState,
    val startedAt: String,
    val durationMs: Long?,
    val exitCode: Int?,
    val output: String,
    val expanded: Boolean = true,
)

/** Termux environment checklist for the Workspaces destination. */
enum class EnvState(val label: String) { Checking("检测中"), Missing("未安装"), Blocked("需授权"), Ready("已就绪"), Failed("失败") }

data class EnvironmentItem(
    val id: String,
    val title: String,
    val description: String,
    val state: EnvState,
    val detail: String? = null,
)

/** MeowAPI account state. Always a real MeowAPI session; there is no offline mode. */
data class Account(
    val signedIn: Boolean,
    val username: String? = null,
    val displayName: String? = null,
    val avatarInitials: String? = null,
    val issuer: String? = null,
    val scope: String? = null,
    val quotaLabel: String? = null,
    val usedLabel: String? = null,
    val tokenExpiresAt: String? = null,
)

/** Theme + preference owned by the host (never persisted by the library). */
data class AppPreferences(
    val darkTheme: Boolean? = null, // null = follow system
    val colorSource: ThemeSource = ThemeSource.DatasetSeed,
    val reduceMotion: Boolean = false,
    val fontSizeScale: Float = 1f,
)

enum class ThemeSource(val label: String) {
    DatasetSeed("默认墨蓝"),
    SystemDynamic("跟随系统壁纸"),
}
