# F1-1 / F1a Step 2 订阅源排查结论

排查对象：原 `RsshubPresets` 硬编码路由 + `BuiltinFeeds` 官方 RSS。
抓取策略：桌面/移动 UA + Referer 重试、跟随重定向、编码嗅探（UTF-8/GBK）、残缺 XML 尽力解析、RSSHub 多镜像竞速。
复核时间：2026-10-25。默认实例 `https://succh.zone.id`，镜像含 `hub.slarker.me`、`rsshub.rssforever.com`。

## 已修复（路径对照 `/api/namespace` 更正）

| 源名 | 旧 URL | 新 URL | 原因 |
| --- | --- | --- | --- |
| 知乎热榜 | `/zhihu/hotlist` | `/zhihu/hot` | 文档路由更名 |
| B站热门 | `/bilibili/hot` | `/bilibili/popular/all` | 无 hot 路由 |
| B站排行榜 | `/bilibili/ranking/0/3` | `/bilibili/ranking/all` | 参数形态变化 |
| B站番剧表 | `/bilibili/bangumi/timeline` | 改为番剧媒体示例 / 综合热门 | timeline 不存在 |
| GitHub Trending | `/github/trending/daily`、`/github/trending/daily/javascript` | `/github/trending/daily/javascript/en` | since/language/spoken_language 文档示例 |
| 36氪 | `/36kr/motif/` | `/36kr/hot-list`、`/36kr/newsflashes` | motif 无对应路由 |
| 掘金热门 | `/juejin/trending` | `/juejin/trending/all/monthly` | 缺 category/type |
| 游研社 | `/yystv/`、`/yystv/docs` | `/yystv/category/recommend` | 根路径无效；docs 在默认实例 503，recommend 可经镜像拿到 |
| 机核网 | `/gcores/` | `/gcores/news` | 根路径无效 |
| 财新网 | `/caixin/latest` | 保持，确认文档仍有效 | — |
| 人民网 | `/people/paper/` | `/people` | 电子版需页码 |
| 纽约时报 | `/nytimes/daily_briefing_chinese` | 预置改为 `/nytimes/rss/HomePage` | 中文简报公共实例 503 |
| 什么值得买 | `/smzdm/haowen/1` | `/smzdm/haowen/fenlei/shenghuodianqi` | 文档好文分类示例 |
| GitHub Releases | `/github/release/:repo` | 改为 Issues / 仓库列表 | 当前实例无 release 路由，404 |

官方 RSS 侧（2026-10-25 实测 HTTP 200 且为 feed）：少数派、Solidot、爱范儿、IT之家、cnBeta、开源中国、InfoQ 中文、小众软件、异次元、人人都是产品经理、数字尾巴、美团技术、Hacker News、阮一峰、云风、月光博客、王垠、PinSite、Anime News Network、豆瓣书评影评、机核官方 RSS、Kotlin Blog、LWN.net、Phoronix、Steam RDF。解析覆盖 UTF-8/GBK 嗅探、UA/Referer、RDF（Steam）、Atom `rel=alternate`、残缺前言剥离。

纽约时报中文网官方 `https://cn.nytimes.com/rss.html` 本次探测 SSL 超时，保留官方地址；RSSHub 侧改用 `/nytimes/rss/HomePage`。Google Developers Blog 官方 feed 本次连接超时，预置保留。

## Step 2 复核：默认实例实测

| 源名 | URL | 默认实例 | 镜像 | 结论 |
| --- | --- | --- | --- | --- |
| 知乎热榜 / 日报 | `/zhihu/hot` `/zhihu/daily` | 200 FEED | — | 可用 |
| B站综合热门 | `/bilibili/popular/all` | 200 FEED | — | 可用 |
| B站排行榜 | `/bilibili/ranking/all` | 503 HTML | hub.slarker.me 200 | 竞速可用 |
| Hacker News | `/hackernews` | 200 FEED | — | 可用 |
| 掘金 / 36氪 / 少数派 / Solidot | 对应路径 | 200 FEED | — | 可用 |
| 华尔街见闻 / 财联社 / 财新 / 人民网 / BBC | 对应路径 | 200 FEED | — | 可用 |
| 豆瓣热映/即将/口碑 | 对应路径 | 200 FEED | — | 可用 |
| Bangumi 每日放送 | `/bangumi.tv/calendar/today` | 200 FEED | — | 可用 |
| 机核资讯/文章 | `/gcores/news` `/gcores/articles` | 200 FEED | — | 可用 |
| 虎嗅资讯 | `/huxiu/article` | 200 FEED | — | 可用 |
| V2EX 最新 | `/v2ex/topics/latest` | 503 HTML | hub.slarker.me 200 | 竞速可用 |
| 澎湃头条 | `/thepaper/featured` | 503 HTML | hub.slarker.me 200 | 竞速可用 |
| 游研社推荐 | `/yystv/category/recommend` | 503 HTML | hub.slarker.me 200 | 竞速可用 |
| GitHub Issues / Repos | `/github/issue/...` `/github/repos/...` | 200 FEED | — | 可用 |

抓取层把 RSSHub 的 4xx/5xx 与 Welcome 错误页归类为 `RSSHub 路由不可用`，写入 `Feed.lastRefreshError`；订阅列表红色展示该分类。

## 修不好（保留并在 UI 标注，不删除）

| 源名 | URL | 失败原因分类 |
| --- | --- | --- |
| 微博热搜 | `/weibo/search/hot` | RSSHub 路由不可用：公共实例 503/反爬，需微博 Cookie 自建实例 |
| 小红书热门 | `/xiaohongshu/board` | RSSHub 路由不可用：缺少 board_id，公共实例 404 |
| 抖音热门 | `/douyin/hot` | RSSHub 路由不可用：文档无 hot 路由，公共实例 404 |
| Pixiv 日榜 / 周榜 | `/pixiv/ranking/day` `/pixiv/ranking/week` | 需要特定请求头：Pixiv 令牌，公共实例通常 503 |
| B站热搜 | `/bilibili/hot-search` | RSSHub 路由不可用：多镜像均 503，上游反爬 |
| 贴吧帖子 | `/tieba/forum/linux` | RSSHub 路由不可用：当前实例无 tieba 命名空间，404 |
| 纽约时报中文简报 | `/nytimes/daily_briefing_chinese` | RSSHub 路由不可用：公共实例 503；预置已改 HomePage |

这些源仍出现在「添加订阅 → 精选目录 → 暂不可用」分组，条目展示失败分类，点击不可订阅。用户手动添加后刷新失败会把同样的分类写入 `Feed.lastRefreshError`，列表用红色提示。
