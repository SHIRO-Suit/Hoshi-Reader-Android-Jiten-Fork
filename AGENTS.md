# Hoshi Reader Android Agent 说明

本仓库是 iOS SwiftUI 应用 Hoshi Reader 的 Android/Kotlin/Jetpack Compose 原生复刻项目。

## iOS 参考源码

- iOS 参考源码 submodule：`reference/Hoshi-Reader-iOS`
- 上游分支：`develop`
- 只把 iOS 项目当作行为参考，不把它当作 Android 架构参考。
- 不要把 Swift 源码复制到 Android app source set 中。
- 查找 iOS 行为时，使用 `reference/Hoshi-Reader-iOS` 下的路径。

常用示例：

```bash
rg "ReaderViewModel" reference/Hoshi-Reader-iOS
rg "LookupEngine" reference/Hoshi-Reader-iOS
```

## 辞典引擎

- 辞典导入和查询应使用 `third_party/hoshidicts-kotlin-bridge`。
- bridge 的 Kotlin JNI 绑定在 `app/src/main/java/de/manhhao/hoshi/HoshiDicts.kt`。
- bridge 的 native 构建参考文件在 `third_party/hoshidicts-kotlin-bridge/app/src/main/cpp`。
- 本项目采用 GPLv3，必须使用 `third_party/hoshidicts-gplv3` 中的 GPLv3 版 `hoshidicts` 分支。
- bridge 仓库自身嵌套的 `app/src/main/cpp/hoshidicts` submodule 指向 `main-mit`；不要构建或链接这份嵌套副本。
- 将 native bridge 接入 Android app 时，需要调整 CMake 路径，让 `hoshidicts_jni.cpp` 链接到 `third_party/hoshidicts-gplv3`。
- bridge 是辞典数据类和 native 入口的事实来源。
- 除非 bridge 缺少必要行为并且已先记录差距，否则不要重新实现 Yomitan 导入、变形还原、查词、媒体读取或样式提取。

常用示例：

```bash
rg "external fun" third_party/hoshidicts-kotlin-bridge
rg "importDictionary" third_party/hoshidicts-kotlin-bridge
git -C third_party/hoshidicts-gplv3 branch --show-current
```

本仓库的 submodule 初始化方式：

```bash
git submodule update --init reference/Hoshi-Reader-iOS third_party/hoshidicts-kotlin-bridge third_party/hoshidicts-gplv3
git -C third_party/hoshidicts-gplv3 submodule update --init --recursive
```

不要在仓库根目录运行通用的 `git submodule update --init --recursive`，除非只是为了调查 bridge 内部的 `main-mit` 嵌套 submodule。

## Android 技术方向

- UI：Jetpack Compose。
- 语言：Kotlin。
- 构建脚本：Kotlin DSL。
- 设计系统：Material 3 + 自定义 Hoshi 主题。真实页面实现后不能停留在默认模板样式，界面应现代、精致。
- 导航：优先使用 AndroidX Navigation Compose；如果当前切片用更简单的局部方案明显足够，也可以保持简单。
- 状态：ViewModel 暴露不可变 UI state，优先使用 `StateFlow`。
- 数据工作：repository 负责文件、数据库、辞典、EPUB、网络等逻辑。
- 异步：使用 Kotlin coroutines；不要阻塞主线程。
- 结构化数据：JSON 优先使用 Kotlin Serialization 或 Moshi。
- 持久化：iOS app 使用大量书籍 sidecar JSON 文件。Android 首版应尽量保留这种形态，除非 Room 能明显简化某个功能。
- Android API 选择应符合 Android 行为，不要机械映射 iOS API。
- 文件导入使用 Android Storage Access Framework 和 app-specific storage。

## 迁移工作流

- 不要尝试一次性把整个 SwiftUI 项目翻译成 Android。
- 每个功能切片开始时，先查看 `reference/Hoshi-Reader-iOS` 下相关 iOS 文件，再总结行为，然后实现 Android 版本。
- iOS singleton 和 `@Observable` 只作为行为参考；Android 中应映射为 repository、ViewModel 和不可变 UI state。
- 按垂直切片推进：model/storage、bookshelf import、reader、dictionary popup、Anki、sync、settings。
- 不要从完整设置页开始。主路径是 bookshelf -> import EPUB -> open reader -> select text -> lookup。
- 不要把 Swift 源码复制到 `app/src/main` 或任何 Android package。

## 阅读器方向

- 在移植 iOS `EPUBKit` 之前，先评估 Readium Kotlin Toolkit 是否适合 EPUB 解析和资源处理。
- 如果 Readium 与 Hoshi 特定行为冲突，可以让 Readium 负责 EPUB resource/model 处理，再用 Compose + Android WebView 实现 Hoshi 阅读器行为。
- Android WebView 可通过 Compose `AndroidView` 嵌入。
- 本地章节内容优先使用 `WebViewAssetLoader` 或 `loadDataWithBaseURL()`。
- 不要为了省事启用宽泛的 file URL 访问，例如 `allowUniversalAccessFromFileURLs`。
- 阅读器实现必须考虑日文竖排、自定义 CSS、字体/主题变化、进度恢复、文本选择、高亮和辞典弹窗定位。

## 集成注意事项

- Anki 行为不能直接照搬 iOS。iOS app 使用 AnkiMobile x-callback 和 AnkiConnect 风格路径；Android 实现前必须调查 AnkiDroid API、intent 或 Android 可用的 AnkiConnect 路径。
- Google Drive sync 必须使用 Android 合适的 Google Sign-In/OAuth/Drive API，不要直接复用 iOS token/keychain 思路。
- Audio 和 Sasayaki 播放切片开始时，应使用 AndroidX Media3/ExoPlayer 做原型验证。

## 许可证

- 本项目采用 GPLv3。保留根目录 `LICENSE`。
- 添加面向发布的元数据时，应保留 Hoshi Reader 和 GPLv3 `hoshidicts` 的许可证/版权说明。
- 添加第三方依赖前，先检查许可证。

## 验证

声明实现完成前，运行相关 Gradle 检查，通常是：

```bash
./gradlew test
./gradlew assembleDebug
```

如果修改影响资源、manifest、UI 或打包，运行 Android lint：

```bash
./gradlew lint
```
