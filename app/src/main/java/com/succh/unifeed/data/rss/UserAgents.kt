package com.succh.unifeed.data.rss

/**
 * 抓取层共用的 User-Agent。
 * 历史上桌面/移动 UA 在 4 个文件里各抄了一份，字符串一旦调整极易漏改，
 * 而同一订阅源在「发现 / 抓取 / 拉图 / 路由文档」用不同指纹也会让站点的反爬判定不一致。
 */
internal object UserAgents {
    const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    const val MOBILE =
        "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
}
