---
name: meowcode-app-termux-role
description: "Termux in meowcode-app is the runtime environment for the agent, not the app host"
metadata:
  node_type: memory
  type: project
  originSessionId: bf74a85a-8a67-45f1-8c8e-f4f2337f69ca
  modified: 2026-11-26T04:24:15.225Z
---

In the MeowCode Android app, **Termux only provides the environment** — a POSIX userland
(Node.js, git, the MeowCode CLI, the working tree) that the app drives. The app itself is
a normal native Android app; it must NOT be a Termux addon, a Termux:X11 client, or a
shell that greets you with `$ PS1`.

Target architecture (not yet implemented): an in-app **Termux environment manager**
screen that checks for Termux + `termux-exec`/`RUN_COMMAND` intent availability and
bootstraps `pkg install nodejs git` + `npm i -g` MeowCode, exposing setup as a UI flow.
Upstream reference: `github.com/termux/termux-packages` (packaging), Termux `RUN_COMMAND_INTENT`
and `com.termux.service_execute` requirements (RUN_COMMAND permission, allow-external-apps).

**Why:** user explicitly said "termux 集成只是提供环境，并不是直接在 termux 里面运行 meowcode".
**How to apply:** the stub ships a **模拟环境** (MockTermux) so every setup state
(未安装 / 已安装 / 需授权 / 准备中 / 就绪 / 失败) is reachable in the UI without Termux
installed. Real integration is a later milestone; never present mock state as real.
