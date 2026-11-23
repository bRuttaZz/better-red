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
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import site.brutt.betterred.MainActivity
import site.brutt.betterred.R
import site.brutt.betterred.Words
import site.brutt.betterred.data.Article
import site.brutt.betterred.data.NewsCache
import site.brutt.betterred.data.Prefs
import site.brutt.betterred.data.Store
import site.brutt.betterred.data.ThemeMode
import site.brutt.betterred.domain.CycleCalculator
import site.brutt.betterred.domain.CycleModel
import site.brutt.betterred.domain.DayInfo
import site.brutt.betterred.domain.History
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
        worker.execute {
            safely("cycle") { renderCycle(app) } // separately, so one failing can't stop the other
            safely("news") { renderNews(app) }
        }
    }

    fun run(block: () -> Unit, done: () -> Unit) = worker.execute { try { safely("update", block) } finally { done() } }

    /** A widget that fails to draw keeps its last picture; the reason goes to the log (adb logcat -s BetterRed). */
    private fun safely(what: String, block: () -> Unit) {
        runCatching(block).onFailure { Log.w("BetterRed", "Widget $what failed", it) }
    }

    /**
     * Her colors for a widget, in light and in dark.
     *
     * Widgets are drawn once and then shown by the launcher, so a widget drawn in light mode would
     * stay light when the phone switches to dark. On Android 12+ we hand the launcher both colors
     * and it picks the right one itself, at once. With theme "Light" or "Dark" both are the same.
     * (Android 10–11 can't do that: there the widget is redrawn when the app opens, and hourly.)
     */
    class Palette(val light: ColorScheme, val dark: ColorScheme, private val nowDark: Boolean) {
        val now: ColorScheme get() = if (nowDark) dark else light

        fun set(views: RemoteViews, id: Int, method: String, pick: (ColorScheme) -> Color) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) views.setColorInt(id, method, pick(light).toArgb(), pick(dark).toArgb())
            else views.setInt(id, method, pick(now).toArgb())
        }

        fun textColor(views: RemoteViews, id: Int, pick: (ColorScheme) -> Color) = set(views, id, "setTextColor", pick)

        /** An image drawn in her colors (the ring), in both modes. */
        fun image(views: RemoteViews, id: Int, draw: (ColorScheme) -> Bitmap) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val l = Icon.createWithBitmap(draw(light))
                views.setIcon(id, "setImageIcon", l, if (dark === light) l else Icon.createWithBitmap(draw(dark)))
            } else {
                views.setImageViewBitmap(id, draw(now))
            }
        }

        companion object {
            fun of(context: Context, prefs: Prefs): Palette = when (prefs.theme) {
                ThemeMode.SYSTEM -> Palette(
                    colorSchemeFor(context, prefs.accent, dark = false),
                    colorSchemeFor(context, prefs.accent, dark = true),
                    nowDark = prefs.theme.isDark(context),
                )
                else -> colorSchemeFor(context, prefs.accent, prefs.theme.isDark(context)).let { Palette(it, it, prefs.theme == ThemeMode.DARK) }
            }
        }
    }

    private class Snapshot(val prefs: Prefs, val palette: Palette, val model: CycleModel, val readLinks: Set<String>, val enabledFeeds: Set<String>)

    private fun snapshot(context: Context): Snapshot {
        val store = Store(context)
        try {
            val prefs = Prefs.from(store.loadSettings())
            val periodDays = store.loadPeriodDays(History.firstDay(LocalDate.now()))
            val model = CycleCalculator.model(periodDays, prefs.cycle, LocalDate.now())
            return Snapshot(prefs, Palette.of(context, prefs), model, store.loadReadLinks(), store.loadFeeds().filter { it.enabled }.map { it.url }.toSet())
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

    private fun RemoteViews.background(palette: Palette) = palette.set(this, R.id.widget_bg, "setColorFilter") { it.surfaceContainer }

    // ------------------------------------------------------------------ cycle widget

    fun renderCycle(context: Context, only: IntArray? = null) {
        val widgetIds = only ?: ids(context, CycleWidget::class.java)
        if (widgetIds.isEmpty()) return
        val snap = snapshot(context)
        val pal = snap.palette
        val m = snap.model
        val today = CycleCalculator.dayInfo(m.today, m)
        val fert = snap.prefs.showFertility

        val views = RemoteViews(context.packageName, R.layout.widget_cycle).apply {
            background(pal)
            pal.textColor(this, R.id.cycle_phase) { it.onSurface }
            pal.textColor(this, R.id.cycle_next) { it.onSurfaceVariant }
            if (!m.hasData) {
                setViewVisibility(R.id.cycle_ring, View.GONE)
                setTextViewText(R.id.cycle_phase, Words.APP_NAME)
                setTextViewText(R.id.cycle_next, Words.Widget.NO_DATA)
            } else {
                setViewVisibility(R.id.cycle_ring, View.VISIBLE)
                pal.image(this, R.id.cycle_ring) { cs -> ringBitmap(context, today, m, cs, fert) }
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

    class NewsData(val articles: List<Article>, val readLinks: Set<String>, val palette: Palette)

    /** Everything the list needs: saved articles from switched-on feeds, which ones she read, and her colors. */
    fun newsData(context: Context): NewsData {
        val snap = snapshot(context)
        val articles = NewsCache(context).load().filter { it.feedUrl in snap.enabledFeeds }
        return NewsData(articles, snap.readLinks, snap.palette)
    }

    /**
     * Draws the widget frame and its scrollable list of articles.
     *
     * Android 12+: the rows go inside the same update as the frame, so both always change together.
     * (The older service-based list is converted by newer Android in the background, and its
     * "list changed" refresh then put back the *previous* frame: new rows, old colors.)
     * Android 10–11: the rows come from [NewsWidgetService], asked to reload after the frame is drawn.
     */
    @Suppress("DEPRECATION") // setRemoteAdapter(Intent) is only used on Android 10 and 11
    fun renderNews(context: Context, only: IntArray? = null) {
        val widgetIds = only ?: ids(context, NewsWidget::class.java)
        if (widgetIds.isEmpty()) return
        val news = newsData(context)
        val pal = news.palette
        val manager = AppWidgetManager.getInstance(context)
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        for (id in widgetIds) {
            // Tapping a row fills its article into this template (so it must be mutable; it only targets our own activity).
            val rowTap = PendingIntent.getActivity(
                context, 10,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_news).apply {
                background(pal)
                setTextViewText(R.id.news_header, Words.News.TITLE)
                pal.textColor(this, R.id.news_header) { it.primary }
                setOnClickPendingIntent(R.id.news_header, openApp(context, 2) { putExtra(EXTRA_TAB, TAB_NEWS) })
                setTextViewText(R.id.news_empty, Words.Widget.NEWS_EMPTY)
                pal.textColor(this, R.id.news_empty) { it.onSurfaceVariant }
                setOnClickPendingIntent(R.id.news_empty, openApp(context, 3) { putExtra(EXTRA_TAB, TAB_NEWS) })
                if (modern) {
                    val items = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
                    news.articles.forEachIndexed { i, a -> items.addItem(i.toLong(), newsRow(context, a, a.link !in news.readLinks, pal)) }
                    setRemoteAdapter(R.id.news_list, items.build())
                } else {
                    setRemoteAdapter(R.id.news_list, Intent(context, NewsWidgetService::class.java).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME)) // one list per widget
                    })
                }
                setEmptyView(R.id.news_list, R.id.news_empty)
                setPendingIntentTemplate(R.id.news_list, rowTap)
            }
            manager.updateAppWidget(id, views)
        }
        if (!modern) manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.news_list)
    }

    /** One article row: title (dimmer once read), "■ New · source · when", and what a tap opens. */
    fun newsRow(context: Context, a: Article, isNew: Boolean, pal: Palette) =
        RemoteViews(context.packageName, R.layout.widget_news_item).apply {
            setTextViewText(R.id.news_item_title, a.title)
            pal.textColor(this, R.id.news_item_title) { if (isNew) it.onSurface else it.onSurfaceVariant }
            val meta = SpannableString(Words.Widget.newsMeta(isNew, a.source, ago(a.published)))
            if (isNew) {
                val end = meta.indexOf(" ·").takeIf { it > 0 } ?: meta.length
                meta.setSpan(ForegroundColorSpan(NEW_RED), 0, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            setTextViewText(R.id.news_item_meta, meta)
            pal.textColor(this, R.id.news_item_meta) { it.primary }
            pal.set(this, R.id.news_item_divider, "setBackgroundColor") { it.outlineVariant }
            // Filled into the list's tap template: opens this article in the reader.
            setOnClickFillInIntent(R.id.news_item, Intent().apply {
                putExtra(EXTRA_ARTICLE_LINK, a.link)
                putExtra(EXTRA_ARTICLE_TITLE, a.title)
                putExtra(EXTRA_ARTICLE_SOURCE, a.source)
            })
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
