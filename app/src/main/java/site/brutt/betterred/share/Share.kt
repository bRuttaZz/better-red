package site.brutt.betterred.share

import org.json.JSONObject
import site.brutt.betterred.data.DayJson
import site.brutt.betterred.domain.DayLog
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

/** Partner sharing: fixed values, the QR code format, and what goes inside the encrypted blobs. */
object Share {
    /** Most days ever shared (the broker may lower it, never raise it). */
    const val MAX_DAYS = 60

    const val DEFAULT_SERVER = "better-red.brutt.site"

    /** Minutes between background syncs (Android's minimum is 15). */
    const val SYNC_MINUTES = 30L

    /** Key of the settings blob. Day blobs use "YYYY-MM-DD". */
    const val CONFIG_KEY = "0"

    private val HOST = Regex("""^(?=.{1,253}$)[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+(:\d{1,5})?$""")
    private val LOCAL = Regex("""^(localhost|10\.0\.2\.2)(:\d{1,5})?$""")

    /**
     * Cleans what she typed into a server address: "https://Better-Red.brutt.site/" → "better-red.brutt.site".
     * Only a domain (and optional port) is accepted; the app adds the paths itself. Null when invalid.
     * [allowLocal]: debug builds may use a broker on this computer (localhost, or 10.0.2.2 from the emulator).
     */
    fun normalizeServer(input: String, allowLocal: Boolean = false): String? {
        val s = input.trim().lowercase().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (allowLocal && LOCAL.matches(s)) return s
        return s.takeIf { HOST.matches(it) && !LOCAL.matches(it) }
    }

    /** Base URL for a server address. Plain http only for the local test broker (debug builds). */
    fun baseUrl(server: String): String = if (LOCAL.matches(server)) "http://$server" else "https://$server"

    fun isLocal(server: String) = LOCAL.matches(server)
}

/**
 * A broker: its domain (and port), plus a password if it's a private, self-hosted one (started
 * with BR_PASSWORD). She types it like a connection string, "password@domain"; the default server
 * has no password. The password goes along with every request (X-Better-Red-Password) and is in
 * the pairing code, because the partner's phone needs it too. Never shown on screen.
 */
data class Server(val host: String, val password: String? = null) {
    /** Stored form (device table, QR code): "domain" or "<password, URL-encoded>@domain". */
    fun encode(): String = password?.let { "${URLEncoder.encode(it, "UTF-8")}@$host" } ?: host

    override fun toString() = host // never the password (logs, screens)

    companion object {
        private val PASSWORD = Regex("""^[\x21-\x7E]{1,128}$""") // printable ASCII, no spaces (same rule as the broker)

        fun validPassword(p: String) = PASSWORD.matches(p)

        /** What she typed: "domain" or "password@domain" (the last "@" splits them). Null when invalid. */
        fun parse(input: String, allowLocal: Boolean = false): Server? {
            var s = input.trim()
            for (scheme in listOf("https://", "http://")) if (s.startsWith(scheme, ignoreCase = true)) s = s.substring(scheme.length)
            val at = s.lastIndexOf('@')
            val host = Share.normalizeServer(if (at >= 0) s.substring(at + 1) else s, allowLocal) ?: return null
            if (at < 0) return Server(host)
            val password = s.substring(0, at)
            return if (validPassword(password)) Server(host, password) else null
        }

        /** The stored form ([encode]). Null when invalid. */
        fun decode(stored: String, allowLocal: Boolean = false): Server? {
            val at = stored.lastIndexOf('@')
            val host = Share.normalizeServer(if (at >= 0) stored.substring(at + 1) else stored, allowLocal) ?: return null
            if (at < 0) return Server(host)
            val password = runCatching { URLDecoder.decode(stored.substring(0, at), "UTF-8") }.getOrNull() ?: return null
            return if (validPassword(password)) Server(host, password) else null
        }
    }
}

