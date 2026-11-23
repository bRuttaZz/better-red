package site.brutt.betterred.data

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One download: the bytes, where we ended up after redirects, and the content type. */
class HttpResult(val bytes: ByteArray, val finalUrl: String, val contentType: String?)

/** Minimal HTTPS GET with redirects, timeouts and a size limit. No library needed. */
object Http {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) BetterRed/0.1"

    fun get(url: String, accept: String, maxBytes: Int): HttpResult {
        var current = URL(url)
        repeat(6) { // follow redirects ourselves (Java won't follow http→https on its own)
            val conn = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", accept)
            }
            try {
                when (val code = conn.responseCode) {
                    in 300..399 -> current = URL(current, conn.getHeaderField("Location") ?: error("Redirect without a location"))
                    in 200..299 -> return HttpResult(conn.inputStream.use { it.readLimited(maxBytes) }, current.toString(), conn.contentType)
                    else -> error("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Too many redirects")
    }

    private fun java.io.InputStream.readLimited(max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) error("Too large")
        }
        return out.toByteArray()
    }
}
