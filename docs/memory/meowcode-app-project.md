---
name: meowcode-app-project
description: "MeowCode Android app (/run/media/xingguangcuican/Project/meowcode-app) — stub-first native Compose client for the MeowCode agent, not a Termux wrapper"
metadata:
  node_type: memory
  type: project
  originSessionId: bf74a85a-8a67-45f1-8c8e-f4f2337f69ca
  modified: 2026-11-26T04:24:04.155Z
---

`/run/media/xingguangcuican/Project/meowcode-app` is a **new native Android app** for
MeowCode (the Claude-Code-style agentic CLI, repo `xingguangcuican6666/MeowCode`).
First milestone (2026-11-26) is a **stub app**: complete UI shell + local mock data,
no real agent/backend connectivity yet.

Decisions taken at kickoff:
- Native Jetpack Compose app (Kotlin), packaged UI from the ABK design standard
  ([[meowcode-app-design-boundary]]); Termux only supplies the runtime environment
  ([[meowcode-app-termux-role]]) — the app is NOT launched inside Termux.
- Five equal-weight bottom tabs: 会话 / 终端 / 文件 / 工作区 / 我.
- Chat surface is **UI-ized dialogue** (message cards, tool cards, permission
  dialogs), not a raw TUI transcript — see [[meowcode-app-ui-principles]].
- Terminal tab is also UI-ized: each command is a card with status + output, backed
  by an in-process `MockShell` in the stub.
- Login via MeowAPI (new-api) OAuth 2.0 + PKCE — see [[meowapi-oauth-facts]].
- Reference clones live in `meowcode-app/.refs/` (gitignored): `MeowCode`,
  `claude-code-open`.

Reference source of truth for product behaviour: `MeowCode/src/lib/oauth.ts`,
`newapi.ts`, `src/webui/*` (web UI of the agent), `MEOWCODE.md` (architecture map).
new-api side: `/run/media/xingguangcuican/Project/new-api-ach` (`oauth-server-docs/`,
`controller/oauth_server.go`, `model/oauth_client.go`).

**Why:** stub boundary keeps the first sign-off about UI/UX and contract shape;
wiring the real agent transport comes later.
**How to apply:** when adding features, keep the "示例 / 未连接" labelling until a
real backend exists; never silently swap mock behaviour for network calls.