/** What she lets her partner see. Period dates are always shared; sex is never shared. */
data class ShareChoices(
    val mood: Boolean = true,
    val symptoms: Boolean = false,
    val notes: Boolean = false,
    val fertility: Boolean = false,
)

/**
 * What the QR code holds: "BR1|<server>|<pair id>|<private key>|<pair secret>" (keys base64url;
 * server as in [Server.encode], so "<password>@domain" for a private broker; older app versions
 * refuse such codes instead of failing later).
 * Whoever scans it can read what she shares, so it is shown only on her screen, once.
 */
data class ShareCode(val server: Server, val pairId: String, val privateKey: ByteArray, val secret: ByteArray) {
    fun encode(): String = listOf("BR1", server.encode(), pairId, ShareCrypto.b64(privateKey), ShareCrypto.b64(secret)).joinToString("|")

    companion object {
        fun parse(text: String, allowLocal: Boolean = false): ShareCode? {
            val parts = text.trim().split('|')
            if (parts.size != 5 || parts[0] != "BR1") return null
            val server = Server.decode(parts[1], allowLocal) ?: return null
            if (!Regex("""^[A-Za-z0-9_-]{22}\.[A-Za-z0-9_-]{32}$""").matches(parts[2])) return null
            val priv = runCatching { ShareCrypto.unb64(parts[3]) }.getOrNull()?.takeIf { it.size == 32 } ?: return null
            val secret = runCatching { ShareCrypto.unb64(parts[4]) }.getOrNull()?.takeIf { it.size == 32 } ?: return null
            return ShareCode(server, parts[2], priv, secret)
        }
    }

    override fun equals(other: Any?) = other is ShareCode && other.server == server && other.pairId == pairId &&
        other.privateKey.contentEquals(privateKey) && other.secret.contentEquals(secret)

    override fun hashCode() = pairId.hashCode()
}

/** The settings blob (key "0"): her name, cycle numbers, and what she shares. Sent on every sync. */
data class ShareConfig(
    val name: String,
    val cycleLength: Int,
    val periodLength: Int,
    val lutealLength: Int,
    val choices: ShareChoices,
    val days: Int,
) {
    fun toJson(): String = JSONObject().apply {
        put("v", 1)
        put("name", name)
        put("cycle", cycleLength)
        put("period", periodLength)
        put("luteal", lutealLength)
        put("days", days)
        put("share", JSONObject().apply {
            put("mood", choices.mood); put("symptoms", choices.symptoms)
            put("notes", choices.notes); put("fertility", choices.fertility)
        })
    }.toString()

    companion object {
        fun fromJson(json: String): ShareConfig {
            val o = JSONObject(json)
            val s = o.optJSONObject("share") ?: JSONObject()
            return ShareConfig(
                name = o.optString("name").take(24),
                cycleLength = o.optInt("cycle", 28),
                periodLength = o.optInt("period", 5),
                lutealLength = o.optInt("luteal", 14),
                choices = ShareChoices(
                    mood = s.optBoolean("mood"), symptoms = s.optBoolean("symptoms"),
                    notes = s.optBoolean("notes"), fertility = s.optBoolean("fertility"),
                ),
                days = o.optInt("days", Share.MAX_DAYS).coerceIn(1, Share.MAX_DAYS),
            )
        }
    }
}

/** Only what she chose to share from one day, or null if nothing of that day is shared. */
fun DayLog.forPartner(choices: ShareChoices): DayLog? {
    val shared = DayLog(
        date = date,
        flow = flow,
        mood = mood.takeIf { choices.mood },
        symptoms = if (choices.symptoms) symptoms else emptySet(),
        note = if (choices.notes) note else "",
        sex = null, // never shared
    )
    return shared.takeIf { !it.isEmpty }
}

/** The day blob's JSON is the same format as the local day_log.data (see DayJson). */
fun dayToJson(log: DayLog): String = DayJson.write(log)

fun dayFromJson(date: LocalDate, json: String): DayLog = DayJson.read(date, periodDay = false, json = json)
