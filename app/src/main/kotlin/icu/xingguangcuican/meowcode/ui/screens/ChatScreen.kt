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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import icu.xingguangcuican.meowcode.model.ApprovalBlock
import icu.xingguangcuican.meowcode.model.ApprovalState
import icu.xingguangcuican.meowcode.model.AssistantMessage
import icu.xingguangcuican.meowcode.model.ChatBlock
import icu.xingguangcuican.meowcode.model.DiffType
import icu.xingguangcuican.meowcode.model.NoticeTone
import icu.xingguangcuican.meowcode.model.PlanBlock
import icu.xingguangcuican.meowcode.model.StepState
import icu.xingguangcuican.meowcode.model.SystemNotice
import icu.xingguangcuican.meowcode.model.ToolCall
import icu.xingguangcuican.meowcode.model.ToolState
import icu.xingguangcuican.meowcode.model.UserMessage
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkChip
import io.github.xingguangcuican6666.abk.ui.components.AbkEmptyState
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkInlineCode
import io.github.xingguangcuican6666.abk.ui.components.AbkLoadingDots
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import kotlinx.coroutines.launch

@Composable
fun ChatScreen(modifier: Modifier, snackbars: AbkSnackbarHostState) {
    val meow = LocalMeow.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(meow.messages.size) {
        if (meow.messages.isNotEmpty()) listState.animateScrollToItem(meow.messages.lastIndex)
    }

    Column(modifier.fillMaxSize()) {
        SessionStrip()
        Box(Modifier.weight(1f)) {
            if (meow.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AbkEmptyState("还没有对话", "输入一句你想让 MeowCode 做的事。", icon = AbkIcons.Edit)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(AbkSpacing.Md),
                ) {
                    items(meow.messages, key = { it.id }) { block ->
                        when (block) {
                            is UserMessage -> UserBubble(block)
                            is AssistantMessage -> AssistantBlock(block)
                            is ToolCall -> ToolCard(block, onToggle = { meow.toggleToolCard(block.id) })
                            is PlanBlock -> PlanCard(block)
                            is ApprovalBlock -> ApprovalCard(
                                block,
                                onRespond = { state ->
                                    meow.respondToApproval(block.id, state)
                                    scope.launch {
                                        snackbars.show(
                                            when (state) {
                                                ApprovalState.Allowed -> "本次已允许（模拟）"
                                                ApprovalState.AlwaysAllowed -> "该项目内总是允许（模拟）"
                                                else -> "已拒绝（模拟）"
                                            },
                                            if (state == ApprovalState.Denied) AbkTone.Warning else AbkTone.Success,
                                        )
                                        Unit
                                    }
                                },
                            )
                            is SystemNotice -> NoticeCard(block)
                        }
                    }
                    item("tail") { SampleFooter() }
                }
            }
        }
        Composer()
    }
}

@Composable
private fun SessionStrip() {
    val meow = LocalMeow.current
    Column(Modifier.fillMaxWidth().padding(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Sm)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            items(meow.sessions, key = { it.id }) { session ->
                AbkChip(
                    label = if (session.running) "● ${session.title}" else session.title,
                    selected = session.id == meow.activeSessionId,
                    onSelectedChange = { meow.activeSessionId = session.id },
                )
            }
        }
    }
}

@Composable
private fun UserBubble(message: UserMessage) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(Modifier.widthIn(max = 340.dp).background(AbkTheme.colors.accent.container, io.github.xingguangcuican6666.abk.ui.theme.AbkShapes.Large)
            .padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = AbkTheme.colors.accent.onContainer,
            )
            Text(message.at, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.accent.onContainer.copy(alpha = .7f))
        }
    }
}

@Composable
private fun AssistantBlock(message: AssistantMessage) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeowMark()
            AbkText("MeowCode", style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkMuted)
            message.activity?.let {
                AbkText("· $it", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkFaint)
            }
        }
        if (message.text.isBlank()) {
            AbkLoadingDots(label = "正在生成回复")
        } else {
            RichText(message.text)
        }
        if (message.streaming && message.text.isNotBlank()) AbkLoadingDots(label = "仍在生成")
    }
}

/**
 * Minimal inline formatter: `code` spans go to the mono family, paragraphs get
 * spacing. Deliberately not a Markdown engine — the stub should not pretend to be one.
 */
