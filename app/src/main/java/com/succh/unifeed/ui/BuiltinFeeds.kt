package com.succh.unifeed.ui

/**
 * 预置订阅源（F1-3）。
 *
 * [url] 官方 RSS 用完整地址；RSSHub 源用相对路径（以 `/` 开头），订阅时拼到当前实例。
 * [source] 取值：`官方 RSS` / `RSSHub`。
 * 修不好的源不会删除，只在 [failureHint] 里标注失败分类，UI 照样展示。
 */
data class BuiltinFeed(
    val title: String,
    val url: String,
    val category: String,
    val source: String,
    val description: String = "",
    val failureHint: String? = null
)

data class BuiltinCategory(
    val name: String,
    val feeds: List<BuiltinFeed>
)

object BuiltinFeeds {

    val categories: List<BuiltinCategory> = listOf(
        BuiltinCategory(
            "科技",
            listOf(
                BuiltinFeed("少数派", "https://sspai.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("Solidot", "https://www.solidot.org/index.rss", "科技", "官方 RSS"),
                BuiltinFeed("爱范儿", "https://www.ifanr.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("IT之家", "https://www.ithome.com/rss/", "科技", "官方 RSS"),
                BuiltinFeed("cnBeta.COM.TW", "https://www.cnbeta.com.tw/backend.php", "科技", "官方 RSS"),
                BuiltinFeed("开源中国", "https://www.oschina.net/news/rss", "科技", "官方 RSS"),
                BuiltinFeed("InfoQ 中文", "https://www.infoq.cn/feed", "科技", "官方 RSS"),
                BuiltinFeed("小众软件", "https://www.appinn.com/feed/", "科技", "官方 RSS"),
                BuiltinFeed("异次元软件世界", "https://www.iplaysoft.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("人人都是产品经理", "https://www.woshipm.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("数字尾巴", "https://www.dgtle.com/rss/dgtle.xml", "科技", "官方 RSS"),
                BuiltinFeed("美团技术团队", "https://tech.meituan.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("Hacker News", "https://hnrss.org/frontpage", "科技", "官方 RSS"),
                BuiltinFeed("极客公园", "https://www.geekpark.net/rss", "科技", "官方 RSS"),
                BuiltinFeed("雷锋网", "https://www.leiphone.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("量子位", "https://www.qbitai.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("蓝点网", "https://www.landiannews.com/feed", "科技", "官方 RSS"),
                BuiltinFeed("GitHub Blog", "https://github.blog/feed/", "科技", "官方 RSS"),
                BuiltinFeed("The Verge", "https://www.theverge.com/rss/index.xml", "科技", "官方 RSS"),
                BuiltinFeed("Ars Technica", "https://feeds.arstechnica.com/arstechnica/index", "科技", "官方 RSS"),
                BuiltinFeed("Wired", "https://www.wired.com/feed/rss", "科技", "官方 RSS"),
                BuiltinFeed("36氪热榜", "/36kr/hot-list", "科技", "RSSHub"),
                BuiltinFeed("36氪快讯", "/36kr/newsflashes", "科技", "RSSHub"),
                BuiltinFeed("掘金本月最热", "/juejin/trending/all/monthly", "科技", "RSSHub"),
                BuiltinFeed("虎嗅资讯", "/huxiu/article", "科技", "RSSHub")
            )
        ),
        BuiltinCategory(
            "新闻",
            listOf(
                BuiltinFeed("纽约时报中文网", "https://cn.nytimes.com/rss.html", "新闻", "官方 RSS"),
                BuiltinFeed("BBC 中文", "https://feeds.bbci.co.uk/zhongwen/simp/rss.xml", "新闻", "官方 RSS"),
                BuiltinFeed("知乎日报", "/zhihu/daily", "新闻", "RSSHub"),
                BuiltinFeed("知乎热榜", "/zhihu/hot", "新闻", "RSSHub"),
                BuiltinFeed("澎湃新闻头条", "/thepaper/featured", "新闻", "RSSHub"),
                BuiltinFeed("华尔街见闻", "/wallstreetcn/news", "新闻", "RSSHub"),
                BuiltinFeed("财联社电报", "/cls/telegraph", "新闻", "RSSHub"),
                BuiltinFeed("财新最新", "/caixin/latest", "新闻", "RSSHub"),
                BuiltinFeed("人民网头条", "/people", "新闻", "RSSHub"),
                BuiltinFeed("纽约时报首页", "/nytimes/rss/HomePage", "新闻", "RSSHub"),
                BuiltinFeed("联合早报中国", "/zaobao/realtime/china", "新闻", "RSSHub"),
                BuiltinFeed("BBC 世界新闻", "/bbc/world", "新闻", "RSSHub")
            )
        ),
        BuiltinCategory(
            "独立博客",
            listOf(
                BuiltinFeed("阮一峰的网络日志", "https://www.ruanyifeng.com/blog/atom.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("云风的 BLOG", "https://blog.codingnow.com/atom.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("月光博客", "https://www.williamlong.info/rss.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("王垠", "https://www.yinwang.org/atom.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("PinSite", "https://immmmm.com/atom.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("卢涛南", "https://www.lutaonan.com/rss.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("Draveness", "https://draveness.me/feed.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("透明创业实验", "https://blog.t9t.io/atom.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("漫游代码", "https://manateelazycat.github.io/feed.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("董伟明", "https://www.dongwm.com/atom.xml", "独立博客", "官方 RSS"),
                BuiltinFeed("少数派 Matrix", "/sspai/matrix", "独立博客", "RSSHub")
            )
        ),
        BuiltinCategory(
            "二次元/ACG",
            listOf(
                BuiltinFeed("Anime News Network", "https://www.animenewsnetwork.com/all/rss.xml", "二次元/ACG", "官方 RSS"),
                BuiltinFeed("ANN 新闻", "https://www.animenewsnetwork.com/news/rss.xml", "二次元/ACG", "官方 RSS"),
                BuiltinFeed("豆瓣最受欢迎影评", "https://movie.douban.com/feed/review/movie", "二次元/ACG", "官方 RSS"),
                BuiltinFeed("豆瓣最受欢迎书评", "https://www.douban.com/feed/review/book", "二次元/ACG", "官方 RSS"),
                BuiltinFeed("Bangumi 每日放送", "/bangumi.tv/calendar/today", "二次元/ACG", "RSSHub"),
                BuiltinFeed("B站综合热门", "/bilibili/popular/all", "二次元/ACG", "RSSHub"),
                BuiltinFeed("B站排行榜", "/bilibili/ranking/all", "二次元/ACG", "RSSHub"),
                BuiltinFeed("豆瓣正在热映", "/douban/movie/playing", "二次元/ACG", "RSSHub"),
                BuiltinFeed("豆瓣即将上映", "/douban/movie/coming", "二次元/ACG", "RSSHub"),
                BuiltinFeed("豆瓣一周口碑榜", "/douban/movie/weekly", "二次元/ACG", "RSSHub"),
                BuiltinFeed("豆瓣热门图书", "/douban/book/rank/fiction", "二次元/ACG", "RSSHub")
            )
        ),
        BuiltinCategory(
            "社区与开发",
            listOf(
                BuiltinFeed("Kotlin Blog", "https://blog.jetbrains.com/kotlin/feed/", "社区与开发", "官方 RSS"),
                BuiltinFeed("Google Blog", "https://blog.google/rss/", "社区与开发", "官方 RSS"),
                BuiltinFeed("LWN.net", "https://lwn.net/headlines/rss", "社区与开发", "官方 RSS"),
                BuiltinFeed("Phoronix", "https://www.phoronix.com/rss.php", "社区与开发", "官方 RSS"),
                BuiltinFeed("V2EX 官方", "https://www.v2ex.com/index.xml", "社区与开发", "官方 RSS"),
                BuiltinFeed("V2EX 最新", "/v2ex/topics/latest", "社区与开发", "RSSHub"),
                BuiltinFeed("V2EX 最热", "/v2ex/topics/hot", "社区与开发", "RSSHub"),
                BuiltinFeed("GitHub Trending", "/github/trending/daily/javascript/en", "社区与开发", "RSSHub"),
                BuiltinFeed("GitHub Issues 示例", "/github/issue/DIYgod/RSSHub", "社区与开发", "RSSHub"),
                BuiltinFeed("机核资讯", "/gcores/news", "社区与开发", "RSSHub"),
                BuiltinFeed("机核文章", "/gcores/articles", "社区与开发", "RSSHub")
            )
        ),
        BuiltinCategory(
            "游戏",
            listOf(
                BuiltinFeed("Steam 新闻", "https://store.steampowered.com/feeds/news.xml", "游戏", "官方 RSS"),
                BuiltinFeed("游研社官方", "https://www.yystv.cn/rss/news", "游戏", "官方 RSS"),
                BuiltinFeed("游研社推荐", "/yystv/category/recommend", "游戏", "RSSHub")
            )
        ),
        BuiltinCategory(
            "财经商业",
            listOf(
                BuiltinFeed("财联社热门", "/cls/hot", "财经商业", "RSSHub"),
                BuiltinFeed("华尔街见闻快讯", "/wallstreetcn/live", "财经商业", "RSSHub"),
                BuiltinFeed("财新周刊", "/caixin/weekly", "财经商业", "RSSHub")
            )
        )
    )

    /** 扁平全集：目录固定，用 val 只算一次（此前每次访问都重新 flatMap，列表重组时会反复触发） */
    val all: List<BuiltinFeed> = categories.flatMap { it.feeds }

    /**
     * F1-1 修不好的源：保留在精选里，但标注失败分类，禁止静默删除。
     * 这些路由在公共实例上长期 403/503/反爬，应用侧无法单独修好。
     */
    val unrepaired: List<BuiltinFeed> = listOf(
        BuiltinFeed(
            title = "微博热搜",
            url = "/weibo/search/hot",
            category = "社交媒体",
            source = "RSSHub",
            failureHint = "RSSHub 路由不可用：公共实例对该路由返回 503/反爬，需要配置微博 Cookie 的自建实例"
        ),
        BuiltinFeed(
            title = "小红书热门",
            url = "/xiaohongshu/board",
            category = "社交媒体",
            source = "RSSHub",
            failureHint = "RSSHub 路由不可用：文档中无 board 路由，公共实例 404"
        ),
        BuiltinFeed(
            title = "抖音热门",
            url = "/douyin/hot",
            category = "社交媒体",
            source = "RSSHub",
            failureHint = "RSSHub 路由不可用：需要登录态/反爬，公共实例无法抓取"
        ),
        BuiltinFeed(
            title = "Pixiv 日榜",
            url = "/pixiv/ranking/day",
            category = "二次元/ACG",
            source = "RSSHub",
            failureHint = "需要特定请求头：Pixiv 路由依赖令牌，公共实例通常 503"
        ),
        BuiltinFeed(
            title = "B站热搜",
            url = "/bilibili/hot-search",
            category = "社交媒体",
            source = "RSSHub",
            failureHint = "RSSHub 路由不可用：公共实例对该路由返回 503，上游反爬"
        ),
        BuiltinFeed(
            title = "贴吧帖子",
            url = "/tieba/forum/linux",
            category = "社交媒体",
            source = "RSSHub",
            failureHint = "RSSHub 路由不可用：当前实例 /api/namespace 无 tieba 命名空间，请求 404"
        ),
        BuiltinFeed(
            title = "纽约时报中文简报",
            url = "/nytimes/daily_briefing_chinese",
            category = "新闻",
            source = "RSSHub",
            failureHint = "RSSHub 路由不可用：公共实例对该路由返回 503，可改订 /nytimes/rss/HomePage"
        ),
        BuiltinFeed(
            title = "机核官方 RSS",
            url = "https://www.gcores.com/rss",
            category = "社区与开发",
            source = "官方 RSS",
            failureHint = "非标准/残缺 feed：站点 WAF 把 RSS 换成 HTML 拦截页，改用 RSSHub /gcores/news"
        )
    )

    /** 精选目录展示：可用源 + 标注了失败原因的源（不删除） */
    val displayCategories: List<BuiltinCategory> =
        categories + BuiltinCategory("暂不可用（已标注原因）", unrepaired)
}
