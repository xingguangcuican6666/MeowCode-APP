package icu.xingguangcuican.meowcode.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import icu.xingguangcuican.meowcode.ui.screens.ChatScreen
import icu.xingguangcuican.meowcode.ui.screens.FilesScreen
import icu.xingguangcuican.meowcode.ui.screens.MeScreen
import icu.xingguangcuican.meowcode.ui.screens.SettingsSheet
import icu.xingguangcuican.meowcode.ui.screens.TerminalScreen
import icu.xingguangcuican.meowcode.ui.screens.WorkspaceScreen
import icu.xingguangcuican.meowcode.ui.state.Destinations
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import icu.xingguangcuican.meowcode.ui.theme.MeowTheme
import io.github.xingguangcuican6666.abk.ui.components.AbkBottomNavigation
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkNavigationItem
import io.github.xingguangcuican6666.abk.ui.components.AbkScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHost
import io.github.xingguangcuican6666.abk.ui.components.AbkTopBar
import io.github.xingguangcuican6666.abk.ui.components.rememberAbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons

private data class Tab(val id: String, val label: String, val icon: ImageVector)

/** Five equal-weight destinations: 会话 / 终端 / 文件 / 工作区 / 我. */
private val Tabs = listOf(
    Tab(Destinations.Chat, "会话", AbkIcons.Edit),
    Tab(Destinations.Terminal, "终端", AbkIcons.Terminal),
    Tab(Destinations.Files, "文件", AbkIcons.Modules),
    Tab(Destinations.Workspace, "工作区", AbkIcons.Build),
    Tab(Destinations.Me, "我", AbkIcons.Controls),
)

@Composable
fun MeowApp(state: icu.xingguangcuican.meowcode.ui.state.MeowState) {
    MeowTheme(state.preferences) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMeow provides state) {
            val snackbars = rememberAbkSnackbarHostState()
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            val dark = state.preferences.darkTheme ?: isSystemInDarkTheme()

            AbkScaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    AbkTopBar(
                        title = titleFor(state.destination),
                        subtitle = subtitleFor(state),
                        actions = {
                            if (state.session != null) {
                                AbkIconButton(AbkIcons.Moon, "切换明暗主题", { state.toggleTheme(dark) })
                            }
                            AbkIconButton(AbkIcons.Settings, "设置", { settingsOpen = true })
                        },
                    )
                },
                bottomBar = {
                    AbkBottomNavigation(
                        items = Tabs.map { AbkNavigationItem(it.id, it.label, it.icon) },
                        selectedId = state.destination,
                        onSelect = { state.destination = it },
                    )
                },
                snackbarHost = { AbkSnackbarHost(snackbars) },
            ) { padding ->
                AnimatedContent(
                    targetState = state.destination,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "destination",
                ) { destination ->
                    val modifier = Modifier.fillMaxSize().padding(padding)
                    when (destination) {
                        Destinations.Terminal -> TerminalScreen(modifier, snackbars)
                        Destinations.Files -> FilesScreen(modifier)
                        Destinations.Workspace -> WorkspaceScreen(modifier, snackbars)
                        Destinations.Me -> MeScreen(modifier, snackbars, onOpenSettings = { settingsOpen = true })
                        else -> ChatScreen(modifier, snackbars)
                    }
                }
            }
            if (settingsOpen) SettingsSheet(state, onDismiss = { settingsOpen = false })
        }
    }
}

private fun titleFor(destination: String): String = when (destination) {
    Destinations.Terminal -> "终端"
    Destinations.Files -> "文件"
    Destinations.Workspace -> "工作区"
    Destinations.Me -> "我"
    else -> "MeowCode"
}

private fun subtitleFor(state: icu.xingguangcuican.meowcode.ui.state.MeowState): String {
    val workspace = state.activeWorkspace
    return when (state.destination) {
        Destinations.Terminal -> "${workspace.name} · ${workspace.branch} · 模拟终端"
        Destinations.Files -> "${workspace.name} / ${state.selectedFilePath.substringBeforeLast('/', "根目录")}"
        Destinations.Workspace -> "Termux 环境 · 仅提供运行时"
        Destinations.Me -> if (state.session != null) "MeowAPI · 已登录（模拟会话）" else "MeowAPI · 未登录"
        else -> "${workspace.name} · ${workspace.branch} · 示例数据"
    }
}
