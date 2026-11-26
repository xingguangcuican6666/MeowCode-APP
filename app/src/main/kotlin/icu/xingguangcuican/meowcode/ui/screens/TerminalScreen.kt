package icu.xingguangcuican.meowcode.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import icu.xingguangcuican.meowcode.model.TerminalCommand
import icu.xingguangcuican.meowcode.model.ToolState
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
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
import kotlinx.coroutines.launch

/**
 * Terminal as cards, not a scrollback. Each submitted command is one card with its
 * phase, exit code and output; the raw pty view is a later, secondary affordance.
 */
@Composable
fun TerminalScreen(modifier: Modifier, snackbars: AbkSnackbarHostState) {
    val meow = LocalMeow.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(meow.terminal.size) {
        if (meow.terminal.isNotEmpty()) listState.animateScrollToItem(meow.terminal.lastIndex)
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AbkText("终端", style = MaterialTheme.typography.titleSmall, color = AbkTheme.colors.ink)
            AbkBadge("未接入运行时", tone = AbkTone.Neutral)
            Spacer(Modifier.weight(1f))
            AbkButton(
                { meow.clearTerminal(); scope.launch { snackbars.show("已清空命令卡片") } },
                style = AbkButtonStyle.Ghost,
                size = AbkSize.Small,
                enabled = meow.terminal.isNotEmpty(),
            ) { AbkIcon(AbkIcons.Trash, null); Text("清空") }
        }

        Box(Modifier.weight(1f)) {
            if (meow.terminal.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AbkEmptyState("没有命令", "Termux 运行时桥接接入后才能提交命令。", icon = AbkIcons.Terminal)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(AbkSpacing.Md),
                ) {
                    items(meow.terminal, key = { it.id }) { card -> CommandCard(card, { meow.toggleCommandCard(card.id) }) }
                    item {
                        AbkText(
                            "命令只会记录成卡片，不会提交到任何进程——终端桥接尚未接入。",
                            style = MaterialTheme.typography.labelSmall,
                            color = AbkTheme.colors.inkFaint,
                            modifier = Modifier.padding(vertical = AbkSpacing.Sm),
                        )
                    }
                }
            }
        }
        TerminalComposer()
    }
}

@Composable
private fun CommandCard(card: TerminalCommand, onToggle: () -> Unit) {
    val tone = when (card.state) {
        ToolState.Running -> AbkTone.Accent
        ToolState.Succeeded -> AbkTone.Success
        ToolState.Failed -> AbkTone.Danger
        else -> AbkTone.Neutral
    }
    AbkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            Box(Modifier.size(30.dp).background(tone.containerFor(), AbkShapes.Small), contentAlignment = Alignment.Center) {
                AbkIcon(if (card.state == ToolState.Running) AbkIcons.Refresh else AbkIcons.Terminal, null, tint = tone.colorFor(), size = 16.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("\$ ${card.command}", fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AbkText(card.startedAt, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkFaint)
                    card.durationMs?.let { AbkText("${it} ms", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkFaint) }
                    card.exitCode?.let {
                        AbkText("退出码 $it", style = MaterialTheme.typography.labelSmall,
                            color = if (it == 0) AbkTheme.colors.success.color else AbkTheme.colors.danger.color)
                    }
                }
            }
            AbkBadge(card.state.label, tone = tone, dot = card.state == ToolState.Running)
            AbkIconButton(if (card.expanded) AbkIcons.Back else AbkIcons.ArrowRight,
                if (card.expanded) "收起输出" else "展开输出", onToggle, size = AbkSize.Small)
        }
        if (card.expanded) {
            Column(Modifier.fillMaxWidth().background(AbkTheme.colors.sunken, AbkShapes.Medium).padding(AbkSpacing.Sm)) {
                Text(card.output, fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodySmall,
                    color = AbkTheme.colors.inkSoft)
            }
        }
    }
}

@Composable
private fun TerminalComposer() {
    val meow = LocalMeow.current
    Column(Modifier.fillMaxWidth().padding(horizontal = AbkSpacing.Md, vertical = AbkSpacing.Sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            AbkTextField(
                value = meow.terminalDraft,
                onValueChange = { meow.terminalDraft = it },
                label = "命令",
                modifier = Modifier.weight(1f),
                placeholder = "输入命令",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.None, autoCorrect = false),
                keyboardActions = KeyboardActions(onSend = { meow.submitCommand() }),
                prefix = { Text("$", fontFamily = AbkTheme.monoFont, color = AbkTheme.colors.inkMuted) },
            )
            AbkButton({ meow.submitCommand() }, style = AbkButtonStyle.Primary, enabled = meow.terminalDraft.isNotBlank()) {
                Text("记录")
            }
        }
    }
}

@Composable
private fun AbkTone.colorFor() = AbkTheme.colors.status(this).color

@Composable
private fun AbkTone.containerFor() = AbkTheme.colors.status(this).container
