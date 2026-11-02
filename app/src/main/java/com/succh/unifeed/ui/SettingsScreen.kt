package com.succh.unifeed.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.succh.unifeed.data.db.entity.FilterRule
import com.succh.unifeed.data.rss.RsshubEndpoints
import com.succh.unifeed.data.work.RefreshInterval
import com.succh.unifeed.data.work.RefreshWorker
import com.succh.unifeed.ui.theme.FoloDimens
import com.succh.unifeed.ui.theme.GlassPanel
import com.succh.unifeed.ui.theme.glassChipBorder
import com.succh.unifeed.ui.theme.glassChipColors
import com.succh.unifeed.ui.theme.glassTopAppBarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 公共实例快捷选择：直接取自抓取层的镜像清单，顺序即竞速优先级 */
private val RSSHUB_PRESETS = RsshubEndpoints.MIRRORS

/** 设置 Tab：列表设置、内容过滤、RSSHub 实例配置、关于。分组走玻璃面板。 */
@OptIn(ExperimentalMaterial3Api::class)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            GlassPanel {
                Text("内容过滤", style = MaterialTheme.typography.titleMedium)
                Text(
                    "标题含关键字时自动处理。隐藏：不出现在列表且不计未读；自动标已读：仍可见但标为已读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (uiState.feeds.isEmpty()) {
                    Text(
                        "还没有订阅源。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                RSSHUB_PRESETS.chunked(3).forEach { rowPresets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        rowPresets.forEach { preset ->
                            AssistChip(
                                onClick = { applyInstance(preset) },
                                label = { Text(preset.removePrefix("https://")) }
                            )
                        }
                    }
                }
            }

            GlassPanel {
                Text("OPML", style = MaterialTheme.typography.titleMedium)
                Text(
                    "导入或导出订阅列表。导出走系统分享/保存，每个源含 title 与 xmlUrl。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                Text("后台自动刷新", style = MaterialTheme.typography.titleMedium)
                Text(
                    "按所选间隔在后台抓取新文章。选「手动」后只保留下拉刷新，不再跑周期任务。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            GlassPanel {
                Text("关于", style = MaterialTheme.typography.titleMedium)
                Text(
                    "UniFeed v1.1 • 开源 RSS 阅读器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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