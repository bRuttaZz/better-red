package site.brutt.betterred.share

import org.json.JSONObject
import site.brutt.betterred.data.DayJson
import site.brutt.betterred.domain.DayLog
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

/** What she lets her partner see. Period dates are always shared; sex is never shared. */
data class ShareChoices(
    val mood: Boolean = true,
    val symptoms: Boolean = false,
    val notes: Boolean = false,
    val fertility: Boolean = false,
)

/**
 * What the QR code holds: "BR1|<server>|<pair id>|<private key>|<pair secret>" (keys base64url).
 * Whoever scans it can read what she shares, so it is shown only on her screen, once.
 */
data class ShareCode(val server: String, val pairId: String, val privateKey: ByteArray, val secret: ByteArray) {
    fun encode(): String = listOf("BR1", server, pairId, ShareCrypto.b64(privateKey), ShareCrypto.b64(secret)).joinToString("|")

    companion object {
        fun parse(text: String, allowLocal: Boolean = false): ShareCode? {
            val parts = text.trim().split('|')
            if (parts.size != 5 || parts[0] != "BR1") return null
            val server = Share.normalizeServer(parts[1], allowLocal) ?: return null
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
