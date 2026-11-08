package com.succh.unifeed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
