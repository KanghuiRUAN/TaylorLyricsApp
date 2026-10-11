# Taylor Swift 歌词库 (TaylorLyrics)

一个离线、原生 Android 的歌词查询软件（Kotlin + Jetpack Compose）。
内置 Taylor Swift 全曲目资料库（1701 首，含纯文本歌词与逐行同步歌词），无需联网即可浏览、搜索与阅读歌词。

## 功能

- 🎵 **离线全量**：内置 1701 首歌曲、1685 首带歌词、1650 首带逐行同步时间轴，覆盖 146 张专辑。
- 🔍 **搜索**：支持按歌名或歌词全文搜索（大小写不敏感、忽略干扰字符）。
- 📀 **专辑浏览**：按专辑聚合浏览曲目。
- 📖 **歌词查看**：纯文本视图与「逐行（LRC 同步）」视图一键切换。
- 🎨 **歌词内定位**：在歌词页内输入关键词可高亮定位。

## 技术栈

- Kotlin 2.0.20 + Jetpack Compose (Material 3, BOM 2024.09.03)
- AGP 8.5.2 / Gradle 8.9 / JDK 17
- minSdk 24 / compileSdk & targetSdk 35
- 数据：`app/src/main/assets/taylor_lyrics.json`（内置，App 启动时读取）

## 构建 APK（GitHub Actions）

本工程已配置 `.github/workflows/build-apk.yml`，通过 GitHub Actions 自动产出可安装 APK：

1. 把本目录推送到你的 GitHub 仓库（`main` 或 `master` 分支）。
2. Actions 会自动运行 `./gradlew assembleDebug` 与 `assembleRelease`。
3. 构建完成后，从 **Actions** 页面对应运行的 **Artifacts** 下载 `app-debug` / `app-release-unSigned` 即可安装到手机。

> 说明：release 版为未签名 APK，如需正式发布请配置签名；debug 版可直接安装使用。

## 本地构建（需本机装有 JDK 17 + Android SDK）

```bash
chmod +x gradlew
./gradlew assembleDebug
# 产物在 app/build/outputs/apk/debug/app-debug.apk
```

## 目录结构

```
TaylorLyricsApp/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/taylor_lyrics.json     # 内置歌词数据（约 7.6MB）
│       ├── java/com/example/taylorlyrics/
│       │   ├── MainActivity.kt           # 入口 + 三级导航
│       │   ├── data/Models.kt            # 数据模型 + LRC 解析
│       │   ├── data/LyricsRepository.kt  # assets 加载 + 专辑/搜索
│       │   └── ui/Screens.kt             # 主页/专辑/歌词 UI
│       └── res/                          # 图标、主题、字符串
├── .github/workflows/build-apk.yml       # CI 构建
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
└── gradle/wrapper/
```