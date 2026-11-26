package icu.xingguangcuican.meowcode.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import icu.xingguangcuican.meowcode.ui.screens.ChatScreen
import icu.xingguangcuican.meowcode.ui.screens.FilesScreen
import icu.xingguangcuican.meowcode.ui.screens.MeScreen
import icu.xingguangcuican.meowcode.ui.screens.SettingsSheet
import icu.xingguangcuican.meowcode.ui.screens.TerminalScreen
import icu.xingguangcuican.meowcode.ui.screens.WorkspaceScreen
import icu.xingguangcuican.meowcode.ui.state.Destinations
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import icu.xingguangcuican.meowcode.ui.theme.MeowTheme
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkNavigationDrawer
import io.github.xingguangcuican6666.abk.ui.components.AbkNavigationItem
import io.github.xingguangcuican6666.abk.ui.components.AbkScaffold
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHost
import io.github.xingguangcuican6666.abk.ui.components.AbkTopBar
import io.github.xingguangcuican6666.abk.ui.components.rememberAbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkShapes
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone

private data class Tab(val id: String, val label: String, val icon: ImageVector)

/** Five equal-weight destinations: 会话 / 终端 / 文件 / 工作区 / 我. */
private val Tabs = listOf(
    Tab(Destinations.Chat, "会话", AbkIcons.Edit),
    Tab(Destinations.Terminal, "终端", AbkIcons.Terminal),
    Tab(Destinations.Files, "文件", AbkIcons.Modules),
    Tab(Destinations.Workspace, "工作区", AbkIcons.Build),
    Tab(Destinations.Me, "我", AbkIcons.Controls),
)

/** Material's medium window class: at or above this width the side rail stays put. */
private val PermanentNavMinWidth = 840.dp

@Composable
fun MeowApp(state: icu.xingguangcuican.meowcode.ui.state.MeowState) {
    MeowTheme(state.preferences) {
        androidx.compose.runtime.CompositionLocalProvider(LocalMeow provides state) {
            val snackbars = rememberAbkSnackbarHostState()
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            var drawerOpen by rememberSaveable { mutableStateOf(false) }
            val dark = state.preferences.darkTheme ?: isSystemInDarkTheme()

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val permanent = maxWidth >= PermanentNavMinWidth
                AbkNavigationDrawer(
                    items = Tabs.map { AbkNavigationItem(it.id, it.label, it.icon) },
                    selectedId = state.destination,
                    onSelect = {
                        state.destination = it
                        // On a phone the drawer is an overlay: picking a destination dismisses it.
                        if (!permanent) drawerOpen = false
                    },
                    open = drawerOpen,
                    onOpenChange = { drawerOpen = it },
                    permanent = permanent,
                    header = { DrawerHeader(state) },
                    footer = {
                        DrawerRow(
                            icon = AbkIcons.Settings,
                            label = "设置",
                            onClick = { settingsOpen = true },
                        )
                        Text(
                            "MeowCode 0.1.0",
                            Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = AbkTheme.colors.inkFaint,
                        )
                    },
                ) {
                    AbkScaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = {
                            AbkTopBar(
                                title = titleFor(state.destination),
                                subtitle = subtitleFor(state),
                                navigationIcon = if (permanent) null else {
                                    { AbkIconButton(AbkIcons.Menu, "打开导航", { drawerOpen = true }) }
                                },
                                actions = {
                                    if (state.session != null) {
                                        AbkIconButton(AbkIcons.Moon, "切换明暗主题", { state.toggleTheme(dark) })
                                    }
                                    AbkIconButton(AbkIcons.Settings, "设置", { settingsOpen = true })
                                },
                            )
                        },
                        snackbarHost = { AbkSnackbarHost(snackbars) },
                        // The permanent rail consumes the start inset itself; the scrim
                        // covers the whole screen, so the content still owns all four sides
                        // in modal mode.
                        contentWindowInsets = if (permanent) {
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)
                        } else {
                            WindowInsets.safeDrawing
                        },
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

/**
 * Brand + current-session context at the top of the drawer. It carries the app name, the
 * signed-in identity (or a sign-in hint) and the active workspace, so the rail has weight
 * instead of being five bare rows.
 */
@Composable
private fun DrawerHeader(state: icu.xingguangcuican.meowcode.ui.state.MeowState) {
    val c = AbkTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Md)) {
        Box(Modifier.size(40.dp).clip(AbkShapes.Medium).background(c.accent.container), contentAlignment = Alignment.Center) {
            AbkIcon(AbkIcons.Terminal, null, tint = c.accent.onContainer, size = 22.dp)
        }
        Column(Modifier.weight(1f)) {
            Text("MeowCode", style = MaterialTheme.typography.titleMedium, color = c.ink)
            Text("agentic coding", style = MaterialTheme.typography.labelSmall, color = c.inkMuted)
        }
    }
    AbkBadge(
        if (state.session != null) "MeowAPI 已登录" else "MeowAPI 未登录",
        tone = if (state.session != null) AbkTone.Success else AbkTone.Neutral,
    )
    state.activeWorkspace?.let { workspace ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            AbkIcon(AbkIcons.Modules, null, tint = c.inkSoft, size = 18.dp)
            Column(Modifier.weight(1f)) {
                Text(workspace.name, style = MaterialTheme.typography.labelMedium, color = c.ink)
                Text(
                    "${workspace.branch} · ${workspace.origin.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = c.inkMuted,
                )
            }
        }
    }
}

/** Low-frequency drawer action; same row language as a destination, but not part of the group. */
@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = AbkTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(AbkShapes.Small).clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp).padding(horizontal = AbkSpacing.Md, vertical = AbkSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Md),
    ) {
        AbkIcon(icon, null, tint = c.inkMuted, size = 22.dp)
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
    }
}

private fun subtitleFor(state: icu.xingguangcuican.meowcode.ui.state.MeowState): String {
    val workspace = state.activeWorkspace?.let { "${it.name} · ${it.branch}" } ?: "未连接工作区"
    return when (state.destination) {
        Destinations.Terminal -> "$workspace · 终端未接入"
        Destinations.Files -> "文件访问未接入"
        Destinations.Workspace -> "Termux 环境 · 仅提供运行时"
        Destinations.Me -> if (state.session != null) "MeowAPI · 已登录" else "MeowAPI · 未登录"
        else -> "$workspace · 未连接智能体"
    }
}
