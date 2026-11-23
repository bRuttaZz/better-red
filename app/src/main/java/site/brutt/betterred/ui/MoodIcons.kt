package site.brutt.betterred.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import site.brutt.betterred.domain.Mood

/**
 * Outline faces for the moods, all in the same line style (a 24×24 grid, like Material icons).
 * Drawn in black; Icon() tints them to the current text color, so they follow her theme.
 */
object MoodIcons {
    fun of(mood: Mood): ImageVector = when (mood) {
        Mood.HAPPY -> happy
        Mood.CALM -> calm
        Mood.TIRED -> tired
        Mood.LOW -> low
        Mood.IRRITABLE -> irritable
        Mood.ANXIOUS -> anxious
    }

    private const val LINE = 1.6f

    private val happy by lazy {
        face("Happy") {
            dotEyes(10f)
            line { moveTo(7.8f, 13.6f); quadTo(12f, 18.4f, 16.2f, 13.6f) } // big smile
        }
    }

    private val calm by lazy {
        face("Calm") {
            line { moveTo(7.4f, 10.4f); quadTo(9f, 11.9f, 10.6f, 10.4f) } // closed, relaxed eyes
            line { moveTo(13.4f, 10.4f); quadTo(15f, 11.9f, 16.6f, 10.4f) }
            line { moveTo(9.4f, 14.8f); quadTo(12f, 16.8f, 14.6f, 14.8f) } // soft smile
        }
    }

    private val tired by lazy {
        face("Tired") {
            line { moveTo(7.4f, 10.8f); lineTo(10.6f, 10.8f) } // shut eyes
            line { moveTo(13.4f, 10.8f); lineTo(16.6f, 10.8f) }
            line { circle(12f, 15.6f, 1.4f) } // yawn
        }
    }

    private val low by lazy {
        face("Low") {
            dotEyes(10f)
            line { moveTo(8.6f, 16.2f); quadTo(12f, 12.8f, 15.4f, 16.2f) } // frown
        }
    }

    private val irritable by lazy {
        face("Irritable") {
            line { moveTo(7.4f, 8.2f); lineTo(10.4f, 9.6f) } // brows down to the middle
            line { moveTo(16.6f, 8.2f); lineTo(13.6f, 9.6f) }
            dotEyes(11.6f)
            line { moveTo(9f, 15.8f); lineTo(15f, 15.8f) } // flat mouth
        }
    }

    private val anxious by lazy {
        face("Anxious") {
            line { moveTo(7.4f, 9f); lineTo(10.4f, 7.8f) } // brows up in the middle
            line { moveTo(16.6f, 9f); lineTo(13.6f, 7.8f) }
            dotEyes(11.4f)
            line { // wobbly mouth
                moveTo(8.2f, 16f)
                quadTo(9.15f, 14.8f, 10.1f, 16f); quadTo(11.05f, 17.2f, 12f, 16f)
                quadTo(12.95f, 14.8f, 13.9f, 16f); quadTo(14.85f, 17.2f, 15.8f, 16f)
            }
        }
    }

    // ---------------------------------------------------------------- drawing helpers

    private class Face(val b: ImageVector.Builder) {
        fun line(draw: PathBuilder.() -> Unit) {
            b.path(
                fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = LINE,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw,
            )
        }

        fun dotEyes(y: Float) {
            b.path(fill = SolidColor(Color.Black)) { circle(9f, y, 1.15f); circle(15f, y, 1.15f) }
        }
    }

    private fun face(name: String, features: Face.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name = "Mood.$name", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        Face(b).apply {
            line { circle(12f, 12f, 9f) }
            features()
        }
        return b.build()
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }
}
