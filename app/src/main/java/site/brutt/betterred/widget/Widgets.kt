package site.brutt.betterred.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import site.brutt.betterred.MainActivity
import site.brutt.betterred.R
import site.brutt.betterred.Words
import site.brutt.betterred.data.Article
import site.brutt.betterred.data.NewsCache
import site.brutt.betterred.data.Prefs
import site.brutt.betterred.data.Store
import site.brutt.betterred.domain.CycleCalculator
import site.brutt.betterred.domain.CycleModel
import site.brutt.betterred.domain.DayInfo
import site.brutt.betterred.domain.Wishes
import site.brutt.betterred.domain.daysBetween
import site.brutt.betterred.ui.theme.colorSchemeFor
import site.brutt.betterred.ui.theme.isDark
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws both home-screen widgets. Plain Android RemoteViews: no extra library.
 * Everything runs on one background thread (database reads, drawing the ring).
 */
object Widgets {
    /** Extras for the intents widgets use to open the app. */
    const val EXTRA_TAB = "betterred.tab"
    const val EXTRA_ARTICLE_LINK = "betterred.article.link"
    const val EXTRA_ARTICLE_TITLE = "betterred.article.title"
    const val EXTRA_ARTICLE_SOURCE = "betterred.article.source"
    const val TAB_CALENDAR = "calendar"
    const val TAB_NEWS = "news"

    private val worker = Executors.newSingleThreadExecutor()

    /** Redraw every widget she has placed. Safe to call often (e.g. after every save). */
    fun updateAll(context: Context) {
        val app = context.applicationContext
        worker.execute { runCatching { renderCycle(app); renderNews(app) } }
    }

    fun run(block: () -> Unit, done: () -> Unit) = worker.execute { try { runCatching(block) } finally { done() } }

    private class Snapshot(val prefs: Prefs, val scheme: ColorScheme, val model: CycleModel, val readLinks: Set<String>, val enabledFeeds: Set<String>)

    private fun snapshot(context: Context): Snapshot {
        val store = Store(context)
        try {
            val prefs = Prefs.from(store.loadSettings())
            val periodDays = store.loadLogs().filterValues { it.flow != null }.keys
            val scheme = colorSchemeFor(context, prefs.accent, prefs.theme.isDark(context))
            val model = CycleCalculator.model(periodDays, prefs.cycle, LocalDate.now())
            return Snapshot(prefs, scheme, model, store.loadReadLinks(), store.loadFeeds().filter { it.enabled }.map { it.url }.toSet())
        } finally {
            store.close()
        }
    }

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    private fun openApp(context: Context, requestCode: Int, setup: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            setup()
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun RemoteViews.background(scheme: ColorScheme) {
        setInt(R.id.widget_bg, "setColorFilter", scheme.surfaceContainer.toArgb())
    }

    // ------------------------------------------------------------------ cycle widget

    fun renderCycle(context: Context, only: IntArray? = null) {
        val widgetIds = only ?: ids(context, CycleWidget::class.java)
        if (widgetIds.isEmpty()) return
        val snap = snapshot(context)
        val cs = snap.scheme
        val m = snap.model
        val today = CycleCalculator.dayInfo(m.today, m)
        val fert = snap.prefs.showFertility

        val views = RemoteViews(context.packageName, R.layout.widget_cycle).apply {
            background(cs)
            setTextColor(R.id.cycle_phase, cs.onSurface.toArgb())
            setTextColor(R.id.cycle_next, cs.onSurfaceVariant.toArgb())
            if (!m.hasData) {
                setViewVisibility(R.id.cycle_ring, View.GONE)
                setTextViewText(R.id.cycle_phase, Words.APP_NAME)
                setTextViewText(R.id.cycle_next, Words.Widget.NO_DATA)
            } else {
                setViewVisibility(R.id.cycle_ring, View.VISIBLE)
                setImageViewBitmap(R.id.cycle_ring, ringBitmap(context, today, m, cs, fert))
                setTextViewText(R.id.cycle_phase, Wishes.label(today.phase, fert) ?: Words.APP_NAME)
                setTextViewText(
                    R.id.cycle_next,
                    when {
                        m.daysLate > 0 -> Words.Calendar.daysLate(m.daysLate)
                        today.isPeriod -> Words.Widget.periodDay(daysBetween(m.periods.last().start, m.today) + 1)
                        else -> Words.Widget.periodIn(daysBetween(m.today, m.nextStart!!))
                    },
                )
            }
            setOnClickPendingIntent(android.R.id.background, openApp(context, 1) { putExtra(EXTRA_TAB, TAB_CALENDAR) })
        }
        AppWidgetManager.getInstance(context).updateAppWidget(widgetIds, views)
    }

    /** The same ring as in the app: period arc, (optional) fertile arc, a dot for today, and the day number. */
    private fun ringBitmap(context: Context, info: DayInfo, m: CycleModel, cs: ColorScheme, fertility: Boolean): Bitmap {
        val px = dp(context, 78f).toInt()
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val cycle = info.cycle ?: return bmp
        val len = cycle.length.toFloat()
        val stroke = px * 0.11f
        val inset = stroke / 2 + dp(context, 4f)
        val oval = RectF(inset, inset, px - inset, px - inset)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke }
        fun arc(from: Float, to: Float, color: Int) {
            paint.color = color
            c.drawArc(oval, -90f + from / len * 360f, (to - from) / len * 360f, false, paint)
        }
        arc(0f, len, cs.surfaceContainerHighest.toArgb())
        arc(0f, min(m.periodLength.toFloat(), len), cs.primary.toArgb())
        if (fertility) arc(
            daysBetween(cycle.start, cycle.fertileStart).toFloat(), daysBetween(cycle.start, cycle.fertileEnd) + 1f,
            cs.tertiary.copy(alpha = 0.55f).toArgb(),
        )
        val day = (info.cycleDay ?: 1) - 0.5f
        val angle = day / len * 2 * PI
        val r = oval.width() / 2
        val kx = px / 2f + r * sin(angle).toFloat()
        val ky = px / 2f - r * cos(angle).toFloat()
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = cs.surfaceContainer.toArgb(); c.drawCircle(kx, ky, stroke * 0.75f + dp(context, 2.5f), fill)
        fill.color = cs.onSurface.toArgb(); c.drawCircle(kx, ky, stroke * 0.75f, fill)

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        text.color = cs.onSurfaceVariant.toArgb(); text.textSize = sp(context, 10f)
        c.drawText(Words.Calendar.RING_DAY, px / 2f, px / 2f - sp(context, 5f), text)
        text.color = cs.onSurface.toArgb(); text.textSize = sp(context, 22f); text.typeface = Typeface.DEFAULT_BOLD
        c.drawText("${info.cycleDay}", px / 2f, px / 2f + sp(context, 15f), text)
        return bmp
    }

