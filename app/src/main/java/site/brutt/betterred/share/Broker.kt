package site.brutt.betterred.share

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/** Why a sync didn't work. Stored by name in the device table, shown with words from Words.Partner. */
enum class SyncError {
    OFFLINE, // no network, or the server didn't answer
    WAITING, // the partner: the server has nothing for this pair (yet, or any more): wait for her phone
    INVALID, // the server doesn't accept this pair id (e.g. another server, or its key changed): pair again
    CLOCK,   // the partner's one-time code was refused: usually a wrong clock on one of the phones
    SERVER,  // the server failed or refused the request
    PASSWORD, // a private (self-hosted) server refused the password, or wants one
    DATA,    // what came back couldn't be read (wrong key, damaged data)
}

class BrokerException(val kind: SyncError, message: String) : IOException(message)

/** One stored value on the broker: an encrypted blob and when it was last changed (unix seconds). */
data class RemoteEntry(val blob: String, val updatedAt: Long)

/**
 * Talks to a Better Red broker (better-red-broker, the Go server). Paths are added here; the user
 * only ever enters a domain (with a password for a private broker, sent on every request). The pair id goes in a header, never in the URL. Every call is plain
 * HTTPS with JSON; see docs/partner-sharing.md. Not for the main thread.
 *
 * Her phone proves a pair id is hers with its device key; the partner with a one-time code made
 * from the pair secret. The broker stores nothing but what her phone writes.
 */
class Broker(server: Server) {
    private val base = Share.baseUrl(server.host)
    private val password = server.password

    data class Minted(val pairId: String, val maxDays: Int)
    data class Index(val days: Map<String, Long>, val maxDays: Int)
    data class Remote(val days: Map<String, RemoteEntry>, val maxDays: Int)

    /** Her phone: a new signed pair id. The server deletes every earlier pair of this device. */
    fun mint(deviceKey: ByteArray): Minted {
        val o = call("POST", "/v1/pairs", body = JSONObject().put("deviceKey", ShareCrypto.b64(deviceKey)))
        return Minted(o.getString("pairId"), o.optInt("maxDays", Share.MAX_DAYS))
    }

    /** Her phone: which keys the server has and when they changed (empty if it has nothing). */
    fun index(pairId: String, deviceKey: ByteArray): Index {
        val o = call("GET", "/v1/days/index", her(pairId, deviceKey))
        val days = o.optJSONObject("days") ?: JSONObject()
        return Index(days.keys().asSequence().associateWith { days.getLong(it) }, o.optInt("maxDays", Share.MAX_DAYS))
    }

    /** Her phone: writes (the server creates the row if it's missing). The secret goes along every time. */
    fun push(pairId: String, deviceKey: ByteArray, secret: ByteArray, put: Map<String, RemoteEntry>, delete: Collection<String>) {
        val puts = JSONObject()
        for ((k, e) in put) puts.put(k, JSONObject().put("blob", e.blob).put("updatedAt", e.updatedAt))
        val body = JSONObject().put("secret", ShareCrypto.b64(secret)).put("put", puts).put("delete", JSONArray(delete.toList()))
        call("PUT", "/v1/days", her(pairId, deviceKey), body)
    }

    /** Her phone: unpair, the server forgets the pair. */
    fun delete(pairId: String, deviceKey: ByteArray) {
        call("DELETE", "/v1/days", her(pairId, deviceKey))
    }

    /** The partner: everything, blobs included. */
    fun read(pairId: String, secret: ByteArray): Remote {
        val o = call("GET", "/v1/days", partner = pairId to secret)
        val days = o.optJSONObject("days") ?: JSONObject()
        val out = HashMap<String, RemoteEntry>()
        for (k in days.keys()) {
            val e = days.optJSONObject(k) ?: continue
            out[k] = RemoteEntry(e.optString("blob"), e.optLong("updatedAt"))
        }
        return Remote(out, o.optInt("maxDays", Share.MAX_DAYS))
    }

    // ---------------------------------------------------------------- HTTP

    private fun her(pairId: String, deviceKey: ByteArray) =
        mapOf("X-Better-Red-Pair" to pairId, "X-Better-Red-Device" to ShareCrypto.b64(deviceKey))

    private fun call(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: JSONObject? = null,
        partner: Pair<String, ByteArray>? = null, // pair id + secret: a fresh one-time code per attempt
        retried: Boolean = false,
    ): JSONObject {
        val conn = try {
            (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "BetterRed")
                setRequestProperty("Accept", "application/json")
                if (password != null) setRequestProperty("X-Better-Red-Password", password)
                for ((k, v) in headers) setRequestProperty(k, v)
                if (partner != null) {
                    setRequestProperty("X-Better-Red-Pair", partner.first)
                    setRequestProperty("X-Better-Red-Code", ShareCrypto.totp(partner.second, now()))
                }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toString().toByteArray()) }
                }
            }
        } catch (e: IOException) {
            throw BrokerException(SyncError.OFFLINE, e.message ?: "offline")
        }
        try {
            val code = try { conn.responseCode } catch (e: IOException) { throw BrokerException(SyncError.OFFLINE, e.message ?: "offline") }
            learnClock(conn.date)
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readLimited(MAX_RESPONSE) }?.toString(Charsets.UTF_8).orEmpty()
            return when {
                code in 200..299 -> if (text.isBlank()) JSONObject() else JSONObject(text)
                code == 404 -> throw BrokerException(SyncError.WAITING, "nothing stored")
                code == 403 -> throw BrokerException(SyncError.INVALID, "pair id refused")
                // A private broker refusing the password: before the other 401s (no clock retry).
                code == 401 && conn.getHeaderField("WWW-Authenticate") == "BetterRed-Password" ->
                    throw BrokerException(SyncError.PASSWORD, "password refused")
                // The partner's code: retry once with the clock corrected from the Date header.
                code == 401 && partner != null && !retried -> call(method, path, headers, body, partner, retried = true)
                code == 401 && partner != null -> throw BrokerException(SyncError.CLOCK, "code refused")
                code == 401 -> throw BrokerException(SyncError.INVALID, "not this pair's device")
                else -> throw BrokerException(SyncError.SERVER, "HTTP $code")
            }
        } catch (e: org.json.JSONException) {
            throw BrokerException(SyncError.SERVER, "bad response")
        } finally {
            conn.disconnect()
        }
    }

    private fun InputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw BrokerException(SyncError.SERVER, "response too large")
        }
        return out.toByteArray()
    }

    companion object {
        private const val MAX_RESPONSE = 2 * 1024 * 1024

        /** Seconds to add to this phone's clock to match the server's (learned from every reply). */
        @Volatile private var clockOffset = 0L

        fun now(): Long = System.currentTimeMillis() / 1000 + clockOffset

        private fun learnClock(serverMillis: Long) {
            if (serverMillis <= 0) return
            val offset = serverMillis / 1000 - System.currentTimeMillis() / 1000
            clockOffset = if (abs(offset) > 10) offset else 0
        }
    }
}
