package site.brutt.betterred.ui.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import site.brutt.betterred.Words
import site.brutt.betterred.data.Article
import site.brutt.betterred.data.Feed
import site.brutt.betterred.data.Feeds
import site.brutt.betterred.ui.AppState
import site.brutt.betterred.ui.AppViewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The logo's red, used to mark articles she hasn't opened yet. */
private val NewRed = Color(0xFFFF0000)

/**
 * "…and the doctor said": the latest posts from her feeds.
 * Loads fresh every time the tab opens. Unread articles get a red mark.
 */
@Composable
fun NewsScreen(state: AppState, vm: AppViewModel, onOpen: (Article) -> Unit, modifier: Modifier = Modifier) {
    val news by vm.news.collectAsState()
    var showSources by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { vm.refreshNews() } // every time the tab is opened

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Words.News.TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(Words.News.SUBTITLE, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            IconButton(onClick = vm::refreshNews, enabled = !news.loading) { Icon(Icons.Rounded.Refresh, Words.News.REFRESH) }
            IconButton(onClick = { showSources = true }) { Icon(Icons.Rounded.Tune, Words.News.SOURCES) }
        }
        Box(Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 16.dp)) {
            if (news.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // Only show articles from feeds that are switched on (the saved list may include others).
        val enabledUrls = state.feeds.filter { it.enabled }.map { it.url }.toSet()
        val shown = news.articles.filter { it.feedUrl in enabledUrls }
        when {
            enabledUrls.isEmpty() -> Empty(Words.News.EMPTY, Words.News.EMPTY_SUB, Words.News.SOURCES) { showSources = true }
            shown.isEmpty() && !news.loadedOnce -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            shown.isEmpty() && news.failed.isNotEmpty() -> Empty(Words.News.OFFLINE, null, Words.News.TRY_AGAIN, vm::refreshNews)
            shown.isEmpty() -> Empty(Words.News.EMPTY, Words.News.EMPTY_SUB, Words.News.SOURCES) { showSources = true }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (news.failed.isNotEmpty()) item {
                    Text(
                        news.failed.joinToString("\n") { Words.News.couldNotLoad(it) },
                        style = MaterialTheme.typography.labelMedium, color = cs.error,
                    )
                }
                items(shown, key = { it.link }) { article ->
                    ArticleRow(article, read = article.link in state.readLinks) {
                        vm.markRead(article.link)
                        onOpen(article)
                    }
                }
            }
        }
    }

    if (showSources) SourcesSheet(state.feeds, vm, onDismiss = { showSources = false; vm.refreshNews() })
}

@Composable
private fun ArticleRow(article: Article, read: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                article.title, style = MaterialTheme.typography.titleMedium,
                fontWeight = if (read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (read) cs.onSurfaceVariant else cs.onSurface,
            )
            if (article.summary.isNotBlank()) Text(
                article.summary, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!read) {
                    // little red square, like the one in the logo
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(NewRed))
                    Spacer(Modifier.width(6.dp))
                    Text(Words.News.NEW, style = MaterialTheme.typography.labelMedium, color = NewRed, fontWeight = FontWeight.SemiBold)
                    Text("  ·  ", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Text(
                    Words.News.meta(article.source, ago(article.published)), style = MaterialTheme.typography.labelMedium,
                    color = cs.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Empty(title: String, sub: String?, action: String, onAction: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.RssFeed, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (sub != null) Text(
            sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

/** Turn feeds on/off, remove her own, add up to [Feeds.MAX_CUSTOM]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesSheet(feeds: List<Feed>, vm: AppViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val custom = feeds.filter { !it.builtIn }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(Words.News.SOURCES, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

            SectionLabel(Words.News.BUILT_IN)
            feeds.filter { it.builtIn }.forEach { FeedRow(it, vm, removable = false) }

            SectionLabel(Words.News.yourFeeds(custom.size, Feeds.MAX_CUSTOM))
            custom.forEach { FeedRow(it, vm, removable = true) }

            if (custom.size < Feeds.MAX_CUSTOM) {
                OutlinedTextField(
                    value = address, onValueChange = { address = it; error = null }, singleLine = true,
                    label = { Text(Words.News.FEED_ADDRESS) }, placeholder = { Text(Words.News.FEED_ADDRESS_HINT) },
                    isError = error != null, supportingText = error?.let { { Text(it) } },
                    // web-address keyboard, and no autocorrect (it would split the address into words)
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            error = vm.addFeed(address)
                            if (error == null) address = ""
                            busy = false
                        }
                    },
                    enabled = !busy && address.isNotBlank(),
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                ) { Text(if (busy) Words.News.ADDING else Words.News.ADD) }
            } else {
                Text(Words.News.maxFeeds(Feeds.MAX_CUSTOM), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun FeedRow(feed: Feed, vm: AppViewModel, removable: Boolean) {
    ListItem(
        headlineContent = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(feed.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(Icons.Rounded.RssFeed, null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = feed.enabled, onCheckedChange = { vm.setFeedEnabled(feed, it) })
                if (removable) IconButton(onClick = { vm.removeFeed(feed) }) { Icon(Icons.Rounded.Close, Words.News.REMOVE) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 0.dp),
    )
}

/** "2h ago", "3d ago", or a date for older posts. */
private fun ago(published: Instant?): String {
    published ?: return ""
    val d = Duration.between(published, Instant.now())
    return when {
        d.isNegative || d.toMinutes() < 1 -> Words.News.JUST_NOW
        d.toHours() < 1 -> Words.News.minutesAgo(d.toMinutes())
        d.toDays() < 1 -> Words.News.hoursAgo(d.toHours())
        d.toDays() < 7 -> Words.News.daysAgo(d.toDays())
        else -> published.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
    }
}
