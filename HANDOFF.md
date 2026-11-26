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
or register a new callback unilaterally. The stub's simulated OAuth exists so the UI
can be built before the redirect contract is approved.

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
- `SimulatedOAuthTransport` stays as an explicit 「离线演示」 button for UI work offline.
- `Device Code` flow: new-api implements only `authorization_code` and `refresh_token`
  (`controller/oauth_server.go`), so the device flow is not available.
