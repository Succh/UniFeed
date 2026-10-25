package com.succh.unifeed.ui

/**
 * RSSHub 精选路由。
 * [path] 为文档路径（以 `/` 开头），订阅时拼到当前 RSSHub 实例。
 * 对照 /api/namespace 修正了过期路径；需要参数的路由用 [description] 提示，并用 [defaultParam] 给一键订阅默认值。
 */
data class RsshubRoute(
    val title: String,
    val path: String,
    val description: String = "",
    val defaultParam: String = ""
) {
    /** 兼容旧字段名：完整 URL 在订阅时由实例地址拼接 */
    val url: String get() = path
    val needsParam: Boolean get() = description.isNotBlank() && defaultParam.isBlank()
}

data class RsshubCategory(
    val name: String,
    val icon: String,
    val routes: List<RsshubRoute>
)

object RsshubPresets {

    val allRoutes: List<Pair<String, RsshubRoute>>
        get() = categories.flatMap { cat -> cat.routes.map { cat.name to it } }

    fun resolve(instance: String, route: RsshubRoute, extra: String = ""): String {
        val base = instance.trim().trimEnd('/')
        val extraPart = extra.trim().trim('/')
        val path = when {
            extraPart.isEmpty() && route.defaultParam.isNotEmpty() ->
                route.path.trimEnd('/') + "/" + route.defaultParam.trim('/')
            extraPart.isEmpty() -> route.path
            else -> route.path.trimEnd('/') + "/" + extraPart
        }
        return if (path.startsWith("http")) path else base + (if (path.startsWith("/")) path else "/$path")
    }

    val categories: List<RsshubCategory> = listOf(
        RsshubCategory("社交", "social", listOf(
            RsshubRoute("微博热搜", "/weibo/search/hot"),
            RsshubRoute("微博用户", "/weibo/user/", "填入用户 UID"),
            RsshubRoute("微博关键词", "/weibo/keyword/", "填入关键词", "RSSHub"),
            RsshubRoute("知乎热榜", "/zhihu/hot"),
            RsshubRoute("知乎日报", "/zhihu/daily"),
            RsshubRoute("知乎专栏", "/zhihu/zhuanlan/", "填入专栏 ID", "googledevelopers"),
            RsshubRoute("B站综合热门", "/bilibili/popular/all"),
            RsshubRoute("B站排行榜", "/bilibili/ranking/all"),
            RsshubRoute("B站UP主", "/bilibili/user/video/", "填入 UID", "2267573"),
            RsshubRoute("Telegram 频道", "/telegram/channel/", "填入频道名", "awesomeRSSHub")
        )),
        RsshubCategory("科技与编程", "tech", listOf(
            RsshubRoute("GitHub Trending", "/github/trending/daily/javascript/en"),
            RsshubRoute("GitHub Issues", "/github/issue/", "填入 owner/repo", "DIYgod/RSSHub"),
            RsshubRoute("GitHub 仓库列表", "/github/repos/", "填入用户名", "DIYgod"),
            RsshubRoute("Hacker News", "/hackernews"),
            RsshubRoute("V2EX 最新", "/v2ex/topics/latest"),
            RsshubRoute("V2EX 最热", "/v2ex/topics/hot"),
            RsshubRoute("掘金本月最热", "/juejin/trending/all/monthly"),
            RsshubRoute("Solidot", "/solidot"),
            RsshubRoute("少数派首页", "/sspai/index"),
            RsshubRoute("少数派 Matrix", "/sspai/matrix")
        )),
        RsshubCategory("新闻与资讯", "news", listOf(
            RsshubRoute("36氪热榜", "/36kr/hot-list"),
            RsshubRoute("36氪快讯", "/36kr/newsflashes"),
            RsshubRoute("澎湃新闻头条", "/thepaper/featured"),
            RsshubRoute("华尔街见闻", "/wallstreetcn/news"),
            RsshubRoute("财联社电报", "/cls/telegraph"),
            RsshubRoute("财新最新", "/caixin/latest"),
            RsshubRoute("人民网头条", "/people"),
            RsshubRoute("纽约时报首页", "/nytimes/rss/HomePage"),
            RsshubRoute("BBC 世界新闻", "/bbc/world"),
            RsshubRoute("联合早报中国", "/zaobao/realtime/china")
        )),
        RsshubCategory("生活与娱乐", "life", listOf(
            RsshubRoute("豆瓣正在热映", "/douban/movie/playing"),
            RsshubRoute("豆瓣即将上映", "/douban/movie/coming"),
            RsshubRoute("豆瓣一周口碑榜", "/douban/movie/weekly"),
            RsshubRoute("什么值得买好文", "/smzdm/haowen/fenlei/shenghuodianqi")
        )),
        RsshubCategory("二次元/ACG", "acg", listOf(
            RsshubRoute("Bangumi 每日放送", "/bangumi.tv/calendar/today"),
            RsshubRoute("B站综合热门", "/bilibili/popular/all"),
            RsshubRoute("Pixiv 周榜", "/pixiv/ranking/week")
        )),
        RsshubCategory("游戏", "game", listOf(
            RsshubRoute("游研社推荐", "/yystv/category/recommend"),
            RsshubRoute("机核资讯", "/gcores/news"),
            RsshubRoute("机核文章", "/gcores/articles")
        )),
        RsshubCategory("财经商业", "finance", listOf(
            RsshubRoute("财联社热门", "/cls/hot"),
            RsshubRoute("华尔街见闻快讯", "/wallstreetcn/live"),
            RsshubRoute("财新周刊", "/caixin/weekly")
        ))
    )
}
