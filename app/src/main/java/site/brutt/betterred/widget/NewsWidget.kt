package site.brutt.betterred.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import site.brutt.betterred.data.Article
import site.brutt.betterred.data.NewsCache
import site.brutt.betterred.data.ParsedFeed
import site.brutt.betterred.data.Rss
import site.brutt.betterred.data.Store
import site.brutt.betterred.data.mergeNews
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Home-screen widget: the latest articles. Android wakes it about every 6 hours; it then
 * fetches her feeds (at most ~20 seconds) and redraws. It also redraws whenever the app loads news.
 */
class NewsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val app = context.applicationContext
        val pending = goAsync()
        Widgets.run({
            Widgets.renderNews(app, ids) // show what we have right away
            if (refresh(app) != null) Widgets.renderNews(app, ids) // then the fresh list
        }, pending::finish)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pending = goAsync()
        Widgets.run({ Widgets.renderNews(context.applicationContext, intArrayOf(id)) }, pending::finish)
    }

    /** Fetches enabled feeds in parallel with an overall time limit, and updates the saved list. */
    private fun refresh(context: Context): List<Article>? {
        val store = Store(context)
        val feeds = try { store.loadFeeds().filter { it.enabled } } finally { store.close() }
        if (feeds.isEmpty()) return null
        val pool = Executors.newFixedThreadPool(feeds.size.coerceAtMost(4))
        try {
            val futures = feeds.map { feed -> feed to pool.submit<Result<ParsedFeed>> { runCatching { Rss.fetch(feed.url) } } }
            val deadline = System.currentTimeMillis() + 20_000
            val results = futures.map { (feed, future) ->
                feed to runCatching { future.get((deadline - System.currentTimeMillis()).coerceAtLeast(1), TimeUnit.MILLISECONDS) }
                    .getOrElse { future.cancel(true); Result.failure(it) }
            }
            if (results.none { it.second.isSuccess }) return null
            val cache = NewsCache(context)
            return mergeNews(cache.load(), results).also(cache::save)
        } finally {
            pool.shutdownNow()
        }
    }
}
