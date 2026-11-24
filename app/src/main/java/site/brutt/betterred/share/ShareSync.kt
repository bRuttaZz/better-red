package site.brutt.betterred.share

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import site.brutt.betterred.data.Prefs
import site.brutt.betterred.data.Store
import site.brutt.betterred.domain.CycleCalculator
import site.brutt.betterred.domain.History
import java.time.LocalDate

enum class ShareRole {
    PUBLISHER, // her phone: shares
    PARTNER,   // the partner's phone: view only
}

/** The device row (see Store.kt). Secrets stay in here; the UI only gets [ShareView]. */
data class ShareInfo(
    val deviceKey: String,
    val role: ShareRole? = null,
    val server: String? = null,
    val pairId: String? = null,
    /** The pair secret, on both phones (it's in the QR code). The partner's one-time codes come from it. (base64url) */
    val secret: String? = null,
    /** Publisher: the share public key. Partner: the share private key. (base64url) */
    val shareKey: String? = null,
    /** Publisher: when the share choices last changed, so every day is sent again. */
    val sharedAt: Long = 0,
    val syncedAt: Long = 0,
    val syncError: SyncError? = null,
    /** Partner: her name, from the settings blob. */
    val partnerName: String? = null,
) {
    val paired: Boolean get() = role != null && server != null && pairId != null && secret != null && shareKey != null

    fun view(): ShareView {
        if (!paired) return ShareView(role = null)
        return ShareView(role, server, syncedAt, syncError, partnerName, ShareCrypto.fingerprint(pairId!!, ShareCrypto.unb64(secret!!)))
    }
}

/** What the screens may know about sharing (no keys). */
data class ShareView(
    val role: ShareRole?,
    val server: String? = null,
    val syncedAt: Long = 0,
    val error: SyncError? = null,
    val partnerName: String? = null,
    val fingerprint: String = "",
)

/**
 * Partner sharing, end to end. One sync at a time per process ([lock]).
 *
 * The broker is a dumb cache that keeps nothing but what her phone writes:
 * Publisher (her phone), every sync:
 *   1. ask the broker which days it has and when they changed (index, no blobs; empty if it lost
 *      everything, and then everything is simply sent again);
 *   2. send every day of the window that is missing or changed, encrypted, with the pair secret;
 *   3. always send the settings blob "0" (name, cycle numbers, share choices).
 * Partner (view-only phone), every sync: download everything, decrypt, replace the local days.
 * Nothing on the broker = wait for her phone. Both only ever handle the last [Share.MAX_DAYS] days.
 */
