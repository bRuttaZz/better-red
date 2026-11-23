package site.brutt.betterred

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.brutt.betterred.data.Block
import site.brutt.betterred.data.ReadMode

/** Reader view, tested against a sample page shaped like a WordPress "Divi" article (made-up content). */
class ReadModeTest {
    private val url = "https://example.org/2026/10/29/packing-a-small-period-kit/"

    private fun page() = javaClass.classLoader!!.getResourceAsStream("sample-article.html").use {
        ReadMode.extract(Jsoup.parse(it, null, url))
    }

    private fun text(b: Block) = when (b) {
        is Block.Paragraph -> b.spans.joinToString("") { it.text }
        is Block.Heading -> b.text
        is Block.ListItem -> b.spans.joinToString("") { it.text }
        is Block.Quote -> b.spans.joinToString("") { it.text }
        is Block.Image -> "[img ${b.url}]"
    }

    @Test fun `keeps the story`() {
        val page = assertNotNull(page()).let { page()!! }
        val all = page.blocks.joinToString("\n") { text(it) }
        assertEquals("Packing a Small Period Kit for School", page.title)
        assertEquals("Example Staff", page.byline)
        assertTrue(all.contains("A period can start at any time"))
        assertTrue(all.contains("many schools now keep free products in the office"))
        // bold one-line paragraphs become headings
        assertTrue(page.blocks.any { it is Block.Heading && it.text == "Start with a Small Pouch" })
        assertTrue(page.blocks.any { it is Block.Heading && it.text == "Asking for a Break" })
        // lists, italics and relative links survive
        assertEquals(3, page.blocks.count { it is Block.ListItem })
        val withLink = page.blocks.filterIsInstance<Block.Paragraph>().first { p -> p.spans.any { it.link != null } }
        assertEquals("https://example.org/more-tips", withLink.spans.first { it.link != null }.link)
        assertTrue(withLink.spans.any { it.italic })
        // the author photo is kept, with its caption attached
        val photo = page.blocks.filterIsInstance<Block.Image>().first { it.url.contains("Rivera") }
        assertEquals("Jamie Rivera, school nurse", photo.caption)
        assertFalse(page.blocks.any { it is Block.Paragraph && text(it) == "Jamie Rivera, school nurse" })
    }

    @Test fun `drops forms, menus and the site logo`() {
        val all = page()!!.blocks.joinToString("\n") { text(it) }
        assertFalse(all.contains("First Name"))
        assertFalse(all.contains("Sign Up For Our News"))
        assertFalse(all.contains("Members Portal"))
        assertFalse(all.contains("donation"))
        assertFalse(all.contains("logo", ignoreCase = true))
    }

    @Test fun `a page without an article gives null`() {
        val doc = Jsoup.parse("<html><body><nav><a href='/a'>Home</a></nav><p>Hi</p></body></html>", "https://example.org/")
        assertNull(ReadMode.extract(doc))
    }

    @Test fun `links, bold and lists survive`() {
        val html = """<html><head><title>T</title></head><body><article>
            <p>First paragraph with <a href="/more">a link</a>, some <b>bold</b> text, and enough words to count as content here.</p>
            <ul><li>One, with a comma</li><li>Two</li></ul>
            <p>Second paragraph, again long enough, so that the article has plenty of real text in it overall, yes indeed, quite long.</p>
            <p>Third paragraph, still going, because we need over two hundred and fifty characters of text in total for the reader.</p>
            </article></body></html>"""
        val page = ReadMode.extract(Jsoup.parse(html, "https://example.org/post"))!!
        val p = page.blocks.first() as Block.Paragraph
        assertEquals("https://example.org/more", p.spans.first { it.link != null }.link)
        assertTrue(p.spans.any { it.bold && it.text == "bold" })
        assertEquals(2, page.blocks.count { it is Block.ListItem })
    }
}
