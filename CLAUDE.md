# CLAUDE.md

Guidance for Claude Code (and other agents) working in this repository.

## What this is

`meowcode-app` is the **Android app** for MeowCode, a Claude-Code-style agentic coding
CLI (`github.com/xingguangcuican6666/MeowCode`). The phone app drives the same agent
and presents it as an **AI-chat UI** — message cards, tool cards, permission cards —
rather than a terminal emulator. There is a terminal destination, but it is card-ised
too.

The current milestone is a **chat shell with no sample content**: the full UI (side
navigation, five destinations, login) is in place, and every list that the agent would
fill (workspaces, sessions, messages, commands, environment, files) starts empty and
shows an empty state saying it is not connected yet (「未接入」). Login is real; nothing
talks to the agent transport, a shell, or the file system. Never add mock/sample rows or
simulated output back — an unwired surface says so instead.

Termux is **only the runtime environment** for the agent (Node.js, git, the working
tree). The app is a normal native Android app; it is not launched inside Termux and is
not a terminal-emulator front end for it.

## Modules

- `:abk-ui` — a **vendored snapshot** of the ABK design system library from
  `../rebuild_abk_design` (commit `d3b86db`, 2026-11-23). Visual language, tokens,
  icons and components live there and must not be forked inside `:app`.
- `:app` — the MeowCode app: navigation, screens, OAuth client, theme.

`:abk-ui` and `:app` are both released under GPL-3.0 (see `LICENSE`, `docs/licenses.md`).
Login is real (MeowAPI OAuth); everything the agent would do is still unwired.

Vendoring changes (see `docs/vendoring.md`): fonts use the system default
(`AbkFonts.Hand`/`Mono` → `FontFamily.Default`/`Monospace`), the 47 MB Yozai/WenKai
files are not shipped, the `materialkolor` dependency was removed in favour of the
fixed seed tokens from `rebuild_abk_design/assets/css/tokens.css`, and the library's
Robolectric tests were not copied.

## Commands

The Android SDK on this machine is `/run/media/xingguangcuican/数据/NDK/`. Pass it via
`ANDROID_HOME` rather than `local.properties` (the CJK path does not survive
`Properties`' ISO-8859-1 parsing). Never commit `local.properties`, SDKs, keys or
build outputs.

```bash
ANDROID_HOME=/run/media/xingguangcuican/数据/NDK JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
ANDROID_HOME=/run/media/xingguangcuican/数据/NDK JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:lintDebug
ANDROID_HOME=/run/media/xingguangcuican/数据/NDK JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:testDebugUnitTest
```

Toolchain: JDK 17, Gradle wrapper 8.13 (sha256 pinned), AGP 8.11.1, Kotlin/Compose
compiler 2.2.21, Compose BOM 2025.11.01, `compileSdk`/`targetSdk` 36, `minSdk` 26.

## Design rules (from the ABK standard)

- Lucide only, the 30 semantic roles in `AbkIcons`; directional icons mirror for RTL.
- Equal-weight destinations — selection changes the row's surface (the web
  `.toc__link.is-active` accent fill), never its icon, size or hit box.
- ≥48dp touch targets; visible focus; status is never colour-only (icon + text + colour).
- External, undecorated field labels; errors carry a text message and semantics.
- Build/chat cards use ordinary surfaces; tone colours stay on status and progress.
- `AbkTheme` never persists preferences; the host owns state.

## App conventions

- Five destinations, equal weight: **会话 / 终端 / 文件 / 工作区 / 我**, reached through
  `AbkNavigationDrawer`: a slide-out modal below 840dp, a permanent side bar at or above
  it. There is no bottom bar.
- State lives in `MeowState` (`ui/state/`), exposed through a `CompositionLocal`.
  Screens are stateless composables taking data + callbacks wherever practical.
- The app ships **no sample data**. Lists the agent transport will fill start empty and
  render an empty state that says the surface is not connected; do not fabricate rows,
  streaming replies or command output to make a screen look full.
- The OAuth client (`data/oauth/`) implements the real PKCE contract; the browser
  round-trip is `BrowserOAuthTransport` and callback routing is `CallbackSlot`. There is
  no simulated/offline login — never reintroduce one; tokens only come from a real code
  exchange.
- New strings are Chinese by default (the product language); keep technical terms in
  their original spelling.

## Boundaries

Do not commit SDK paths, credentials, signing keys, caches or build outputs. Do not
publish APKs or push branches without an explicit request. Do not re-copy `abk-ui`
from the design repo without re-applying the font/materialkolor patch.
