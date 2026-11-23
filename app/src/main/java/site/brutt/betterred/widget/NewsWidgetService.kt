package site.brutt.betterred.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import site.brutt.betterred.R
import site.brutt.betterred.data.Article

/**
 * Supplies the rows of the news widget's list on Android 10 and 11 only (newer versions get the
 * rows inside the widget update itself; see [Widgets.renderNews]).
 */
class NewsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var rows: List<Article> = emptyList()
        private var read: Set<String> = emptySet()
        private var palette: Widgets.Palette? = null

        override fun onCreate() = Unit
        override fun onDestroy() = Unit

        override fun onDataSetChanged() {
            val data = Widgets.newsData(context)
            rows = data.articles
            read = data.readLinks
            palette = data.palette
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true
        override fun getItemId(position: Int) = rows.getOrNull(position)?.link?.hashCode()?.toLong() ?: position.toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val a = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_news_item)
            val pal = palette ?: Widgets.newsData(context).palette.also { palette = it }
            return Widgets.newsRow(context, a, a.link !in read, pal)
        }
    }
}
