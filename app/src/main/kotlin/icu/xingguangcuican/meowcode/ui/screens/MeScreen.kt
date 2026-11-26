package icu.xingguangcuican.meowcode.ui.screens

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import icu.xingguangcuican.meowcode.data.oauth.SimulatedOutcome
import icu.xingguangcuican.meowcode.ui.state.LocalMeow
import icu.xingguangcuican.meowcode.ui.state.LoginPhase
import io.github.xingguangcuican6666.abk.ui.components.AbkAlert
import io.github.xingguangcuican6666.abk.ui.components.AbkAvatar
import io.github.xingguangcuican6666.abk.ui.components.AbkBadge
import io.github.xingguangcuican6666.abk.ui.components.AbkButton
import io.github.xingguangcuican6666.abk.ui.components.AbkButtonStyle
import io.github.xingguangcuican6666.abk.ui.components.AbkCard
import io.github.xingguangcuican6666.abk.ui.components.AbkDivider
import io.github.xingguangcuican6666.abk.ui.components.AbkIconButton
import io.github.xingguangcuican6666.abk.ui.components.AbkKeyValue
import io.github.xingguangcuican6666.abk.ui.components.AbkOption
import io.github.xingguangcuican6666.abk.ui.components.AbkSegmented
import io.github.xingguangcuican6666.abk.ui.components.AbkSelect
import io.github.xingguangcuican6666.abk.ui.components.AbkSettingSwitch
import io.github.xingguangcuican6666.abk.ui.components.AbkSize
import io.github.xingguangcuican6666.abk.ui.components.AbkSnackbarHostState
import io.github.xingguangcuican6666.abk.ui.components.AbkStep
import io.github.xingguangcuican6666.abk.ui.components.AbkStepState
import io.github.xingguangcuican6666.abk.ui.components.AbkSteps
import io.github.xingguangcuican6666.abk.ui.components.AbkText
import io.github.xingguangcuican6666.abk.ui.components.AbkTextField
import io.github.xingguangcuican6666.abk.ui.theme.AbkSpacing
import io.github.xingguangcuican6666.abk.ui.theme.AbkTheme
import io.github.xingguangcuican6666.abk.ui.theme.AbkTone
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcon
import io.github.xingguangcuican6666.abk.ui.icons.AbkIcons
import kotlinx.coroutines.launch

@Composable
fun MeScreen(modifier: Modifier, snackbars: AbkSnackbarHostState, onOpenSettings: () -> Unit) {
    val meow = LocalMeow.current
    val scope = rememberCoroutineScope()
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = AbkSpacing.Lg, vertical = AbkSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(AbkSpacing.Md),
    ) {
        item("account") {
            if (meow.session != null) SignedInCard(snackbars) else if (meow.phase == LoginPhase.Failed) FailedCard() else SignInCard(snackbars)
        }
        if (meow.session != null) {
            item("keys") { KeyManagerCard(snackbars) }
        }
        item("preferences") {
            AbkCard(Modifier.fillMaxWidth(), onClick = onOpenSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        AbkText("外观与偏好", style = MaterialTheme.typography.titleSmall)
                        AbkText("主题、字体缩放、减少动效", style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                    }
                    AbkIcon(AbkIcons.ArrowRight, null, tint = AbkTheme.colors.inkMuted)
                }
            }
        }
        item("about") {
            AbkCard(Modifier.fillMaxWidth()) {
                AbkText("关于", style = MaterialTheme.typography.titleSmall)
                AbkKeyValue("版本", "0.1.0 · stub")
                AbkKeyValue("设计规范", "ABK UI（Lucide · 等权导航 · 系统字体）")
                AbkKeyValue("运行时", "Termux 提供环境，应用本身为原生 Compose")
                AbkDivider()
                AbkText(
                    "登录已连接真实 MeowAPI；对话、命令、文件与环境仍是本地示例，不会执行命令或读取设备文件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = AbkTheme.colors.inkMuted,
                )
            }
        }
        item("signout") {
            if (meow.session != null) {
                AbkButton(
                    {
                        val wasSimulated = meow.account.simulated
                        meow.signOut()
                        scope.launch {
                            snackbars.show(if (wasSimulated) "已退出离线演示" else "已退出登录，令牌已吊销", AbkTone.Neutral)
                            Unit
                        }
                    },
                    Modifier.fillMaxWidth(),
                    style = AbkButtonStyle.Ghost,
                ) { AbkIcon(AbkIcons.Close, null); Spacer(Modifier.width(6.dp)); Text("退出登录") }
            }
        }
    }
}

