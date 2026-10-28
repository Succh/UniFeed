package com.succh.unifeed.data.rss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** RSSHub 文档里的一个站点（namespace） */
data class RsshubNamespace(
    val id: String,
    val name: String,
    val categories: List<String>,
    val routes: List<RsshubDocRoute>
)

/** RSSHub 文档里的一条路由 */
data class RsshubDocRoute(
    val nsId: String,
    val nsName: String,
    val title: String,
    val path: String,
    val example: String,
    val paramHint: String,
    val categories: List<String>
) {
    /** 是否还需要用户填必选参数（示例路径仍含 :param 或没有示例时） */
    val needsUserParam: Boolean
        get() = hasRequiredParam(example.ifBlank { path })
}

/** 去掉可选段后再看是否还剩 :param，避免把 `:id?` 当成必选 */
private val OPTIONAL_PARAM = Regex(""":[A-Za-z_][\w.]*\?""")
private val BARE_PARAM = Regex(""":[A-Za-z_][\w.]*""")
private val REPEATED_SLASH = Regex("/{2,}")
private val WHITESPACE = Regex("\\s+")

private fun stripOptional(path: String): String =
    path.replace(OPTIONAL_PARAM, "").replace(REPEATED_SLASH, "/").trimEnd('/')

private fun hasRequiredParam(path: String): Boolean = BARE_PARAM.containsMatchIn(stripOptional(path))

/** 去掉可选段后还剩几个必选 :param：只有恰好 1 个时才能用用户输入替换的把它 */
private fun requiredParamCount(path: String): Int = BARE_PARAM.findAll(stripOptional(path)).count()

/**
 * 从 RSSHub 实例的 /api/namespace 拉取路由库（与 docs.rsshub.app 同源数据）。
 * 失败时按镜像列表回退，保证浏览功能在单个实例宕机时仍可用。
 */
