# 设计规范与依赖说明

本仓库包含两个部分，各自的许可来源不同：

## `:abk-ui` — vendored 快照

从 `rebuild_abk_design/abk-ui`（commit `d3b86db`，2026-11-23）复制而来，
**没有**附带该仓库的 `LICENSE` 文件；`docs/vendoring.md` 记录了字体与
`materialkolor` 的改动。它按本项目的 GPL-3.0 一并分发。

若设计与上游 ABK 库另有许可（例如 MIT），需要将其许可证文本原样加入
`docs/`，并在此处注明——请与设计仓库维护者确认，不要自行假设。

## 示例数据

`app/src/main/kotlin/.../data/MockData.kt` 中的对话、命令输出、文件列表、
环境项都是手写的中文示例文本，不含第三方受版权保护的内容。

## 图标与字体

- 图标为 ABK 库内的 Lucide 路径数据，其许可证随库分发于
  `abk-ui/src/main/assets/abk/licenses/LICENSE-lucide`（Lucide 采用 ISC 许可）。
- 字体使用系统默认，仓库内不含任何字体文件（vendoring 时已删除 Yozai/WenKai
  字体及其 OFL 许可证文本，见 `docs/vendoring.md`）。

## 第三方库

依赖通过 Gradle Version Catalog 声明（`gradle/libs.versions.toml`）：
AndroidX、Jetpack Compose、Kotlin/协程、JUnit、org.json（仅测试）。各库按其
自身许可证使用，未修改其源码。
