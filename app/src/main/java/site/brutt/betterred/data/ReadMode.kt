package site.brutt.betterred.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** A piece of text with simple styling. The UI turns a list of these into styled text. */
data class Span(val text: String, val bold: Boolean = false, val italic: Boolean = false, val link: String? = null)

/** One block of a cleaned-up article. */
sealed interface Block {
    data class Heading(val text: String) : Block
    data class Paragraph(val spans: List<Span>) : Block
    data class Image(val url: String, val caption: String?) : Block
    data class ListItem(val spans: List<Span>, val marker: String) : Block
    data class Quote(val spans: List<Span>) : Block
}

data class ReaderPage(val title: String, val byline: String?, val heroImage: String?, val blocks: List<Block>)

/**
 * "Reader view": downloads an article and keeps only the story — headings, text, photos, lists —
 * dropping menus, sidebars, forms, ads and comments. A small, Readability-style heuristic.
 * Returns null when the page doesn't look like an article (the app then shows the original page).
 */
object ReadMode {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MIN_TEXT = 250

    fun fetch(url: String): ReaderPage? {
        val r = Http.get(url, "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5", MAX_BYTES)
        return extract(Jsoup.parse(r.bytes.inputStream(), null, r.finalUrl))
    }

    fun extract(doc: Document): ReaderPage? {
        val title = meta(doc, "og:title") ?: doc.selectFirst("h1")?.text()?.ifBlank { null } ?: doc.title()
        val hero = meta(doc, "og:image")?.let { doc.absUrlOf(it) }?.takeIf { it.startsWith("https://") }
        val byline = meta(doc, "author") ?: doc.selectFirst("[rel=author], .author, .byline")?.text()?.take(80)?.ifBlank { null }

        clean(doc)
        val root = pickContent(doc) ?: return null
        val blocks = mutableListOf<Block>()
        walk(root, blocks)

        // Don't repeat the title, and drop empty or duplicate images.
        val out = blocks.filterNot { it is Block.Heading && it.text.equals(title.trim(), ignoreCase = true) }
            .distinctBy { if (it is Block.Image) it.url else it }
        val textLength = out.sumOf { b ->
            when (b) {
                is Block.Paragraph -> b.spans.sumOf { it.text.length }
                is Block.ListItem -> b.spans.sumOf { it.text.length }
                is Block.Quote -> b.spans.sumOf { it.text.length }
                else -> 0
            }
        }
        if (textLength < MIN_TEXT) return null
        val heroOrNull = hero?.takeIf { h -> out.none { it is Block.Image && it.url == h } }
        return ReaderPage(title.trim(), byline, heroOrNull, out)
    }

    // ------------------------------------------------------------------ cleaning

    private val JUNK_TAGS = "script, style, noscript, iframe, form, nav, header, footer, aside, button, svg, input, select, textarea, " +
        "canvas, video, audio, object, embed, template, [role=navigation], [role=banner], [role=contentinfo], [hidden]"
    private val UNLIKELY = Regex(
        "comment|share|social|related|sidebar|widget|footer|header|menu|nav|promo|advert|ads?\\b|cookie|newsletter|subscribe|" +
            "breadcrumb|popup|modal|banner|sponsor|donat|signup|sign-up|author-box|pagination|tags?\\b|meta\\b|cta",
        RegexOption.IGNORE_CASE,
    )
    private val MAYBE = Regex("article|body|content|entry|main|post|story|text", RegexOption.IGNORE_CASE)

    private fun clean(doc: Document) {
        doc.select(JUNK_TAGS).remove()
        for (el in doc.body().select("*").toList()) {
            if (el.parent() == null || el.tagName() in setOf("body", "html", "article", "main")) continue
            val id = el.className() + " " + el.id()
            if (UNLIKELY.containsMatchIn(id) && !MAYBE.containsMatchIn(id)) el.remove()
        }
    }

    // ------------------------------------------------------------------ choosing the article

    /** Scores containers by the paragraph text inside them and returns the best one. */
    private fun pickContent(doc: Document): Element? {
        doc.selectFirst("[itemprop=articleBody]")?.let { if (it.text().length > MIN_TEXT) return it }
        val scores = HashMap<Element, Double>()
        for (p in doc.body().select("p, pre, li, blockquote")) {
            val text = p.text()
            if (text.length < 25) continue
            val score = 1.0 + text.count { it == ',' } + minOf(text.length / 100, 3)
            p.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + score / 2 }
        }
        // Penalise containers that are mostly links (menus, link lists).
        val best = scores.maxByOrNull { (el, s) -> s * (1 - linkDensity(el)) }?.key ?: return null

        // Step out of wrappers that hold nothing else, so neighbouring blocks become visible.
        var top = best
        while (top.parent()?.let { it.children().size == 1 && it.tagName() != "body" } == true) top = top.parent()!!

