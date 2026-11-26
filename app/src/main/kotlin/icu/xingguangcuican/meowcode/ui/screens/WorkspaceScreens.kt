package icu.xingguangcuican.meowcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import icu.xingguangcuican.meowcode.data.MockData
import icu.xingguangcuican.meowcode.model.DiffLine
import icu.xingguangcuican.meowcode.model.DiffType
import icu.xingguangcuican.meowcode.model.EnvState
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * Files: one searchable list and a read-only detail panel. No file-system access.
 */
@Composable
fun FilesScreen(modifier: Modifier) {
    val meow = LocalMeow.current
    val query = meow.fileSearch.trim()
    val visible = MockData.files.filter { query.isEmpty() || it.path.contains(query, ignoreCase = true) }
    val selected = MockData.files.firstOrNull { it.path == meow.selectedFilePath }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
    ) {
        item("search") {
            AbkTextField(
                value = meow.fileSearch,
                onValueChange = { meow.fileSearch = it },
                label = "搜索文件",
                modifier = Modifier.fillMaxWidth(),
                placeholder = "路径或文件名",
                prefix = { AbkIcon(AbkIcons.Search, null, tint = AbkTheme.colors.inkMuted) },
            )
        }
        items(visible, key = { it.path }) { file ->
            AbkListItem(
                title = file.path.substringAfterLast('/'),
                description = file.path.substringBeforeLast('/', "工作区根目录"),
                leading = {
                    AbkIcon(AbkIcons.Modules, null,
                        tint = if (file.changed) AbkTheme.colors.warning.color else AbkTheme.colors.inkMuted)
                },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (file.changed) AbkBadge("已修改", tone = AbkTone.Warning)
                        AbkText(file.sizeLabel, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkFaint)
                    }
                },
                onClick = { meow.selectFile(file.path) },
            )
        }
        if (visible.isEmpty()) {
            item("empty") { AbkEmptyState("没有匹配的文件", "换一个关键词试试。", icon = AbkIcons.Search) }
        }
        if (selected != null) {
            item("preview") {
                AbkCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                        AbkText(selected.path, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        AbkBadge(selected.language, dot = false)
                    }
                    AbkText("只读示例 · 差异来自对话中的编辑卡片，不读取设备文件。",
                        style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                    DiffSection(MockData.oauthFilePreview)
                }
            }
        }
        item("footer") {
            AbkText(
                "文件内容均为样例数据；工作区文件访问将在后续里程碑接入。",
                style = MaterialTheme.typography.labelSmall,
                color = AbkTheme.colors.inkFaint,
            )
        }
    }
}

/**
 * Workspace: Termux environment checklist + workspace switcher. Termux supplies the
 * runtime only; the app is not launched from inside it, so setup is a UI flow here.
 */
@Composable
fun WorkspaceScreen(modifier: Modifier, snackbars: AbkSnackbarHostState) {
    val meow = LocalMeow.current
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
    ) {
        item("intro") {
            Column(verticalArrangement = Arrangement.spacedBy(AbkSpacing.Xs)) {
                AbkText("工作区", style = MaterialTheme.typography.titleLarge)
                AbkText(
                    "Termux 只提供运行时环境（Node.js、git、源码目录）。MeowCode 是原生应用，不从 Termux 内部启动。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AbkTheme.colors.inkMuted,
                )
            }
        }
        items(meow.workspaces, key = { it.id }) { workspace ->
            val selected = workspace.id == meow.activeWorkspaceId
            AbkListItem(
                title = workspace.name,
                description = "${workspace.path} · ${workspace.branch} · ${workspace.origin.label}",
                leading = {
                    AbkIcon(if (selected) AbkIcons.Check else AbkIcons.Modules, null,
                        tint = if (selected) AbkTheme.colors.accent.color else AbkTheme.colors.inkMuted)
                },
                trailing = { if (workspace.dirtyFiles > 0) AbkBadge("${workspace.dirtyFiles} 处改动", tone = AbkTone.Warning) },
                onClick = { meow.selectWorkspace(workspace.id) },
            )
        }
        item("env-title") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AbkText("运行时环境", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AbkButton(
                    { meow.checkEnvironment() },
                    style = AbkButtonStyle.Ghost,
                    size = AbkSize.Small,
                    enabled = !meow.checkingEnvironment,
                    loading = meow.checkingEnvironment,
                ) {
                    AbkIcon(AbkIcons.Refresh, null)
                    Spacer(Modifier.width(4.dp))
                    Text("检测")
                }
            }
        }
        items(meow.environment, key = { it.id }) { item ->
            val tone = envTone(item.state)
            AbkListItem(
                title = item.title,
                description = item.detail?.let { "${item.description} · $it" } ?: item.description,
                leading = { AbkIcon(envIcon(item.state), null, tint = AbkTheme.colors.status(tone).color) },
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                        AbkBadge(item.state.label, tone = tone, dot = item.state == EnvState.Checking)
                        if (item.state != EnvState.Ready) {
                            AbkIconButton(
                                AbkIcons.Download, "模拟安装 ${item.title}",
                                { meow.installEnvironmentItem(item.id) },
                                size = AbkSize.Small,
                                enabled = item.state != EnvState.Checking,
                            )
                        }
                    }
                },
            )
        }
        item("env-footer") {
            AbkText(
                "以上检测与安装均为模拟，不会读写 Termux 或设备存储。",
                style = MaterialTheme.typography.labelSmall,
                color = AbkTheme.colors.inkFaint,
            )
        }
    }
}

private fun envTone(state: EnvState): AbkTone = when (state) {
    EnvState.Ready -> AbkTone.Success
    EnvState.Missing -> AbkTone.Warning
    EnvState.Blocked, EnvState.Failed -> AbkTone.Danger
    EnvState.Checking -> AbkTone.Accent
}

private fun envIcon(state: EnvState) = when (state) {
    EnvState.Ready -> AbkIcons.Check
    EnvState.Missing -> AbkIcons.Download
    EnvState.Blocked -> AbkIcons.Warning
    EnvState.Checking -> AbkIcons.Refresh
    EnvState.Failed -> AbkIcons.Close
}

/** Shared code-style text used by diff previews and output blocks. */
@Composable
internal fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = AbkTheme.colors.inkSoft) {
    Text(text, modifier, fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
internal fun DiffSection(lines: List<DiffLine>) {
    Column(
        Modifier.fillMaxWidth().background(AbkTheme.colors.sunken, AbkShapes.Medium).padding(AbkSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        lines.forEach { line ->
            val (prefix, color) = when (line.type) {
                DiffType.Add -> "+ " to AbkTheme.colors.success.color
                DiffType.Remove -> "- " to AbkTheme.colors.danger.color
                DiffType.Context -> "  " to AbkTheme.colors.inkMuted
            }
            MonoText(prefix + line.text, color = color)
        }
    }
}
