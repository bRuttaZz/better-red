package site.brutt.betterred.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant

/**
 * Remembers the last list of articles (in the app's private cache folder), so the
 * "Doctor said" tab can show it instantly while new posts load. It's only a cache:
 * not part of her exported data, and Android may clear it any time.
 */
class NewsCache(context: Context) {
    private val file = File(context.cacheDir, "news.json")

    fun load(): List<Article> = runCatching {
        val arr = JSONArray(file.readText())
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Article(
                link = o.getString("link"),
                title = o.getString("title"),
                summary = o.optString("summary"),
                source = o.optString("source"),
                published = o.optLong("published", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                feedUrl = o.optString("feedUrl"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(articles: List<Article>) {
        val arr = JSONArray()
        articles.forEach { a ->
            arr.put(
                JSONObject()
                    .put("link", a.link).put("title", a.title).put("summary", a.summary)
                    .put("source", a.source).put("published", a.published?.toEpochMilli() ?: 0).put("feedUrl", a.feedUrl)
            )
        }
        runCatching { file.writeText(arr.toString()) }
    }
}

/**
 * Combines a refresh with the current list: each feed that loaded gets its fresh posts,
 * each feed that failed keeps its previous posts. Newest first, no duplicates.
 * Used by both the app and the news widget.
 */
fun mergeNews(previous: List<Article>, results: List<Pair<Feed, Result<ParsedFeed>>>): List<Article> =
    results.flatMap { (feed, result) ->
        result.fold(
            onSuccess = { parsed -> parsed.articles.map { it.copy(source = feed.title, feedUrl = feed.url) } },
            onFailure = { previous.filter { it.feedUrl == feed.url } },
        )
    }.distinctBy { it.link }.sortedByDescending { it.published ?: Instant.EPOCH }
