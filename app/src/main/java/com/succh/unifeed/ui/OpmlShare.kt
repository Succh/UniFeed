package com.succh.unifeed.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * 把 OPML 写到缓存后走系统分享/保存面板发出，不自造文件选择 UI。
 */
object OpmlShare {

    private const val FILE_NAME = "unifeed-subscriptions.opml"

    fun share(context: Context, opml: String) {
        val file = File(context.cacheDir, FILE_NAME)
        file.writeText(opml, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/xml"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "UniFeed 订阅列表")
            clipData = ClipData.newRawUri(FILE_NAME, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "导出订阅列表").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