@Composable
private fun SignInCard(snackbars: AbkSnackbarHostState) {
    val meow = LocalMeow.current
    val phase = meow.phase
    AbkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Md)) {
            AbkAvatar("喵", size = AbkSize.Large)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AbkText("使用 MeowAPI 登录", style = MaterialTheme.typography.titleMedium)
                AbkText("OAuth 2.0 授权码 + PKCE（S256），在系统浏览器里同意，应用内不输入密码。",
                    style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
            }
        }
        AbkKeyValue("服务地址", meow.oauthConfig.issuer)
        AbkKeyValue("客户端", "公开客户端 · PKCE")
        AbkKeyValue("回调地址", meow.oauthConfig.redirectUri)

        AbkText("申请的权限", style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkSoft)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            icu.xingguangcuican.meowcode.ui.state.RequestedScopes.forEach { (scope, why) ->
                Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
                    AbkText(scope, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.accent.color)
                    AbkText(why, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkMuted)
                }
            }
        }

        when (phase) {
            LoginPhase.Preparing, LoginPhase.AwaitingBrowser, LoginPhase.Exchanging -> {
                AbkSteps(
                    listOf(
                        AbkStep("生成 PKCE 与 state", "本地完成", if (phase == LoginPhase.Preparing) AbkStepState.Current else AbkStepState.Complete),
                        AbkStep("在浏览器中同意授权", "MeowAPI 授权页", when (phase) {
                            LoginPhase.AwaitingBrowser -> AbkStepState.Current
                            LoginPhase.Exchanging -> AbkStepState.Complete
                            else -> AbkStepState.Pending
                        }),
                        AbkStep("交换令牌", "code + code_verifier", if (phase == LoginPhase.Exchanging) AbkStepState.Current else AbkStepState.Pending),
                    ),
                )
                meow.pendingAuthorization?.let { pending ->
                    AbkText("授权链接", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkMuted)
                    MonoText(pending.url)
                }
                if (phase == LoginPhase.AwaitingBrowser) {
                    AbkButton(
                        { meow.startLogin() },
                        Modifier.fillMaxWidth(),
                        style = AbkButtonStyle.Secondary,
                    ) { AbkIcon(AbkIcons.Forward, null); Spacer(Modifier.width(6.dp)); Text("在浏览器中打开授权页") }
                }
                AbkButton({ meow.cancelLogin() }, Modifier.fillMaxWidth(), style = AbkButtonStyle.Ghost) { Text("取消授权") }
            }
            else -> {
                AbkButton(
                    { meow.startLogin() },
                    Modifier.fillMaxWidth(),
                    style = AbkButtonStyle.Primary,
                ) { AbkIcon(AbkIcons.ArrowRight, null); Spacer(Modifier.width(6.dp)); Text("使用 MeowAPI 登录") }
                AbkButton({ meow.startSimulatedLogin() }, Modifier.fillMaxWidth(), style = AbkButtonStyle.Outline) {
                    Text("离线演示（模拟令牌）")
                }
                AbkText("模拟结果", style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkSoft)
                AbkSelect(
                    options = SimulatedOutcome.entries.map { AbkOption(it.name, it.label) },
                    selectedId = meow.simulatedOutcome.name,
                    onSelect = { meow.chooseSimulatedOutcome(SimulatedOutcome.valueOf(it)) },
                    label = "离线演示结果",
                )
                AbkAlert(
                    "离线演示",
                    "「离线演示」不会打开浏览器，也不会访问网络；令牌以 sim_ 前缀生成，仅在本次运行中有效。",
                    tone = AbkTone.Info,
                )
            }
        }
    }
}

