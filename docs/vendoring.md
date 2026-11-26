# Vendoring notes

`:abk-ui` is copied from `../rebuild_abk_design/abk-ui` (base commit
`d3b86db0d1b49d81e03dafc04005502f87007d52`, 2026-11-23), plus one later upstream
commit: `028554e` (2026-11-26) adds
`components/AbkNavigationDrawer.kt` — the side navigation (modal drawer or permanent
rail) — and `api(libs.activity.compose)` for its predictive-back dismiss; `828c60b`
(2026-11-26) fixes its slide-in edge and row styling (start-edge direction for LTR/RTL,
`labelMedium` rows, accent-fill selection). The vendored copy of that file is
byte-identical to upstream.

## Intentional app-specific deltas

- Removed the bundled font resources and changed `AbkFonts.Hand` / `Mono` to
  `FontFamily.Default` / `FontFamily.Monospace`, as requested.
- Replaced the `materialkolor` Tonal Spot seed implementation with explicit
  design-token colors from `../rebuild_abk_design/assets/css/tokens.css` (seed
  #496a78). Android 12+ System color source still derives from Material You.
- Removed test source/resources from the vendored UI library; app-level behavior is
  tested in `:app`.
- The app uses the same namespace and public APIs as upstream. Keep changes to tokens,
  icon geometry, or component behavior in sync with the design project where practical.
