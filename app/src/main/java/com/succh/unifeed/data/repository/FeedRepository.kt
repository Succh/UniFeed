package com.succh.unifeed.data.repository

import com.succh.unifeed.data.cache.ArticleCleanup
import com.succh.unifeed.data.cache.ArticleRetention
import com.succh.unifeed.data.db.AppDatabase
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.FilterRule
import com.succh.unifeed.data.db.entity.Folder
import com.succh.unifeed.data.db.entity.SearchHistory
import com.succh.unifeed.data.filter.ContentFilter
import com.succh.unifeed.data.filter.FilterAction
import com.succh.unifeed.data.model.FeedPreview
import com.succh.unifeed.data.model.ParsedEntry
import com.succh.unifeed.data.model.ParsedFeed
import com.succh.unifeed.data.opml.OpmlFeed
import com.succh.unifeed.data.rss.ContentExtractor
import com.succh.unifeed.data.rss.DiscoveredFeed
import com.succh.unifeed.data.rss.FeedDiscovery
import com.succh.unifeed.data.rss.FeedFetchException
import com.succh.unifeed.data.rss.FeedUrls
import com.succh.unifeed.data.rss.RssParser
import com.succh.unifeed.data.rss.RsshubCatalog
import com.succh.unifeed.data.rss.RsshubEndpoints
import com.succh.unifeed.data.rss.RsshubNamespace
import com.succh.unifeed.data.search.EntrySearch
import com.succh.unifeed.desktop.UnreadWidgetContent
import com.succh.unifeed.desktop.UnreadWidgetItem
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * 「加载全文」结果：
 * - [Extracted] 提取到比现有正文更完整的全文，已写回数据库；
 * - [OpenOriginal] 提取不到有效正文（原文是视频页、正文过短、提取或抓取失败），
 *   调用方应直接打开原文链接，不要停在原地让用户再点一次「阅读原文」。
 */
