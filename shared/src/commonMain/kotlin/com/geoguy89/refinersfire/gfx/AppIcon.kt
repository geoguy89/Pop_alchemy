package com.geoguy89.refinersfire.gfx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The app icon: a flame over a gold crucible inside a gold ring, drawn in the same 108 x 108 space as the Android
 * launcher vector (res/drawable/ic_launcher_foreground.xml), on a dark round backdrop.
 */
object AppIcon {
    private fun p(d: String): Path = PathParser().parsePathString(d).toPath()
    private val ring by lazy { p("M54,24a30,30 0,1 1,0 60a30,30 0,1 1,0 -60z") }
    private val flame by lazy { p("M54,30 C60,39 69,46 69,58 C69,67 62,73 54,73 C46,73 39,67 39,58 C39,51 44,47 46,40 C49,45 51,47 52,49 C54,43 55,37 54,30 Z") }
    private val core by lazy { p("M54,52 C57,57 61,60 61,65 C61,69 58,72 54,72 C50,72 47,69 47,65 C47,61 51,58 54,52 Z") }
    private val crucible by lazy { p("M37,72 L71,72 L67,82 L41,82 Z") }

    fun DrawScope.drawAppIcon() {
        val s = size.minDimension
        drawCircle(Brush.radialGradient(listOf(Color(0xFF4A2A12), Color(0xFF140C08)), center, s / 2), s / 2, center)
        // The artwork fills about three quarters of the 108 viewport; scale it up to the canvas.
        scale(s / 72f, s / 72f, Offset.Zero) {
            translate(-18f, -18f) {
                drawPath(ring, Color(0xFFE8B64A), style = Stroke(2.6f))
                drawPath(flame, Brush.verticalGradient(listOf(Color(0xFFE2361B), Color(0xFFFF7A1A), Color(0xFFFFD27A)), 30f, 73f))
                drawPath(core, Color(0xFFFFF1B0))
                drawPath(crucible, Brush.verticalGradient(listOf(Color(0xFFFFF3B0), Color(0xFFF2C14E), Color(0xFFB07A1C)), 72f, 82f))
                drawPath(crucible, Color(0xFF6E4E1C), style = Stroke(1.2f))
            }
        }
    }
}
