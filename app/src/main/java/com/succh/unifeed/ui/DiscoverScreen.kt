package com.succh.unifeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.succh.unifeed.data.model.FeedPreview
import com.succh.unifeed.data.rss.DiscoveredFeed
import com.succh.unifeed.data.rss.RsshubCatalog
import com.succh.unifeed.data.rss.RsshubDocRoute
import com.succh.unifeed.data.rss.RsshubEndpoints
import com.succh.unifeed.data.rss.RsshubNamespace
import com.succh.unifeed.ui.theme.FoloDimens
import com.succh.unifeed.ui.theme.glassCardBorder
import com.succh.unifeed.ui.theme.glassCardColors
import com.succh.unifeed.ui.theme.glassSurface
import com.succh.unifeed.ui.theme.glassTextFieldColors
import com.succh.unifeed.ui.theme.glassTopAppBarColors

/** 发现页卡片状态：HIDDEN 表示本次访问已添加、应从列表移除；SUBSCRIBED 表示订阅库里已有 */
private enum class DiscoverCardState { ADDABLE, SUBSCRIBED, HIDDEN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    isDiscovering: Boolean,
    discoveredFeeds: List<DiscoveredFeed>,
    discoveryError: String?,
    isSubscribing: Boolean,
    subscribeError: String?,
    onBack: () -> Unit,
    onDiscover: (String) -> Unit,
    onAddDirect: (String, String?) -> Unit,
    onClearError: () -> Unit,
    subscribedUrls: Set<String> = emptySet(),
    feedPreview: FeedPreview? = null,
    isPreviewing: Boolean = false,
    onPreviewUrl: (String) -> Unit = {},
    onConfirmSubscribe: () -> Unit = {},
    onClearPreview: () -> Unit = {},
    rsshubInstance: String = "https://rsshub.app",
    rsshubNamespaces: List<RsshubNamespace> = emptyList(),
    isLoadingRsshub: Boolean = false,
    rsshubError: String? = null,
    onLoadRsshub: () -> Unit = {},
    onSubscribeRsshubRoute: (RsshubDocRoute, String) -> Unit = { _, _ -> },
    onOpenArticleSearch: () -> Unit = {}
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var presetCat by remember { mutableStateOf<BuiltinCategory?>(null) }
    var rsshubCat by remember { mutableStateOf<String?>(null) }
    var rsshubNs by remember { mutableStateOf<RsshubNamespace?>(null) }
    var pendingRoute by remember { mutableStateOf<RsshubRoute?>(null) }
    var pendingDoc by remember { mutableStateOf<RsshubDocRoute?>(null) }
    var blockedFeed by remember { mutableStateOf<BuiltinFeed?>(null) }
    var param by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var urlQuery by remember { mutableStateOf("") }

    // 本次访问里通过「+」或右滑添加的源：卡片立即消失，避免重复提供添加
    val justAdded = remember { mutableStateListOf<String>() }

    LaunchedEffect(activeTab, query) {
        if (activeTab == 1 || query.isNotBlank()) onLoadRsshub()
    }

    fun goBack() {
        when {
            rsshubNs != null -> rsshubNs = null
            rsshubCat != null -> rsshubCat = null
            presetCat != null -> presetCat = null
            else -> onBack()
        }
    }

    BackHandler { goBack() }

    // 精选目录里的相对路径按当前生效实例拼接；与仓库端 resolveFeedUrl 同源，才能对上 DB 里存的 URL
    fun resolveUrl(raw: String): String =
        if (raw.startsWith("/")) rsshubInstance.trimEnd('/') + raw else raw

    fun cardState(rawUrl: String): DiscoverCardState = when {
        resolveUrl(rawUrl) in justAdded -> DiscoverCardState.HIDDEN
        resolveUrl(rawUrl) in subscribedUrls -> DiscoverCardState.SUBSCRIBED
        else -> DiscoverCardState.ADDABLE
    }

    fun subscribeRaw(raw: String, title: String?) {
        val url = resolveUrl(raw)
        // 只有即时订阅（同步落库）的 RSSHub 地址才立刻隐藏卡片；官方源走预览框，不假装成功
        if (RsshubEndpoints.isRsshubUrl(url)) justAdded.add(url)
        onAddDirect(url, title)
    }

    fun subscribeDocRoute(route: RsshubDocRoute, extra: String) {
        justAdded.add(RsshubCatalog.buildSubscribeUrl(rsshubInstance, route, extra))
        onSubscribeRsshubRoute(route, extra)
    }