sealed class FullContentResult {
    data class Extracted(val entry: Entry) : FullContentResult()
    data class OpenOriginal(val reason: String) : FullContentResult()
}

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
    private val searchHistoryDao = db.searchHistoryDao()
    private val filterRuleDao = db.filterRuleDao()

    /**
     * 后台抓取失败时向 UI 暴露错误信息，避免静默丢掉异常。
     * extraBufferCapacity 保证暂时没有订阅者也能暂存错误。
     */
    private val _fetchErrors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val fetchErrors: SharedFlow<String> = _fetchErrors.asSharedFlow()

    /**
     * 可配置的自定义 RSSHub 实例地址（用户在设置项填写）。
     * 非空时会插入镜像组首位优先使用；失败仍自动退回公共镜像。
     */
    @Volatile
    var customRsshubInstance: String? = null
        set(value) {
            field = value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
            // 抓取层做错误分类时要用同一个口径，否则自定义实例的失败会被归成普通 HTTP
            parser.rsshubExtraHost = field?.let(FeedUrls::hostOf)
        }

    /** 当前用于拼接相对路径的 RSSHub 实例 */
    fun effectiveRsshubInstance(): String = customRsshubInstance ?: RsshubEndpoints.DEFAULT

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

    /** 是否 RSSHub 源：内置镜像清单之外还认用户自定义实例。UI 也据此选择订阅路径 */
    fun isRsshubUrl(url: String): Boolean =
        RsshubEndpoints.isRsshubUrl(url, extraHost = customRsshubInstance?.let(FeedUrls::hostOf))

    fun observeFeeds(): Flow<List<Feed>> = feedDao.observeAll()
    fun observeAllEntries(): Flow<List<Entry>> = entryDao.observeVisible()
    fun observeUnreadCount(): Flow<Int> = entryDao.observeUnreadCount()
    suspend fun getUnreadCount(): Int = entryDao.getUnreadCount()
    suspend fun getEntry(id: Long): Entry? = entryDao.getById(id)
    suspend fun getLatestUnreadForWidget(limit: Int = UnreadWidgetContent.MAX_ITEMS): List<UnreadWidgetItem> =
        entryDao.getLatestUnreadForWidget(limit)
    fun observeFilterRules(): Flow<List<FilterRule>> = filterRuleDao.observeAll()

    /**
     * 跨全部已订阅源搜索文章，覆盖标题、摘要、正文。
     * 不依赖当前列表筛选（源/未读/收藏）。
     */
    suspend fun searchEntries(rawQuery: String): List<Entry> {
        val query = EntrySearch.normalizeQuery(rawQuery)
        if (query.isEmpty()) return emptyList()
        return entryDao.searchAll(EntrySearch.escapeLike(query))
    }

    fun observeSearchHistory(): Flow<List<SearchHistory>> = searchHistoryDao.observeAll()

    /**
     * 记录一次搜索：同词刷新时间戳顶到最前，超出上限裁掉最旧记录。
     */
    suspend fun recordSearchHistory(rawQuery: String) {
        val query = EntrySearch.normalizeQuery(rawQuery)
        if (query.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = searchHistoryDao.getByQuery(query)
        if (existing != null) {
            searchHistoryDao.update(existing.copy(searchedAt = now))
        } else {
            searchHistoryDao.insert(SearchHistory(query = query, searchedAt = now))
        }
        val overflow = searchHistoryDao.idsBeyond(MAX_SEARCH_HISTORY)
        if (overflow.isNotEmpty()) {
            searchHistoryDao.deleteByIds(overflow)
        }
    }

    suspend fun deleteSearchHistory(id: Long) {
        searchHistoryDao.deleteById(id)
    }

    suspend fun clearSearchHistory() {
        searchHistoryDao.deleteAll()
    }

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
     * 即时添加订阅源（Folo 式）：只写 URL 与推断标题，立即返回，不做网络请求。
     * 首次抓取由调用方走 [refreshFeed]，元数据在那里补齐，避免同一路由被并发发两次
     * （历史问题：即时订阅的后台抓取与 UI 的刷新同时写库，guid 去重集合各自读到空，可能重复入库）。
     */
    suspend fun addFeedInstant(url: String, titleHint: String? = null): Result<Feed> {
        return try {
            val resolvedUrl = resolveFeedUrl(url)
            val existing = feedDao.getByUrl(resolvedUrl)
            if (existing != null) {
                return Result.success(existing)
            }
            val newFeed = Feed(
                title = titleHint ?: inferTitleFromUrl(resolvedUrl),
                url = resolvedUrl,
                siteUrl = null,
                description = "",
                faviconUrl = ""
            )
            Result.success(newFeed.copy(id = feedDao.insert(newFeed)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun inferTitleFromUrl(url: String): String {
        val path = FeedUrls.pathOf(url).trim('/')
        return if (isRsshubUrl(url) && path.isNotEmpty()) {
            path.split("/").takeLast(2).joinToString("/")
        } else {
            FeedUrls.hostOf(url) ?: url
        }
    }

    /**
     * 真并发镜像竞速：所有候选 URL 同时发起请求，
     * 第一个返回成功且内容非空的胜出，立即 cancel 其余请求。
     */
    private suspend fun fetchWithSmartFallback(url: String): Pair<ParsedFeed, String> {
        val resolved = resolveFeedUrl(url)
        if (isRsshubUrl(resolved)) {
            val outcome = try {
                parser.fetch(resolved) to resolved
            } catch (e: FeedFetchException) {
                // 生效实例对该路由 503/403/错误页（反爬或上游失败）：换镜像清单里的其他实例重试，
                // 全部失败才报错，并在文案里提示去设置更换实例
                if (e.kind != FeedFetchException.Kind.RSSHUB) throw e
                val path = extractRsshubPath(resolved)
                val alternatives = if (path.isBlank()) emptyList() else
                    mirrorCandidates().map { "$it/$path" }.filterNot { it == resolved }.distinct()
                raceFetch(alternatives)
                    ?: throw FeedFetchException(
                        e.kind,
                        "${e.detail}；公共镜像也全部失败，建议在设置中更换 RSSHub 实例"
                    )
            } catch (e: Exception) {
                throw FeedFetchException(FeedFetchException.Kind.NETWORK, e.message ?: "源抓取失败")
            }
            return requireArticles(outcome.first, outcome.second)
        }

        val path = extractRsshubPath(resolved)
        if (path.isEmpty()) {
            throw FeedFetchException(FeedFetchException.Kind.RSSHUB, "无效的 RSSHub URL")
        }

        // 直连优先：走到这里的 URL 多半是不经 RSSHub 的普通源，先直接抓一次，
        // 成功就省掉向全部镜像发无意义请求（每次刷新 6 个必然失败的垃圾请求）；
        // 失败（如未收录的自建 RSSHub 实例域名）才退回镜像竞速
        val direct = runCatching { parser.fetch(resolved) }.getOrNull()
        if (direct != null && (direct.entries.isNotEmpty() || direct.title.isNotEmpty())) {
            return requireArticles(direct, resolved)
        }
        val candidates = (mirrorCandidates().map { "$it/$path" } + resolved).distinct()
        val winner = raceFetch(candidates) ?: throw FeedFetchException(
            FeedFetchException.Kind.RSSHUB,
            "所有镜像均不可用，该路由可能暂不支持"
        )
        return requireArticles(winner.first, winner.second)
    }

    /** 源可访问但零文章，两条抓取分支共用同一分类 */
    private fun requireArticles(parsed: ParsedFeed, url: String): Pair<ParsedFeed, String> {
        if (parsed.entries.isEmpty()) {
            throw FeedFetchException(FeedFetchException.Kind.EMPTY, "源可访问但没有文章")
        }
        return parsed to url
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

    /** 竞速镜像组：用户自定义实例优先，其余按 [RsshubEndpoints.MIRRORS] 顺序兜底 */
    private fun mirrorCandidates(): List<String> {
        val custom = customRsshubInstance ?: return RsshubEndpoints.MIRRORS
        return listOf(custom) + RsshubEndpoints.MIRRORS.filterNot { it == custom }
    }

    /** RSSHub 路由路径（去掉实例前缀 '/rsshub' 与首尾斜杠） */
    private fun extractRsshubPath(url: String): String {
        val path = FeedUrls.pathOf(url).trim('/')
        return if (path.startsWith("rsshub")) path.removePrefix("rsshub").trimStart('/') else path
    }

    /**
     * 刷新单个订阅源。
     * [manual] 为用户主动刷新（下拉/点刷新/订阅首抓），失败一定要播报；
     * 后台自动刷新时同一源错误文案没变化就不再 emit，避免 toast 刷屏。
     * 返回 null 表示成功，否则返回失败原因文案。
     */
    suspend fun refreshFeed(feedId: Long, manual: Boolean = false): String? {
        val feed = feedDao.getById(feedId) ?: return null
        try {
            val (parsed, _) = fetchWithSmartFallback(feed.url)
            saveEntries(feed.id, parsed)
            val now = System.currentTimeMillis()
            // 重读一次，避免抓取期间用户的改名/移动被旧快照覆盖
            val current = feedDao.getById(feedId) ?: feed
            val missingMetadata = current.siteUrl.isNullOrBlank() && current.faviconUrl.isNullOrBlank()
            feedDao.update(
                if (missingMetadata) {
                    // 即时订阅只写了 URL，首次抓取补充元数据；此后不再覆盖用户改名
                    current.copy(
                        title = parsed.title.ifEmpty { current.title },
                        siteUrl = parsed.siteUrl,
                        description = parsed.description?.takeIf { it.isNotBlank() } ?: current.description,
                        faviconUrl = buildFaviconUrl(parsed.siteUrl ?: current.url),
                        lastUpdated = now,
                        lastRefreshTime = now,
                        lastRefreshError = null
                    )
                } else {
                    current.copy(
                        lastUpdated = now,
                        lastRefreshTime = now,
                        lastRefreshError = null
                    )
                }
            )
            feedDao.updateUnreadCount(feed.id)
            return null
        } catch (e: Exception) {
            val now = System.currentTimeMillis()
            val reason = (e as? FeedFetchException)?.message ?: (e.message ?: "未知错误")
            var sameAsLast = false
            runCatching {
                val current = feedDao.getById(feedId) ?: feed
                // per-feed 上次错误态：文案一模一样说明状态没变化，后台刷新不再重复播报
                sameAsLast = current.lastRefreshError == reason
                feedDao.update(
                    current.copy(
                        lastRefreshTime = now,
                        lastRefreshError = reason
                    )
                )
            }
            if (manual || !sameAsLast) {
                _fetchErrors.emit("刷新「${feed.title}」失败：$reason")
            }
            return reason
        }
    }

    suspend fun refreshAll(manual: Boolean = false) {
        val feeds = feedDao.observeAll().first()
        if (feeds.isEmpty()) return
        // 有界并发：串行逐个刷新在订阅源一多时整轮要等太久；
        // 又不能无限并发打爆源站与公共镜像，用信号量限流。
        // refreshFeed 内部已兜底错误，单源失败互不影响
        val gate = Semaphore(REFRESH_CONCURRENCY)
        coroutineScope {
            feeds.forEach { feed ->
                launch { gate.withPermit { refreshFeed(feed.id, manual) } }
            }
        }
    }

    /**
     * 按保留策略删除旧文章。只删已读、非收藏、非稍后读。
     * NEVER 直接返回 0。返回实际删除条数。
     */
    suspend fun cleanupOldArticles(
        retention: ArticleRetention,
        nowMillis: Long = System.currentTimeMillis()
    ): Int {
        val cutoff = ArticleCleanup.cutoffMillis(retention, nowMillis) ?: return 0
        return entryDao.deleteStaleRead(cutoff)
    }

    suspend fun deleteFeed(feed: Feed) {
        entryDao.deleteByFeed(feed.id)
        filterRuleDao.deleteByFeed(feed.id)
        feedDao.delete(feed)
    }

    /**
     * 新增关键字规则。空白关键字直接忽略。
     * 写入后立即回扫已有文章，使列表与未读数立刻生效。
     */
    suspend fun addFilterRule(keyword: String, action: String, feedId: Long? = null): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return false
        val resolvedAction = when (action) {
            FilterRule.ACTION_HIDE -> FilterRule.ACTION_HIDE
            else -> FilterRule.ACTION_MARK_READ
        }
        filterRuleDao.insert(
            FilterRule(keyword = trimmed, action = resolvedAction, feedId = feedId)
        )
        reapplyFilters(feedId)
        return true
    }

    suspend fun deleteFilterRule(id: Long) {
        val all = filterRuleDao.getAll()
        val target = all.firstOrNull { it.id == id } ?: return
        filterRuleDao.deleteById(id)
        reapplyFilters(target.feedId)
    }

    /**
     * 更新已有规则（例如两档动作切换）后立即回扫，使列表与未读数立刻跟上。
     */
    suspend fun updateFilterRule(rule: FilterRule) {
        if (rule.id <= 0L) return
        val trimmed = rule.keyword.trim()
        if (trimmed.isEmpty()) return
        val resolvedAction = when (rule.action) {
            FilterRule.ACTION_HIDE -> FilterRule.ACTION_HIDE
            else -> FilterRule.ACTION_MARK_READ
        }
        filterRuleDao.update(rule.copy(keyword = trimmed, action = resolvedAction))
        reapplyFilters(rule.feedId)
    }

    /** 源级开关：关闭后该源不再套用全局规则，专属规则仍生效 */
    suspend fun setApplyGlobalFilter(feedId: Long, apply: Boolean) {
        feedDao.setApplyGlobalFilter(feedId, apply)
        reapplyFilters(feedId)
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

    suspend fun setReadLater(entry: Entry, readLater: Boolean) {
        entryDao.setReadLater(entry.id, readLater)
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
     * 提取不到有效正文（原文是视频页/正文过短/提取或抓取失败）时不再让用户停在原地
     * 二次点「阅读原文」，而是返回 [FullContentResult.OpenOriginal] 让调用方直接打开原文页。
     */
    suspend fun loadFullContent(entry: Entry): FullContentResult {
        val link = entry.link
        if (link.isNullOrBlank()) {
            return FullContentResult.OpenOriginal("没有原文链接")
        }
        return try {
            val html = parser.fetchHtml(link)
            val extracted = ContentExtractor.extract(html)
            if (extracted.isBlank() || extracted.length <= (entry.content?.length ?: 0)) {
                FullContentResult.OpenOriginal("未能提取到更完整的正文")
            } else {
                val updated = entry.copy(content = extracted)
                entryDao.update(updated)
                FullContentResult.Extracted(updated)
            }
        } catch (e: Exception) {
            FullContentResult.OpenOriginal(e.message ?: "抓取原文失败")
        }
    }

    /**
     * 判断 RSS 内容是否只有摘要/被截断。
     * 双信号：① 正文尾部带明确截断标记（阅读全文/继续阅读/Read more 等）必判不完整；
     * ② 去标签后长度过短（< 400 字）保守视为摘要。
     * 正文够长又没有截断标记就当作完整全文，不再显示「加载全文」按钮要求用户二次点按。
     */
    fun isContentIncomplete(entry: Entry): Boolean {
        if (entry.link.isNullOrBlank()) return false
        val raw = entry.content ?: entry.summary ?: ""
        if (raw.isBlank()) return true
        val plain = stripTags(raw)
        if (TRUNCATION_MARKER.containsMatchIn(plain.takeLast(200))) return true
        return plain.length < 400
    }

    /** 常见 RSS 截断标记；只扫正文尾部，避免正文中间的正常用词误判 */
    private val TRUNCATION_MARKER = Regex(
        "阅读(全文|完整内容|余下内容|剩余.{0,6}(全文|内容))|继续阅读|查看(全文|原文|更多精彩)|" +
            "展开(全文|更多)|打开APP查看|read more|continue reading",
        RegexOption.IGNORE_CASE
    )

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
     * 返回 Pair(成功导入数，跳过数)。
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
        val rules = applicableRules(feedId)
        val newcomers = parsed.entries
            .filter { it.guid.isNotBlank() && it.guid !in existingGuids }
            .map { pe ->
                val action = ContentFilter.match(pe.title, rules)
                Entry(
                    feedId = feedId,
                    guid = pe.guid,
                    title = pe.title,
                    link = pe.link,
                    content = pe.content,
                    summary = buildSummary(pe),
                    author = pe.author,
                    publishedAt = pe.publishedAt,
                    fetchedAt = now,
                    imageUrl = pe.imageUrl,
                    isRead = action == FilterAction.MARK_READ,
                    isHidden = action == FilterAction.HIDE
                )
            }
        if (newcomers.isNotEmpty()) {
            entryDao.insertAll(newcomers)
        }
    }

    private suspend fun applicableRules(feedId: Long): List<FilterRule> {
        val feed = feedDao.getById(feedId)
        val applyGlobal = feed?.applyGlobalFilter ?: true
        return ContentFilter.rulesForFeed(feedId, applyGlobal, filterRuleDao.getAll())
    }

    /**
     * 规则变更后回扫文章：隐藏可随规则撤销，已读一旦标上不再改回未读。
     * [scopeFeedId] 为空表示全局规则变动，只回扫仍套用全局规则的源。
     */
    private suspend fun reapplyFilters(scopeFeedId: Long?) {
        val allRules = filterRuleDao.getAll()
        val feeds = if (scopeFeedId != null) {
            listOfNotNull(feedDao.getById(scopeFeedId))
        } else {
            feedDao.observeAll().first().filter { it.applyGlobalFilter }
        }
        for (feed in feeds) {
            val rules = ContentFilter.rulesForFeed(feed.id, feed.applyGlobalFilter, allRules)
            val entries = entryDao.getByFeed(feed.id)
            val dirty = mutableListOf<Entry>()
            for (entry in entries) {
                val action = ContentFilter.match(entry.title, rules)
                val hidden = action == FilterAction.HIDE
                val read = entry.isRead || action == FilterAction.MARK_READ
                if (hidden != entry.isHidden || read != entry.isRead) {
                    dirty += entry.copy(isHidden = hidden, isRead = read)
                }
            }
            if (dirty.isNotEmpty()) {
                entryDao.updateAll(dirty)
            }
            feedDao.updateUnreadCount(feed.id)
        }
    }

    /** 列表摘要：优先 summary，缺失时返回正文；去标签并截断，避免 HTML 片段直接进列表 */
    private fun buildSummary(parsed: ParsedEntry): String? {
        val raw = parsed.summary?.takeIf { it.isNotBlank() } ?: parsed.content.orEmpty()
        return stripTags(raw).take(MAX_SUMMARY_LENGTH).ifBlank { null }
    }

    private fun stripTags(html: String): String = html.replace(HTML_TAG, "").trim()

    private fun buildFaviconUrl(siteUrl: String): String {
        val host = FeedUrls.hostOf(siteUrl) ?: return ""
        return "https://icons.duckduckgo.com/ip3/$host.ico"
    }

    private companion object {
        const val MAX_SUMMARY_LENGTH = 200
        const val MAX_SEARCH_HISTORY = 20

        /** refreshAll 的并发上限：兼顾整轮耗时与对源站/镜像的礼貌 */
        const val REFRESH_CONCURRENCY = 4

        /** 去 HTML 标签，预编译复用（每次抓取要跑上百篇） */
        val HTML_TAG = Regex("<[^>]+>")
    }
}
