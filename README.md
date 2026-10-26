# HostLauncher —— 内嵌并安装运行应用 B 的宿主 A

自有渠道分发的「安装器 + 启动器」。A 内嵌一份完整的 B.apk：首次打开检测到 B 未安装时，
引导用户授予「安装未知应用」权限、调起系统安装、安装完成后立即拉起 B，让用户感觉像在 A 里用。
以后每次打开优先检测 B，已装则直接拉起。

> ⚠️ 本模式依赖「真正安装 B」+「A 启动 B」。非 root / 非系统签名下，**系统的安装确认弹窗无法绕过**，
> 用户必须在弹窗里点一次「安装」。这是 Android 安全模型的限制，不是代码问题。

## 使用前必须做两件事

1. **放入 B.apk**
   把你的应用 B 重命名为 `B.apk`，放到：
   ```
   app/src/main/assets/B.apk
   ```
   （放好后可删除该目录下的 `PUT_B_APK_HERE.txt`。）

2. **填 B 的包名**
   把 B 的真实 `applicationId` 填到两个地方，保持一致：
   - `app/src/main/java/com/example/hostlauncher/InstallManager.kt` → `const val PKG_B`
   - `app/src/main/AndroidManifest.xml` → `<queries><package android:name="..."/></queries>`

## 编译运行

用 Android Studio 打开本目录（根目录有 `settings.gradle.kts`），等 Gradle 同步完成后直接 Run。
首次打开会由 IDE 生成 Gradle wrapper 的 `gradle-wrapper.jar`；
命令行编译可先执行 `gradle wrapper` 生成 wrapper，再 `./gradlew assembleDebug`。

- compileSdk / targetSdk: 34
- minSdk: 26（Android 8.0；「按应用授权未知来源」从这个版本开始）
- 语言: Kotlin，视图绑定(ViewBinding)，无 Compose

## 流程与代码对应

| 你的需求 | 实现位置 |
|---|---|
| 1. 安装包内嵌完整的 B | `app/src/main/assets/B.apk`（`noCompress "apk"` 保证不被压缩） |
| 2. 打开后提示需安装 B | `LauncherActivity.evaluate()` → `showPrompt(...)` |
| 3. 请求安装权限 | `InstallManager.canInstall()` / `unknownSourcesSettingsIntent()`，`LauncherActivity.onInstallClicked()` |
| 4. 授权后调起 B 的安装 | `InstallManager.installEmbeddedApk()`（`PackageInstaller`） |
| 5. 安装完成标记并加载 B | `InstallResultReceiver` → `LauncherActivity.launchBOrError()` |
| 6. 打开优先检测 B | `LauncherActivity.onResume()` → `evaluate()` → `InstallManager.isBInstalled()` |

## 设计要点

- **「是否已安装」以 `PackageManager.getPackageInfo` 为准**，不依赖本地 flag——用户随时可能手动卸载 B。
- **安装结果走 `PackageInstaller` 回调**（成功/取消/失败分别处理），比旧的 `ACTION_INSTALL_PACKAGE` intent 可靠。
- **无缝体验**：安装成功后立刻 `startActivity` 拉起 B 并 `finish()` 宿主，不回桌面。
- **权限返回无缝续流**：从系统设置页返回后在 `ActivityResultLauncher` 回调里自动重试安装，用户不用再点一次。

## 可选增强（按需再做）

- A、B 用**同一签名**：可免卸载覆盖更新 B，便于共享数据、让切换更连贯。
- 给 `LauncherActivity` 配品牌过场页，盖住系统切换时的闪烁。
- B 装好后隐藏其桌面图标（高版本有收紧，需实测）。
