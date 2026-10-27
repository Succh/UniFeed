package com.succh.unifeed.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.succh.unifeed.data.rss.RsshubEndpoints
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

/** 设置 Tab：列表设置、RSSHub 实例配置、关于。分组走玻璃面板。 */
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val opml = withContext(Dispatchers.IO) { vm.exportOpml() }
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(opml.toByteArray(Charsets.UTF_8))
                        } ?: throw IllegalStateException("无法打开输出流")
                    }.isSuccess
                }
                snackbarHostState.showSnackbar(if (ok) "OPML 导出完成" else "OPML 导出失败")
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("设置") },
                colors = glassTopAppBarColors()
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
                    "导入导出订阅列表，方便备份与迁移。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("unifeed.opml") }) {
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
                    "定期在后台抓取新文章（需要联网）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SwitchSetting("启用后台刷新", prefs.autoRefreshEnabled) {
                    prefs.autoRefreshEnabled = it
                    if (it) {
                        RefreshWorker.schedule(context, prefs.autoRefreshIntervalHours)
                    } else {
                        RefreshWorker.cancel(context)
                    }
                }
                if (prefs.autoRefreshEnabled) {
                    Text("刷新间隔", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2L, 6L, 12L, 24L).forEach { hours ->
                            val selected = prefs.autoRefreshIntervalHours == hours
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    prefs.autoRefreshIntervalHours = hours
                                    RefreshWorker.schedule(context, hours)
                                },
                                label = { Text("${hours}小时") },
                                colors = glassChipColors(),
                                border = glassChipBorder(selected)
                            )
                        }
                    }
                }
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

@Composable
private fun SwitchSetting(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}