object ShareSync {
    private val lock = Mutex()
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires after a sync changed the database (the app then reloads). */
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun allowLocal(context: Context) = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private suspend fun <T> withStore(context: Context, block: (Store) -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val store = Store(context.applicationContext)
            try { block(store) } finally { store.close() }
        }
    }

    /** Runs one sync for whatever this phone is. Returns the error, or null when it worked. */
    suspend fun sync(context: Context): SyncError? = withStore(context) { store ->
        val info = store.loadShare()
        if (!info.paired) return@withStore null
        val error = try {
            when (info.role!!) {
                ShareRole.PUBLISHER -> push(store, info)
                ShareRole.PARTNER -> pull(store, info, partnerCode(info))
            }
            null
        } catch (e: BrokerException) {
            e.kind
        } catch (e: Exception) {
            SyncError.DATA
        }
        val now = Broker.now()
        store.saveShare(store.loadShare().copy(syncError = error, syncedAt = if (error == null) now else info.syncedAt))
        error
    }.also { _changed.tryEmit(Unit) }

    // ---------------------------------------------------------------- publisher

    /**
     * Pairs a new partner: asks the broker to mint a pair id for this phone (which also removes any
     * earlier pairing of it), makes a new pair secret and key pair, keeps the public key, and
     * returns the QR text: server, pair id, private key and secret.
     */
    suspend fun startSharing(context: Context, server: String): String = withStore(context) { store ->
        val info = store.loadShare()
        val minted = Broker(server).mint(ShareCrypto.unb64(info.deviceKey))
        val secret = ShareCrypto.randomKey()
        val (publicKey, privateKey) = ShareCrypto.newKeyPair()
        store.saveShare(
            ShareInfo(
                deviceKey = info.deviceKey, role = ShareRole.PUBLISHER, server = server, pairId = minted.pairId,
                secret = ShareCrypto.b64(secret), shareKey = ShareCrypto.b64(publicKey), sharedAt = Broker.now(),
            )
        )
        // First upload right away, so the partner finds data when scanning. If it fails, the
        // background sync tries again (the partner sees "waiting" until then).
        runCatching { push(store, store.loadShare()) }
            .onSuccess { store.saveShare(store.loadShare().copy(syncedAt = Broker.now())) }
        ShareCode(server, minted.pairId, privateKey, secret).encode()
    }.also { _changed.tryEmit(Unit) }

    /** Unpairs: deletes her data from the broker (if it can be reached) and forgets the pairing. */
    suspend fun stopSharing(context: Context): Boolean = withStore(context) { store ->
        val info = store.loadShare()
        val removed = if (info.paired && info.role == ShareRole.PUBLISHER) {
            runCatching { Broker(info.server!!).delete(info.pairId!!, ShareCrypto.unb64(info.deviceKey)) }
                .fold(onSuccess = { true }, onFailure = { (it as? BrokerException)?.kind == SyncError.INVALID })
        } else true
        store.saveShare(ShareInfo(deviceKey = info.deviceKey))
        removed
    }.also { _changed.tryEmit(Unit) }

    /** Her share choices changed: every day must be sent again (filtered differently). */
    suspend fun choicesChanged(context: Context) = withStore(context) { store ->
        val info = store.loadShare()
        if (info.role == ShareRole.PUBLISHER) store.saveShare(info.copy(sharedAt = Broker.now()))
    }

    private fun partnerCode(info: ShareInfo) =
        ShareCode(info.server!!, info.pairId!!, ShareCrypto.unb64(info.shareKey!!), ShareCrypto.unb64(info.secret!!))

    private fun push(store: Store, info: ShareInfo) {
        val broker = Broker(info.server!!)
        val pairId = info.pairId!!
        val deviceKey = ShareCrypto.unb64(info.deviceKey)
        val publicKey = ShareCrypto.unb64(info.shareKey!!)
        val remote = broker.index(pairId, deviceKey) // empty if the broker has nothing: then all is sent

        val days = minOf(Share.MAX_DAYS, remote.maxDays)
        val today = LocalDate.now()
        val prefs = Prefs.from(store.loadSettings())

        fun seal(key: String, json: String, updatedAt: Long) =
            RemoteEntry(ShareCrypto.b64(ShareCrypto.encrypt(publicKey, ShareCrypto.aad(pairId, key), ShareCrypto.pack(json))), updatedAt)

        // EVERY day of the window is sent, an empty one ("{}") when nothing of it is shared, and blobs
        // are padded (ShareCrypto.PAD): so the server can't tell which days are period days.
        val from = today.minusDays(days.toLong() - 1)
        val logged = store.loadDaysWithTimes(from).associateBy { it.first.date }
        val put = HashMap<String, RemoteEntry>()
        val window = HashSet<String>()
        var date = from
        while (!date.isAfter(today)) {
            val key = date.toString()
            val (log, updatedAt) = logged[date] ?: (null to 0L)
            // A day without a row counts as changed when the choices did (or it was just deleted).
            val time = maxOf(updatedAt, info.sharedAt)
            window += key
            if (remote.days[key] != time) put[key] = seal(key, log?.forPartner(prefs.share)?.let(::dayToJson) ?: "{}", time)
            date = date.plusDays(1)
        }
        val delete = remote.days.keys.filter { it != Share.CONFIG_KEY && it !in window }

        // The settings go every time: they keep the pair alive on the broker (unused pairs expire).
        val model = CycleCalculator.model(store.loadPeriodDays(History.firstDay(today)), prefs.cycle, today)
        val config = ShareConfig(prefs.name, model.cycleLength, model.periodLength, model.lutealLength, prefs.share, days)
        put[Share.CONFIG_KEY] = seal(Share.CONFIG_KEY, config.toJson(), Broker.now())
        broker.push(pairId, deviceKey, ShareCrypto.unb64(info.secret!!), put, delete)
    }

    // ---------------------------------------------------------------- partner

    /**
     * Partner phone: saves the scanned pairing and downloads her data. If the broker has nothing yet
     * (her phone hasn't uploaded), it is saved anyway and waits. Other errors leave nothing saved.
     */
    suspend fun connect(context: Context, code: ShareCode): SyncError? = withStore(context) { store ->
        val info = store.loadShare().copy(
            role = ShareRole.PARTNER, server = code.server, pairId = code.pairId,
            secret = ShareCrypto.b64(code.secret), shareKey = ShareCrypto.b64(code.privateKey),
            syncError = null, sharedAt = 0, partnerName = null,
        )
        val error = try {
            pull(store, info, code)
            store.saveShare(store.loadShare().copy(syncedAt = Broker.now()))
            null
        } catch (e: BrokerException) {
            e.kind
        } catch (e: Exception) {
            SyncError.DATA
        }
        when (error) {
            null -> Unit
            SyncError.WAITING -> {
                store.replaceDays(emptyList())
                store.saveShare(info.copy(syncError = SyncError.WAITING))
            }
            else -> return@withStore error
        }
        store.putSettings(mapOf(Prefs.Keys.ONBOARDED to "true"))
        null
    }.also { _changed.tryEmit(Unit) }

    /** Partner phone: forgets her and everything she shared. The app goes back to the welcome screen. */
    suspend fun disconnect(context: Context) = withStore(context) { store -> store.deleteEverything() }
        .also { _changed.tryEmit(Unit) }

    private fun pull(store: Store, info: ShareInfo, code: ShareCode) {
        val remote = Broker(code.server).read(code.pairId, code.secret)
        fun open(key: String, e: RemoteEntry): String =
            ShareCrypto.unpack(ShareCrypto.decrypt(code.privateKey, ShareCrypto.aad(code.pairId, key), ShareCrypto.unb64(e.blob)))

        val configEntry = remote.days[Share.CONFIG_KEY] ?: throw BrokerException(SyncError.WAITING, "nothing shared yet")
        val config = ShareConfig.fromJson(open(Share.CONFIG_KEY, configEntry))
        val days = minOf(Share.MAX_DAYS, remote.maxDays, config.days)
        val from = LocalDate.now().minusDays(days.toLong())
        val logs = remote.days.mapNotNull { (key, e) ->
            if (key == Share.CONFIG_KEY) return@mapNotNull null
            val date = runCatching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
            if (date.isBefore(from)) return@mapNotNull null
            // A day that can't be opened is skipped; the rest still shows.
            runCatching { dayFromJson(date, open(key, e)).forPartner(config.choices)?.let { it to e.updatedAt } }.getOrNull()
        }

        store.replaceDays(logs)
        store.putSettings(
            mapOf(
                Prefs.Keys.CYCLE_LENGTH to config.cycleLength.toString(),
                Prefs.Keys.PERIOD_LENGTH to config.periodLength.toString(),
                Prefs.Keys.LUTEAL_LENGTH to config.lutealLength.toString(),
                Prefs.Keys.AUTO_ADJUST to "false", // her phone already worked the numbers out
                Prefs.Keys.SHOW_FERTILITY to config.choices.fertility.toString(),
                Prefs.Keys.TRACK_SEX to "false",
                Prefs.Keys.SHARE_MOOD to config.choices.mood.toString(),
                Prefs.Keys.SHARE_SYMPTOMS to config.choices.symptoms.toString(),
                Prefs.Keys.SHARE_NOTES to config.choices.notes.toString(),
                Prefs.Keys.SHARE_FERTILITY to config.choices.fertility.toString(),
            )
        )
        store.saveShare(info.copy(partnerName = config.name, syncError = null))
    }
}
