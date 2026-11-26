---
name: meowcode-app-ui-principles
description: "MeowCode app UI rules — AI-app style chat (not raw TUI), card-ised terminal, no colour-only state"
metadata:
  node_type: memory
  type: feedback
  originSessionId: bf74a85a-8a67-45f1-8c8e-f4f2337f69ca
  modified: 2026-11-26T04:24:15.257Z
---

The MeowCode phone app must not look like a terminal emulator. The user: "有终端，但是
不知道你见过那种aiapp没有，就是UI化的对话框" — i.e. ChatGPT/Claude-mobile style
conversation UI, with a terminal tab that is equally **UI-ised**.

Concretely:
- Chat = message cards with roles, tool-call cards (name, status chip, collapsible
  output, diff-style highlighting), permission cards (允许 / 总是允许 / 拒绝), and a
  composer with attachment/mention/accessory row — not ANSI scrollback.
- Terminal = a list of command cards (command, phase timeline, exit code, output);
  long-press/menu per card; the raw pty view is a secondary "原始输出" affordance.
- Every state (running/paused/failed) is communicated by icon + text + colour, never
  colour alone; touch targets ≥48dp; bottom navigation destinations are equal weight.
- Use the ABK design language: hand-written-looking typography (here: system default
  font per user request), paper/ink tints, Lucide icons, tonal accent #496a78.

**Why:** user reference point is AI chat apps, and the ABK design is explicitly "简约，
而不空泛" with equal-weight navigation.
**How to apply:** when designing new screens, start from `rebuild_abk_design/index.html`
and the `:catalog` examples (`ExampleScreens.kt`) before inventing layouts; do not add
monospace-heavy chrome outside genuinely code/terminal content.