        // Also keep closely related siblings (e.g. a photo block next to the text block).
        val parent = top.parent() ?: return best
        val bestScore = scores[best] ?: 0.0
        val keep = parent.children().filter { sib ->
            sib == top || (scores[sib] ?: 0.0) >= bestScore * 0.2 || (sib.select("img").isNotEmpty() && sib.text().length < 300)
        }
        if (keep.size == 1) return top
        return Element("div").apply {
            setBaseUri(doc.location()) // so relative image/link addresses still resolve
            keep.forEach { appendChild(it.clone()) }
        }
    }

    private fun linkDensity(el: Element): Double {
        val total = el.text().length.coerceAtLeast(1)
        return el.select("a").sumOf { it.text().length }.toDouble() / total
    }

    // ------------------------------------------------------------------ turning HTML into blocks

    private fun walk(el: Element, out: MutableList<Block>) {
        for (child in el.children()) {
            // A caption right after a photo belongs to the photo.
            val last = out.lastOrNull()
            if (last is Block.Image && last.caption == null && (child.hasClass("wp-caption-text") || child.tagName() == "figcaption")) {
                out[out.lastIndex] = last.copy(caption = child.text().ifBlank { null })
                continue
            }
            when (child.tagName()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> child.text().trim().takeIf { it.isNotEmpty() }?.let { out += Block.Heading(it) }
                "p", "pre" -> paragraph(child, out)
                "img" -> image(child, null)?.let { out += it }
                "figure" -> figure(child, out)
                "ul", "ol" -> list(child, ordered = child.tagName() == "ol", out = out)
                "blockquote" -> spans(child).takeIf { it.isNotEmpty() }?.let { out += Block.Quote(it) }
                "table", "hr" -> Unit
                else -> walk(child, out) // div, section, span, … → look inside
            }
        }
    }

    private fun paragraph(p: Element, out: MutableList<Block>) {
        // images inside a paragraph become their own blocks
        p.select("img").forEach { img -> image(img, p.selectFirst(".wp-caption-text, figcaption")?.text())?.let { out += it } }
        p.select("img, .wp-caption, figure").remove()
        val s = spans(p)
        if (s.isEmpty()) return
        val text = s.joinToString("") { it.text }.trim()
        // A short paragraph that is entirely bold is really a heading ("Let's Pack a Period Kit").
        if (text.length <= 90 && s.all { it.bold || it.text.isBlank() } && !text.endsWith('.')) out += Block.Heading(text)
        else out += Block.Paragraph(s)
    }

    private fun figure(f: Element, out: MutableList<Block>) {
        val caption = f.selectFirst("figcaption")?.text()?.ifBlank { null }
        f.select("img").forEach { img -> image(img, caption)?.let { out += it } }
    }

    private fun list(listEl: Element, ordered: Boolean, out: MutableList<Block>) {
        listEl.children().filter { it.tagName() == "li" }.forEachIndexed { i, li ->
            spans(li).takeIf { it.isNotEmpty() }?.let { out += Block.ListItem(it, if (ordered) "${i + 1}." else "•") }
        }
    }

    private fun image(img: Element, caption: String?): Block.Image? {
        val src = sequenceOf("data-src", "data-lazy-src", "data-original", "src")
            .map { img.attr(it) }.firstOrNull { it.isNotBlank() && !it.startsWith("data:") }
            ?: img.attr("srcset").substringBefore(' ').ifBlank { null }
            ?: return null
        val url = img.absUrlOf(src).replaceFirst("http://", "https://")
        if (!url.startsWith("https://")) return null
        val w = img.attr("width").toIntOrNull()
        val h = img.attr("height").toIntOrNull()
        if ((w != null && w < 80) || (h != null && h < 80)) return null // icons, tracking pixels
        if (url.contains("emoji") || url.endsWith(".svg")) return null
        return Block.Image(url, caption)
    }

    /** Inline text with bold / italic / links, whitespace tidied. */
    private fun spans(el: Element): List<Span> {
        val out = mutableListOf<Span>()
        fun visit(node: Node, bold: Boolean, italic: Boolean, link: String?) {
            when (node) {
                is TextNode -> {
                    val t = node.text().replace(Regex("\\s+"), " ")
                    if (t.isNotEmpty()) out += Span(t, bold, italic, link)
                }
                is Element -> when (node.tagName()) {
                    "br" -> out += Span("\n")
                    "img", "script", "style" -> Unit
                    else -> {
                        val b = bold || node.tagName() in setOf("b", "strong") || node.tagName().matches(Regex("h[1-6]"))
                        val i = italic || node.tagName() in setOf("i", "em")
                        val l = if (node.tagName() == "a") node.absUrl("href").takeIf { it.startsWith("http") } ?: link else link
                        node.childNodes().forEach { visit(it, b, i, l) }
                    }
                }
            }
        }
        el.childNodes().forEach { visit(it, bold = false, italic = false, link = null) }
        // trim the ends and merge neighbours with the same style
        val merged = mutableListOf<Span>()
        for (s in out) {
            val last = merged.lastOrNull()
            if (last != null && last.bold == s.bold && last.italic == s.italic && last.link == s.link) merged[merged.lastIndex] = last.copy(text = last.text + s.text)
            else merged += s
        }
        if (merged.isEmpty()) return merged
        merged[0] = merged[0].copy(text = merged[0].text.trimStart())
        merged[merged.lastIndex] = merged.last().copy(text = merged.last().text.trimEnd())
        return merged.filter { it.text.isNotEmpty() }.takeIf { list -> list.any { it.text.isNotBlank() } } ?: emptyList()
    }

    private fun meta(doc: Document, name: String): String? =
        (doc.selectFirst("meta[property=$name]") ?: doc.selectFirst("meta[name=$name]"))?.attr("content")?.trim()?.ifBlank { null }

    private fun Document.absUrlOf(url: String): String = Element("a").also { it.setBaseUri(location()); it.attr("href", url) }.absUrl("href")
    private fun Element.absUrlOf(url: String): String = Element("a").also { it.setBaseUri(baseUri()); it.attr("href", url) }.absUrl("href")
}
