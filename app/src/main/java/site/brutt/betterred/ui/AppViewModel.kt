package site.brutt.betterred.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import site.brutt.betterred.Words
import site.brutt.betterred.data.Accent
import site.brutt.betterred.data.Article
import site.brutt.betterred.data.Feed
import site.brutt.betterred.data.Feeds
import site.brutt.betterred.data.NewsCache
import site.brutt.betterred.data.mergeNews
import site.brutt.betterred.widget.Widgets
import site.brutt.betterred.data.Rss
import site.brutt.betterred.data.Prefs
import site.brutt.betterred.data.Store
import site.brutt.betterred.data.ThemeMode
import site.brutt.betterred.domain.CycleSettings
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Flow
import site.brutt.betterred.domain.History
import site.brutt.betterred.domain.Mood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class AppState(
    val loaded: Boolean = false,
    val logs: Map<LocalDate, DayLog> = emptyMap(),
    val prefs: Prefs = Prefs(),
    val feeds: List<Feed> = emptyList(),
    /** Links of articles she has opened (the rest get the red "New" mark). */
    val readLinks: Set<String> = emptySet(),
) {
    val periodDays: Set<LocalDate> get() = logs.filterValues { it.flow != null }.keys
}

/** What the "Doctor said" tab shows. Only the latest items each feed provides; nothing is archived. */
data class NewsState(
    val loading: Boolean = false,
    val articles: List<Article> = emptyList(),
    /** Names of feeds that couldn't be loaded on the last refresh. */
    val failed: List<String> = emptyList(),
    val loadedOnce: Boolean = false,
)

/** Something a widget asked the app to show. */
sealed interface OpenRequest {
    data class Tab(val name: String) : OpenRequest
    data class Read(val article: Article) : OpenRequest
}

