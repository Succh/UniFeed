package com.succh.unifeed.data.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RsshubCatalogTest {

    @Test
    fun parseNamespaceJson() {
        val json = """
            {
              "zhihu": {
                "name": "知乎",
                "lang": "zh-CN",
                "categories": ["social-media"],
                "routes": {
                  "/hot/:category?": {
                    "path": "/hot/:category?",
                    "name": "知乎热榜",
                    "example": "/zhihu/hot",
                    "parameters": {"category": "分类"}
                  },
                  "/zhuanlan/:id": {
                    "path": "/zhuanlan/:id",
                    "name": "专栏",
                    "example": "/zhihu/zhuanlan/googledevelopers",
                    "parameters": {"id": "专栏 ID"}
                  }
                }
              }
            }
        """.trimIndent()
        val list = RsshubCatalog.parse(json)
        assertEquals(1, list.size)
        assertEquals("知乎", list[0].name)
        assertEquals(2, list[0].routes.size)
        val hot = list[0].routes.first { it.title == "知乎热榜" }
        assertEquals("/zhihu/hot", hot.example)
        assertFalse(hot.needsUserParam)
        val url = RsshubCatalog.buildSubscribeUrl("https://rsshub.app", hot)
        assertEquals("https://rsshub.app/zhihu/hot", url)
        val col = list[0].routes.first { it.title == "专栏" }
        assertFalse(col.needsUserParam)
        assertEquals(
            "https://rsshub.app/zhihu/zhuanlan/googledevelopers",
            RsshubCatalog.buildSubscribeUrl("https://rsshub.app", col)
        )
        assertEquals(
            "https://rsshub.app/zhihu/zhuanlan/custom-id",
            RsshubCatalog.buildSubscribeUrl("https://rsshub.app", col, "custom-id")
        )
    }

    @Test
    fun parseUsesRouteLevelCategories() {
        val json = """
            {
              "sspai": {
                "name": "少数派",
                "routes": {
                  "/index": {
                    "path": "/index",
                    "name": "首页",
                    "example": "/sspai/index",
                    "categories": ["new-media"]
                  }
                }
              }
            }
        """.trimIndent()
        val list = RsshubCatalog.parse(json)
        assertEquals(listOf("new-media"), list[0].categories)
        assertEquals(listOf("new-media"), list[0].routes[0].categories)
    }

    @Test
    fun needsUserParamWhenExampleMissing() {
        val json = """
            {
              "demo": {
                "name": "Demo",
                "routes": {
                  "/user/:id": {
                    "path": "/user/:id",
                    "name": "用户",
                    "example": "",
                    "categories": ["other"]
                  }
                }
              }
            }
        """.trimIndent()
        val route = RsshubCatalog.parse(json)[0].routes[0]
        assertTrue(route.needsUserParam)
        assertEquals(
            "https://rsshub.app/demo/user/42",
            RsshubCatalog.buildSubscribeUrl("https://rsshub.app", route, "42")
        )
    }

    @Test
    fun searchByTitleAndPath() {
        val json = """
            {
              "zhihu": {
                "name": "知乎",
                "routes": {
                  "/hot": {"path":"/hot","name":"知乎热榜","example":"/zhihu/hot","categories":["social-media"]},
                  "/daily": {"path":"/daily","name":"知乎日报","example":"/zhihu/daily","categories":["social-media"]}
                }
              }
            }
        """.trimIndent()
        val ns = RsshubCatalog.parse(json)
        val hits = RsshubCatalog.search(ns, "热榜")
        assertEquals(1, hits.size)
        assertEquals("知乎热榜", hits[0].title)
        assertTrue(RsshubCatalog.search(ns, "zhihu").size >= 2)
    }

    @Test
    fun categoryLabel() {
        assertEquals("社交媒体", RsshubCatalog.labelOf("social-media"))
        assertEquals("二次元/ACG", RsshubCatalog.labelOf("anime"))
    }

    @Test
    fun builtinFeedCount() {
        val n = com.succh.unifeed.ui.BuiltinFeeds.all.size
        assertTrue("预置源应不少于 50 个，实际 $n", n >= 50)
        val cats = com.succh.unifeed.ui.BuiltinFeeds.categories.map { it.name }
        assertTrue(cats.contains("科技"))
        assertTrue(cats.contains("新闻"))
        assertTrue(cats.contains("独立博客"))
        assertTrue(cats.contains("二次元/ACG"))
        assertTrue(cats.contains("财经商业"))
        assertTrue(com.succh.unifeed.ui.BuiltinFeeds.unrepaired.isNotEmpty())
        val unrepairedUrls = com.succh.unifeed.ui.BuiltinFeeds.unrepaired.map { it.url }
        assertTrue(unrepairedUrls.contains("/weibo/search/hot"))
        assertTrue(unrepairedUrls.contains("/xiaohongshu/board"))
        assertTrue(unrepairedUrls.contains("/douyin/hot"))
        assertTrue(unrepairedUrls.contains("/pixiv/ranking/day"))
        assertTrue(unrepairedUrls.contains("/bilibili/hot-search"))
        assertTrue(unrepairedUrls.contains("/tieba/forum/linux"))
    }

    @Test
    fun githubTrendingUsesSpokenLanguage() {
        val hit = com.succh.unifeed.ui.BuiltinFeeds.all.first { it.title.contains("GitHub Trending") }
        assertEquals("/github/trending/daily/javascript/en", hit.url)
    }
}
