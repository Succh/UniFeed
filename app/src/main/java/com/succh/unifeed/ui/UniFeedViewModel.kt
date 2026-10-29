package com.succh.unifeed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.succh.unifeed.UniFeedApp
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.Folder
import com.succh.unifeed.data.model.FeedPreview
import com.succh.unifeed.data.opml.OpmlManager
import com.succh.unifeed.data.rss.DiscoveredFeed
import com.succh.unifeed.data.rss.RsshubCatalog
import com.succh.unifeed.data.rss.RsshubDocRoute
import com.succh.unifeed.data.rss.RsshubNamespace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

data class UniFeedUiState(
    val feeds: List<Feed> = emptyList(),
    val entries: List<Entry> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    /** Room 文章表是否已完成首次发射，用于区分「尚未加载」与「已加载但为空」 */
    val hasLoadedEntries: Boolean = false,
    /** 本地是否已有任意文章（与当前筛选无关），用于刷新空库时展示骨架屏 */
    val hasLocalEntries: Boolean = false,
    val error: String? = null,
    val selectedFeedId: Long? = null,
    val filterMode: FilterMode = FilterMode.ALL,
    val isDiscovering: Boolean = false,
    val discoveredFeeds: List<DiscoveredFeed> = emptyList(),
    val discoveryError: String? = null,
    val isSubscribing: Boolean = false,
    val subscribeError: String? = null,
    val folders: List<Folder> = emptyList(),
    val searchQuery: String = "",
    val feedPreview: FeedPreview? = null,
    val isPreviewing: Boolean = false,
    val isLoadingFullContent: Boolean = false,
    val latestTitles: Map<Long, String> = emptyMap(),
    val rsshubNamespaces: List<RsshubNamespace> = emptyList(),
    val isLoadingRsshub: Boolean = false,
    val rsshubError: String? = null,
    /** 是否展示全文搜索页（覆盖当前列表/发现页） */
    val showArticleSearch: Boolean = false,
    /** 全文搜索输入，独立于订阅源列表筛选 */
    val articleSearchQuery: String = "",
    val articleSearchResults: List<Entry> = emptyList(),
    val isSearchingArticles: Boolean = false
)

enum class FilterMode { ALL, UNREAD, STARRED }

class UniFeedViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as UniFeedApp).repository

    private val _uiState = MutableStateFlow(UniFeedUiState())
    val uiState: StateFlow<UniFeedUiState> = _uiState.asStateFlow()

    /** 全文搜索防抖任务，输入变化时取消上一次查询 */
    private var articleSearchJob: Job? = null

    init {
        viewModelScope.launch {
            repo.observeFeeds().collect { feeds ->
                _uiState.update { it.copy(feeds = feeds) }
            }
        }
        viewModelScope.launch {
            repo.observeUnreadCount().collect { count ->
                _uiState.update { it.copy(unreadCount = count) }
            }
        }
        viewModelScope.launch {
            repo.observeFolders().collect { folders ->
                _uiState.update { it.copy(folders = folders) }
            }
        }
        viewModelScope.launch {
            combine(
                repo.observeAllEntries(),
                _uiState.map { it.filterMode },
                _uiState.map { it.selectedFeedId }
            ) { allEntries, filter, feedId ->
                val filtered = when (filter) {
                    FilterMode.UNREAD -> allEntries.filter { !it.isRead }
                    FilterMode.STARRED -> allEntries.filter { it.isStarred }
                    FilterMode.ALL -> if (feedId != null) allEntries.filter { it.feedId == feedId } else allEntries
                }
                // 侧边栏「最新一篇」标题：与列表同一次全表读取算出，省掉第二条 Flow 的重复查询
                val latestTitles = allEntries.groupBy { it.feedId }
                    .mapValues { (_, list) -> list.maxByOrNull { it.publishedAt }?.title.orEmpty() }
                // 同一拍带上「库里有没有文章」，避免空态/骨架屏和列表数据错位
                EntrySnapshot(filtered, allEntries.isNotEmpty(), latestTitles)
            }.collect { snapshot ->
                _uiState.update {
                    it.copy(
                        entries = snapshot.entries,
                        hasLoadedEntries = true,
                        hasLocalEntries = snapshot.hasLocalEntries,
                        latestTitles = snapshot.latestTitles
                    )
                }
            }
        }
        viewModelScope.launch {
            repo.fetchErrors.collect { message ->
                _uiState.update { it.copy(error = message, isLoading = false) }
            }
        }
    }

    fun discoverFeeds(input: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true, discoveryError = null, discoveredFeeds = emptyList(), feedPreview = null) }
            val result = repo.discoverFeeds(input)
            result.onSuccess { feeds ->
                _uiState.update { it.copy(isDiscovering = false, discoveredFeeds = feeds) }
            }.onFailure { e ->
                _uiState.update { it.copy(isDiscovering = false, discoveryError = e.message ?: "探测失败") }
            }
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch { repo.createFolder(name) }
    }

    fun renameFolder(id: Long, name: String) {
        viewModelScope.launch { repo.renameFolder(id, name) }
    }

    fun deleteFolder(id: Long) {
        viewModelScope.launch { repo.deleteFolder(id) }
    }

    fun moveFeedToFolder(feedId: Long, folderId: Long?) {
        viewModelScope.launch { repo.moveFeedToFolder(feedId, folderId) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openArticleSearch() {
        _uiState.update { it.copy(showArticleSearch = true) }
    }

    fun closeArticleSearch() {
        articleSearchJob?.cancel()
        _uiState.update {
            it.copy(
                showArticleSearch = false,
                articleSearchQuery = "",
                articleSearchResults = emptyList(),
                isSearchingArticles = false
            )
        }
    }

    /**
     * 全文搜索：覆盖全部已订阅源的标题、摘要、正文，不跟随当前列表筛选。
     * 输入做短防抖，避免每个按键都打库。
     */
    fun setArticleSearchQuery(query: String) {
        _uiState.update { it.copy(articleSearchQuery = query) }
        articleSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(articleSearchResults = emptyList(), isSearchingArticles = false) }
            return
        }
        articleSearchJob = viewModelScope.launch {
            delay(ARTICLE_SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearchingArticles = true) }
            val results = repo.searchEntries(trimmed)
            _uiState.update { it.copy(articleSearchResults = results, isSearchingArticles = false) }
        }
    }

    fun renameFeed(feedId: Long, newTitle: String) {
        viewModelScope.launch { repo.renameFeed(feedId, newTitle) }
    }

    fun clearDiscovery() {
        _uiState.update {
            it.copy(
                isDiscovering = false,
                discoveredFeeds = emptyList(),
                discoveryError = null,
                feedPreview = null,
                isPreviewing = false
            )
        }
    }

    /** 输入 URL 后先抓取解析展示预览，此阶段不入库 */
    fun previewFeed(url: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPreviewing = true,
                    subscribeError = null,
                    feedPreview = null,
                    discoveryError = null
                )
            }
            val result = repo.previewFeed(url.trim())
            result.onSuccess { preview ->
                _uiState.update { it.copy(isPreviewing = false, feedPreview = preview) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isPreviewing = false, subscribeError = e.message ?: "预览失败")
                }
            }
        }
    }

    /** 用户点「订阅」才真正插入 Feed 并保存文章 */
    fun confirmSubscribe() {
        val preview = _uiState.value.feedPreview ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubscribing = true, subscribeError = null) }
            val result = repo.subscribePreview(preview)
            result.onSuccess { feed ->
                _uiState.update {
                    it.copy(
                        isSubscribing = false,
                        selectedFeedId = feed.id,
                        feedPreview = null
                    )
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isSubscribing = false, subscribeError = e.message ?: "订阅失败") }
            }
        }
    }

    fun clearFeedPreview() {
        _uiState.update { it.copy(feedPreview = null, isPreviewing = false) }
    }

    fun loadRsshubCatalog() {
        if (_uiState.value.rsshubNamespaces.isNotEmpty() || _uiState.value.isLoadingRsshub) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRsshub = true, rsshubError = null) }
            val result = repo.loadRsshubCatalog()
            result.onSuccess { list ->
                _uiState.update { it.copy(isLoadingRsshub = false, rsshubNamespaces = list) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoadingRsshub = false, rsshubError = e.message ?: "无法加载 RSSHub 路由库")
                }
            }
        }
    }

    fun subscribeRsshubRoute(route: RsshubDocRoute, extraParam: String = "") {
        val url = RsshubCatalog.buildSubscribeUrl(repo.effectiveRsshubInstance(), route, extraParam)
        val title = if (extraParam.isNotBlank()) "${route.nsName} · ${route.title}: ${extraParam.trim()}"
        else "${route.nsName} · ${route.title}"
        addFeed(url, title)
    }

    /**
     * 添加订阅：RSSHub 源走即时订阅（先落库、再单次抓取补齐元数据），普通源走预览流程
     */
    fun addFeed(url: String, titleHint: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubscribing = true, subscribeError = null) }
            val trimmed = repo.resolveFeedUrl(url)
            if (repo.isRsshubUrl(trimmed)) {
                val result = repo.addFeedInstant(trimmed, titleHint)
                result.onSuccess { feed ->
                    _uiState.update {
                        it.copy(isSubscribing = false, selectedFeedId = feed.id, isLoading = true)
                    }
                    repo.refreshFeed(feed.id)
                    _uiState.update { it.copy(isLoading = false) }
                }.onFailure { e ->
                    _uiState.update { it.copy(isSubscribing = false, subscribeError = e.message ?: "订阅失败") }
                }
                return@launch
            }
            val looksLikeFeed = trimmed.endsWith(".xml") || trimmed.endsWith(".rss") ||
                trimmed.endsWith(".atom") || trimmed.contains("/feed")
            val feedUrl = if (looksLikeFeed) {
                trimmed
            } else {
                repo.discoverFeeds(trimmed).getOrNull()?.firstOrNull()?.feedUrl ?: trimmed
            }
            _uiState.update { it.copy(isSubscribing = false) }
            previewFeed(feedUrl)
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.refreshAll()
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshCurrentFeed() {
        viewModelScope.launch {
            val feedId = _uiState.value.selectedFeedId
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (feedId != null) {
                repo.refreshFeed(feedId)
            } else {
                repo.refreshAll()
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun refreshFeedById(feedId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repo.refreshFeed(feedId)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun deleteFeed(feed: Feed) {
        viewModelScope.launch {
            repo.deleteFeed(feed)
        }
    }

    fun markRead(entry: Entry) {
        viewModelScope.launch { repo.markRead(entry) }
    }

    fun saveReadProgress(entryId: Long, position: Float) {
        viewModelScope.launch { repo.saveReadProgress(entryId, position) }
    }

    fun markUnread(entry: Entry) {
        viewModelScope.launch { repo.markUnread(entry) }
    }

    fun setStarred(entry: Entry, starred: Boolean) {
        viewModelScope.launch { repo.setStarred(entry, starred) }
    }

    fun selectFeed(feedId: Long?) {
        _uiState.update { it.copy(selectedFeedId = feedId, filterMode = FilterMode.ALL) }
    }

    fun setFilter(mode: FilterMode) {
        _uiState.update { it.copy(filterMode = mode, selectedFeedId = null) }
    }

    fun markAllRead() {
        viewModelScope.launch {
            repo.markAllReadAll()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, subscribeError = null) }
    }

    fun isContentIncomplete(entry: Entry): Boolean = repo.isContentIncomplete(entry)

    fun loadFullContent(entry: Entry, onUpdated: (Entry) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingFullContent = true, error = null) }
            val result = repo.loadFullContent(entry)
            result.onSuccess { updated ->
                _uiState.update { it.copy(isLoadingFullContent = false) }
                onUpdated(updated)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isLoadingFullContent = false, error = e.message ?: "加载全文失败")
                }
            }
        }
    }

    suspend fun exportOpml(): String = withContext(Dispatchers.IO) {
        val feeds = repo.observeFeeds().first()
        val folders = repo.observeFolders().first()
        OpmlManager.export(feeds, folders)
    }

    suspend fun importOpml(inputStream: InputStream): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val list = OpmlManager.parse(inputStream)
        repo.importFeeds(list)
    }
}

private const val ARTICLE_SEARCH_DEBOUNCE_MS = 250L

/** 一次全表读取算出的列表派生数据，供 [UniFeedViewModel] 合并成单条 StateFlow */
private data class EntrySnapshot(
    val entries: List<Entry>,
    val hasLocalEntries: Boolean,
    val latestTitles: Map<Long, String>
)
