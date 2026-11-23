package site.brutt.betterred.data

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory

data class Article(
    val link: String,
    val title: String,
    val summary: String,
    val source: String,
    val published: Instant?,
    /** The feed this came from (set by the app after fetching). */
    val feedUrl: String = "",
)

data class ParsedFeed(val title: String, val articles: List<Article>)

/**
 * Tiny RSS 2.0 / RSS 1.0 / Atom reader. No libraries, no images, no history:
 * it returns exactly what the feed currently lists.
 */
object Rss {
    private const val MAX_BYTES = 3 * 1024 * 1024
    private const val MAX_ITEMS = 30

    /** Downloads and parses a feed. Throws on network or format errors. */
    fun fetch(url: String): ParsedFeed {
        val r = Http.get(url, "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.5", MAX_BYTES)
        return parse(r.bytes.inputStream(), fallbackTitle = java.net.URL(r.finalUrl).host)
    }

    /** Parses feed XML. Public so it can be unit-tested with a saved feed. */
    fun parse(input: InputStream, fallbackTitle: String): ParsedFeed {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Don't let a feed pull in outside files (XXE). Not every parser knows these flags.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        }
        val root = factory.newDocumentBuilder().parse(input).documentElement
        val isAtom = root.localNameOrTag == "feed"
        val channelTitle = if (isAtom) root.childText("title")
        else root.firstChild("channel")?.childText("title") ?: root.descendants("channel").firstOrNull()?.childText("title")
        val source = channelTitle?.ifBlank { null } ?: fallbackTitle

        val items = if (isAtom) root.descendants("entry") else root.descendants("item")
        val articles = items.mapNotNull { item ->
            val link = (if (isAtom) atomLink(item) else item.childText("link") ?: item.childText("guid"))?.trim()
            val title = item.childText("title")?.let(::plainText)?.trim()
            if (link.isNullOrBlank() || !link.startsWith("http") || title.isNullOrBlank()) return@mapNotNull null
            val summaryHtml = item.childText("description") ?: item.childText("summary") ?: item.childText("content") ?: ""
            val date = item.childText("pubDate") ?: item.childText("published") ?: item.childText("updated") ?: item.childText("date")
            Article(link, title, plainText(summaryHtml).take(220), source, date?.let(::parseDate))
        }.take(MAX_ITEMS)
        return ParsedFeed(source, articles)
    }

    private fun atomLink(entry: Element): String? {
        val links = entry.children("link")
        val alt = links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull()
        return alt?.getAttribute("href")
    }

    internal fun parseDate(text: String): Instant? {
        val t = text.trim()
        return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(t) }.getOrNull()
    }

    /** HTML → short plain text: drop tags, decode common entities, squash whitespace. */
    internal fun plainText(html: String): String {
        val noTags = html.replace(Regex("(?s)<(script|style)[^>]*>.*?</\\1>"), " ").replace(Regex("<[^>]+>"), " ")
        val decoded = Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(noTags) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> NAMED[e]
            } ?: m.value
        }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "hellip" to "…", "mdash" to "—", "ndash" to "–", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“",
    )

    // ---- small DOM helpers (namespace-agnostic: match on local name) ----

    private val Node.localNameOrTag: String get() = localName ?: nodeName.substringAfter(':')

    private fun Element.children(name: String): List<Element> {
        val out = mutableListOf<Element>()
        var n = firstChild
        while (n != null) {
            if (n is Element && n.localNameOrTag == name) out += n
            n = n.nextSibling
        }
        return out
    }

    private fun Element.firstChild(name: String): Element? = children(name).firstOrNull()

    private fun Element.childText(name: String): String? = firstChild(name)?.textContent

    private fun Element.descendants(name: String): List<Element> {
        val list = getElementsByTagNameNS("*", name)
        val out = (0 until list.length).map { list.item(it) as Element }
        if (out.isNotEmpty()) return out
        val plain = getElementsByTagName(name)
        return (0 until plain.length).map { plain.item(it) as Element }
    }
}
