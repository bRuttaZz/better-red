package site.brutt.betterred.ui.news

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import site.brutt.betterred.data.Http

/** Tiny image loader: download, shrink to screen width, keep recent ones in memory. */
object ImageLoader {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun peek(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String, maxWidthPx: Int): Bitmap? = cache.get(url) ?: withContext(Dispatchers.IO) {
        runCatching {
            val bytes = Http.get(url, "image/avif,image/webp,image/*;q=0.8", 12 * 1024 * 1024).bytes
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidthPx) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()?.also { cache.put(url, it) }
    }
}

private sealed interface ImageState {
    data object Loading : ImageState
    data class Loaded(val bitmap: Bitmap) : ImageState
    data object Failed : ImageState
}

/** A full-width photo from the web. Shows a soft placeholder while loading and hides itself if it fails. */
@Composable
fun NetImage(url: String, modifier: Modifier = Modifier) {
    val maxWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val state by produceState<ImageState>(ImageLoader.peek(url)?.let { ImageState.Loaded(it) } ?: ImageState.Loading, url) {
        if (value is ImageState.Loading) value = ImageLoader.load(url, maxWidth)?.let { ImageState.Loaded(it) } ?: ImageState.Failed
    }
    val shape = RoundedCornerShape(16.dp)
    when (val s = state) {
        ImageState.Loading -> Box(modifier.fillMaxWidth().height(200.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh))
        is ImageState.Loaded -> Image(
            bitmap = s.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth().aspectRatio(s.bitmap.width.toFloat() / s.bitmap.height.coerceAtLeast(1)).clip(shape),
        )
        ImageState.Failed -> Unit
    }
}
