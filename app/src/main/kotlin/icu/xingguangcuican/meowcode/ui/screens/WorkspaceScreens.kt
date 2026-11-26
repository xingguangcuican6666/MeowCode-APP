package icu.xingguangcuican.meowcode.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import icu.xingguangcuican.meowcode.model.EnvState
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkListItem
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

/**
 * Files: the workspace file list. There is no file-system access yet, so the screen
 * holds an empty state and says so rather than showing an invented tree.
 */
@Composable
fun FilesScreen(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AbkEmptyState(
            "尚未连接工作区",
            "工作区文件访问将在后续里程碑接入；现在这里不会读取设备上的任何文件。",
            icon = AbkIcons.Modules,
        )
    }
}

/**
 * Workspace: the workspace switcher and the Termux environment checklist. Both lists
 * are populated by the transport when it lands; until then the screen explains what
 * will appear instead of staging a simulated check.
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
        if (meow.workspaces.isEmpty()) {
            item("workspaces-empty") {
                AbkEmptyState("还没有工作区", "工作区接入后会出现在这里。", icon = AbkIcons.Modules)
            }
        } else {
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
        }
        item("env-title") {
            AbkText("运行时环境", style = MaterialTheme.typography.titleMedium)
        }
        if (meow.environment.isEmpty()) {
            item("env-empty") {
                AbkText(
                    "环境检测接入后会列出 Node.js、git 等依赖的状态。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AbkTheme.colors.inkMuted,
                )
            }
        } else {
            items(meow.environment, key = { it.id }) { item ->
                val tone = envTone(item.state)
                AbkListItem(
                    title = item.title,
                    description = item.detail?.let { "${item.description} · $it" } ?: item.description,
                    leading = { AbkIcon(envIcon(item.state), null, tint = AbkTheme.colors.status(tone).color) },
                    trailing = { AbkBadge(item.state.label, tone = tone, dot = item.state == EnvState.Checking) },
                )
            }
        }
        item("env-footer") {
            AbkText(
                "检测与安装尚未接入，不会读写 Termux 或设备存储。",
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

/** Shared code-style text used by output blocks. */
@Composable
internal fun MonoText(text: String, modifier: Modifier = Modifier, color: Color = AbkTheme.colors.inkSoft) {
    Text(text, modifier, fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodySmall, color = color)
}
