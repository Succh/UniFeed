package com.succh.unifeed.data.repository

import com.succh.unifeed.data.db.AppDatabase
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.Folder
import com.succh.unifeed.data.model.FeedPreview
import com.succh.unifeed.data.model.ParsedFeed
import com.succh.unifeed.data.opml.OpmlFeed
import com.succh.unifeed.data.rss.ContentExtractor
import com.succh.unifeed.data.rss.DiscoveredFeed
import com.succh.unifeed.data.rss.FeedDiscovery
import com.succh.unifeed.data.rss.FeedFetchException
import com.succh.unifeed.data.rss.RssParser
import com.succh.unifeed.data.rss.RsshubCatalog
import com.succh.unifeed.data.rss.RsshubNamespace
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * 订阅源仓库：统一管理订阅 CRUD 与抓取
 */
class FeedRepository(
    private val db: AppDatabase,
    private val parser: RssParser = RssParser(),
    private val discovery: FeedDiscovery = FeedDiscovery(),
    private val rsshubCatalog: RsshubCatalog = RsshubCatalog()
) {
    private val feedDao = db.feedDao()
    private val entryDao = db.entryDao()
    private val folderDao = db.folderDao()
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 后台抓取失败时向 UI 暴露错误信息，避免静默吞掉异常。
     * extraBufferCapacity 保证暂时没有订阅者也能暂存错误。
     */
    private val _fetchErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val fetchErrors: SharedFlow<String> = _fetchErrors.asSharedFlow()

    /**
     * 可配置的自定义 RSSHub 实例地址（用户在设置页填写）。
     * 非空时会插入镜像组首位优先使用；失败仍自动回退公共镜像。
     */
    @Volatile
    var customRsshubInstance: String? = null
        set(value) {
            field = value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        }

    /**
     * 已知 RSSHub 实例域名（官方 + 公共镜像 + 用户自定义）。
     * 用于判断一个 URL 是否属于 RSSHub 源。
     */
    private val knownRsshubHosts: List<String>
        get() = buildList {
            add("rsshub.app")
            addAll(PUBLIC_MIRRORS.map { it.removePrefix("https://").removePrefix("http://") })
            customRsshubInstance?.let { inst ->
                runCatching { android.net.Uri.parse(inst).host }.getOrNull()?.let { add(it) }
            }
        }

    /** 当前用于拼接相对路径的 RSSHub 实例 */
    fun effectiveRsshubInstance(): String = customRsshubInstance ?: RSSHUB_DEFAULT

    /**
     * 把相对 RSSHub 路径（如 `/zhihu/hot`）或完整 URL 规范成可订阅地址。
     */
    fun resolveFeedUrl(raw: String): String {
        val t = raw.trim()
        if (t.startsWith("/")) return effectiveRsshubInstance() + t
        return t
    }

    suspend fun loadRsshubCatalog(): Result<List<RsshubNamespace>> {
        return try {
            Result.success(rsshubCatalog.load(effectiveRsshubInstance()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isRsshubUrl(url: String): Boolean {
        return runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?.let { host -> knownRsshubHosts.any { it.equals(host, ignoreCase = true) } } ?: false
    }

    fun observeFeeds(): Flow<List<Feed>> = feedDao.observeAll()
    fun observeEntries(feedId: Long): Flow<List<Entry>> = entryDao.observeByFeed(feedId)
    fun observeAllEntries(): Flow<List<Entry>> = entryDao.observeAll()
    fun observeUnreadEntries(): Flow<List<Entry>> = entryDao.observeUnread()
    fun observeStarredEntries(): Flow<List<Entry>> = entryDao.observeStarred()
    fun observeUnreadCount(): Flow<Int> = entryDao.observeUnreadCount()

    suspend fun discoverFeeds(input: String): Result<List<DiscoveredFeed>> {
        return try {
            Result.success(discovery.discover(input))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 预览订阅源：抓取解析后返回卡片数据，此阶段不入库。
     */
    suspend fun previewFeed(url: String): Result<FeedPreview> {
        return try {
            val (parsed, actualUrl) = fetchWithSmartFallback(resolveFeedUrl(url))
            Result.success(
                FeedPreview(
                    title = parsed.title.ifEmpty { actualUrl },
                    url = actualUrl,
                    siteUrl = parsed.siteUrl,
                    description = parsed.description,
                    faviconUrl = buildFaviconUrl(parsed.siteUrl ?: actualUrl),
                    recentTitles = parsed.entries.take(5).map { it.title },
                    parsed = parsed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 用户确认订阅：插入 Feed 并批量保存 RSS 解析出的文章。
     */
    suspend fun subscribePreview(preview: FeedPreview): Result<Feed> {
        return try {
            val existing = feedDao.getByUrl(preview.url)
            val feed = if (existing != null) {
                val updated = existing.copy(
                    title = preview.title,
                    siteUrl = preview.siteUrl,
                    description = preview.description,
                    faviconUrl = preview.faviconUrl.ifEmpty { existing.faviconUrl },
                    lastUpdated = System.currentTimeMillis(),
                    lastRefreshTime = System.currentTimeMillis(),
                    lastRefreshError = null
                )
                feedDao.update(updated)
                updated
            } else {
                val newFeed = Feed(
                    title = preview.title,
                    url = preview.url,
                    siteUrl = preview.siteUrl,
                    description = preview.description,
                    faviconUrl = preview.faviconUrl,
                    lastUpdated = System.currentTimeMillis(),
                    lastRefreshTime = System.currentTimeMillis(),
                    lastRefreshError = null
                )
                val newId = feedDao.insert(newFeed)
                newFeed.copy(id = newId)
            }
            saveEntries(feed.id, preview.parsed)
            feedDao.updateUnreadCount(feed.id)
            Result.success(feed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addFeed(url: String): Result<Feed> {
        return try {
            val preview = previewFeed(url).getOrThrow()
            subscribePreview(preview)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 即时添加订阅源（Folo 式）：先存 URL 和标题到数据库，立即返回成功；
     * 后台异步 fetch 文章内容。用户无需等待网络请求。
     */
    suspend fun addFeedInstant(url: String, titleHint: String? = null): Result<Feed> {
        return try {
            val resolvedUrl = resolveFeedUrl(url)
            val existing = feedDao.getByUrl(resolvedUrl)
            if (existing != null) {
                return Result.success(existing)
            }
            val inferredTitle = titleHint ?: inferTitleFromUrl(resolvedUrl)
            val newFeed = Feed(
                title = inferredTitle,
                url = resolvedUrl,
                siteUrl = null,
                description = "",
                faviconUrl = ""
            )
            val newId = feedDao.insert(newFeed)
            val savedFeed = newFeed.copy(id = newId)
            bgScope.launch {
                try {
                    val (parsed, actualUrl) = fetchWithSmartFallback(resolvedUrl)
                    val now = System.currentTimeMillis()
                    feedDao.update(savedFeed.copy(
                        title = parsed.title.ifEmpty { inferredTitle },
                        siteUrl = parsed.siteUrl,
                        description = parsed.description,
                        faviconUrl = buildFaviconUrl(parsed.siteUrl ?: actualUrl),
                        url = actualUrl,
                        lastUpdated = now,
                        lastRefreshTime = now,
                        lastRefreshError = null
                    ))
                    saveEntries(newId, parsed)
                    feedDao.updateUnreadCount(newId)
                } catch (e: Exception) {
                    runCatching {
                        val current = feedDao.getById(newId) ?: savedFeed
                        feedDao.update(
                            current.copy(
                                lastUpdated = System.currentTimeMillis(),
                                lastRefreshTime = System.currentTimeMillis(),
                                lastRefreshError = e.message ?: "未知错误"
                            )
                        )
                    }
                    _fetchErrors.emit("订阅源「${savedFeed.title}」抓取失败: ${e.message ?: "未知错误"}")
                }
            }
            Result.success(savedFeed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inferTitleFromUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            val path = uri.path?.trim('/') ?: ""
            if (isRsshubUrl(url) && path.isNotEmpty()) {
                val parts = path.split("/")
                parts.takeLast(2).joinToString("/")
            } else {
                uri.host ?: url
            }
        } catch (_: Exception) {
            url
        }
    }

    /**
     * 真并发镜像竞速：所有候选 URL 同时发起请求，
     * 第一个返回成功且内容非空的胜出，立即 cancel 其余请求。
     */
    private suspend fun fetchWithSmartFallback(url: String): Pair<ParsedFeed, String> {
        val resolved = resolveFeedUrl(url)
        if (!isRsshubUrl(resolved)) {
            return try {
                val parsed = parser.fetch(resolved)
                if (parsed.entries.isEmpty()) {
                    throw FeedFetchException(FeedFetchException.Kind.EMPTY, "源可访问但没有文章")
                }
                parsed to resolved
            } catch (e: FeedFetchException) {
                throw e
            } catch (e: Exception) {
                throw FeedFetchException(FeedFetchException.Kind.NETWORK, e.message ?: "源抓取失败")
            }
        }

        val path = extractRsshubPath(resolved)
        if (path.isEmpty()) {
            throw FeedFetchException(FeedFetchException.Kind.RSSHUB, "无效的 RSSHub URL")
        }

        val mirrorGroup = getMirrorForRoute(path)
        val candidates = (mirrorGroup.map { buildRsshubUrl(it, path) } + resolved).distinct()

        val winner = raceFetch(candidates)
        if (winner != null) {
            if (winner.first.entries.isEmpty()) {
                throw FeedFetchException(FeedFetchException.Kind.EMPTY, "源可访问但没有文章")
            }
            return winner
        }

        throw FeedFetchException(FeedFetchException.Kind.RSSHUB, "所有镜像均不可用，该路由可能暂不支持")
    }

    /**
     * 用 select + Channel 实现竞速：每个镜像各发一次结果（成功对或 null），
     * 第一个非空结果胜出并立刻 cancel 其余请求。
     */
    private suspend fun raceFetch(urls: List<String>): Pair<ParsedFeed, String>? = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope null
        val channel = Channel<Pair<ParsedFeed, String>?>(Channel.UNLIMITED)
        val jobs = urls.map { candidate ->
            async {
                val payload = try {
                    val result = parser.fetch(candidate)
                    if (result.entries.isNotEmpty() || result.title.isNotEmpty()) {
                        result to candidate
                    } else {
                        null
                    }
                } catch (_: Exception) {
                    null
                }
                try {
                    channel.send(payload)
                } catch (_: Exception) {
                }
            }
        }
        try {
            repeat(urls.size) {
                val item = select<Pair<ParsedFeed, String>?> {
                    channel.onReceive { it }
                }
                if (item != null) {
                    jobs.forEach { it.cancel() }
                    return@coroutineScope item
                }
            }
            null
        } finally {
            jobs.forEach { it.cancel() }
            channel.close()
        }
    }

    /**
     * 根据路由前缀返回镜像组（顺序越前优先级越高）
     */
    private fun getMirrorForRoute(path: String): List<String> {
            val routeBased = listOf(
            RSSHUB_DEFAULT, RSSHUB_RSSFOREVER, RSSHUB_SLARKER, RSSHUB_CUPS, RSSHUB_OWO, RSSHUB_OFFICIAL
        )
        return customRsshubInstance?.let { listOf(it) + routeBased.filter { m -> m != it } } ?: routeBased
    }

    private fun extractRsshubPath(url: String): String {
        try {
            val uri = android.net.Uri.parse(url)
            val path = uri.path?.trim('/') ?: ""
            return if (path.startsWith("rsshub")) {
                path.removePrefix("rsshub").trimStart('/')
            } else {
                path
            }
        } catch (_: Exception) {
            return ""
        }
    }

    private fun buildRsshubUrl(mirror: String, path: String): String {
        return "$mirror/$path"
    }

    suspend fun refreshFeed(feedId: Long) {
        val feed = feedDao.getById(feedId) ?: return
        try {
            val (parsed, _) = fetchWithSmartFallback(feed.url)
            saveEntries(feed.id, parsed)
            val now = System.currentTimeMillis()
            feedDao.update(
                feed.copy(
                    lastUpdated = now,
                    lastRefreshTime = now,
                    lastRefreshError = null
                )
            )
            feedDao.updateUnreadCount(feed.id)
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val reason = (e as? FeedFetchException)?.message ?: (e.message ?: "未知错误")
            runCatching {
                val current = feedDao.getById(feedId) ?: feed
                feedDao.update(
                    current.copy(
                        lastRefreshTime = now,
                        lastRefreshError = reason
                    )
                )
            }
            _fetchErrors.emit("刷新「${feed.title}」失败: $reason")
        }
    }

    suspend fun refreshAll() {
        feedDao.observeAll().first().forEach { refreshFeed(it.id) }
    }

    suspend fun deleteFeed(feed: Feed) {
        entryDao.deleteByFeed(feed.id)
        feedDao.delete(feed)
    }

    suspend fun markRead(entry: Entry) {
        entryDao.markRead(entry.id)
        feedDao.updateUnreadCount(entry.feedId)
    }

    suspend fun markUnread(entry: Entry) {
        entryDao.markUnread(entry.id)
        feedDao.updateUnreadCount(entry.feedId)
    }

    suspend fun setStarred(entry: Entry, starred: Boolean) {
        entryDao.setStarred(entry.id, starred)
    }

    /** 节流后由阅读器调用，写入 0~1 滚动比例与时间戳 */
    suspend fun saveReadProgress(entryId: Long, position: Float) {
        entryDao.updateReadProgress(
            id = entryId,
            position = position.coerceIn(0f, 1f),
            readAt = System.currentTimeMillis()
        )
    }

    suspend fun markAllRead(feedId: Long) {
        entryDao.markAllRead(feedId)
        feedDao.updateUnreadCount(feedId)
    }

    suspend fun markAllReadAll() {
        entryDao.markAllReadGlobal()
        feedDao.observeAll().first().forEach { feedDao.updateUnreadCount(it.id) }
    }

    /**
     * 按需抓取原文：复用 ContentExtractor，成功后写回 Entry.content。
     */
    suspend fun loadFullContent(entry: Entry): Result<Entry> {
        val link = entry.link
        if (link.isNullOrBlank()) {
            return Result.failure(Exception("没有原文链接"))
        }
        return try {
            val html = parser.fetchHtml(link)
            val extracted = ContentExtractor.extract(html)
            if (extracted.isBlank() || extracted.length <= (entry.content?.length ?: 0)) {
                return Result.failure(Exception("未能提取到更完整的正文"))
            }
            val updated = entry.copy(content = extracted)
            entryDao.update(updated)
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 判断 RSS 内容是否只有摘要（长度过短视为不完整） */
    fun isContentIncomplete(entry: Entry): Boolean {
        val content = entry.content ?: entry.summary ?: ""
        val plain = content.replace(Regex("<[^>]+>"), "").trim()
        return plain.length < 400 && !entry.link.isNullOrBlank()
    }

    fun observeFolders(): Flow<List<Folder>> = folderDao.observeAll()

    suspend fun createFolder(name: String): Long {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return -1L
        val existing = folderDao.getByName(trimmed)
        if (existing != null) return existing.id
        return folderDao.insert(Folder(name = trimmed, createdAt = System.currentTimeMillis()))
    }

    suspend fun renameFolder(id: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val folder = folderDao.getById(id) ?: return false
        folderDao.update(folder.copy(name = trimmed))
        return true
    }

    suspend fun deleteFolder(id: Long) {
        feedDao.clearFolderId(id)
        folderDao.deleteById(id)
    }

    suspend fun moveFeedToFolder(feedId: Long, folderId: Long?) {
        feedDao.updateFolderId(feedId, folderId)
    }

    suspend fun renameFeed(feedId: Long, newTitle: String): Boolean {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return false
        val feed = feedDao.getById(feedId) ?: return false
        feedDao.update(feed.copy(title = trimmed))
        return true
    }

    /**
     * OPML 批量导入：按 URL 去重，已存在的跳过；
     * 有文件夹名的先建文件夹再关联，无文件夹名的平铺导入。
     * 返回 Pair(成功导入数, 跳过数)。
     */
    suspend fun importFeeds(list: List<OpmlFeed>): Pair<Int, Int> {
        var imported = 0
        var skipped = 0
        val folderCache = mutableMapOf<String, Long>()
        for (opmlFeed in list) {
            val url = opmlFeed.xmlUrl.trim()
            if (url.isEmpty()) {
                skipped++
                continue
            }
            if (feedDao.getByUrl(url) != null) {
                skipped++
                continue
            }
            val folderId = opmlFeed.folderName?.takeIf { it.isNotEmpty() }?.let { folderName ->
                folderCache.getOrPut(folderName) {
                    folderDao.getByName(folderName)?.id
                        ?: folderDao.insert(Folder(name = folderName))
                }
            }
            feedDao.insert(
                Feed(
                    title = opmlFeed.title.ifEmpty { url },
                    url = url,
                    siteUrl = opmlFeed.htmlUrl.takeIf { it.isNotEmpty() },
                    description = "",
                    faviconUrl = "",
                    folderId = folderId
                )
            )
            imported++
        }
        return imported to skipped
    }

    /**
     * 刷新入库：只保存 RSS 解析出的内容/摘要，禁止逐篇 fetchHtml。
     * 一次查出已有 guid，过滤后 insertAll 批量插入。
     */
    private suspend fun saveEntries(feedId: Long, parsed: ParsedFeed) {
        val existingGuids = entryDao.getGuidsByFeed(feedId).toHashSet()
        val now = System.currentTimeMillis()
        val newcomers = parsed.entries
            .filter { it.guid.isNotBlank() && it.guid !in existingGuids }
            .map { pe ->
                Entry(
                    feedId = feedId,
                    guid = pe.guid,
                    title = pe.title,
                    link = pe.link,
                    content = pe.content,
                    summary = pe.summary ?: pe.content?.take(200),
                    author = pe.author,
                    publishedAt = pe.publishedAt,
                    fetchedAt = now,
                    imageUrl = pe.imageUrl
                )
            }
        if (newcomers.isNotEmpty()) {
            entryDao.insertAll(newcomers)
        }
    }

    private fun buildFaviconUrl(siteUrl: String): String {
        return try {
            val uri = android.net.Uri.parse(siteUrl)
            "https://icons.duckduckgo.com/ip3/${uri.host}.ico"
        } catch (_: Exception) {
            ""
        }
    }

    private companion object {
        /** 默认自建实例（老板的专属实例，优先使用） */
        const val RSSHUB_DEFAULT = "https://succh.zone.id"
        const val RSSHUB_OFFICIAL = "https://rsshub.app"
        const val RSSHUB_CUPS = "https://rsshub.cups.moe"
        const val RSSHUB_OWO = "https://rss.owo.nz"
        const val RSSHUB_BALANCER = "https://rsshub-balancer.virworks.moe"
        const val RSSHUB_SLARKER = "https://hub.slarker.me"
        const val RSSHUB_RSSFOREVER = "https://rsshub.rssforever.com"

        /** 公共镜像全集（用于域名识别），含默认自建实例 */
        val PUBLIC_MIRRORS = listOf(
            RSSHUB_DEFAULT, RSSHUB_OFFICIAL, RSSHUB_CUPS, RSSHUB_OWO,
            RSSHUB_BALANCER, RSSHUB_SLARKER, RSSHUB_RSSFOREVER
        )
    }
}