@Composable
private fun RichText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
        text.split("\n\n").forEach { paragraph ->
            // Render one paragraph line by line so inline code keeps its own style.
            val parts = paragraph.split("`")
            androidx.compose.foundation.text.BasicText(
                buildAnnotated(parts),
                style = MaterialTheme.typography.bodyLarge.copy(color = AbkTheme.colors.ink, lineHeight = MaterialTheme.typography.bodyLarge.lineHeight),
            )
        }
    }
}

private fun buildAnnotated(parts: List<String>): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) {
                withStyle(SpanStyle(background = Color(0x1A496A78))) { append(part) }
            } else {
                append(part)
            }
        }
    }

@Composable
private fun MeowMark() {
    Box(Modifier.size(26.dp).background(AbkTheme.colors.accent.color, CircleShape), contentAlignment = Alignment.Center) {
        Text("喵", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.accent.onColor)
    }
}

@Composable
private fun ToolCard(call: ToolCall, onToggle: () -> Unit) {
    val tone = when (call.state) {
        ToolState.Succeeded -> AbkTone.Success
        ToolState.Failed -> AbkTone.Danger
        ToolState.Running -> AbkTone.Accent
        else -> AbkTone.Neutral
    }
    AbkCard(Modifier.fillMaxWidth(), style = io.github.xingguangcuican6666.abk.ui.components.AbkCardStyle.Flat) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            Box(
                Modifier.size(34.dp).background(tone.container(), io.github.xingguangcuican6666.abk.ui.theme.AbkShapes.Medium),
                contentAlignment = Alignment.Center,
            ) {
                AbkIcon(toolIcon(call.tool), null, tint = tone.color(), size = 18.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AbkText(call.title, style = MaterialTheme.typography.titleSmall, color = AbkTheme.colors.ink)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    AbkBadge(call.state.label, tone = tone, dot = call.state == ToolState.Running)
                    call.durationMs?.let { AbkText("${it} ms", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkFaint) }
                }
            }
            AbkIconButton(
                if (call.expanded) AbkIcons.Back else AbkIcons.ArrowRight,
                if (call.expanded) "收起输出" else "展开输出",
                onToggle,
                size = AbkSize.Small,
            )
        }
        call.argument?.let { AbkInlineCode(it) }
        if (call.expanded) {
            if (call.diff.isNotEmpty()) DiffList(call) else OutputBlock(call.result)
        }
    }
}