    // 不可用源不再是死点击：点开就展示失败原因与换实例建议
    fun builtinTap(feed: BuiltinFeed) {
        if (feed.failureHint != null) blockedFeed = feed else onPreviewUrl(resolveUrl(feed.url))
    }

    pendingRoute?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingRoute = null },
            title = { Text(r.title) },
            text = {
                Column {
                    Text(
                        r.description.ifBlank { "该路由需要补充参数" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        param,
                        { param = it },
                        label = { Text("参数") },
                        singleLine = true,
                        placeholder = { Text(r.defaultParam.ifBlank { r.description }) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "订阅地址：${RsshubPresets.resolve(rsshubInstance, r, param.ifBlank { r.defaultParam })}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val extra = param.trim().ifBlank { r.defaultParam }
                        val url = RsshubPresets.resolve(rsshubInstance, r, extra)
                        val title = r.title + if (extra.isNotBlank()) ": $extra" else ""
                        pendingRoute = null
                        subscribeRaw(url, title)
                    },
                    enabled = param.isNotBlank() || r.defaultParam.isNotBlank() || r.description.isBlank()
                ) { Text("订阅") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRoute = null }) { Text("取消") }
            }
        )
    }

    pendingDoc?.let { r ->
        AlertDialog(
            onDismissRequest = { pendingDoc = null },
            title = { Text(r.title) },
            text = {
                Column {
                    Text(
                        r.paramHint.ifBlank { "该路由需要补充参数，示例：${r.example}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        param,
                        { param = it },
                        label = { Text("参数") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "订阅地址：${RsshubCatalog.buildSubscribeUrl(rsshubInstance, r, param)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val extra = param.trim()
                        pendingDoc = null
                        subscribeDocRoute(r, extra)
                    },
                    enabled = param.isNotBlank() || !r.needsUserParam
                ) { Text("订阅") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDoc = null }) { Text("取消") }
            }
        )
    }

    blockedFeed?.let { feed ->
        AlertDialog(
            onDismissRequest = { blockedFeed = null },
            title = { Text(feed.title) },
            text = {
                Column {
                    Text(
                        "订阅地址：${resolveUrl(feed.url)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        feed.failureHint.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "若怀疑是实例反爬，可在设置 → RSSHub 实例更换后重试。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                // 订阅按钮保持禁用：当前源不可用，只能看原因
                Button(onClick = {}, enabled = false) { Text("订阅（当前不可用）") }
            },
            dismissButton = {
                TextButton(onClick = { blockedFeed = null }) { Text("关闭") }
            }
        )
    }

    // 这三个是 delegated var (MutableStateOf)，Kotlin 不能 smart cast，取一次本地不可变副本代替 !!
    val selectedPreset = presetCat
    val selectedCat = rsshubCat
    val selectedNs = rsshubNs
    val titleText = when {
        selectedNs != null -> selectedNs.name
        selectedCat != null -> RsshubCatalog.labelOf(selectedCat)
        selectedPreset != null -> selectedPreset.name
        else -> "添加订阅"
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(titleText) },
                navigationIcon = {
                    IconButton(onClick = { goBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = glassTopAppBarColors()
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (selectedPreset == null && selectedCat == null && selectedNs == null) {
                ArticleSearchEntry(
                    onClick = onOpenArticleSearch,
                    placeholder = "搜索全部已订阅文章",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (selectedPreset == null && selectedCat == null && selectedNs == null && activeTab != 2) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索订阅源或 RSSHub 路由") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(FoloDimens.SearchRadius),
                    colors = glassTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .glassSurface(shape = RoundedCornerShape(FoloDimens.SearchRadius), elevation = 4.dp)
                )
            }

            if (selectedPreset == null && selectedCat == null && selectedNs == null && (query.isBlank() || activeTab == 2)) {
                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) {
                        Text("精选目录", Modifier.padding(vertical = 8.dp))
                    }
                    Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) {
                        Text("RSSHub 路由", Modifier.padding(vertical = 8.dp))
                    }
                    Tab(selected = activeTab == 2, onClick = { activeTab = 2 }) {
                        Text("网址发现", Modifier.padding(vertical = 8.dp))
                    }
                }
            }

            if (isSubscribing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("正在订阅...", style = MaterialTheme.typography.bodyMedium)
                }
            }

            subscribeError?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            err,
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onClearError) { Text("知道了") }
                    }
                }
            }

            when {
                query.isNotBlank() && activeTab != 2 && selectedPreset == null && selectedCat == null && selectedNs == null -> {
                    SearchResults(
                        query = query,
                        namespaces = rsshubNamespaces,
                        rsshubInstance = rsshubInstance,
                        cardState = { url -> cardState(url) },
                        onAddBuiltin = { feed -> subscribeRaw(feed.url, feed.title) },
                        onPreviewBuiltin = { feed -> builtinTap(feed) },
                        onAddPreset = { route ->
                            subscribeRaw(RsshubPresets.resolve(rsshubInstance, route, route.defaultParam), route.title)
                        },
                        onParamPreset = { route ->
                            param = route.defaultParam
                            pendingRoute = route
                        },
                        onAddDoc = { route -> subscribeDocRoute(route, "") },
                        onParamDoc = { route ->
                            param = ""
                            pendingDoc = route
                        }
                    )
                }

                selectedPreset != null -> {
                    // justAdded 是 SnapshotStateList，读取即被组合跟踪，不需要 remember
                    val shown = selectedPreset.feeds.filter { cardState(it.url) != DiscoverCardState.HIDDEN }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(shown, key = { it.url + it.title }) { feed ->
                            BuiltinFeedRow(
                                feed = feed,
                                isSubscribed = cardState(feed.url) == DiscoverCardState.SUBSCRIBED,
                                onAdd = { subscribeRaw(feed.url, feed.title) },
                                onPreview = { builtinTap(feed) }
                            )
                        }
                    }
                }

                selectedNs != null -> {
                    val shown = selectedNs.routes.filter { route ->
                        selectedCat == null ||
                            selectedCat in route.categories ||
                            selectedCat in selectedNs.categories
                    }.filter { route ->
                        cardState(RsshubCatalog.buildSubscribeUrl(rsshubInstance, route, "")) != DiscoverCardState.HIDDEN
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(shown, key = { it.path + it.title }) { route ->
                            RouteRow(
                                title = route.title,
                                sub = if (route.needsUserParam) {
                                    "需参数 · ${route.example.ifBlank { route.path }}"
                                } else {
                                    "一键订阅 · ${route.example}"
                                },
                                canAddDirect = !route.needsUserParam,
                                isSubscribed = cardState(RsshubCatalog.buildSubscribeUrl(rsshubInstance, route, "")) == DiscoverCardState.SUBSCRIBED,
                                onAdd = {
                                    if (route.needsUserParam) {
                                        param = ""
                                        pendingDoc = route
                                    } else {
                                        subscribeDocRoute(route, "")
                                    }
                                },
                                onClick = {
                                    if (route.needsUserParam) {
                                        param = ""
                                        pendingDoc = route
                                    } else {
                                        subscribeDocRoute(route, "")
                                    }
                                }
                            )
                        }
                    }
                }

                selectedCat != null -> {
                    val nss = remember(rsshubNamespaces, selectedCat) {
                        rsshubNamespaces.filter { namespace ->
                            selectedCat in namespace.categories ||
                                namespace.routes.any { selectedCat in it.categories }
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(nss, key = { it.id }) { namespace ->
                            val count = namespace.routes.count {
                                selectedCat in it.categories || selectedCat in namespace.categories
                            }
                            CategoryCard(
                                title = namespace.name,
                                subtitle = "$count 条路由 · ${namespace.id}",
                                onClick = { rsshubNs = namespace }
                            )
                        }
                    }
                }

                activeTab == 0 -> {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(BuiltinFeeds.displayCategories, key = { it.name }) { c ->
                            CategoryCard(
                                title = c.name,
                                subtitle = "${c.feeds.size} 个源",
                                onClick = { presetCat = c }
                            )
                        }
                    }
                }

                activeTab == 1 -> {
                    RsshubBrowsePane(
                        instance = rsshubInstance,
                        namespaces = rsshubNamespaces,
                        isLoading = isLoadingRsshub,
                        error = rsshubError,
                        onRetry = onLoadRsshub,
                        onOpenCategory = { rsshubCat = it }
                    )
                }

                else -> {
                    UrlDiscoverPane(
                        query = urlQuery,
                        onQuery = { urlQuery = it },
                        isDiscovering = isDiscovering,
                        isSubscribing = isSubscribing,
                        isPreviewing = isPreviewing,
                        discoveredFeeds = discoveredFeeds,
                        discoveryError = discoveryError,
                        onDiscover = onDiscover,
                        onPreviewUrl = onPreviewUrl
                    )
                }
            }
        }
    }

    // 预览框原先只渲染在「网址发现」页签内，精选目录点卡片等于隐形预览；改为页面级 Dialog
    feedPreview?.let { preview ->
        Dialog(onDismissRequest = onClearPreview) {
            Surface(
                shape = RoundedCornerShape(FoloDimens.CardRadius),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                FeedPreviewCard(
                    preview = preview,
                    isSubscribing = isSubscribing,
                    subscribeError = subscribeError,
                    onSubscribe = onConfirmSubscribe,
                    onDismiss = onClearPreview
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    query: String,
    namespaces: List<RsshubNamespace>,
    rsshubInstance: String,
    cardState: (String) -> DiscoverCardState,
    onAddBuiltin: (BuiltinFeed) -> Unit,
    onPreviewBuiltin: (BuiltinFeed) -> Unit,
    onAddPreset: (RsshubRoute) -> Unit,
    onParamPreset: (RsshubRoute) -> Unit,
    onAddDoc: (RsshubDocRoute) -> Unit,
    onParamDoc: (RsshubDocRoute) -> Unit
) {
    val q = query.trim()
    val builtinHits = remember(q) {
        BuiltinFeeds.all.filter {
            it.title.contains(q, true) || it.category.contains(q, true) || it.url.contains(q, true)
        }
    }
    val presetHits = remember(q) {
        RsshubPresets.allRoutes.filter { (cat, route) ->
            route.title.contains(q, true) || cat.contains(q, true) || route.path.contains(q, true)
        }
    }
    val docHits = remember(namespaces, q) { RsshubCatalog.search(namespaces, q) }

    // 右滑/点「+」成功后的源直接不再出现；已订阅的仍可展示但只留「已添加」态
    val visibleBuiltin = builtinHits.filter { cardState(it.url) != DiscoverCardState.HIDDEN }
    val visiblePreset = presetHits.filter { entry ->
        cardState(RsshubPresets.resolve(rsshubInstance, entry.second)) != DiscoverCardState.HIDDEN
    }
    val visibleDoc = docHits.filter {
        cardState(RsshubCatalog.buildSubscribeUrl(rsshubInstance, it, "")) != DiscoverCardState.HIDDEN
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
        if (visibleBuiltin.isEmpty() && visiblePreset.isEmpty() && visibleDoc.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("没有找到相关订阅源，试试其他关键词", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(visibleBuiltin, key = { "b_${it.url}_${it.title}" }) { feed ->
            BuiltinFeedRow(
                feed = feed,
                isSubscribed = cardState(feed.url) == DiscoverCardState.SUBSCRIBED,
                onAdd = { onAddBuiltin(feed) },
                onPreview = { onPreviewBuiltin(feed) }
            )
        }
        items(visiblePreset, key = { "p_${it.second.path}_${it.second.title}" }) { entry ->
            val route = entry.second
            RouteRow(
                title = route.title,
                sub = "${entry.first} · ${if (route.description.isNotBlank()) route.description else "一键订阅"}",
                canAddDirect = !route.needsParam,
                isSubscribed = cardState(RsshubPresets.resolve(rsshubInstance, route)) == DiscoverCardState.SUBSCRIBED,
                onAdd = { if (route.needsParam) onParamPreset(route) else onAddPreset(route) },
                onClick = { if (route.needsParam) onParamPreset(route) else onAddPreset(route) }
            )
        }
        items(visibleDoc, key = { "d_${it.path}_${it.title}" }) { route ->
            RouteRow(
                title = "${route.nsName} · ${route.title}",
                sub = route.example.ifBlank { route.path },
                canAddDirect = !route.needsUserParam,
                isSubscribed = cardState(RsshubCatalog.buildSubscribeUrl(rsshubInstance, route, "")) == DiscoverCardState.SUBSCRIBED,
                onAdd = { if (route.needsUserParam) onParamDoc(route) else onAddDoc(route) },
                onClick = { if (route.needsUserParam) onParamDoc(route) else onAddDoc(route) }
            )
        }
    }
}

@Composable
private fun RsshubBrowsePane(
    instance: String,
    namespaces: List<RsshubNamespace>,
    isLoading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpenCategory: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "实例：$instance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Text(
            "路由数据来自实例 /api/namespace（与 docs.rsshub.app 同源）。可在设置里更换实例。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        when {
            isLoading && namespaces.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            error != null && namespaces.isEmpty() -> Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text("重试") }
            }
            else -> {
                val grouped = remember(namespaces) {
                    namespaces
                        .flatMap { ns ->
                            val cats = (ns.categories + ns.routes.flatMap { it.categories }).distinct()
                            cats.map { it to ns }
                        }
                        .groupBy({ it.first }, { it.second })
                        .toSortedMap(compareBy { RsshubCatalog.labelOf(it) })
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                    grouped.forEach { (cat, nss) ->
                        item(key = cat) {
                            CategoryCard(
                                title = RsshubCatalog.labelOf(cat),
                                subtitle = "${nss.distinctBy { it.id }.size} 个站点",
                                onClick = { onOpenCategory(cat) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlDiscoverPane(
    query: String,
    onQuery: (String) -> Unit,
    isDiscovering: Boolean,
    isSubscribing: Boolean,
    isPreviewing: Boolean,
    discoveredFeeds: List<DiscoveredFeed>,
    discoveryError: String?,
    onDiscover: (String) -> Unit,
    onPreviewUrl: (String) -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        TextField(
            value = query,
            onValueChange = onQuery,
            label = { Text("网站地址 或 RSS/Atom 链接") },
            placeholder = { Text("https://example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            shape = RoundedCornerShape(FoloDimens.SearchRadius),
            colors = glassTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .glassSurface(shape = RoundedCornerShape(FoloDimens.SearchRadius), elevation = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = { if (query.isNotBlank()) onDiscover(query) },
                enabled = query.isNotBlank() && !isDiscovering && !isSubscribing
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("发现")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = { if (query.isNotBlank()) onPreviewUrl(query) },
                enabled = query.isNotBlank() && !isDiscovering && !isSubscribing && !isPreviewing
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("预览并订阅")
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            isDiscovering -> Box(
                Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            discoveredFeeds.isNotEmpty() -> {
                Text("发现 ${discoveredFeeds.size} 个订阅源，点击预览：", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(discoveredFeeds) { feed ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPreviewUrl(feed.feedUrl) },
                            colors = glassCardColors(),
                            border = glassCardBorder()
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.RssFeed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(feed.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        feed.feedUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
            discoveryError != null -> Column(
                Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(discoveryError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { if (query.isNotBlank()) onDiscover(query) }) { Text("重试") }
            }
            else -> Text(
                "输入网站地址可自动发现 RSS 订阅源，或直接粘贴 RSS 链接",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = glassCardColors(),
        border = glassCardBorder()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 右滑=添加订阅；卡片永不真正划走，是否从列表消失由父级按 HIDDEN 过滤决定 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToAddRow(
    enabled: Boolean,
    onSwipeAdd: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd && enabled) onSwipeAdd()
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = enabled,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(FoloDimens.CardRadius))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "右滑添加订阅",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    ) { content() }
}

@Composable
private fun BuiltinFeedRow(
    feed: BuiltinFeed,
    isSubscribed: Boolean,
    onAdd: () -> Unit,
    onPreview: () -> Unit
) {
    val blocked = feed.failureHint != null
    SwipeToAddRow(enabled = !blocked && !isSubscribed, onSwipeAdd = onAdd) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = glassCardColors(),
            border = glassCardBorder()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isSubscribed -> Icon(
                        Icons.Default.Check,
                        contentDescription = "已添加",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    blocked -> IconButton(onClick = onPreview) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "查看不可用原因",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "订阅", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                // 行身点击=预览；不可用源则打开原因框，任何情况都不是死点击
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onPreview)
                ) {
                    Text(
                        feed.title + if (isSubscribed) " · 已添加" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${feed.category} · ${feed.source}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (blocked) {
                        Text(
                            feed.failureHint.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteRow(
    title: String,
    sub: String,
    canAddDirect: Boolean,
    isSubscribed: Boolean,
    onAdd: () -> Unit,
    onClick: () -> Unit
) {
    SwipeToAddRow(enabled = canAddDirect && !isSubscribed, onSwipeAdd = onAdd) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = glassCardColors(),
            border = glassCardBorder()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSubscribed) {
                    Icon(Icons.Default.Check, contentDescription = "已添加", tint = MaterialTheme.colorScheme.primary)
                } else {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "订阅", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onClick)
                ) {
                    Text(
                        title + if (isSubscribed) " · 已添加" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPreviewCard(
    preview: FeedPreview,
    isSubscribing: Boolean,
    subscribeError: String?,
    onSubscribe: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = glassCardColors(),
        border = glassCardBorder()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (preview.faviconUrl.isNotBlank()) {
                    AsyncImage(model = preview.faviconUrl, contentDescription = null, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                } else {
                    Icon(Icons.Default.RssFeed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(preview.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!preview.description.isNullOrBlank()) {
                        Text(
                            preview.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (preview.recentTitles.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("最近文章", style = MaterialTheme.typography.labelMedium)
                preview.recentTitles.forEach { title ->
                    Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
            }
            // 发现页没有 Snackbar 宿主，订阅失败必须落在卡片里可见
            subscribeError?.let { err ->
                Spacer(Modifier.height(12.dp))
                Text(
                    err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onSubscribe, enabled = !isSubscribing) {
                    if (isSubscribing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("订阅")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    }
}
