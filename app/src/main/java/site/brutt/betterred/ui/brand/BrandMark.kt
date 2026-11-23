package site.brutt.betterred.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import site.brutt.betterred.Words
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val LogoGrey = Color(0xFF767676)
private val LogoGreyOnDark = Color(0xFFA8A8A8)
private val LogoRed = Color(0xFFFF0000)

/**
 * The Better Red mark: grey "b" + red square.
 * With [animate] = true the "b" fades up and the red square pops in (same as the splash).
 */
@Composable
fun BrandMark(height: Dp, modifier: Modifier = Modifier, animate: Boolean = false) {
    val onDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val grey = if (onDark) LogoGreyOnDark else LogoGrey
    val b = remember { PathParser().parsePathString(BrandPaths.B).toPath().apply { fillType = PathFillType.EvenOdd } }
    val dot = remember { PathParser().parsePathString(BrandPaths.DOT).toPath() }

    val bAlpha = remember { Animatable(if (animate) 0f else 1f) }
    val bLift = remember { Animatable(if (animate) 4f else 0f) }
    val dotScale = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        launch { bAlpha.animateTo(1f, tween(350)) }
        launch { bLift.animateTo(0f, tween(450)) }
        delay(300)
        dotScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }

    Canvas(
        modifier
            .size(width = height * (BrandPaths.WIDTH / BrandPaths.HEIGHT), height = height)
            .semantics { contentDescription = Words.APP_NAME }
    ) {
        scale(size.height / BrandPaths.HEIGHT, pivot = Offset.Zero) {
            translate(top = bLift.value) { drawPath(b, grey, alpha = bAlpha.value) }
            scale(dotScale.value, pivot = Offset(BrandPaths.DOT_CENTER_X, BrandPaths.DOT_CENTER_Y)) {
                drawPath(dot, LogoRed)
            }
        }
    }
}
