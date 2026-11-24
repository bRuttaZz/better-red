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
import site.brutt.betterred.share.Broker
import site.brutt.betterred.share.BrokerException
import site.brutt.betterred.share.Share
import site.brutt.betterred.share.ShareCode
import site.brutt.betterred.share.ShareRole
import site.brutt.betterred.share.ShareSync
import site.brutt.betterred.share.ShareView
import site.brutt.betterred.share.ShareWorker
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
    /** Partner sharing: is this phone sharing (hers), viewing (partner's), or neither. */
    val share: ShareView = ShareView(role = null),
) {
    /** The partner's phone: everything is view only. */
    val isPartner: Boolean get() = share.role == ShareRole.PARTNER

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
    init {
        write { deleteDaysBefore(History.firstDay(LocalDate.now())) }
        // A sync (here or in the background) changed the database: show it.
        viewModelScope.launch { ShareSync.changed.collect { write { } } }
        // Catch up right away when the app opens (the background job runs every 30 minutes).
        viewModelScope.launch {
            val error = ShareSync.sync(app)
            if (error == null) Widgets.updateAll(app)
        }
    }

    /**
     * Runs [block] on the database, then reloads. [share] = it changed something her partner sees,
     * so (if she is sharing) a sync is scheduled.
     */
    private fun write(done: String? = null, share: Boolean = false, block: Store.() -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                lock.withLock {
                    runCatching {
                        store.block()
                        AppState(
                            loaded = true, logs = store.loadLogs(History.firstDay(LocalDate.now())), prefs = Prefs.from(store.loadSettings()),
                            feeds = store.loadFeeds(), readLinks = store.loadReadLinks(), share = store.loadShare().view(),
                        )
                    }
                }
            }
            result.onSuccess {
                val roleBefore = _state.value.share.role
                _state.value = it
                done?.let(_messages::tryEmit)
                val app = getApplication<Application>()
                if (it.share.role != roleBefore) {
                    if (it.share.role != null) ShareWorker.schedule(app) else ShareWorker.cancel(app)
                }
                if (share && it.share.role == ShareRole.PUBLISHER) ShareWorker.soon(app)
                Widgets.updateAll(getApplication()) // logs, settings and read marks all show on widgets
            }
                .onFailure { _messages.tryEmit(it.message ?: Words.Messages.SOMETHING_WRONG) }
        }
    }

    private fun log(date: LocalDate) = _state.value.logs[date] ?: DayLog(date)

    // ---------- logging ----------

    // On the partner's phone everything is view only: these do nothing there.

    fun saveLog(log: DayLog) {
        if (_state.value.isPartner) return
        write(Words.Messages.SAVED, share = true) { saveLog(log) }
    }

    fun togglePeriod(date: LocalDate) {
        if (_state.value.isPartner) return
        val current = log(date)
        val updated = current.copy(flow = if (current.flow == null) Flow.MEDIUM else null)
        write(if (updated.flow != null) Words.Messages.PERIOD_LOGGED else Words.Messages.PERIOD_REMOVED, share = true) { saveLog(updated) }
    }

    fun setMood(date: LocalDate, mood: Mood) {
        if (_state.value.isPartner) return
        val current = log(date)
        write(share = true) { saveLog(current.copy(mood = if (current.mood == mood) null else mood)) }
    }

    // ---------- settings ----------

    // Name and cycle numbers are part of what a partner sees, so they count as shared changes.
    private fun put(vararg pairs: Pair<String, Any>) = write(share = true) { putSettings(pairs.associate { (k, v) -> k to v.toString() }) }

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

    /** Deletes everything. If she was sharing, her data is first removed from the server too. */
    fun deleteEverything() {
        viewModelScope.launch {
            if (_state.value.share.role == ShareRole.PUBLISHER) ShareSync.stopSharing(getApplication())
            write(Words.Messages.ALL_DELETED) { deleteEverything() }
        }
    }

    // ---------- partner sharing ----------

    private val _pairingCode = MutableStateFlow<String?>(null)
    /** The QR text while the pairing code is on screen (it holds the private key: never stored). */
    val pairingCode: StateFlow<String?> = _pairingCode.asStateFlow()

    private val _shareBusy = MutableStateFlow(false)
    val shareBusy: StateFlow<Boolean> = _shareBusy.asStateFlow()

    private fun busy(block: suspend () -> Unit) {
        if (_shareBusy.value) return
        _shareBusy.value = true
        viewModelScope.launch { try { block() } finally { _shareBusy.value = false } }
    }

    /** Her phone: registers with the server and shows the pairing code (replacing any earlier partner). */
    fun startSharing(serverInput: String) {
        val app = getApplication<Application>()
        val server = Share.normalizeServer(serverInput, ShareSync.allowLocal(app))
        if (server == null) { _messages.tryEmit(Words.Partner.BAD_SERVER); return }
        busy {
            runCatching { ShareSync.startSharing(app, server) }
                .onSuccess { _pairingCode.value = it }
                .onFailure { _messages.tryEmit(Words.Partner.error((it as? BrokerException)?.kind ?: site.brutt.betterred.share.SyncError.SERVER)) }
        }
    }

    fun closePairingCode() { _pairingCode.value = null }

    fun syncNow() = busy {
        val error = ShareSync.sync(getApplication())
        _messages.tryEmit(error?.let { if (_state.value.isPartner) Words.Partner.partnerError(it, _state.value.share.partnerName.orEmpty()) else Words.Partner.error(it) } ?: Words.Partner.SYNCED)
        if (error == null) Widgets.updateAll(getApplication())
    }

    fun stopSharing() = busy {
        val removed = ShareSync.stopSharing(getApplication())
        _messages.tryEmit(if (removed) Words.Partner.STOPPED else Words.Partner.STOPPED_OFFLINE)
    }

    /** Her phone: what the partner may see. Every day is sent again with the new choice. */
    fun setShareChoice(key: String, on: Boolean) {
        write(share = true) {
            putSettings(mapOf(key to on.toString()))
            loadShare().takeIf { it.role == ShareRole.PUBLISHER }?.let { saveShare(it.copy(sharedAt = Broker.now())) }
        }
    }

    /** The partner's phone: checks and saves a scanned code. Returns an error message, or null when connected. */
    suspend fun connectPartner(text: String): String? {
        val app = getApplication<Application>()
        val code = ShareCode.parse(text, ShareSync.allowLocal(app)) ?: return Words.Partner.NOT_A_CODE
        val error = ShareSync.connect(app, code) ?: run {
            Widgets.updateAll(app)
            return null
        }
        return Words.Partner.partnerError(error, "")
    }

    /** The partner's phone: forgets her data and goes back to the welcome screen. */
    fun disconnectPartner() = busy {
        ShareSync.disconnect(getApplication())
        Widgets.updateAll(getApplication())
    }

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