    // ------------------------------------------------------------------ news widget

    const val NEW_RED = 0xFFFF0000.toInt()

    /** Colors for list rows (computed here, used by [NewsWidgetService]). */
    data class NewsColors(val title: Int, val dimTitle: Int, val meta: Int, val divider: Int) {
        companion object {
            val DEFAULT = NewsColors(0xFF231919.toInt(), 0xFF534342.toInt(), 0xFFB3474E.toInt(), 0x1F000000)
        }
    }

    class NewsData(val articles: List<Article>, val readLinks: Set<String>, val colors: NewsColors)

    /** Everything the list needs: saved articles from switched-on feeds, which ones she read, and her colors. */
    fun newsData(context: Context): NewsData {
        val snap = snapshot(context)
        val cs = snap.scheme
        val articles = NewsCache(context).load().filter { it.feedUrl in snap.enabledFeeds }
        return NewsData(
            articles, snap.readLinks,
            NewsColors(cs.onSurface.toArgb(), cs.onSurfaceVariant.toArgb(), cs.primary.toArgb(), cs.outlineVariant.toArgb()),
        )
    }

    /** Draws the widget frame and (re)connects the scrollable list, then asks the list to reload. */
    @Suppress("DEPRECATION") // setRemoteAdapter(Intent) is the way that works on Android 10 and 11 too
    fun renderNews(context: Context, only: IntArray? = null) {
        val widgetIds = only ?: ids(context, NewsWidget::class.java)
        if (widgetIds.isEmpty()) return
        val cs = snapshot(context).scheme
        val manager = AppWidgetManager.getInstance(context)

        for (id in widgetIds) {
            val adapter = Intent(context, NewsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) // one list per widget
            }
            // Tapping a row fills its article into this template (so it must be mutable; it only targets our own activity).
            val rowTap = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_news).apply {
                background(cs)
                setTextViewText(R.id.news_header, Words.News.TITLE)
                setTextColor(R.id.news_header, cs.primary.toArgb())
                setOnClickPendingIntent(R.id.news_header, openApp(context, 2) { putExtra(EXTRA_TAB, TAB_NEWS) })
                setTextViewText(R.id.news_empty, Words.Widget.NEWS_EMPTY)
                setTextColor(R.id.news_empty, cs.onSurfaceVariant.toArgb())
                setOnClickPendingIntent(R.id.news_empty, openApp(context, 3) { putExtra(EXTRA_TAB, TAB_NEWS) })
                setRemoteAdapter(R.id.news_list, adapter)
                setEmptyView(R.id.news_list, R.id.news_empty)
                setPendingIntentTemplate(R.id.news_list, rowTap)
            }
            manager.updateAppWidget(id, views)
        }
        manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.news_list)
    }

    fun ago(published: Instant?): String {
        published ?: return ""
        val d = Duration.between(published, Instant.now())
        return when {
            d.isNegative || d.toMinutes() < 1 -> Words.News.JUST_NOW
            d.toHours() < 1 -> Words.News.minutesAgo(d.toMinutes())
            d.toDays() < 1 -> Words.News.hoursAgo(d.toHours())
            d.toDays() < 7 -> Words.News.daysAgo(d.toDays())
            else -> published.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
        }
    }

    private fun dp(context: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
    private fun sp(context: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, context.resources.displayMetrics)
}