@Composable
private fun DiffList(call: ToolCall) {
    Column(
        Modifier.fillMaxWidth().background(AbkTheme.colors.sunken, io.github.xingguangcuican6666.abk.ui.theme.AbkShapes.Medium)
            .padding(AbkSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        call.diff.forEach { line ->
            val (prefix, color) = when (line.type) {
                DiffType.Add -> "+ " to AbkTheme.colors.success.color
                DiffType.Remove -> "- " to AbkTheme.colors.danger.color
                DiffType.Context -> "  " to AbkTheme.colors.inkMuted
            }
            Text(
                prefix + line.text,
                fontFamily = AbkTheme.monoFont,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Composable
private fun OutputBlock(text: String) {
    Column(
        Modifier.fillMaxWidth().background(AbkTheme.colors.sunken, io.github.xingguangcuican6666.abk.ui.theme.AbkShapes.Medium)
            .padding(AbkSpacing.Sm),
    ) {
        Text(text.ifBlank { "（无输出）" }, fontFamily = AbkTheme.monoFont,
            style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkSoft)
    }
}

@Composable
private fun PlanCard(plan: PlanBlock) {
    AbkCard(Modifier.fillMaxWidth()) {
        AbkText(plan.title, style = MaterialTheme.typography.titleSmall)
        plan.steps.forEach { step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                val icon = when (step.state) {
                    StepState.Done -> AbkIcons.Check
                    StepState.Current -> AbkIcons.Dot
                    StepState.Pending -> AbkIcons.Pending
                }
                AbkIcon(
                    icon, null,
                    tint = when (step.state) {
                        StepState.Done -> AbkTheme.colors.success.color
                        StepState.Current -> AbkTheme.colors.accent.color
                        StepState.Pending -> AbkTheme.colors.inkFaint
                    },
                    size = 16.dp,
                )
                AbkText(
                    "${step.index}. ${step.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (step.state == StepState.Pending) AbkTheme.colors.inkMuted else AbkTheme.colors.ink,
                )
            }
        }
    }
}

@Composable
private fun ApprovalCard(card: ApprovalBlock, onRespond: (ApprovalState) -> Unit) {
    AbkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            AbkIcon(AbkIcons.Warning, null, tint = AbkTheme.colors.warning.color, size = 20.dp)
            AbkText(card.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            AbkBadge(card.state.label, tone = if (card.state == ApprovalState.Pending) AbkTone.Warning else AbkTone.Neutral)
        }
        AbkText(card.detail, style = MaterialTheme.typography.bodyMedium, color = AbkTheme.colors.inkMuted)
        card.command?.let {
            Column(Modifier.fillMaxWidth().background(AbkTheme.colors.sunken, io.github.xingguangcuican6666.abk.ui.theme.AbkShapes.Medium).padding(AbkSpacing.Sm)) {
                Text("\$ $it", fontFamily = AbkTheme.monoFont, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (card.state == ApprovalState.Pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                AbkButton({ onRespond(ApprovalState.Allowed) }, style = AbkButtonStyle.Primary, size = AbkSize.Small) { Text("允许") }
                AbkButton({ onRespond(ApprovalState.AlwaysAllowed) }, style = AbkButtonStyle.Secondary, size = AbkSize.Small) { Text("总是允许") }
                AbkButton({ onRespond(ApprovalState.Denied) }, style = AbkButtonStyle.Ghost, size = AbkSize.Small) { Text("拒绝") }
            }
        }
    }
}

@Composable
private fun NoticeCard(notice: SystemNotice) {
    AbkAlert(
        notice.title,
        notice.detail,
        tone = when (notice.tone) {
            NoticeTone.Info -> AbkTone.Info
            NoticeTone.Success -> AbkTone.Success
            NoticeTone.Warning -> AbkTone.Warning
            NoticeTone.Danger -> AbkTone.Danger
        },
    )
}

@Composable
private fun SampleFooter() {
    Row(Modifier.fillMaxWidth().padding(top = AbkSpacing.Sm), horizontalArrangement = Arrangement.Center) {
        AbkText("— 示例对话结束 —", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkFaint)
    }
}

@Composable
private fun Composer() {
    val meow = LocalMeow.current
    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val tight = with(density) { windowInfo.containerSize.width.toDp() } < 360.dp
    Column(Modifier.fillMaxWidth().padding(horizontal = AbkSpacing.Md, vertical = AbkSpacing.Sm)) {
        AbkTextField(
            value = meow.composerText,
            onValueChange = { meow.composerText = it },
            label = "消息",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "描述你的任务，例如：修复 OAuth 刷新竞态",
            singleLine = false,
            minLines = 1,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { meow.sendMessage() }),
        )
        Spacer(Modifier.height(AbkSpacing.Xs))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Xs)) {
            if (!tight) {
                AbkChip("@ 文件", selected = false, onSelectedChange = {}, icon = AbkIcons.Plus)
                AbkChip("计划模式", selected = false, onSelectedChange = {}, icon = AbkIcons.Controls)
            }
            Spacer(Modifier.weight(1f))
            if (meow.isThinking) {
                AbkButton(meow::stopReply, style = AbkButtonStyle.Outline, size = AbkSize.Small) { Text("停止") }
            }
            AbkButton(
                { meow.sendMessage() },
                style = AbkButtonStyle.Primary,
                size = AbkSize.Small,
                enabled = meow.composerText.isNotBlank(),
            ) {
                AbkIcon(AbkIcons.ArrowRight, null)
                Spacer(Modifier.width(4.dp))
                Text("发送")
            }
        }
    }
}

private fun toolIcon(kind: icu.xingguangcuican.meowcode.model.ToolKind) = when (kind) {
    icu.xingguangcuican.meowcode.model.ToolKind.Read -> AbkIcons.Modules
    icu.xingguangcuican.meowcode.model.ToolKind.Edit -> AbkIcons.Edit
    icu.xingguangcuican.meowcode.model.ToolKind.Write -> AbkIcons.Upload
    icu.xingguangcuican.meowcode.model.ToolKind.Bash -> AbkIcons.Terminal
    icu.xingguangcuican.meowcode.model.ToolKind.Glob -> AbkIcons.Search
    icu.xingguangcuican.meowcode.model.ToolKind.Grep -> AbkIcons.Search
    icu.xingguangcuican.meowcode.model.ToolKind.Agent -> AbkIcons.Cpu
    icu.xingguangcuican.meowcode.model.ToolKind.Web -> AbkIcons.Download
    icu.xingguangcuican.meowcode.model.ToolKind.Todo -> AbkIcons.Check
}

@Composable
private fun AbkTone.color(): Color = AbkTheme.colors.status(this).color
@Composable
private fun AbkTone.container(): Color = AbkTheme.colors.status(this).container
