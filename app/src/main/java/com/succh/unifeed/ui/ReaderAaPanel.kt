package com.succh.unifeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.succh.unifeed.ui.theme.glassBarColor

/**
 * 阅读页 Aa 快捷设置面板，参考 Folo 阅读体验。
 * 支持字号 / 行距 / 主题（浅色、米黄、夜间）/ 显示图片 / 衬线字体 / 两端对齐。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AaSettingsPanel(
    prefs: ReaderPrefs,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = glassBarColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("阅读设置", style = MaterialTheme.typography.titleMedium)

            // 字号
            SettingRow(label = "字号") {
                SizeButton("-") { prefs.fontSize = (prefs.fontSize - 1f).coerceAtLeast(13f) }
                Text("${prefs.fontSize.toInt()}px", modifier = Modifier.padding(horizontal = 12.dp))
                SizeButton("+") { prefs.fontSize = (prefs.fontSize + 1f).coerceAtMost(24f) }
            }

            // 行距
            SettingRow(label = "行距") {
                SizeButton("-") { prefs.lineHeight = (prefs.lineHeight - 0.1f).coerceAtLeast(1.2f) }
                Text(String.format("%.1f", prefs.lineHeight), modifier = Modifier.padding(horizontal = 12.dp))
                SizeButton("+") { prefs.lineHeight = (prefs.lineHeight + 0.1f).coerceAtMost(2.2f) }
            }

            // 主题
            SettingRow(label = "主题") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTheme.entries.forEach { t ->
                        val selected = prefs.theme == t
                        FilledTonalButton(
                            onClick = { prefs.theme = t },
                            colors = if (selected) ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ) else ButtonDefaults.filledTonalButtonColors(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                when (t) {
                                    ReaderTheme.LIGHT -> "浅色"
                                    ReaderTheme.SEPIA -> "米黄"
                                    ReaderTheme.DARK -> "夜间"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            // 开关项
            SwitchSetting("显示图片", prefs.showImages) { prefs.showImages = it }
            SwitchSetting("衬线字体", prefs.serifFont) { prefs.serifFont = it }
            SwitchSetting("两端对齐", prefs.justifyText) { prefs.justifyText = it }
        }
    }
}

@Composable
private fun SettingRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(64.dp))
        content()
    }
}

@Composable
private fun SizeButton(text: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(36.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
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
