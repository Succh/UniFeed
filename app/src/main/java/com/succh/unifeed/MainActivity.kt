package com.succh.unifeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.succh.unifeed.ui.UniFeedApp
import com.succh.unifeed.ui.theme.GlassBackdrop
import com.succh.unifeed.ui.theme.UniFeedTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UniFeedTheme {
                // 全屏光晕背景，后续顶栏/底栏/卡片半透明才能透出玻璃感
                GlassBackdrop(modifier = Modifier.fillMaxSize()) {
                    UniFeedApp()
                }
            }
        }
    }
}
