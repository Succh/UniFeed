package com.succh.unifeed.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.db.entity.Folder

@Composable
fun UniFeedApp(
    viewModel: UniFeedViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var showDiscover by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<Entry?>(null) }
    var restoreProgressOnOpen by remember { mutableStateOf(true)}
    var readerList by remember { mutableStateOf<List<Entry>>(emptyList())}
    var tab by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val prefs = remember { ReaderPrefs(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 抓取/刷新失败时用 Snackbar 通知用户，避免静默失败
    LaunchedEffect(state.error) {
        val message = state.error
        if (!message.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    fun updateRsshubInstance(instance: String?) {
        prefs.rsshubInstance = instance
        // 注意：必须用全限定名，避免与本文件的 Composable 函数 UniFeedApp 重名冲突
        (context.applicationContext as? com.succh.unifeed.UniFeedApp)
            ?.repository?.customRsshubInstance = instance
    }

    LaunchedEffect(state.selectedFeedId) {
        if (state.selectedFeedId != null) {
            tab = 1
            showDiscover = false
        }
    }

    if (showDiscover) {
        DiscoverScreen(
            isDiscovering = state.isDiscovering,
            discoveredFeeds = state.discoveredFeeds,
            discoveryError = state.discoveryError,
            isSubscribing = state.isSubscribing,
            subscribeError = state.subscribeError,
            onBack = {
                showDiscover = false
                viewModel.clearDiscovery()
                viewModel.clearError()
            },
            onDiscover = { viewModel.discoverFeeds(it) },
            onAddDirect = { viewModel.addFeed(it) },
            onSubscribe = { url, title -> viewModel.addFeed(url, title) },
            onClearError = { viewModel.clearError() },
            feedPreview = state.feedPreview,
            isPreviewing = state.isPreviewing,
            onPreviewUrl = { viewModel.previewFeed(it) },
            onConfirmSubscribe = { viewModel.confirmSubscribe() },
            onClearPreview = { viewModel.clearFeedPreview() },
            rsshubInstance = prefs.effectiveRsshubInstance,
            rsshubNamespaces = state.rsshubNamespaces,
            isLoadingRsshub = state.isLoadingRsshub,
            rsshubError = state.rsshubError,
            onLoadRsshub = { viewModel.loadRsshubCatalog() },
            onSubscribeRsshubRoute = { route, extra -> viewModel.subscribeRsshubRoute(route, extra) }
        )
        return
    }

    val entry = selectedEntry
    if (entry != null) {
        val feed = state.feeds.firstOrNull { it.id == entry.feedId }
        val liveEntry = state.entries.firstOrNull { it.id == entry.id}?: entry
        ReaderScreen(
            entry = liveEntry,
            prefs = prefs,
            feedTitle = feed?.title,
            feedFaviconUrl = feed?.faviconUrl,
            contentIncomplete = viewModel.isContentIncomplete(liveEntry),
            isLoadingFullContent = state.isLoadingFullContent,
            onLoadFullContent = {
                viewModel.loadFullContent(liveEntry) { updated -> selectedEntry = updated}
            },
            onBack = { selectedEntry = null },
            onToggleStar = { e, s -> viewModel.setStarred(e, s) },
            onOpenBrowser = { e ->
                e.link?.let {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
                }
            },
            onShare = { e ->
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "${e.title}\n${e.link}")
                }
                runCatching { context.startActivity(Intent.createChooser(sendIntent, "分享")) }
            },
            listEntries = readerList,
            restoreProgress = restoreProgressOnOpen,
            onSwitchEntry = { next ->
                restoreProgressOnOpen = false
                selectedEntry = next
            },
            onSaveProgress = { id, position -> viewModel.saveReadProgress(id, position) },
            onMarkRead = { e -> if (!e.isRead) viewModel.markRead(e) }
        )
    } else {
        MainTabScreen(
            tab = tab,
            onTabChange = { tab = it },
            state = state,
            prefs = prefs,
            snackbarHostState = snackbarHostState,
            onSelectFeed = { feedId ->
                viewModel.selectFeed(feedId)
                if (feedId != null) tab = 1
            },
            onAddFeed = { showDiscover = true },
            onDeleteFeed = viewModel::deleteFeed,
            onEntryClick = { clicked ->
                restoreProgressOnOpen = true
                readerList = if (tab == 2) state.entries.filter { it.isStarred } else state.entries
                selectedEntry = clicked
            },
            onStarToggle = viewModel::setStarred,
            onFilter = viewModel::setFilter,
            onRefresh = viewModel::refreshCurrentFeed,
            onMarkAllRead = viewModel::markAllRead,
            onRsshubInstanceChange = { updateRsshubInstance(it) },
            folders = state.folders,
            onCreateFolder = viewModel::createFolder,
            onRenameFolder = viewModel::renameFolder,
            onDeleteFolder = viewModel::deleteFolder,
            onMoveFeedToFolder = viewModel::moveFeedToFolder,
            onRenameFeed = viewModel::renameFeed,
            searchQuery = state.searchQuery,
            onSearchQueryChange = viewModel::setSearchQuery,
            onMarkRead = viewModel::markRead,
            onMarkUnread = viewModel::markUnread,
            onRefreshFeed = { feed -> viewModel.refreshFeedById(feed.id) }
        )
    }
}

@Composable
private fun MainTabScreen(
    tab: Int,
    onTabChange: (Int) -> Unit,
    state: UniFeedUiState,
    prefs: ReaderPrefs,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    onSelectFeed: (Long?) -> Unit,
    onAddFeed: () -> Unit,
    onDeleteFeed: (com.succh.unifeed.data.db.entity.Feed) -> Unit,
    onEntryClick: (Entry) -> Unit,
    onStarToggle: (Entry, Boolean) -> Unit,
    onFilter: (FilterMode) -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onRsshubInstanceChange: (String?) -> Unit = {},
    folders: List<Folder> = emptyList(),
    onCreateFolder: (String) -> Unit = {},
    onRenameFolder: (Long, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Long) -> Unit = {},
    onMoveFeedToFolder: (Long, Long?) -> Unit = { _, _ -> },
    onRenameFeed: (Long, String) -> Unit = { _, _ -> },
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onMarkRead: (Entry) -> Unit = {},
    onMarkUnread: (Entry) -> Unit = {},
    onRefreshFeed: (Feed) -> Unit = {}
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { onTabChange(0) },
                    icon = { Icon(Icons.Default.RssFeed, contentDescription = null) },
                    label = { Text("订阅源") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { onTabChange(1) },
                    icon = { Icon(Icons.Default.Article, contentDescription = null) },
                    label = { Text("文章") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { onTabChange(2) },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text("收藏") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { onTabChange(3) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> FeedListScreen(
                feeds = state.feeds,
                selectedFeedId = state.selectedFeedId,
                onSelectFeed = onSelectFeed,
                onAddFeed = onAddFeed,
                onDeleteFeed = onDeleteFeed,
                folders = folders,
                onCreateFolder = onCreateFolder,
                onRenameFolder = onRenameFolder,
                onDeleteFolder = onDeleteFolder,
                onMoveFeedToFolder = onMoveFeedToFolder,
                onRenameFeed = onRenameFeed,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onRefreshFeed = onRefreshFeed,
                latestTitles = state.latestTitles,
                modifier = Modifier.padding(padding)
            )
            1 -> {
                val headerFeed = if (state.filterMode == FilterMode.ALL) {
                    state.selectedFeedId?.let { id -> state.feeds.firstOrNull { it.id == id } }
                } else null
                EntryListScreen(
                    entries = state.entries,
                    feeds = state.feeds,
                    onEntryClick = onEntryClick,
                    onStarToggle = onStarToggle,
                    showSummary = prefs.listShowSummary,
                    modifier = Modifier.padding(padding),
                    onRefresh = onRefresh,
                    isRefreshing = state.isLoading,
                    onFilter = onFilter,
                    filterMode = state.filterMode,
                    onMarkAllRead = onMarkAllRead,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    onMarkRead = onMarkRead,
                    onMarkUnread = onMarkUnread,
                    headerFeed = headerFeed,
                    headerEntryCount = state.entries.size,
                    hasLoadedEntries = state.hasLoadedEntries,
                    hasLocalEntries = state.hasLocalEntries,
                    onAddFeed = onAddFeed
                )
            }
            2 -> EntryListScreen(
                entries = state.entries.filter { it.isStarred },
                feeds = state.feeds,
                onEntryClick = onEntryClick,
                onStarToggle = onStarToggle,
                showSummary = prefs.listShowSummary,
                modifier = Modifier.padding(padding),
                onRefresh = onRefresh,
                isRefreshing = state.isLoading,
                showFilter = false,
                filterMode = FilterMode.STARRED,
                hasLoadedEntries = state.hasLoadedEntries,
                hasLocalEntries = state.hasLocalEntries
            )
            3 -> SettingsScreen(
                prefs = prefs,
                modifier = Modifier.padding(padding),
                onRsshubInstanceChange = onRsshubInstanceChange
            )
        }
    }
}