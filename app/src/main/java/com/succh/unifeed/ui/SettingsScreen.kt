package com.succh.unifeed.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.succh.unifeed.data.cache.ArticleRetention
import com.succh.unifeed.data.cache.ByteSize
import com.succh.unifeed.data.cache.ImageCache
import com.succh.unifeed.data.cache.ImageCacheLimit
import com.succh.unifeed.data.db.entity.FilterRule
import com.succh.unifeed.data.rss.RsshubEndpoints
import com.succh.unifeed.data.work.RefreshInterval
import com.succh.unifeed.data.work.RefreshWorker
import com.succh.unifeed.desktop.UnreadBadge
import com.succh.unifeed.ui.theme.AccentTheme
import com.succh.unifeed.ui.theme.FoloDimens
import com.succh.unifeed.ui.theme.GlassBlur
import com.succh.unifeed.ui.theme.GlassPanel
import com.succh.unifeed.ui.theme.ThemeMode
import com.succh.unifeed.ui.theme.GlassTokens
import com.succh.unifeed.ui.theme.glassChipBorder
import com.succh.unifeed.ui.theme.glassChipColors
import com.succh.unifeed.ui.theme.glassContentColor
import com.succh.unifeed.ui.theme.glassGlowColor
import com.succh.unifeed.ui.theme.glassMutedContentColor
import com.succh.unifeed.ui.theme.glassTopAppBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 公共实例快捷选择：直接取自抓取层的镜像清单，顺序即竞速优先级 */
private val RSSHUB_PRESETS = RsshubEndpoints.MIRRORS

