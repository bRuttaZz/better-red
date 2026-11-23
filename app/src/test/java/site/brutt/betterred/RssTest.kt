package site.brutt.betterred

import site.brutt.betterred.data.Rss
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Feed parsing, tested against a WordPress-style sample feed (made-up content). */
class RssTest {

    @Test fun `parses a WordPress RSS feed`() {
        val feed = javaClass.classLoader!!.getResourceAsStream("sample-feed.xml").use {
            Rss.parse(it, fallbackTitle = "fallback")
        }
        assertEquals("Example Health Collective", feed.title)
        assertEquals(3, feed.articles.size)
        assertEquals("Sleep & Your Cycle", feed.articles[1].title)

        val first = feed.articles.first()
        assertEquals("Packing a Small Period Kit for School", first.title)
        assertTrue(first.link.startsWith("https://example.org/2026/10/29/"))
        assertEquals(Instant.parse("2026-10-29T18:42:51Z"), first.published)
        assertTrue(feed.articles.all { it.summary.isNotBlank() && !it.summary.contains('<') })
    }

    @Test fun `parses an Atom feed`() {
        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Example Health</title>
              <entry>
                <title>Iron &amp; your cycle</title>
                <link rel="alternate" href="https://example.org/iron"/>
                <updated>2026-11-01T10:00:00Z</updated>
                <summary type="html">&lt;p&gt;Why iron matters.&lt;/p&gt;</summary>
              </entry>
            </feed>"""
        val feed = Rss.parse(xml.byteInputStream(), fallbackTitle = "example.org")
        assertEquals("Example Health", feed.title)
        val a = feed.articles.single()
        assertEquals("Iron & your cycle", a.title)
        assertEquals("https://example.org/iron", a.link)
        assertEquals("Why iron matters.", a.summary)
        assertEquals(Instant.parse("2026-11-01T10:00:00Z"), a.published)
    }

    @Test fun `html summaries become plain text`() {
        assertEquals("Hello world — it’s fine", Rss.plainText("<p>Hello <b>world</b> &mdash; it&#8217;s fine</p>"))
        assertFalse(Rss.plainText("<script>alert(1)</script>ok").contains("alert"))
    }
}
