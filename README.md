# UniFeed

本地无限订阅源的 RSS / Atom 阅读器，受 [Folo](https://github.com/RSSNext/Folo) 启发。

**当前最新版本：[v0.1.35](https://github.com/Succh/UniFeed/releases/tag/v0.1.35)**

- GitHub：[https://github.com/Succh/UniFeed/releases/latest/download/app-debug.apk](https://github.com/Succh/UniFeed/releases/latest/download/app-debug.apk)
- Gitee（国内）：[https://gitee.com/succh/UniFeed/releases/download/v0.1.35/app-debug.apk](https://gitee.com/succh/UniFeed/releases/download/v0.1.35/app-debug.apk)

## 功能特点

### 订阅管理

- 无限订阅源：本地存储，数量无上限
- 添加 RSS / Atom 链接，支持自动发现
- 订阅源重命名、删除
- 文件夹分类：创建、重命名、删除，并把订阅源归入文件夹
- OPML 导入 / 导出，保留文件夹结构

### 阅读体验

- 按订阅源或文件夹浏览文章；全部 / 未读 / 收藏筛选
- 全文提取阅读器：打开文章时抽取正文，沉浸阅读
- 图片灯箱：点按正文图片全屏查看，支持双指缩放与双击放大
- 视频占位点按加载：正文中的视频、音频、iframe 先显示封面，点按后再加载，避免列表留白
- 手势：右滑切换已读 / 未读；左滑稍后读；点按星标收藏 / 取消收藏
- 全部标为已读
- 搜索：文章搜标题、摘要与正文；订阅源搜标题与 URL
- Favicon：订阅列表显示站点图标

### RSSHub

- 设置页切换 RSSHub 实例，提供公共镜像快捷选择
- 自定义实例优先；失败时自动回退公共镜像
- 设置页入口打开自建实例指南：[https://github.com/Succh/UniFeed/blob/main/docs/rsshub-selfhost.md](https://github.com/Succh/UniFeed/blob/main/docs/rsshub-selfhost.md)

### 外观与后台

- 皮肤自定义：深浅模式（跟随系统 / 浅色 / 深色）、六套主题色、背景模糊（弱 / 标准 / 强），改动即时预览
- 后台自动刷新：15 分钟 / 30 分钟 / 1 小时，或改为仅手动下拉刷新
- 可限制仅在 Wi-Fi 时后台刷新
- 列表下拉刷新

## 下载安装

当前发布的是 Debug 包 `app-debug.apk`，下载后直接安装即可。

| 渠道 | 直链（点开即下载 APK） |
| --- | --- |
| GitHub Releases（主，始终最新版） | [https://github.com/Succh/UniFeed/releases/latest/download/app-debug.apk](https://github.com/Succh/UniFeed/releases/latest/download/app-debug.apk) |
| Gitee Releases（国内，当前 v0.1.35） | [https://gitee.com/succh/UniFeed/releases/download/v0.1.35/app-debug.apk](https://gitee.com/succh/UniFeed/releases/download/v0.1.35/app-debug.apk) |

GitHub 在国内有时会超时，优先用 Gitee 直链。Gitee 同步步骤见 [https://github.com/Succh/UniFeed/blob/main/docs/gitee-release-setup.md](https://github.com/Succh/UniFeed/blob/main/docs/gitee-release-setup.md)。

签名已固定。从 v0.1.33 起可直接覆盖安装，无需卸载重装。

系统要求：Android 8.0（API 26）及以上。首次安装需允许「未知来源」或「安装未知应用」。

## 构建

### 环境

- JDK 17+
- Android SDK（compileSdk 35）
- Gradle 8.11.1（仓库已带 Wrapper）

### 本地构建

```bash
./gradlew assembleDebug
```

APK 输出：`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions

推送到 `main` 后自动构建 Debug APK，并以 `v0.1.<run_number>` 发布到 GitHub Releases。Release 发布后，若已配置 Gitee Secret，会把同一份 APK 同步到 Gitee Releases。

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose + Material 3
- 数据库：Room（SQLite）
- 后台任务：WorkManager
- 网络：OkHttp
- HTML / RSS 解析：Jsoup
- 图片加载：Coil

## 目录结构

```
.
├── app/src/main/java/com/succh/unifeed/
│   ├── data/          # 订阅、RSS 解析、Room、OPML、后台刷新
│   ├── ui/            # Compose 界面（列表、阅读器、设置、发现）
│   ├── desktop/       # 桌面未读相关
│   ├── MainActivity.kt
│   └── UniFeedApp.kt
├── docs/              # RSSHub 自建指南、Gitee 发版说明
├── .github/workflows/ # 自动构建与 Gitee 同步
├── gradle/wrapper/    # Gradle 8.11.1
└── README.md
```

## 许可证

仓库目前没有 LICENSE 文件。