class RsshubCatalog(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    @Volatile
    private var cached: Pair<String, List<RsshubNamespace>>? = null

    suspend fun load(instance: String): List<RsshubNamespace> = withContext(Dispatchers.IO) {
        val base = instance.trim().trimEnd('/')
        cached?.let { if (it.first == base) return@withContext it.second }
        val candidates = (listOf(base) + FALLBACK_INSTANCES).distinct()
        var lastError: Exception? = null
        for (host in candidates) {
            try {
                val json = fetchJson("$host/api/namespace")
                val parsed = parse(json)
                if (parsed.isNotEmpty()) {
                    cached = base to parsed
                    return@withContext parsed
                }
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: Exception("无法加载 RSSHub 路由文档")
    }

    fun clearCache() {
        cached = null
    }

    private fun fetchJson(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Accept", "application/json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("Empty body")
        }
    }

    companion object {
        private const val UA = UserAgents.MOBILE

        /** 兜底实例与 [RsshubEndpoints] 共用，避免和抓取镜像清单漂移 */
        val FALLBACK_INSTANCES: List<String> = RsshubEndpoints.CATALOG_FALLBACKS

        val CATEGORY_LABELS = mapOf(
            "social-media" to "社交媒体",
            "programming" to "科技与编程",
            "new-media" to "新闻资讯",
            "traditional-media" to "传统媒体",
            "blog" to "博客与刊物",
            "anime" to "二次元/ACG",
            "game" to "游戏",
            "finance" to "财经商业",
            "multimedia" to "视频与直播",
            "design" to "设计与创意",
            "bbs" to "论坛社区",
            "reading" to "阅读",
            "picture" to "图片",
            "shopping" to "购物",
            "journal" to "期刊",
            "university" to "高校",
            "government" to "政务",
            "forecast" to "预报",
            "travel" to "出行",
            "study" to "学习",
            "program-update" to "应用更新",
            "other" to "其他",
            "uncategorized" to "未分类"
        )

        fun labelOf(category: String): String = CATEGORY_LABELS[category] ?: category

        fun parse(raw: String): List<RsshubNamespace> {
            val root = JSONObject(raw)
            val result = ArrayList<RsshubNamespace>(root.length())
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val obj = root.optJSONObject(id) ?: continue
                val nsName = obj.optString("name").ifBlank { id }
                // 新版 /api/namespace 分类写在路由上，命名空间级经常为空
                val nsCatsRaw = jsonStringList(obj.optJSONArray("categories"))
                val routesObj = obj.optJSONObject("routes") ?: JSONObject()
                val routes = ArrayList<RsshubDocRoute>(routesObj.length())
                val rKeys = routesObj.keys()
                while (rKeys.hasNext()) {
                    val pathKey = rKeys.next()
                    val rv = routesObj.optJSONObject(pathKey) ?: continue
                    val path = rv.optString("path").ifBlank { pathKey }
                    val title = rv.optString("name").ifBlank { path }
                    val example = rv.optString("example")
                    val hint = paramHint(rv)
                    val routeCats = jsonStringList(rv.optJSONArray("categories")).ifEmpty { nsCatsRaw }
                    routes += RsshubDocRoute(
                        nsId = id,
                        nsName = nsName,
                        title = title,
                        path = joinNsPath(id, path),
                        example = normalizeExample(id, example, path),
                        paramHint = hint,
                        categories = routeCats.ifEmpty { listOf("uncategorized") }
                    )
                }
                if (routes.isNotEmpty()) {
                    val nsCats = nsCatsRaw.ifEmpty {
                        routes.flatMap { it.categories }.distinct()
                    }.ifEmpty { listOf("uncategorized") }
                    result += RsshubNamespace(id = id, name = nsName, categories = nsCats, routes = routes)
                }
            }
            return result.sortedBy { it.name }
        }

        private fun joinNsPath(nsId: String, path: String): String {
            val p = if (path.startsWith("/")) path else "/$path"
            return if (p == "/$nsId" || p.startsWith("/$nsId/")) p else "/$nsId$p"
        }

        /** 把文档 example 补成带 namespace 的绝对路径 */
        private fun normalizeExample(nsId: String, example: String, path: String): String {
            val ex = example.trim()
            if (ex.isNotEmpty()) {
                return if (ex.startsWith("/$nsId") || ex.startsWith(nsId)) {
                    if (ex.startsWith("/")) ex else "/$ex"
                } else {
                    "/" + nsId.trim('/') + "/" + ex.trimStart('/')
                }
            }
            // 没有 example 时去掉可选参数，留下可订阅的骨架路径
            val stripped = stripOptional(path)
            return joinNsPath(nsId, stripped.ifBlank { "/" })
        }

        private fun paramHint(rv: JSONObject): String {
            val params = rv.optJSONObject("parameters") ?: return ""
            val parts = mutableListOf<String>()
            val it = params.keys()
            while (it.hasNext()) {
                val key = it.next()
                val v = params.opt(key)
                val desc = when (v) {
                    is JSONObject -> v.optString("description")
                    else -> v?.toString().orEmpty()
                }.replace(WHITESPACE, " ").trim()
                if (desc.isNotEmpty()) parts += "$key: ${desc.take(80)}"
                else parts += key
            }
            return parts.joinToString("；")
        }

        private fun jsonStringList(arr: org.json.JSONArray?): List<String> {
            if (arr == null) return emptyList()
            return buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) add(s)
                }
            }
        }

        fun buildSubscribeUrl(instance: String, route: RsshubDocRoute, extraParam: String = ""): String {
            val base = instance.trim().trimEnd('/')
            val extra = extraParam.trim().trim('/')
            val path = when {
                extra.isEmpty() -> route.example.ifBlank { route.path }
                route.example.isNotBlank() && !route.example.contains(':') &&
                    requiredParamCount(route.path) != 1 ->
                    route.example.trimEnd('/') + "/" + extra
                else -> fillRequiredParam(route.path, extra)
            }
            return base + (if (path.startsWith("/")) path else "/$path")
        }

        /** 去掉可选段后，把第一个 :param 换成用户输入 */
        private fun fillRequiredParam(path: String, extra: String): String {
            return BARE_PARAM.replaceFirst(stripOptional(path), extra)
        }

        fun search(namespaces: List<RsshubNamespace>, query: String, limit: Int = 80): List<RsshubDocRoute> {
            val q = query.trim()
            if (q.isEmpty()) return emptyList()
            return namespaces.asSequence()
                .flatMap { ns ->
                    ns.routes.asSequence().filter { route ->
                        route.title.contains(q, true) ||
                            ns.name.contains(q, true) ||
                            ns.id.contains(q, true) ||
                            route.path.contains(q, true) ||
                            route.example.contains(q, true) ||
                            route.paramHint.contains(q, true)
                    }
                }
                .take(limit)
                .toList()
        }
    }
}
