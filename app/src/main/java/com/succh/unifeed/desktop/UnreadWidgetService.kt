package com.succh.unifeed.desktop

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.succh.unifeed.MainActivity
import com.succh.unifeed.R
import com.succh.unifeed.UniFeedApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** 小部件列表数据：在 binder 线程拉取最新未读。 */
class UnreadWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return UnreadWidgetFactory(applicationContext)
    }
}

private class UnreadWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<UnreadWidgetItem> = emptyList()

    override fun onCreate() {}

    override fun onDestroy() {
        items = emptyList()
    }

    override fun onDataSetChanged() {
        val app = context.applicationContext as? UniFeedApp ?: return
        items = runCatching {
            runBlocking(Dispatchers.IO) { app.repository.getLatestUnreadForWidget() }
        }.getOrDefault(emptyList())
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.unread_widget_item)
        val item = items.getOrNull(position) ?: return views
        views.setTextViewText(
            R.id.widget_item_title,
            UnreadWidgetContent.displayTitle(item.title)
        )
        val feed = UnreadWidgetContent.displayFeed(item.feedTitle)
        val time = UnreadWidgetContent.displayTime(item.publishedAt)
        val meta = if (time.isEmpty()) feed else "$feed · $time"
        views.setTextViewText(R.id.widget_item_meta, meta)
        val fillIn = Intent().apply {
            data = Uri.parse("unifeed://entry/${item.id}")
            putExtra(MainActivity.EXTRA_OPEN_ENTRY_ID, item.id)
        }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
