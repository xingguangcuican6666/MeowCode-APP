# MeowCode App

Android 客户端：把 [MeowCode](https://github.com/xingguangcuican6666/MeowCode)（一个
类似 Claude Code 的智能体编码 CLI）搬进手机，用**对话式 UI**——消息卡片、工具卡片、
权限卡片——呈现智能体的工作过程，而不是终端模拟器。终端页也存在，但是卡片化的。

## 现状

| 部分 | 状态 |
|---|---|
| 界面（会话 / 终端 / 文件 / 工作区 / 我） | ✅ 完整，遵循 ABK 设计规范 |
| 登录 MeowAPI（OAuth 2.0 + PKCE，系统浏览器回跳） | ✅ 真实接入，令牌用 Android Keystore 加密保存 |
| 账号信息、余额 | ✅ 真实 `/oauth2/userinfo`、`/oauth2/api/wallet` |
| API 密钥管理（apikeys.manage） | ✅ 列表 / 显示 / 新建 / 删除 |
| 对话、终端命令、文件、工作区、模型目录 | ⚠️ 尚未接入智能体；页面为空状态并标注「未接入」 |
| 智能体运行时（Termux + Node.js + MeowCode CLI） | ⏳ 下一步 |

**Termux 只提供运行环境**（Node.js、git、工作树）。应用本身是原生 Android 应用，
不会在 Termux 里启动，也不是终端模拟器的前端。

## 构建

需要 JDK 17 与 Android SDK（compileSdk 36）。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew :app:assembleDebug   # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

`ANDROID_HOME` 优先于 `local.properties`（后者按 ISO-8859-1 解析，路径含非 ASCII
字符时会出错）。不要提交 `local.properties`、SDK、签名密钥或构建产物。

推送 `v*` 标签会由 GitHub Actions 构建 debug APK 并创建**草稿** Release。

## 登录

- 公开客户端 + PKCE（S256），无 client secret。
- 回调地址：`icu.xingguangcuican.meowcode://oauth/callback`（应用自有 scheme，
  遵循 RFC 8252 §7.1，不用任何应用都能声明的通用 `meowcode://`）。
- 申请的 scope：`openid profile email groups wallet.read apikeys.manage models.read models.invoke`。
- `at_` 访问令牌本身即可调用模型接口；refresh token 会轮换，因此刷新是单飞的，
  并在锁内立即落盘。

登录只有一条路径：系统浏览器里的 MeowAPI 授权页。应用内不输入密码，也没有离线或
模拟登录——任何令牌都只能来自真实的授权码交换。

## 结构

```
:abk-ui   vendored ABK 设计系统（见 docs/vendoring.md）
:app      导航（侧边栏 / 抽屉）、页面、OAuth 客户端、主题
```

- 状态集中在 `ui/state/MeowState.kt`，通过 `CompositionLocal` 下发。工作区、会话、
  命令、环境列表都由未来的传输层填充，应用不自带样例数据。
- 应用不携带任何示例内容：没有接入的部分显示空状态与「未接入」说明。
- OAuth 在 `data/oauth/`；真实浏览器回跳是 `BrowserOAuthTransport`。
- 设计规则、命令与约定见 [CLAUDE.md](CLAUDE.md)；进度记录见 [HANDOFF.md](HANDOFF.md)。

## 许可

GNU General Public License v3.0，见 [LICENSE](LICENSE)。
