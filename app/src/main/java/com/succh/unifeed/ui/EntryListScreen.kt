package com.succh.unifeed.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.ui.theme.FoloDimens
import com.succh.unifeed.ui.theme.formatRelativeTimeZh
import com.succh.unifeed.ui.theme.glassCardBorder
import com.succh.unifeed.ui.theme.glassCardColors
import com.succh.unifeed.ui.theme.glassChipBorder
import com.succh.unifeed.ui.theme.glassChipColors
import com.succh.unifeed.ui.theme.glassSurface
import kotlinx.coroutines.flow.distinctUntilChanged

/** 滚动时向视口下方预取的封面数量 */
private const val COVER_PREFETCH_COUNT = 8

/** 骨架屏占位卡片数量，覆盖首屏即可 */
private const val SKELETON_CARD_COUNT = 8

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryListScreen(
    entries: List<Entry>,
    onEntryClick: (Entry) -> Unit,
    onStarToggle: (Entry, Boolean) -> Unit,
    showSummary: Boolean = false,
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    onFilter: (FilterMode) -> Unit = {},
    filterMode: FilterMode = FilterMode.ALL,
    onMarkAllRead: () -> Unit = {},
    showFilter: Boolean = true,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onOpenArticleSearch: () -> Unit = {},
    onMarkRead: (Entry) -> Unit = {},
    onMarkUnread: (Entry) -> Unit = {},
    feeds: List<Feed> = emptyList(),
    headerFeed: Feed? = null,
    headerEntryCount: Int = 0,
    hasLoadedEntries: Boolean = true,
    hasLocalEntries: Boolean = true,
    onAddFeed: () -> Unit = {}
) {
    // 用 feedId 索引订阅源，供卡片顶部展示名称与 favicon
    val feedById = remember(feeds) { feeds.associateBy { it.id } }
    val listState = rememberLazyListState()
    // 首次加载或刷新且本地无数据时走骨架屏，避免空白闪一下再出列表
    val showSkeleton = entries.isEmpty() &&
        (!hasLoadedEntries || (isRefreshing && !hasLocalEntries))
    val hasUnread = remember(entries) { entries.any { !it.isRead } }

    CoverPrefetcher(
        entries = entries,
        listState = listState,
        headerCount = if (headerFeed != null) 1 else 0
    )

    PullToRefreshBox(
        isRefreshing = isRefreshing && !showSkeleton,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ArticleSearchEntry(
                onClick = onOpenArticleSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FoloDimens.ScreenHorizontal, vertical = 8.dp)
            )
            if (showFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = filterMode == FilterMode.ALL,
                        onClick = { onFilter(FilterMode.ALL) },
                        label = { Text("全部") },
                        colors = glassChipColors(),
                        border = glassChipBorder(filterMode == FilterMode.ALL)
                    )
                    FilterChip(
                        selected = filterMode == FilterMode.UNREAD,
                        onClick = { onFilter(FilterMode.UNREAD) },
                        label = { Text("未读") },
                        colors = glassChipColors(),
                        border = glassChipBorder(filterMode == FilterMode.UNREAD)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (hasUnread) {
                        TextButton(onClick = onMarkAllRead) {
                            Text("全部标为已读")
                        }
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = FoloDimens.ScreenHorizontal,
                    vertical = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(FoloDimens.CardSpacing)
            ) {
                if (headerFeed != null && !showSkeleton) {
                    item(key = "feed_header", contentType = "feed_header") {
                        FeedInfoHeader(feed = headerFeed, entryCount = headerEntryCount)
                    }
                }
                when {
                    showSkeleton -> {
                        item(key = "skeleton_list", contentType = "skeleton") {
                            // 闪烁动画提到列表层，8 张占位卡共用同一拍，避免各自开无限动画
                            val infinite = rememberInfiniteTransition(label = "entry_skeleton")
                            val pulseAlpha by infinite.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 0.72f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "entry_skeleton_alpha"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(FoloDimens.CardSpacing)) {
                                repeat(SKELETON_CARD_COUNT) {
                                    EntrySkeletonCard(alpha = pulseAlpha)
                                }
                            }
                        }
                    }
                    entries.isEmpty() -> {
                        item(key = "empty", contentType = "empty") {
                            EntryEmptyState(
                                filterMode = filterMode,
                                searchQuery = "",
                                onAddFeed = onAddFeed,
                                modifier = Modifier.fillParentMaxSize()
                            )
                        }
                    }
                    else -> {
                        items(
                            items = entries,
                            key = { it.id },
                            contentType = { "entry" }
                        ) { entry ->
                            val feed = feedById[entry.feedId]
                            SwipeableEntryItem(
                                entry = entry,
                                feedTitle = feed?.title.orEmpty(),
                                feedFaviconUrl = feed?.faviconUrl,
                                showSummary = showSummary,
                                onClick = { onEntryClick(entry) },
                                onStar = { onStarToggle(entry, !entry.isStarred) },
                                onMarkRead = { onMarkRead(entry) },
                                onMarkUnread = { onMarkUnread(entry) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 根据当前可见范围，用 Coil 预取即将进入视口的封面，减少滚到才加载的空白。
 */
@Composable
private fun CoverPrefetcher(
    entries: List<Entry>,
    listState: LazyListState,
    headerCount: Int
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverWpx = with(density) { FoloDimens.EntryCoverWidth.roundToPx() }
    val coverHpx = with(density) { FoloDimens.EntryCoverHeight.roundToPx() }

    LaunchedEffect(entries, headerCount, coverWpx, coverHpx) {
        val imageLoader = context.imageLoader
        fun enqueueFrom(startEntryIndex: Int) {
            val end = (startEntryIndex + COVER_PREFETCH_COUNT).coerceAtMost(entries.size)
            if (startEntryIndex >= end) return
            for (i in startEntryIndex until end) {
                val url = entries[i].imageUrl
                if (url.isNullOrBlank()) continue
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .size(coverWpx, coverHpx)
                        .memoryCacheKey(url)
                        .build()
                )
            }
        }
        enqueueFrom(0)
        snapshotFlow {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            (lastVisible - headerCount + 1).coerceAtLeast(0)
        }
            .distinctUntilChanged()
            .collect { nextIndex -> enqueueFrom(nextIndex) }
    }
}

@Composable
private fun EntrySkeletonCard(alpha: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FoloDimens.CardRadius),
        colors = glassCardColors(),
        border = glassCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(FoloDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SkeletonBlock(
                    modifier = Modifier
                        .width(88.dp)
                        .height(12.dp),
                    alpha = alpha
                )
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(18.dp),
                    alpha = alpha
                )
                Spacer(Modifier.height(8.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(18.dp),
                    alpha = alpha
                )
                Spacer(Modifier.height(10.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(12.dp),
                    alpha = alpha
                )
                Spacer(Modifier.height(6.dp))
                SkeletonBlock(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(12.dp),
                    alpha = alpha
                )
            }
            Spacer(Modifier.width(12.dp))
            SkeletonBlock(
                modifier = Modifier.size(
                    width = FoloDimens.EntryCoverWidth,
                    height = FoloDimens.EntryCoverHeight
                ),
                alpha = alpha,
                corner = 8.dp
            )
        }
    }
}

@Composable
private fun SkeletonBlock(
    modifier: Modifier,
    alpha: Float,
    corner: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

@Composable
private fun EntryEmptyState(
    filterMode: FilterMode,
    searchQuery: String,
    onAddFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val icon: ImageVector
    val title: String
    val subtitle: String
    val showAddAction: Boolean
    when {
        searchQuery.isNotBlank() -> {
            icon = Icons.Default.Inbox
            title = "没有匹配的文章"
            subtitle = "试试其他关键词，或清空搜索"
            showAddAction = false
        }
        filterMode == FilterMode.UNREAD -> {
            icon = Icons.Filled.MarkEmailRead
            title = "已全部读完"
            subtitle = "当前没有未读文章，去看看全部列表吧"
            showAddAction = false
        }
        filterMode == FilterMode.STARRED -> {
            icon = Icons.Default.StarBorder
            title = "还没有收藏"
            subtitle = "在文章上点星标，就可以在这里找到它们"
            showAddAction = false
        }
        else -> {
            icon = Icons.Default.RssFeed
            title = "还没有文章"
            subtitle = "添加订阅源后，最新内容会出现在这里"
            showAddAction = true
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (showAddAction) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAddFeed) {
                Text("添加订阅源")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEntryItem(
    entry: Entry,
    feedTitle: String,
    feedFaviconUrl: String?,
    showSummary: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    // 右滑：切换已读/未读，不真正移除条目
                    if (entry.isRead) onMarkUnread() else onMarkRead()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    // 左滑：收藏/取消收藏
                    onStar()
                    false
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
            val icon = when (direction) {
                SwipeToDismissBoxValue.StartToEnd ->
                    if (entry.isRead) Icons.Default.MarkEmailUnread else Icons.Default.MarkEmailRead
                SwipeToDismissBoxValue.EndToStart ->
                    if (entry.isStarred) Icons.Default.StarOutline else Icons.Default.Star
                else -> null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(FoloDimens.CardRadius))
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.CenterEnd
                }
            ) {
                icon?.let {
                    Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    ) {
        EntryItem(
            entry = entry,
            feedTitle = feedTitle,
            feedFaviconUrl = feedFaviconUrl,
            showSummary = showSummary,
            onClick = onClick,
            onStar = onStar
        )
    }
}

@Composable
private fun FeedInfoHeader(feed: Feed, entryCount: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(shape = RoundedCornerShape(FoloDimens.CardRadius), elevation = 4.dp)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!feed.faviconUrl.isNullOrBlank()) {
            AsyncImage(
                model = feed.faviconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Icon(
                Icons.Default.RssFeed,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = colors.primary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(feed.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                append("${entryCount} 篇")
                append(" · ${feed.unreadCount} 未读")
                if (feed.lastRefreshTime > 0L) {
                    append(" · ${formatRelativeTimeZh(feed.lastRefreshTime)}")
                }
            }
            Text(meta, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            if (!feed.lastRefreshError.isNullOrBlank()) {
                Text(feed.lastRefreshError, style = MaterialTheme.typography.labelSmall, color = colors.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EntryItem(
    entry: Entry,
    feedTitle: String,
    feedFaviconUrl: String?,
    showSummary: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // 已读只用字重区分，避免整卡半透明导致玻璃底上文字发灰
    val imageUrl = entry.imageUrl
    var showThumb by remember(entry.id, imageUrl) { mutableStateOf(!imageUrl.isNullOrBlank()) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val coverWpx = with(density) { FoloDimens.EntryCoverWidth.roundToPx() }
    val coverHpx = with(density) { FoloDimens.EntryCoverHeight.roundToPx() }
    val coverRequest = remember(imageUrl, coverWpx, coverHpx) {
        if (imageUrl.isNullOrBlank()) {
            null
        } else {
            ImageRequest.Builder(context)
                .data(imageUrl)
                .size(coverWpx, coverHpx)
                .memoryCacheKey(imageUrl)
                .crossfade(true)
                .build()
        }
    }
    val relativeTime = remember(entry.publishedAt) { formatRelativeTimeZh(entry.publishedAt) }
    val summary = entry.summary.orEmpty()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(FoloDimens.CardRadius),
        colors = glassCardColors(),
        border = glassCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (!entry.isRead) {
                Box(
                    modifier = Modifier
                        .width(FoloDimens.UnreadBarWidth)
                        .fillMaxHeight()
                        .background(colors.primary)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = FoloDimens.CardPadding,
                        top = 12.dp,
                        end = 4.dp,
                        bottom = 12.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!feedFaviconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = feedFaviconUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(FoloDimens.EntryFaviconSize)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    } else {
                        Icon(
                            Icons.Default.RssFeed,
                            contentDescription = null,
                            modifier = Modifier.size(FoloDimens.EntryFaviconSize),
                            tint = colors.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = feedTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (entry.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (showSummary && summary.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                color = if (entry.isRead) colors.outline else colors.primary,
                                shape = CircleShape
                            )
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onStar, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (entry.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "收藏",
                            tint = if (entry.isStarred) Color(0xFFFFB300) else colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            if (showThumb && coverRequest != null) {
                AsyncImage(
                    model = coverRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 12.dp, top = 12.dp, bottom = 12.dp)
                        .size(
                            width = FoloDimens.EntryCoverWidth,
                            height = FoloDimens.EntryCoverHeight
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .align(Alignment.CenterVertically),
                    contentScale = ContentScale.Crop,
                    onError = { _ -> showThumb = false }
                )
            }
        }
    }
}
