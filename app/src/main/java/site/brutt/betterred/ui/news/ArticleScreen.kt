package site.brutt.betterred.ui.news

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.brutt.betterred.Words
import site.brutt.betterred.ui.openExternally
import site.brutt.betterred.data.Article
import site.brutt.betterred.data.Block
import site.brutt.betterred.data.ReadMode
import site.brutt.betterred.data.ReaderPage
import site.brutt.betterred.data.Span
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Cleaned-up articles opened recently, so going back and forth is instant. */
private object ReaderCache : LruCache<String, ReaderPage>(12)

private sealed interface Load {
    data object Loading : Load
    data class Ready(val page: ReaderPage) : Load
    data object NotAnArticle : Load
    data object Failed : Load
}

/**
 * Reads an article inside the app. By default it shows a clean "reader view" (text + photos in the
 * app's own style). If the page can't be cleaned up, or she asks for it, the original page is shown.
 * "Open in browser" is always at the top.
 */
@Composable
fun ArticleScreen(article: Article, onClose: () -> Unit) {
    val context = LocalContext.current
    var retry by remember { mutableIntStateOf(0) }
    var showOriginal by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val load by produceState<Load>(ReaderCache.get(article.link)?.let { Load.Ready(it) } ?: Load.Loading, article.link, retry) {
        if (value is Load.Ready) return@produceState
        value = Load.Loading
        value = withContext(Dispatchers.IO) {
            runCatching { ReadMode.fetch(article.link) }.fold(
                onSuccess = { page -> page?.also { ReaderCache.put(article.link, it) }?.let { Load.Ready(it) } ?: Load.NotAnArticle },
                onFailure = { Load.Failed },
            )
        }
    }
    LaunchedEffect(load) { if (load is Load.NotAnArticle) showOriginal = true }

    BackHandler {
        val web = webView
        when {
            showOriginal && web != null && web.canGoBack() -> web.goBack()
            showOriginal && load is Load.Ready -> showOriginal = false
            else -> onClose()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ListItem(
                leadingContent = { IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, Words.News.CLOSE) } },
                headlineContent = { Text(article.source, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(Uri.parse(article.link).host.orEmpty(), maxLines = 1) },
                trailingContent = {
                    Row {
                        if (load is Load.Ready) IconButton(onClick = { showOriginal = !showOriginal }) {
                            if (showOriginal) Icon(Icons.AutoMirrored.Rounded.Article, Words.News.SHOW_READER)
                            else Icon(Icons.Rounded.Public, Words.News.SHOW_ORIGINAL)
                        }
                        IconButton(onClick = { openExternally(context, webView?.url ?: article.link) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, Words.News.OPEN_IN_BROWSER)
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
            if (showOriginal) OriginalPage(article.link, onWebView = { webView = it })
            else ReaderBody(article, load, onRetry = { retry++ }, onOpenBrowser = { openExternally(context, article.link) })
        }
    }
}

// ---------------------------------------------------------------- reader view

@Composable
private fun ReaderBody(article: Article, load: Load, onRetry: () -> Unit, onOpenBrowser: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val page = (load as? Load.Ready)?.page
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp)) {
        item {
            val date = article.published?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
            Text(
                listOfNotNull(date, page?.byline?.let(Words.News::by)).joinToString("  ·  "),
                style = MaterialTheme.typography.labelLarge, color = cs.primary,
            )
            Text(
                page?.title ?: article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
        }
        when (load) {
            Load.Loading, Load.NotAnArticle -> item {
                // The title shows right away; the rest fills in when it arrives.
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(Words.News.READER_LOADING, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                if (article.summary.isNotBlank()) Text(
                    article.summary, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Load.Failed -> item {
                Text(Words.News.READER_FAILED, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onRetry) { Text(Words.News.TRY_AGAIN) }
                    OutlinedButton(onClick = onOpenBrowser) { Text(Words.News.OPEN_IN_BROWSER) }
                }
            }
            is Load.Ready -> {
                load.page.heroImage?.let { hero -> item { NetImage(hero, Modifier.padding(bottom = 16.dp)) } }
                items(load.page.blocks) { BlockView(it) }
            }
        }
    }
}

private val bodyLineHeight = 27.sp

@Composable
private fun BlockView(block: Block) {
    val cs = MaterialTheme.colorScheme
    when (block) {
        is Block.Heading -> Text(
            block.text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        is Block.Paragraph -> Text(
            styled(block.spans), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = bodyLineHeight),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        is Block.ListItem -> Row(Modifier.padding(vertical = 4.dp)) {
            Text(block.marker, style = MaterialTheme.typography.bodyLarge, color = cs.primary, modifier = Modifier.width(28.dp))
            Text(styled(block.spans), style = MaterialTheme.typography.bodyLarge.copy(lineHeight = bodyLineHeight))
        }
        is Block.Quote -> Row(Modifier.padding(vertical = 10.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(cs.primary))
            Spacer(Modifier.width(14.dp))
            Text(
                styled(block.spans), fontStyle = FontStyle.Italic,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = bodyLineHeight), color = cs.onSurfaceVariant,
            )
        }
        is Block.Image -> Column(Modifier.padding(vertical = 12.dp)) {
            NetImage(block.url)
            block.caption?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** Bold / italic / links. Tapping a link opens it in her browser. */
@Composable
private fun styled(spans: List<Span>): AnnotatedString {
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    return buildAnnotatedString {
        for (s in spans) {
            val style = SpanStyle(
                fontWeight = if (s.bold) FontWeight.SemiBold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
            )
            if (s.link != null) withLink(LinkAnnotation.Url(s.link, linkStyle)) { withStyle(style) { append(s.text) } }
            else withStyle(style) { append(s.text) }
        }
    }
}

// ---------------------------------------------------------------- original page

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun OriginalPage(url: String, onWebView: (WebView?) -> Unit) {
    var progress by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (progress in 0..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true // most sites need it
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (request.url.scheme == "https" || request.url.scheme == "http") return false
                            openExternally(ctx, request.url.toString()) // mailto:, tel:, app links…
                            return true
                        }

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) { progress = 0 }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    loadUrl(url)
                    onWebView(this)
                }
            },
            onRelease = { onWebView(null); it.stopLoading(); it.destroy() },
        )
    }
}
