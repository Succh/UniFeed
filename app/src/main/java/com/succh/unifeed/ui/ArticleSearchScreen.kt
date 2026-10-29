package com.succh.unifeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.succh.unifeed.data.db.entity.Entry
import com.succh.unifeed.data.db.entity.Feed
import com.succh.unifeed.data.search.EntrySearch
import com.succh.unifeed.ui.theme.FoloDimens
import com.succh.unifeed.ui.theme.formatRelativeTimeZh
import com.succh.unifeed.ui.theme.glassCardBorder
import com.succh.unifeed.ui.theme.glassCardColors
import com.succh.unifeed.ui.theme.glassSurface
import com.succh.unifeed.ui.theme.glassTextFieldColors
import com.succh.unifeed.ui.theme.glassTopAppBarColors

/**
 * 文章列表 / 发现页顶部的全文搜索入口，点击进入独立搜索页。
 */
@Composable
fun ArticleSearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索全部已订阅文章"
) {
    Row(
        modifier = modifier
            .glassSurface(shape = RoundedCornerShape(FoloDimens.SearchRadius), elevation = 4.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 全文搜索页：覆盖全部已订阅源的标题、摘要、正文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleSearchScreen(
    query: String,
    results: List<Entry>,
    feeds: List<Feed>,
    isSearching: Boolean,
    showSummary: Boolean,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onEntryClick: (Entry) -> Unit,
    onStarToggle: (Entry, Boolean) -> Unit
) {
    val feedById = remember(feeds) { feeds.associateBy { it.id } }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("搜索文章") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = glassTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("搜索全部已订阅文章") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                shape = RoundedCornerShape(FoloDimens.SearchRadius),
                colors = glassTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = FoloDimens.ScreenHorizontal, vertical = 8.dp)
                    .glassSurface(shape = RoundedCornerShape(FoloDimens.SearchRadius), elevation = 4.dp)
                    .focusRequester(focusRequester)
            )
            when {
                query.isBlank() -> {
                    SearchHint(
                        text = "输入关键词，搜索所有已订阅源的标题、摘要和正文",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isSearching && results.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                results.isEmpty() -> {
                    SearchHint(
                        text = "没有匹配的文章，试试其他关键词",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = FoloDimens.ScreenHorizontal,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(FoloDimens.CardSpacing)
                    ) {
                        item(key = "count") {
                            Text(
                                text = "共 ${results.size} 条结果",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(results, key = { it.id }) { entry ->
                            val feed = feedById[entry.feedId]
                            SearchResultCard(
                                entry = entry,
                                feedTitle = feed?.title.orEmpty(),
                                feedFaviconUrl = feed?.faviconUrl,
                                query = query,
                                showSummary = showSummary,
                                onClick = { onEntryClick(entry) },
                                onStar = { onStarToggle(entry, !entry.isStarred) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Inbox,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchResultCard(
    entry: Entry,
    feedTitle: String,
    feedFaviconUrl: String?,
    query: String,
    showSummary: Boolean,
    onClick: () -> Unit,
    onStar: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val snippet = remember(entry.title, entry.summary, entry.content, query) {
        EntrySearch.resultSnippet(entry.title, entry.summary, entry.content, query)
    }
    val highlightedTitle = rememberHighlightedText(entry.title, query)
    val highlightedSnippet = rememberHighlightedText(snippet, query)
    val relativeTime = remember(entry.publishedAt) { formatRelativeTimeZh(entry.publishedAt) }
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
                .padding(FoloDimens.CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                    text = highlightedTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (entry.isRead) FontWeight.Normal else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (snippet.isNotBlank() && (showSummary || query.isNotBlank())) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = highlightedSnippet,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = relativeTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
            if (!entry.imageUrl.isNullOrBlank()) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = entry.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(
                            width = FoloDimens.EntryCoverWidth,
                            height = FoloDimens.EntryCoverHeight
                        )
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            IconButton(onClick = onStar, modifier = Modifier.size(36.dp)) {
                Icon(
                    if (entry.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏",
                    tint = if (entry.isStarred) Color(0xFFFFB300) else colors.onSurfaceVariant
                )
            }
        }
    }
}