/** 设置 Tab：列表设置、内容过滤、桌面角标、RSSHub 实例、缓存与清理、关于。分组走玻璃面板。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    prefs: ReaderPrefs,
    modifier: Modifier = Modifier,
    onRsshubInstanceChange: (String?) -> Unit = {}
) {
    var instanceInput by remember(prefs.rsshubInstance) { mutableStateOf(prefs.rsshubInstance ?: "") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val vm: UniFeedViewModel = viewModel()
    val uiState by vm.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var keywordInput by remember { mutableStateOf("") }
    var addAction by remember { mutableStateOf(FilterRule.ACTION_MARK_READ) }
    var cacheBytes by remember { mutableLongStateOf(0L) }
    var cacheBusy by remember { mutableStateOf(false) }
    var cleanupBusy by remember { mutableStateOf(false) }

    fun refreshCacheSize() {
        scope.launch {
            cacheBytes = withContext(Dispatchers.IO) { ImageCache.sizeBytes(context) }
        }
    }

    LaunchedEffect(Unit) { refreshCacheSize() }

    fun exportOpml() {
        scope.launch {
            val opml = withContext(Dispatchers.IO) { vm.exportOpml() }
            if (opml == null) {
                // 无订阅时明确提示，不生成空 OPML
                snackbarHostState.showSnackbar("当前没有订阅源，无法导出")
                return@launch
            }
            runCatching { OpmlShare.share(context, opml) }
                .onFailure {
                    snackbarHostState.showSnackbar("OPML 导出失败")
                }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val (imported, skipped) = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@withContext (0 to 0)
                    input.use { vm.importOpml(it) }
                }
                snackbarHostState.showSnackbar("导入 $imported 个，跳过 $skipped 个")
            }
        }
    }

    fun applyInstance(value: String?) {
        val v = value?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        instanceInput = v ?: ""
        prefs.rsshubInstance = v
        onRsshubInstanceChange(v)
        testResult = null
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
                colors = glassTopAppBarColors(),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FoloDimens.ScreenHorizontal, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassPanel {
                Text("列表设置", style = MaterialTheme.typography.titleMedium)
                SwitchSetting("列表显示摘要", prefs.listShowSummary) { prefs.listShowSummary = it }
                SwitchSetting("打开即标记已读", prefs.markReadOnOpen) { prefs.markReadOnOpen = it }
                Text(
                    "关闭时，打开文章后停留超过 2 秒才标记已读，快速划过不标。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
            }

            // 皮肤自定义：深浅模式 / 主题色 / 背景模糊，改动即时生效即预览
            GlassPanel {
                Text("外观", style = MaterialTheme.typography.titleMedium)
                Text(
                    "深浅模式决定玻璃底色与文字对比；主题色与模糊强度改动后全屏立即预览。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Text("深浅模式", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = prefs.themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { prefs.themeMode = mode },
                            label = { Text(mode.label) },
                            colors = glassChipColors(),
                            border = glassChipBorder(selected)
                        )
                    }
                }
                Text("主题色", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AccentTheme.entries.forEach { accentOption ->
                        val selected = prefs.accent == accentOption
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(accentOption.swatch)
                                .border(
                                    width = if (selected) 2.5.dp else GlassTokens.BorderWidth,
                                    color = if (selected) {
                                        glassContentColor()
                                    } else {
                                        glassGlowColor()
                                    },
                                    shape = CircleShape
                                )
                                .clickable { prefs.accent = accentOption },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = accentOption.label,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                Text("背景模糊", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassBlur.entries.forEach { blur ->
                        val selected = prefs.glassBlur == blur
                        FilterChip(
                            selected = selected,
                            onClick = { prefs.glassBlur = blur },
                            label = { Text(blur.label) },
                            colors = glassChipColors(),
                            border = glassChipBorder(selected)
                        )
                    }
                }
            }

            GlassPanel {
                Text("内容过滤", style = MaterialTheme.typography.titleMedium)
                Text(
                    "标题含关键字时自动处理。隐藏：不出现在列表且不计未读；自动标已读：仍可见但标为已读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(FoloDimens.SearchRadius),
                    colors = fieldColors,
                    placeholder = { Text("输入要屏蔽的关键字") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = addAction == FilterRule.ACTION_MARK_READ,
                        onClick = { addAction = FilterRule.ACTION_MARK_READ },
                        label = { Text("自动标已读") },
                        colors = glassChipColors(),
                        border = glassChipBorder(addAction == FilterRule.ACTION_MARK_READ)
                    )
                    FilterChip(
                        selected = addAction == FilterRule.ACTION_HIDE,
                        onClick = { addAction = FilterRule.ACTION_HIDE },
                        label = { Text("隐藏") },
                        colors = glassChipColors(),
                        border = glassChipBorder(addAction == FilterRule.ACTION_HIDE)
                    )
                }
                Button(
                    onClick = {
                        val keyword = keywordInput.trim()
                        if (keyword.isNotEmpty()) {
                            vm.addFilterRule(keyword, addAction)
                            keywordInput = ""
                        }
                    },
                    enabled = keywordInput.trim().isNotEmpty()
                ) {
                    Text("添加规则")
                }
                val globalRules = uiState.filterRules.filter { it.feedId == null }
                val feedRules = uiState.filterRules.filter { it.feedId != null }
                val feedTitleById = uiState.feeds.associate { it.id to it.title }
                if (uiState.filterRules.isEmpty()) {
                    Text(
                        "暂无屏蔽规则。添加后对新文章立即生效，也会回扫已有文章。",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassMutedContentColor()
                    )
                } else {
                    if (globalRules.isNotEmpty()) {
                        Text("全局规则", style = MaterialTheme.typography.labelMedium)
                        globalRules.forEach { rule ->
                            FilterRuleRow(
                                rule = rule,
                                subtitle = null,
                                onActionChange = { action -> vm.updateFilterRule(rule.copy(action = action)) },
                                onDelete = { vm.deleteFilterRule(rule.id) }
                            )
                        }
                    }
                    if (feedRules.isNotEmpty()) {
                        Text("订阅源专属规则", style = MaterialTheme.typography.labelMedium)
                        feedRules.forEach { rule ->
                            FilterRuleRow(
                                rule = rule,
                                subtitle = feedTitleById[rule.feedId] ?: "未知订阅源",
                                onActionChange = { action -> vm.updateFilterRule(rule.copy(action = action)) },
                                onDelete = { vm.deleteFilterRule(rule.id) }
                            )
                        }
                    }
                }
                Text("订阅源", style = MaterialTheme.typography.labelMedium)
                Text(
                    "关闭某源的「套用全局过滤」后，该源只走专属规则。可在订阅源菜单里开关。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                if (uiState.feeds.isEmpty()) {
                    Text(
                        "还没有订阅源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassMutedContentColor()
                    )
                } else {
                    uiState.feeds.forEach { feed ->
                        SwitchSetting("套用全局过滤 · ${feed.title}", feed.applyGlobalFilter) {
                            vm.setApplyGlobalFilter(feed.id, it)
                        }
                    }
                }
            }

            GlassPanel {
                Text("RSSHub 实例", style = MaterialTheme.typography.titleMedium)
                Text(
                    "当前生效：${prefs.effectiveRsshubInstance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "填写自建或第三方实例地址，优先使用；失败时自动回退公共镜像。留空则使用默认自建实例。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Text(
                    "想自己部署？Docker Compose 一键自建 RSSHub（含 Redis 缓存与 HTTPS 要点）见仓库文档 docs/rsshub-selfhost.md。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                TextButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    "https://github.com/Succh/UniFeed/blob/main/docs/rsshub-selfhost.md".toUri()
                                )
                            )
                        }
                    }
                ) { Text("打开自建部署指南 ↗") }
                OutlinedTextField(
                    value = instanceInput,
                    onValueChange = { instanceInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(FoloDimens.SearchRadius),
                    colors = fieldColors,
                    placeholder = { Text("https://rsshub.example.com") },
                    trailingIcon = {
                        if (instanceInput.isNotEmpty() || prefs.rsshubInstance != null) {
                            TextButton(onClick = { applyInstance(null) }) { Text("恢复默认") }
                        }
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { applyInstance(instanceInput) },
                        enabled = instanceInput.trim().isNotEmpty()
                    ) { Text("保存") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            val target = instanceInput.trim().trimEnd('/').ifEmpty { prefs.effectiveRsshubInstance }
                            testing = true
                            testResult = null
                            scope.launch {
                                val start = System.currentTimeMillis()
                                // 后台线程测试实例连通性（20s 超时，Vercel 冷启动可能较慢）
                                val result = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val conn = (URL("$target/36kr/hot-list").openConnection() as HttpURLConnection).apply {
                                            connectTimeout = 20000
                                            readTimeout = 20000
                                            instanceFollowRedirects = true
                                        }
                                        val code = conn.responseCode
                                        conn.disconnect()
                                        code
                                    }.getOrNull()
                                }
                                val elapsed = System.currentTimeMillis() - start
                                testing = false
                                testResult = if (result != null && result in 200..399) {
                                    "实例可用（HTTP $result，${elapsed}ms）"
                                } else {
                                    "实例不可用（${result ?: "连接超时"}，${elapsed}ms）请检查地址或网络"
                                }
                            }
                        },
                        enabled = !testing
                    ) { Text(if (testing) "测试中..." else "测试实例") }
                }
                testResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("公共实例快捷选择", style = MaterialTheme.typography.labelMedium)
                // FlowRow 按 chip 实际宽度自然换行，替代固定每行 3 个的 chunked(3)（末行稀疏显散）
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RSSHUB_PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = { applyInstance(preset) },
                            label = { Text(preset.removePrefix("https://")) }
                        )
                    }
                }
            }

            GlassPanel {
                Text("OPML", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导入或导出订阅列表。导出走系统分享/保存，每个源含 title 与 xmlUrl。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportOpml() }) {
                        Text("导出 OPML")
                    }
                    Button(onClick = { importLauncher.launch("text/xml") }) {
                        Text("导入 OPML")
                    }
                }
            }

            GlassPanel {
                Text("桌面", style = MaterialTheme.typography.titleMedium)
                SwitchSetting("应用图标未读角标", prefs.unreadBadgeEnabled) { enabled ->
                    prefs.unreadBadgeEnabled = enabled
                    scope.launch { UnreadBadge.syncFromDb(context) }
                }
                Text(
                    "显示未读文章数。不支持角标的启动器可能不显示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Text(
                    "可长按桌面空白处添加「最新未读」小部件。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
            }

            GlassPanel {
                Text("后台自动刷新", style = MaterialTheme.typography.titleMedium)
                Text(
                    "按所选间隔在后台抓取新文章。选「手动」后只保留下拉刷新，不再跑周期任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Text("刷新间隔", style = MaterialTheme.typography.labelMedium)
                RefreshInterval.entries.chunked(2).forEach { rowSlots ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSlots.forEach { slot ->
                            val selected = prefs.refreshInterval == slot
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    prefs.refreshInterval = slot
                                    RefreshWorker.reschedule(context, prefs)
                                },
                                label = { Text(refreshIntervalLabel(slot)) },
                                colors = glassChipColors(),
                                border = glassChipBorder(selected)
                            )
                        }
                    }
                }
                if (prefs.refreshInterval == RefreshInterval.MANUAL) {
                    Text(
                        "手动：关闭后台周期刷新，需要时在列表下拉即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassMutedContentColor()
                    )
                }
                SwitchSetting("仅 Wi-Fi 时后台刷新", prefs.refreshWifiOnly) {
                    prefs.refreshWifiOnly = it
                    RefreshWorker.reschedule(context, prefs)
                }
                Text(
                    if (prefs.refreshWifiOnly) {
                        "仅 Wi-Fi：后台任务只在非计费网络（通常是 Wi-Fi）时运行，避免消耗移动流量。"
                    } else {
                        "关闭后，任意可用网络都会触发后台刷新。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
            }

            GlassPanel {
                Text("缓存与清理", style = MaterialTheme.typography.titleMedium)
                Text(
                    "清理图片缓存不影响文章正文、收藏与稍后读。旧文章清理只删除已读且未收藏、未稍后读的文章，未读一律保留。",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Text("图片缓存上限", style = MaterialTheme.typography.labelMedium)
                ImageCacheLimit.entries.chunked(2).forEach { rowSlots ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSlots.forEach { slot ->
                            val selected = prefs.imageCacheLimit == slot
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    prefs.imageCacheLimit = slot
                                    ImageCache.applyLimit(context, slot)
                                },
                                label = { Text(imageCacheLimitLabel(slot)) },
                                colors = glassChipColors(),
                                border = glassChipBorder(selected)
                            )
                        }
                    }
                }
                Text(
                    "当前占用：${ByteSize.format(cacheBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = glassMutedContentColor()
                )
                Button(
                    onClick = {
                        cacheBusy = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { ImageCache.clear(context) }
                                refreshCacheSize()
                                snackbarHostState.showSnackbar("已清理图片缓存")
                            } finally {
                                cacheBusy = false
                            }
                        }
                    },
                    enabled = !cacheBusy
                ) {
                    Text(if (cacheBusy) "清理中..." else "清理图片缓存")
                }
                Text("旧文章清理策略", style = MaterialTheme.typography.labelMedium)
                ArticleRetention.entries.chunked(2).forEach { rowSlots ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowSlots.forEach { slot ->
                            val selected = prefs.articleRetention == slot
                            FilterChip(
                                selected = selected,
                                onClick = { prefs.articleRetention = slot },
                                label = { Text(articleRetentionLabel(slot)) },
                                colors = glassChipColors(),
                                border = glassChipBorder(selected)
                            )
                        }
                    }
                }
                Button(
                    onClick = {
                        cleanupBusy = true
                        scope.launch {
                            try {
                                val count = vm.cleanupOldArticles(prefs.articleRetention)
                                snackbarHostState.showSnackbar("已清理 $count 条旧文章")
                            } finally {
                                cleanupBusy = false
                            }
                        }
                    },
                    enabled = !cleanupBusy
                ) {
                    Text(if (cleanupBusy) "清理中..." else "立即清理旧文章")
                }
                if (prefs.articleRetention == ArticleRetention.NEVER) {
                    Text(
                        "当前为不清理：不会按时间删除文章。",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassMutedContentColor()
                    )
                }
            }

            GlassPanel {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Text(
                    "UniFeed v1.1 • 开源 RSS 阅读器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassMutedContentColor()
                )
            }
        }
    }
}

private fun refreshIntervalLabel(interval: RefreshInterval): String = when (interval) {
    RefreshInterval.MINUTES_15 -> "15 分钟"
    RefreshInterval.MINUTES_30 -> "30 分钟"
    RefreshInterval.HOUR_1 -> "1 小时"
    RefreshInterval.MANUAL -> "手动"
}

private fun imageCacheLimitLabel(limit: ImageCacheLimit): String = when (limit) {
    ImageCacheLimit.MB_100 -> "100MB"
    ImageCacheLimit.MB_500 -> "500MB"
    ImageCacheLimit.GB_1 -> "1GB"
    ImageCacheLimit.UNLIMITED -> "不限"
}

private fun articleRetentionLabel(retention: ArticleRetention): String = when (retention) {
    ArticleRetention.DAYS_30 -> "30 天"
    ArticleRetention.DAYS_90 -> "90 天"
    ArticleRetention.DAYS_180 -> "180 天"
    ArticleRetention.NEVER -> "不清理"
}

@Composable
private fun SwitchSetting(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}

/** 单条关键字规则：两档动作切换 + 删除 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRuleRow(
    rule: FilterRule,
    subtitle: String?,
    onActionChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.keyword, style = MaterialTheme.typography.bodyLarge)
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = glassMutedContentColor()
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = rule.action == FilterRule.ACTION_MARK_READ,
                onClick = { onActionChange(FilterRule.ACTION_MARK_READ) },
                label = { Text("自动标已读") },
                colors = glassChipColors(),
                border = glassChipBorder(rule.action == FilterRule.ACTION_MARK_READ)
            )
            FilterChip(
                selected = rule.action == FilterRule.ACTION_HIDE,
                onClick = { onActionChange(FilterRule.ACTION_HIDE) },
                label = { Text("隐藏") },
                colors = glassChipColors(),
                border = glassChipBorder(rule.action == FilterRule.ACTION_HIDE)
            )
        }
    }
}