# MeowCode App — Kickoff

**Kickoff:** 2026-11-26

## Goal

Build a native Android phone app for MeowCode. It should feel like an AI companion
app — UI conversation, tool activity, confirmations — rather than a phone terminal.
A terminal is available as one destination, but command execution is shown in cards.

Termux integration **only provides the runtime environment** (Node.js, git, source
workspace); the MeowCode app itself is a native app and does not run inside Termux.

## First milestone

A runnable **stub app** based on `../rebuild_abk_design`'s ABK design system:

- Five equally weighted destinations: 会话 / 终端 / 文件 / 工作区 / 我.
- Chat: seeded messages, activity cards, approval card and a local composer.
- Terminal: sample command cards and a simulated command submission.
- Files/workspace: sample repo explorer and setup checklist.
- Me: MeowAPI sign-in screen + local simulation of OAuth consent/result states.
- Clear labels distinguish sample / simulation from real actions.
- System default font. No Yozai/WenKai font package.

No backend, real model calls, PTY, file access, or real OAuth requests in this stub.

## References

- `../rebuild_abk_design` — ABK components and interaction rules.
- `../new-api-ach/oauth-server-docs` — MeowAPI OAuth server behavior.
- `.refs/MeowCode` — the CLI's OAuth client, agent architecture, WebUI surface.
- `.refs/claude-code-open` — untrusted/source-inspection reference only; not the app's
  runtime implementation.
- `termux/termux-packages` — environment packages; integration to be designed later.

## Later milestones

1. Real MeowAPI OAuth PKCE via Android Custom Tabs/AppAuth (requires a registered
   native redirect URI) and secure token persistence.
2. Real MeowCode agent transport/protocol with cancellation and streamed events.
3. Termux environment detection/permission/onboarding and runtime bootstrap.
4. Actual file/workspace integration, permissions, scoped storage, import/export.
5. Device UX: large fonts, TalkBack, keyboard, edge-to-edge, battery/background behavior.

## Open decision: OAuth redirect registration

MeowCode CLI registers `http://127.0.0.1:8788/callback`. That loopback callback is not
appropriate for Android browser OAuth. The Android app needs a **custom URI scheme** or
verified app link registered exactly against the public OAuth client. Do not fabricate
or register a new callback unilaterally.

## 2026-11-26 · stub milestone reached

`:app:assembleDebug`, `:app:lintDebug` (0 issues) and `:app:testDebugUnitTest` (7/7) pass.

- APK: `app/build/outputs/apk/debug/app-debug.apk` (~12 MB); nothing was installed or run on a device (no ADB target attached).
- OAuth helpers now do their own RFC 3986 percent coding (`Pkce.kt`) instead of `android.net.Uri`, so the PKCE / state / token contract is unit-tested on the plain JVM; `org.json` comes from a real test dependency.
- Launcher icon is a placeholder vector (caret in a terminal frame); backups are disabled by both `backup_rules.xml` and `data_extraction_rules.xml`.
- Repo is now a git repository with `.gitignore`; nothing has been committed.
- Still open: register `meowcode://oauth/callback` with MeowAPI before a real browser login can complete.

## 2026-11-26 · real MeowAPI login wired

The user (root) registered a dedicated public client, so the redirect decision is closed.

- Client: `cli_44cf45ec8ef543e49edede2e329836fe`, public (PKCE only).
- Redirect URI (exact match): `icu.xingguangcuican.meowcode://oauth/callback`
  — app-owned scheme per RFC 8252 §7.1, not a generic `meowcode://` any app could claim.
- Requested scopes: `openid profile email groups wallet.read models.read models.invoke`.
  `apikeys.manage` is granted to the client but deliberately not requested (no key UI).
- Transport: `BrowserOAuthTransport` opens `/oauth2/authorize` with a plain `ACTION_VIEW`
  intent (no browser dependency added), and `MainActivity` (`launchMode=singleTask`)
  resumes the suspended login from the VIEW intent. No loopback server is involved.
- Token exchange, refresh and the wallet/userinfo reads run off the main thread; refresh
  stays single-flight and the rotated token is persisted inside the lock.
- `SessionStore` seals the session with an AES-GCM key in the Android Keystore; theme
  override is the only other persisted preference. Loss of the keystore key = re-login.
- `Device Code` flow: new-api implements only `authorization_code` and `refresh_token`
  (`controller/oauth_server.go`), so the device flow is not available.

## 2026-11-26 · 产品聚合 + 公开仓库 + CI

The app is now a **product-aggregation client** (the dashboard minus wallet top-up and the
website): it asks for `apikeys.manage` and ships a key-manager screen.

