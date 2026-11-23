package site.brutt.betterred.widget

import android.content.Context
import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import site.brutt.betterred.R
import site.brutt.betterred.Words
import site.brutt.betterred.data.Article

/**
 * Supplies the rows of the news widget's scrollable list. Android calls this in the background
 * whenever the widget asks for fresh data (see [Widgets.renderNews]).
 */
class NewsWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    private class Factory(private val context: Context) : RemoteViewsFactory {
        private var rows: List<Article> = emptyList()
        private var read: Set<String> = emptySet()
        private var colors = Widgets.NewsColors.DEFAULT

        override fun onCreate() = Unit
        override fun onDestroy() = Unit

        override fun onDataSetChanged() {
            val data = Widgets.newsData(context)
            rows = data.articles
            read = data.readLinks
            colors = data.colors
        }

        override fun getCount() = rows.size
        override fun getViewTypeCount() = 1
        override fun hasStableIds() = true
        override fun getItemId(position: Int) = rows.getOrNull(position)?.link?.hashCode()?.toLong() ?: position.toLong()
        override fun getLoadingView(): RemoteViews? = null

        override fun getViewAt(position: Int): RemoteViews {
            val a = rows.getOrNull(position) ?: return RemoteViews(context.packageName, R.layout.widget_news_item)
            val isNew = a.link !in read
            return RemoteViews(context.packageName, R.layout.widget_news_item).apply {
                setTextViewText(R.id.news_item_title, a.title)
                setTextColor(R.id.news_item_title, if (isNew) colors.title else colors.dimTitle)
                val meta = SpannableString(Words.Widget.newsMeta(isNew, a.source, Widgets.ago(a.published)))
                if (isNew) {
                    val end = meta.indexOf(" ·").takeIf { it > 0 } ?: meta.length
                    meta.setSpan(ForegroundColorSpan(Widgets.NEW_RED), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                setTextViewText(R.id.news_item_meta, meta)
                setTextColor(R.id.news_item_meta, colors.meta)
                setInt(R.id.news_item_divider, "setBackgroundColor", colors.divider)
                // Filled into the list's click template: opens this article in the reader.
                setOnClickFillInIntent(R.id.news_item, Intent().apply {
                    putExtra(Widgets.EXTRA_ARTICLE_LINK, a.link)
                    putExtra(Widgets.EXTRA_ARTICLE_TITLE, a.title)
                    putExtra(Widgets.EXTRA_ARTICLE_SOURCE, a.source)
                })
            }
        }
    }
}
