package com.succh.unifeed

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.succh.unifeed.data.update.UpdateManager
import com.succh.unifeed.ui.ReaderPrefs
import com.succh.unifeed.ui.UniFeedApp
import com.succh.unifeed.ui.theme.GlassBackdrop
import com.succh.unifeed.ui.theme.UniFeedTheme

class MainActivity : ComponentActivity() {

    private var openEntryId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openEntryId = readOpenEntryId(intent)
        enableEdgeToEdge()
        setContent {
            // prefs 提升到 Activity 层：主题/皮肤与阅读器共用同一份状态，设置页改动全屏即时生效
            val prefs = remember { ReaderPrefs(this) }
            UniFeedTheme(themeMode = prefs.themeMode, accent = prefs.accent) {
                // 全屏光晕背景，后续顶栏/底栏/卡片半透明才能透出玻璃感
                GlassBackdrop(modifier = Modifier.fillMaxSize(), blurFactor = prefs.glassBlur.factor) {
                    UniFeedApp(
                        prefs = prefs,
                        openEntryId = openEntryId,
                        onOpenEntryConsumed = { openEntryId = null }
                    )
                    // 启动静默检查发现新版时的不打扰弹窗：只弹一次，可忽略
                    UpdateAvailableDialog()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEntryId = readOpenEntryId(intent)
    }

    private fun readOpenEntryId(intent: Intent?): Long? {
        val id = intent?.getLongExtra(EXTRA_OPEN_ENTRY_ID, -1L) ?: return null
        return id.takeIf { it > 0L }
    }

    companion object {
        const val EXTRA_OPEN_ENTRY_ID = "open_entry_id"
    }
}

/**
 * 静默检查发现新版时的更新弹窗：显示版本号与更新内容摘要，
 * 主通道 APP 内下载、备用通道浏览器下载；忽略则本次会话不再弹。
 */
@Composable
private fun UpdateAvailableDialog() {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager.get(context) }
    val updateState by updateManager.state.collectAsState()
    var dismissed by remember { mutableStateOf(false) }

    // 下载进度通知需要 Android 13+ 运行时授权；拒绝不影响下载，只是通知栏看不到进度
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateState.pendingUpdate?.let { updateManager.startDownload(it) }
    }

    val info = updateState.pendingUpdate ?: return
    if (dismissed) return

    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text("发现新版本 ${info.versionName}") },
        text = {
            Text(
                if (info.releaseNotes.isNotBlank()) {
                    info.releaseNotes.take(400) + if (info.releaseNotes.length > 400) "…" else ""
                } else {
                    "点击「立即更新」在 APP 内后台下载并安装，也可以在浏览器中下载。"
                }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                dismissed = true
                if (updateManager.hasNotificationPermission()) {
                    updateManager.startDownload(info)
                } else {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }) {
                Text("立即更新")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    dismissed = true
                    updateManager.openReleasePageInBrowser()
                }) {
                    Text("浏览器下载")
                }
                TextButton(onClick = { dismissed = true }) {
                    Text("忽略")
                }
            }
        }
    )
}