/**
 * Holds the app state and does every write to the database (off the main thread,
 * one at a time), then reloads. The data is small, so reloading everything is fine.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    private val lock = Mutex()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    /** Short messages for the snackbar. */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // On start: forget days older than 5 years (History.YEARS), then load everything.
    init { write { deleteDaysBefore(History.firstDay(LocalDate.now())) } }

    private fun write(done: String? = null, block: Store.() -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                lock.withLock {
                    runCatching {
                        store.block()
                        AppState(
                            loaded = true, logs = store.loadLogs(History.firstDay(LocalDate.now())), prefs = Prefs.from(store.loadSettings()),
                            feeds = store.loadFeeds(), readLinks = store.loadReadLinks(),
                        )
                    }
                }
            }
            result.onSuccess {
                _state.value = it
                done?.let(_messages::tryEmit)
                Widgets.updateAll(getApplication()) // logs, settings and read marks all show on widgets
            }
                .onFailure { _messages.tryEmit(it.message ?: Words.Messages.SOMETHING_WRONG) }
        }
    }

    private fun log(date: LocalDate) = _state.value.logs[date] ?: DayLog(date)

    // ---------- logging ----------

    fun saveLog(log: DayLog) = write(Words.Messages.SAVED) { saveLog(log) }

    fun togglePeriod(date: LocalDate) {
        val current = log(date)
        val updated = current.copy(flow = if (current.flow == null) Flow.MEDIUM else null)
        write(if (updated.flow != null) Words.Messages.PERIOD_LOGGED else Words.Messages.PERIOD_REMOVED) { saveLog(updated) }
    }

    fun setMood(date: LocalDate, mood: Mood) {
        val current = log(date)
        write { saveLog(current.copy(mood = if (current.mood == mood) null else mood)) }
    }

    // ---------- settings ----------

    private fun put(vararg pairs: Pair<String, Any>) = write { putSettings(pairs.associate { (k, v) -> k to v.toString() }) }

    fun setName(name: String) = put(Prefs.Keys.NAME to name.trim().take(24))
    fun setTheme(mode: ThemeMode) = put(Prefs.Keys.THEME to mode.name)
    fun setAccent(accent: Accent) = put(Prefs.Keys.ACCENT to accent.name)
    fun setShowFertility(show: Boolean) = put(Prefs.Keys.SHOW_FERTILITY to show)
    fun setTrackSex(on: Boolean) = put(Prefs.Keys.TRACK_SEX to on)
    fun setAvatar(avatar: String) = put(Prefs.Keys.AVATAR to avatar)
    fun setAutoAdjust(on: Boolean) = put(Prefs.Keys.AUTO_ADJUST to on)
    fun setCycleLength(v: Int) = put(Prefs.Keys.CYCLE_LENGTH to v.coerceIn(CycleSettings.CYCLE_RANGE))
    fun setPeriodLength(v: Int) = put(Prefs.Keys.PERIOD_LENGTH to v.coerceIn(CycleSettings.PERIOD_RANGE))
    fun setLutealLength(v: Int) = put(Prefs.Keys.LUTEAL_LENGTH to v.coerceIn(CycleSettings.LUTEAL_RANGE))

    fun finishOnboarding(name: String, lastPeriodStart: LocalDate?, cycleLength: Int, periodLength: Int) = write(Words.Messages.ALL_SET) {
        putSettings(
            mapOf(
                Prefs.Keys.NAME to name.trim().take(24),
                Prefs.Keys.CYCLE_LENGTH to cycleLength.toString(),
                Prefs.Keys.PERIOD_LENGTH to periodLength.toString(),
                Prefs.Keys.ONBOARDED to "true",
            )
        )
        if (lastPeriodStart != null) {
            val today = LocalDate.now()
            for (i in 0 until periodLength) {
                val d = lastPeriodStart.plusDays(i.toLong())
                if (d.isAfter(today)) break
                val existing = loadLogs(d)[d] ?: DayLog(d)
                if (existing.flow == null) saveLog(existing.copy(flow = Flow.MEDIUM))
            }
        }
    }

    // ---------- export / import ----------

    fun exportTo(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        write(Words.Messages.BACKUP_SAVED) {
            resolver.openOutputStream(uri, "wt")?.use { exportTo(it) } ?: error(Words.Messages.CANT_OPEN_LOCATION)
        }
    }

    fun importFrom(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        write(Words.Messages.BACKUP_RESTORED) {
            resolver.openInputStream(uri)?.use { importFrom(it) } ?: error(Words.Messages.CANT_OPEN_FILE)
        }
    }

    fun deleteEverything() = write(Words.Messages.ALL_DELETED) { deleteEverything() }

    // ---------- news ("Doctor said") ----------

    private val newsCache = NewsCache(app)
    private val _news = MutableStateFlow(NewsState())
    val news: StateFlow<NewsState> = _news.asStateFlow()

    init {
        // Show the last list straight away; fresh posts replace it when they arrive.
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { newsCache.load() }
            if (!_news.value.loadedOnce && _news.value.articles.isEmpty()) _news.value = _news.value.copy(articles = cached)
        }
    }

    /**
     * Fetches every enabled feed at once. The current list stays on screen while loading and is
     * swapped only when all fetches are done. A feed that fails keeps its previous articles.
     * Called each time the tab opens.
     */
    fun refreshNews() {
        if (_news.value.loading) return
        val feeds = _state.value.feeds.filter { it.enabled }
        _news.value = _news.value.copy(loading = true)
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) {
                feeds.map { feed -> async { feed to runCatching { Rss.fetch(feed.url) } } }.awaitAll()
            }
            val articles = mergeNews(_news.value.articles, results)
            _news.value = NewsState(
                loading = false, articles = articles, loadedOnce = true,
                failed = results.filter { it.second.isFailure }.map { it.first.title },
            )
            if (results.any { it.second.isSuccess }) {
                withContext(Dispatchers.IO) { newsCache.save(articles) }
                Widgets.updateAll(getApplication())
            }
        }
    }

    fun markRead(link: String) = write { markRead(link) }

    // ---------- opened from a widget ----------

    private val _open = MutableStateFlow<OpenRequest?>(null)
    val open: StateFlow<OpenRequest?> = _open.asStateFlow()

    fun requestOpen(request: OpenRequest) { _open.value = request }
    fun openHandled() { _open.value = null }

    fun setFeedEnabled(feed: Feed, enabled: Boolean) = write { setFeedEnabled(feed.id, enabled) }

    fun removeFeed(feed: Feed) = write { removeFeed(feed.id) }

    /** Checks the address really is a feed, then saves it. Returns an error message, or null when added. */
    suspend fun addFeed(input: String): String? {
        val feeds = _state.value.feeds
        if (feeds.count { !it.builtIn } >= Feeds.MAX_CUSTOM) return Words.News.maxFeeds(Feeds.MAX_CUSTOM)
        var url = input.filterNot { it.isWhitespace() } // keyboards sometimes sneak in spaces
        if (!url.contains("://")) url = "https://$url"
        url = url.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://") // phones block plain http
        val host = runCatching { java.net.URI(url).host }.getOrNull()
        if (host.isNullOrBlank() || !host.contains('.')) return Words.News.BAD_LINK
        if (feeds.any { it.url.equals(url, ignoreCase = true) }) return Words.News.ALREADY_ADDED

        val parsed = withContext(Dispatchers.IO) { runCatching { Rss.fetch(url) }.getOrNull() }
            ?: return Words.News.NOT_A_FEED
        if (parsed.articles.isEmpty()) return Words.News.NOT_A_FEED
        write(Words.News.FEED_ADDED) { addFeed(url, parsed.title.take(60)) }
        return null
    }
}