@Composable
private fun SignedInCard(snackbars: AbkSnackbarHostState) {
    val meow = LocalMeow.current
    val account = meow.account
    val scope = rememberCoroutineScope()
    AbkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Md)) {
            AbkAvatar(account.avatarInitials ?: "喵", size = AbkSize.Large, online = true, statusDescription = "已登录")
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AbkText(account.displayName ?: account.username ?: "MeowAPI 用户", style = MaterialTheme.typography.titleMedium)
                account.username?.let {
                    AbkText("@$it", style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
                }
            }
            AbkBadge(account.source.label, tone = if (account.simulated) AbkTone.Info else AbkTone.Success, dot = !account.simulated)
        }
        AbkDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Xl)) {
            Column {
                AbkText("余额", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkMuted)
                AbkText(account.quotaLabel ?: "—", style = MaterialTheme.typography.headlineSmall)
            }
            Column {
                AbkText("用量", style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkMuted)
                AbkText(account.usedLabel ?: "—", style = MaterialTheme.typography.bodyMedium)
            }
        }
        AbkKeyValue("服务地址", account.issuer.orEmpty())
        AbkKeyValue("权限", account.scope.orEmpty())
        AbkKeyValue("访问令牌", "at_… · ${account.tokenExpiresAt ?: "未知"}")
        Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            AbkButton(
                {
                meow.refreshTokenNow()
                scope.launch { snackbars.show("正在轮换令牌，旧 refresh token 立即失效", AbkTone.Success); Unit }
            },
                style = AbkButtonStyle.Secondary, size = AbkSize.Small,
            ) { AbkIcon(AbkIcons.Refresh, null); Spacer(Modifier.width(4.dp)); Text("刷新令牌") }
        }
        AbkText(
            "at_ 访问令牌本身即可调用 /v1/messages（scope: models.invoke）；令牌经 Android Keystore 加密后保存在本机，退出登录即吊销。",
            style = MaterialTheme.typography.labelSmall,
            color = AbkTheme.colors.inkFaint,
        )
    }
}

/** apikeys.manage surface: list, reveal, create, delete. Only revealed keys leave the masked form. */
@Composable
private fun KeyManagerCard(snackbars: AbkSnackbarHostState) {
    val meow = LocalMeow.current
    val scope = rememberCoroutineScope()
    AbkCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            AbkIcon(AbkIcons.Controls, null, tint = AbkTheme.colors.inkSoft)
            AbkText("API 密钥", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            AbkBadge("apikeys.manage", tone = AbkTone.Neutral)
            AbkIconButton(if (meow.keysLoading) AbkIcons.Pending else AbkIcons.Refresh, "刷新密钥列表", { meow.loadKeys() }, size = AbkSize.Small)
        }

        meow.keysError?.let { AbkAlert("密钥操作失败", it, tone = AbkTone.Danger) }

        if (meow.keys.isEmpty() && !meow.keysLoading) {
            AbkText("还没有密钥。新建一个即可在任意客户端使用。", style = MaterialTheme.typography.bodySmall, color = AbkTheme.colors.inkMuted)
        }
        meow.keys.forEach { key -> KeyRow(key) }

        AbkDivider()
        AbkTextField(
            value = meow.keyDraftName,
            onValueChange = { meow.keyDraftName = it },
            label = "新密钥名称",
            modifier = Modifier.fillMaxWidth(),
            placeholder = "例如：手机端",
            singleLine = true,
        )
        AbkButton(
            { meow.createKey(); scope.launch { snackbars.show("已创建密钥", AbkTone.Success); Unit } },
            Modifier.fillMaxWidth(),
            style = AbkButtonStyle.Primary,
            size = AbkSize.Small,
            enabled = !meow.keyBusy,
        ) { AbkIcon(AbkIcons.Plus, null); Spacer(Modifier.width(4.dp)); Text(if (meow.keyBusy) "创建中…" else "创建密钥") }

        AbkText(
            "完整密钥只在「显示」时从服务器取一次（服务端会记审计日志），本应用不保存 API 密钥。",
            style = MaterialTheme.typography.labelSmall,
            color = AbkTheme.colors.inkFaint,
        )
    }
}

