---
name: meowcode-app-design-boundary
description: abk-ui is vendored into meowcode-app as a snapshot; system-default fonts; sync rules
metadata:
  node_type: memory
  type: project
  originSessionId: bf74a85a-8a67-45f1-8c8e-f4f2337f69ca
  modified: 2026-11-26T04:24:04.219Z
---

The MeowCode app follows the **rebuild_abk_design** standard
(`/run/media/xingguangcuican/Project/rebuild_abk_design`, the `:abk-ui` Compose
library at commit `d3b86db`, 2026-11-23). It is **vendored as a copy** into
`meowcode-app/abk-ui/` (package `io.github.xingguangcuican6666.abk.ui`), not consumed
as a published AAR — the design repo has never run Android packaging.

Vendoring modifications (intentional, documented in `meowcode-app/docs/vendoring.md`):
- **Fonts removed** — the user asked for 系统默认字体. `AbkFonts.Hand` and `Mono`
  now return `FontFamily.Default` / `FontFamily.Monospace`; the 47 MB Yozai/WenKai
  TTFs are NOT copied.
- **materialkolor dropped** — `abkSeedColors()` uses the fixed light/dark tokens from
  `rebuild_abk_design/assets/css/tokens.css` (seed #496a78) instead of the
  Tonal Spot generator. `AbkColorSource.System` still means Android 12+ dynamic color.
- Robolectric test fixtures / golden palette vectors are not copied.

Design non-negotiables (from the design repo's CLAUDE.md): Lucide 30 icon roles,
equal-weight bottom navigation, ≥48dp touch targets, no colour-only status, external
undecorated field labels, state owned by the host.

**Why:** the design repo's build boundary must not be broken blindly; product code
must not fork the visual language.
**How to apply:** visual/component changes go upstream to rebuild_abk_design first or
are applied in both; re-copying abk-ui must re-apply the font/materialkolor patch.