- Scope request is now `openid profile email groups wallet.read apikeys.manage
  models.read models.invoke` (`MeowState.RequestedScopes`, `OAuthConfig.DEFAULT_SCOPE`).
- `MeowApiClient` + 「我 → API 密钥」: list (`GET /oauth2/api/tokens`, PageInfo `data.items`),
  create, reveal (`POST …/tokens/:id/key`, the only plaintext), delete. Dashboard envelope
  `{success,message,data}` is unwrapped; `success:false` throws with the server message.
- **Server-side requirement:** `apikeys.manage` is a sensitive scope. new-api checks the
  client's **Trusted** flag (`pkg/oauthscope`, `ErrClientNotTrusted`) and answers 403
  `insufficient_scope` otherwise — mark `cli_44cf45ec8ef543e49edede2e329836fe` trusted or
  key management stays 403 even though the token carries the scope.
- Memory snapshots copied to `docs/memory/`; license notes in `docs/licenses.md`.
- Repository: public `github.com/xingguangcuican6666/MeowCode-APP`, GPL-3.0 (`LICENSE`).
- `.github/workflows/android.yml`: on push/PR — unit tests, lint, assembleDebug, APK +
  reports uploaded; on `v*` tags a **draft** release with the debug APK. Actions pinned to
  node24-capable majors (checkout v5, setup-java v5, setup-gradle v5, upload-artifact v7,
  download-artifact v8).
- `.gitignore` lost a `data/` line that had swallowed `app/src/main/kotlin/.../data/` and
  broke the first CI run; CI is green as of `82e601c`.

## 2026-11-26 · 移除离线演示

- 删除了 `SimulatedOAuthTransport` / `SimulatedOutcome`、「我 → 离线演示」按钮、模拟结果
  选择器和 `AccountSource`（「模拟会话」徽标）。登录只剩真实 MeowAPI OAuth。
- `MeowState` 现在用 `browserTransport ?: UnavailableTransport` 构造客户端；后者只会返回
  `browser_unavailable` 或抛 `IOException`，不会产生任何会话。
- `OAuthClientTest` 改用文件内的 `FakeTransport`，不再依赖已删除的模拟器。

## 2026-11-26 · 底部栏改为侧边导航

- 导航从等权底部栏换成 `AbkNavigationDrawer`：窄屏（<840dp）为带 scrim 的滑出抽屉，
  宽屏常驻侧栏。抽屉顶部是品牌块、MeowAPI 登录状态与当前工作区，底部是「设置」与版本行。
- 组件先加在设计仓库（`028554e`），再 vendor 到 `:abk-ui`；`abk-ui` 因此多了
  `api(libs.activity.compose)`（预测式返回关闭抽屉）。
- 修复（设计仓库 `828c60b`）：抽屉在 LTR 下从右侧滑入（先「抽搐」到最右、再只偏移半屏），
  因为 `slideInHorizontally` 的起点按布局方向取负值；现由 `offScreen` 按 RTL 镜像。
  行标签统一 `labelMedium`（与 Web `.toc__link` 的 `--fs-sm` 一致），选中行用
  accent 填充 + `accent.onContainer` 前景，并补了按下态。

## 2026-11-26 · 移除应用内全部示例内容

- 删除 `data/MockData.kt`（示例文件树、OAuth 文件差异、示例账号）与 `MeowState` 里的
  `MockShell`、示例工作区/会话/环境、`isThinking`/`stopReply`、`selectedFilePath`/
  `fileSearch`、`checkEnvironment`/`installEnvironmentItem` 以及模拟运行命令的协程。
- 列表全部从空开始：会话、终端、文件、工作区、运行时环境都由未来的传输层填充，未接入时
  显示空状态并写明「未接入」。`WorkspaceOrigin.Demo` 一并删除。
- 发送消息与提交命令不再伪造结果：消息会留在本地并提示「尚未连接智能体」，命令只记成
  `Pending` 卡片并注明没有提交到任何进程。
- 删除随之失效的模型：`Conversation`、`RepoFile`、`FileTreeNode`/`NodeKind`。
- UI 文案去掉「stub / 模拟 / 示例」：终端页徽标改为「未接入运行时」，清空提示、
  空态、页脚、`subtitleFor` 与「我 → 关于」都改为如实说明。

## 2026-11-26 · 回归修复：退出后重新登录失败

- 回归修复（同一提交）：退出登录后再登录失败是因为 `BrowserOAuthTransport` 复用了已完成的
  `CompletableDeferred`；现在每次登录由 `CallbackSlot.begin()` 开启新尝试，回调只唤醒 state
  匹配的那一次（见 `CallbackSlotTest`）。
