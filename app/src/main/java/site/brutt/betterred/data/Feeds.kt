package site.brutt.betterred.data

/** An RSS/Atom feed shown in the "Doctor said" tab. */
data class Feed(
    val id: Long,
    val url: String,
    val title: String,
    val enabled: Boolean,
    /** Built-in feeds can be turned off but not removed. */
    val builtIn: Boolean,
)

object Feeds {
    /** How many feeds she can add on top of the built-in ones. */
    const val MAX_CUSTOM = 3

    /** Built-in feeds, added on first launch (and when upgrading from an older version). */
    val DEFAULTS = listOf(
        "https://periodeducationproject.org/rss" to "Period Education Project",
    )
}