@Composable
private fun KeyRow(key: icu.xingguangcuican.meowcode.data.oauth.ApiKey) {
    val meow = LocalMeow.current
    val clipboard = LocalClipboardManager.current
    val revealed = meow.revealedKeys[key.id]
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Sm)) {
            AbkText(key.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            AbkBadge(if (key.enabled) "启用" else "停用", tone = if (key.enabled) AbkTone.Success else AbkTone.Neutral)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            AbkText(revealed ?: key.maskedKey, style = MaterialTheme.typography.labelSmall, color = AbkTheme.colors.inkMuted)
            if (revealed != null) {
                AbkIconButton(
                    AbkIcons.Copy, "复制密钥",
                    { clipboard.setText(androidx.compose.ui.text.AnnotatedString(revealed)) },
                    size = AbkSize.Small,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AbkSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
            if (revealed == null) {
                AbkButton({ meow.revealKey(key.id) }, style = AbkButtonStyle.Secondary, size = AbkSize.Small) { Text("显示") }
            }
            AbkButton({ meow.deleteKey(key.id) }, style = AbkButtonStyle.Ghost, size = AbkSize.Small) { Text("删除") }
            Spacer(Modifier.weight(1f))
            AbkText(
                if (key.unlimitedQuota) "无限额度" else "剩余 ${key.remainQuota}",
                style = MaterialTheme.typography.labelSmall,
                color = AbkTheme.colors.inkFaint,
            )
        }
    }
}

@Composable
private fun FailedCard() {
    val meow = LocalMeow.current
    AbkCard(Modifier.fillMaxWidth()) {
        AbkAlert("登录未完成", meow.loginError ?: "未知错误", tone = AbkTone.Danger)
        AbkButton({ meow.cancelLogin() }, Modifier.fillMaxWidth(), style = AbkButtonStyle.Soft) { Text("返回并重试") }
    }
}

@Composable
fun SettingsSheet(meow: icu.xingguangcuican.meowcode.ui.state.MeowState, onDismiss: () -> Unit) {
    val darkLabel = when (meow.preferences.darkTheme) {
        true -> "dark"
        false -> "light"
        null -> "system"
    }
    io.github.xingguangcuican6666.abk.ui.components.AbkBottomSheet("外观与偏好", onDismiss) {
        AbkText("主题", style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkSoft)
        AbkSegmented(
            options = listOf(AbkOption("system", "跟随系统"), AbkOption("light", "浅色"), AbkOption("dark", "深色")),
            selectedId = darkLabel,
            onSelect = { meow.setTheme(when (it) { "dark" -> true; "light" -> false; else -> null }) },
        )
        AbkText("配色", style = MaterialTheme.typography.labelMedium, color = AbkTheme.colors.inkSoft)
        AbkSegmented(
            options = icu.xingguangcuican.meowcode.model.ThemeSource.entries.map { AbkOption(it.name, it.label) },
            selectedId = meow.preferences.colorSource.name,
            onSelect = { meow.setColorSource(icu.xingguangcuican.meowcode.model.ThemeSource.valueOf(it)) },
        )
        AbkSelect(
            options = listOf(AbkOption("1.0", "标准"), AbkOption("1.15", "稍大"), AbkOption("1.3", "大"), AbkOption("1.5", "特大")),
            selectedId = scaleId(meow.preferences.fontSizeScale),
            onSelect = { meow.setFontScale(it.toFloat()) },
            label = "字体缩放",
        )
        AbkSettingSwitch(
            "减少装饰动效",
            "关闭加载点、流光等装饰动画；状态仍由文字与图标表达。",
            meow.preferences.reduceMotion,
            { meow.setReduceMotion(it) },
        )
        AbkText(
            "偏好由应用持有，库不持久化；重启后回到默认值。",
            style = MaterialTheme.typography.labelSmall,
            color = AbkTheme.colors.inkFaint,
        )
    }
}

private fun scaleId(scale: Float): String = when (scale) {
    1.15f -> "1.15"
    1.3f -> "1.3"
    1.5f -> "1.5"
    else -> "1.0"
}
