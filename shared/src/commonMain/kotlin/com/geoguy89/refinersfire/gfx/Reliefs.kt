package com.geoguy89.refinersfire.gfx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.Glyph
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The engraved plates that sit beneath the stones. Each board shows a different relief: the seal, the
 * breastplate, the lights of day and night, the vine, the lampstand, the refiner's furnace, the anchor, the
 * tablets of the law and the rose of Sharon.
 *
 * Every design is drawn in a 900 x 800 space (the 9 x 8 board at 100 units per square) and embossed: each
 * stroke is laid down once in shadow and once in highlight, offset, so it reads as carved relief on both
 * lead and gold.
 */
object Reliefs {
    val names = listOf(
        "The Seal", "The Breastplate", "Day and Night", "The Vine", "The Lampstand",
        "The Refiner's Furnace", "The Anchor", "The Tablets", "The Rose of Sharon",
    )
    val count get() = names.size

    fun forBoard(board: Int): Int = (board - 1).mod(count)

    /** Draw design [index] into the 900 x 800 unit space. [bodyFont] is used for inscriptions. */
    fun DrawScope.drawRelief(index: Int, tm: TextMeasurer, bodyFont: FontFamily, alpha: Float) {
        val e = Engraver(this, tm, bodyFont, alpha)
        when (index.mod(count)) {
            0 -> drawSeal(Offset(450f, 400f), 376f, 0f, alpha)
            1 -> e.breastplate()
            2 -> e.dayAndNight()
            3 -> e.vine()
            4 -> e.lampstand()
            5 -> e.furnace()
            6 -> e.anchor()
            7 -> e.tablets()
            8 -> e.rose()
        }
    }
}

private val pathCache = HashMap<String, Path>()
private fun svg(d: String): Path = pathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() }

private fun stroke(w: Float) = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)

private class Engraver(val s: DrawScope, val tm: TextMeasurer, val font: FontFamily, val alpha: Float) {
    private val c = Offset(450f, 400f)

    /** Draw [block] twice: a shadow offset down-right and a highlight offset up-left. */
    fun emboss(block: DrawScope.(Color, Float) -> Unit) {
        s.translate(3f, 4f) { block(Color.Black, 0.5f * alpha) }
        s.translate(-2f, -2.5f) { block(Color.White, 0.3f * alpha) }
    }

    fun paths(width: Float, vararg d: String) = emboss { col, a -> for (p in d) drawPath(svg(p), col, alpha = a, style = stroke(width)) }

    fun path(width: Float, p: Path) = emboss { col, a -> drawPath(p, col, alpha = a, style = stroke(width)) }

    fun circle(center: Offset, r: Float, width: Float) = emboss { col, a -> drawCircle(col, r, center, alpha = a, style = Stroke(width)) }

    fun dot(center: Offset, r: Float) = emboss { col, a -> drawCircle(col, r, center, alpha = a) }

    fun line(a0: Offset, a1: Offset, width: Float) = emboss { col, a -> drawLine(col, a0, a1, width, StrokeCap.Round, alpha = a) }

    private fun style(size: Float) = TextStyle(
        fontFamily = font, fontWeight = FontWeight.Bold, fontSize = (size / s.density / s.fontScale).sp, letterSpacing = (size * 0.08f / s.density / s.fontScale).sp,
    )

    fun text(str: String, center: Offset, size: Float) {
        val m = tm.measure(str, style(size))
        val tl = center - Offset(m.size.width / 2f, m.size.height / 2f)
        emboss { col, a -> drawText(m, col, tl, alpha = a) }
    }

    /** Letters set around a circle, reading clockwise from [startDeg] (0 = right, -90 = top). */
    fun ringText(str: String, center: Offset, radius: Float, startDeg: Float, size: Float) {
        val st = style(size)
        var ang = startDeg * PI.toFloat() / 180f
        for (ch in str) {
            val m = tm.measure(ch.toString(), st)
            val w = m.size.width.toFloat().coerceAtLeast(size * 0.3f)
            val mid = ang + w / 2f / radius
            s.rotate(mid * 180f / PI.toFloat() + 90f, center) {
                val tl = Offset(center.x - m.size.width / 2f, center.y - radius - m.size.height / 2f)
                emboss { col, a -> drawText(m, col, tl, alpha = a) }
            }
            ang += (w + size * 0.12f) / radius
        }
    }

    fun star(center: Offset, r: Float, width: Float = 3f) = emboss { col, a ->
        drawLine(col, center - Offset(r, 0f), center + Offset(r, 0f), width, StrokeCap.Round, alpha = a)
        drawLine(col, center - Offset(0f, r), center + Offset(0f, r), width, StrokeCap.Round, alpha = a)
        drawLine(col, center - Offset(r, r) * 0.45f, center + Offset(r, r) * 0.45f, width * 0.7f, StrokeCap.Round, alpha = a)
        drawLine(col, center - Offset(r, -r) * 0.45f, center + Offset(r, -r) * 0.45f, width * 0.7f, StrokeCap.Round, alpha = a)
    }

    private fun polar(r: Float, deg: Float, o: Offset = c): Offset {
        val t = deg * PI.toFloat() / 180f
        return o + Offset(cos(t), sin(t)) * r
    }

    /** A plain double-ruled border, used to frame most plates. */
    fun border() {
        emboss { col, a ->
            drawRoundRect(col, Offset(26f, 26f), androidx.compose.ui.geometry.Size(848f, 748f), androidx.compose.ui.geometry.CornerRadius(30f), alpha = a, style = Stroke(5f))
            drawRoundRect(col, Offset(42f, 42f), androidx.compose.ui.geometry.Size(816f, 716f), androidx.compose.ui.geometry.CornerRadius(20f), alpha = a, style = Stroke(2.5f))
        }
        for (p in listOf(Offset(26f, 26f), Offset(874f, 26f), Offset(26f, 774f), Offset(874f, 774f))) star(p, 16f)
    }

    // ---- Board 2: the breastplate, its twelve stones in four rows (Exodus 28:17-20).
    fun breastplate() {
        border()
        text("THE BREASTPLATE", Offset(450f, 96f), 50f)
        // The square plate, with rings at the top corners for its chains.
        paths(6f, "M270 150 L630 150 L630 700 L270 700 Z")
        paths(2.5f, "M286 166 L614 166 L614 684 L286 684 Z")
        circle(Offset(270f, 150f), 20f, 5f)
        circle(Offset(630f, 150f), 20f, 5f)
        paths(3.5f, "M252 138 C200 90 150 110 130 150", "M648 138 C700 90 750 110 770 150")
        // Row by row, as the NLT lists them.
        val rows = listOf(
            listOf(Glyph.CARNELIAN, Glyph.PERIDOT, Glyph.EMERALD),
            listOf(Glyph.TURQUOISE, Glyph.LAPIS, Glyph.MOONSTONE),
            listOf(Glyph.JACINTH, Glyph.AGATE, Glyph.AMETHYST),
            listOf(Glyph.BERYL, Glyph.ONYX, Glyph.JASPER),
        )
        rows.forEachIndexed { r, row ->
            row.forEachIndexed { col, g ->
                val cx = 350f + col * 100f
                val cy = 238f + r * 122f
                circle(Offset(cx, cy), 48f, 3f)
                s.translate(cx - 36f, cy - 36f) {
                    s.scale(0.72f, 0.72f, Offset.Zero) {
                        emboss { colr, a -> drawPath(GlyphPaths.path(g, ThemeId.MODERN), colr, alpha = a, style = stroke(9f)) }
                    }
                }
            }
        }
        text("EXODUS 28", Offset(450f, 740f), 30f)
        for (p in listOf(Offset(150f, 420f), Offset(750f, 420f), Offset(150f, 620f), Offset(750f, 620f))) star(p, 14f)
    }

    // ---- Board 3: the greater and lesser lights.
    fun dayAndNight() {
        border()
        val sun = Offset(300f, 370f)
        for (i in 0 until 24) {
            val deg = i * 15f
            if (i % 2 == 0) {
                line(polar(146f, deg, sun), polar(232f, deg, sun), 5f)
            } else {
                // Wavy flame rays.
                val p = Path()
                for (k in 0..10) {
                    val r = 146f + k * 6.5f
                    val pt = polar(r, deg + sin(k * 1.4f) * 3.2f, sun)
                    if (k == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                }
                path(3.5f, p)
            }
        }
        circle(sun, 132f, 6f)
        circle(sun, 118f, 2.5f)
        paths(
            4.5f,
            "M252 338 Q268 322 284 338", "M316 338 Q332 322 348 338",
            "M300 350 L290 396 L306 400", "M258 424 Q300 454 342 424",
        )
        dot(Offset(268f, 346f), 5f); dot(Offset(332f, 346f), 5f)
        // Crescent moon with a sleeping face.
        paths(6f, "M690 214 A160 160 0 1 0 690 526 A130 130 0 0 1 690 214 Z")
        paths(4f, "M598 330 Q612 342 626 330", "M604 382 L590 402 L606 406", "M600 440 Q614 450 630 442")
        for (p in listOf(Offset(560f, 180f), Offset(800f, 250f), Offset(780f, 520f), Offset(120f, 660f), Offset(470f, 150f), Offset(500f, 640f))) star(p, 18f)
        text("DAY", Offset(300f, 650f), 60f)
        text("NIGHT", Offset(640f, 650f), 60f)
    }

    // ---- Board 4: the vine and the branches (John 15).
    fun vine() {
        // The vine winds round in a ring.
        val ring = Path()
        for (k in 0..360) {
            val deg = k.toFloat()
            val r = 300f + 16f * sin(deg * 6f * PI.toFloat() / 180f)
            val pt = polar(r, deg)
            if (k == 0) ring.moveTo(pt.x, pt.y) else ring.lineTo(pt.x, pt.y)
        }
        path(7f, ring)
        // Leaves along it.
        for (i in 0 until 24) {
            val deg = i * 15f
            val base = polar(300f, deg)
            val out = if (i % 2 == 0) 1f else -1f
            s.rotate(deg + 90f, base) {
                paths(4f, "M${base.x} ${base.y} C${base.x - 22} ${base.y + out * 26} ${base.x - 4} ${base.y + out * 58} ${base.x + 20} ${base.y + out * 52} C${base.x + 26} ${base.y + out * 30} ${base.x + 12} ${base.y + out * 8} ${base.x} ${base.y} Z")
            }
        }
        // Four clusters of grapes.
        for (deg in listOf(45f, 135f, 225f, 315f)) {
            val top = polar(250f, deg)
            for ((row, n) in listOf(0 to 4, 1 to 3, 2 to 2, 3 to 1)) {
                for (k in 0 until n) {
                    val g = top + Offset((k - (n - 1) / 2f) * 20f, row * 18f)
                    circle(g, 10f, 3f)
                }
            }
        }
        circle(c, 200f, 3f)
        ringText("THE VINE AND THE BRANCHES · JOHN 15 · THE VINE AND THE BRANCHES · JOHN 15 · ", c, 170f, -90f, 26f)
        text("JOHN 15", c, 56f)
    }

    // ---- Board 5: the lampstand, seven lamps on one stem (Exodus 25).
    fun lampstand() {
        border()
        text("THE LAMPSTAND", Offset(450f, 96f), 50f)
        val top = 290f
        // Three pairs of branches curving up from the stem.
        for (r in listOf(90f, 170f, 250f)) {
            val arm = Path()
            arm.arcTo(Rect(Offset(450f, top), r), 0f, 180f, true)
            path(9f, arm)
        }
        line(Offset(450f, top), Offset(450f, 660f), 12f)
        // The base.
        paths(7f, "M330 720 L570 720 L520 660 L380 660 Z")
        // Cups and flames on all seven lamps.
        for (x in listOf(200f, 280f, 360f, 450f, 540f, 620f, 700f)) {
            paths(5f, "M${x - 22} ${top} L${x + 22} ${top} L${x + 12} ${top + 22} L${x - 12} ${top + 22} Z")
            paths(4.5f, "M$x ${top - 8} C${x - 18} ${top - 30} ${x - 6} ${top - 60} $x ${top - 78} C${x + 6} ${top - 60} ${x + 18} ${top - 30} $x ${top - 8} Z")
        }
        // Almond-blossom knobs on the stem.
        for (y in listOf(420f, 500f, 580f)) circle(Offset(450f, y), 16f, 4f)
        text("EXODUS 25", Offset(450f, 752f), 26f)
    }

    // ---- Board 6: the refiner's furnace (Malachi 3:3).
    fun furnace() {
        border()
        text("THE REFINER'S FIRE", Offset(450f, 96f), 50f)
        // Furnace.
        paths(6f, "M170 742 L170 480 Q170 450 200 450 L390 450 Q420 450 420 480 L420 742")
        for (row in 0 until 6) {
            val y = 495f + row * 45f
            if (y < 740f) line(Offset(172f, y), Offset(418f, y), 2.5f)
            var x = if (row % 2 == 0) 230f else 200f
            while (x < 410f) {
                if (!(x in 222f..368f && y > 620f)) line(Offset(x, y - 45f + 4f), Offset(x, y - 4f), 2.5f)
                x += 60f
            }
        }
        paths(5f, "M228 742 L228 660 A67 67 0 0 1 362 660 L362 742")
        paths(4f, "M262 742 C240 700 270 690 262 650 C290 680 300 700 290 742", "M300 742 C290 700 320 680 312 632 C342 670 344 710 330 742")
        // The crucible on the fire, the metal molten inside and the dross skimmed off the top.
        paths(7f, "M190 300 L400 300 L370 440 L220 440 Z")
        paths(4f, "M205 330 Q250 316 295 330 Q340 344 385 330")
        for ((x, y, r) in listOf(Triple(250f, 318f, 8f), Triple(330f, 312f, 6f), Triple(292f, 306f, 5f))) circle(Offset(x, y), r, 3f)
        // Heat rising.
        for (x in listOf(240f, 295f, 350f)) paths(3.5f, "M$x 280 C${x - 14} 250 ${x + 14} 230 $x 200")
        // The skimming ladle, and bars of refined silver and gold.
        paths(6f, "M420 250 L640 170", "M640 170 C660 150 700 160 700 190 C700 220 660 226 640 206")
        for ((i, y) in listOf(640f, 580f, 520f).withIndex()) {
            val w = 220f - i * 40f
            val x0 = 620f - w / 2f
            paths(6f, "M$x0 $y L${x0 + w} $y L${x0 + w - 24} ${y - 50} L${x0 + 24} ${y - 50} Z")
        }
        text("MALACHI 3:3", Offset(620f, 700f), 30f)
        for (p in listOf(Offset(760f, 260f), Offset(110f, 300f))) star(p, 16f)
    }

    // ---- Board 7: hope, an anchor for the soul (Hebrews 6:19).
    fun anchor() {
        border()
        circle(Offset(450f, 140f), 40f, 7f)
        line(Offset(450f, 180f), Offset(450f, 650f), 14f)
        line(Offset(300f, 240f), Offset(600f, 240f), 12f)
        for (x in listOf(300f, 600f)) circle(Offset(x, 240f), 12f, 5f)
        // Arms, with the flukes at either end.
        paths(12f, "M200 500 C220 640 360 690 450 690 C540 690 680 640 700 500")
        paths(8f, "M200 500 L170 560 M200 500 L250 540", "M700 500 L730 560 M700 500 L650 540")
        // A rope wound from the ring down the shank.
        val rope = Path()
        for (k in 0..80) {
            val t = k / 80f
            val y = 150f + t * 520f
            val x = 450f + sin(t * 9f * PI.toFloat()) * (50f + 30f * t) + (if (t < 0.1f) (1 - t * 10f) * 40f else 0f)
            if (k == 0) rope.moveTo(x, y) else rope.lineTo(x, y)
        }
        path(4f, rope)
        text("HOPE", Offset(180f, 330f), 54f)
        text("HEBREWS 6:19", Offset(700f, 330f), 30f)
        for (p in listOf(Offset(130f, 150f), Offset(770f, 150f), Offset(130f, 700f), Offset(770f, 700f))) star(p, 14f)
    }

    // ---- Board 8: the two tablets of the law (Exodus 20).
    fun tablets() {
        border()
        paths(7f, "M180 720 L180 230 C180 130 430 130 430 230 L430 720 Z", "M470 720 L470 230 C470 130 720 130 720 230 L720 720 Z")
        paths(2.5f, "M198 704 L198 234 C198 150 412 150 412 234 L412 704 Z", "M488 704 L488 234 C488 150 702 150 702 234 L702 704 Z")
        val left = listOf("I", "II", "III", "IV", "V")
        val right = listOf("VI", "VII", "VIII", "IX", "X")
        left.forEachIndexed { i, n -> text(n, Offset(305f, 270f + i * 95f), 54f) }
        right.forEachIndexed { i, n -> text(n, Offset(595f, 270f + i * 95f), 54f) }
        text("EXODUS 20", Offset(450f, 752f), 26f)
        for (p in listOf(Offset(110f, 150f), Offset(790f, 150f))) star(p, 16f)
    }

    // ---- Board 9: the rose of Sharon.
    fun rose() {
        circle(c, 368f, 5f)
        circle(c, 348f, 2.5f)
        for (i in 0 until 72) dot(polar(358f, i * 5f), 3.5f)
        fun petals(n: Int, rBase: Float, rTip: Float, offset: Float, width: Float) {
            for (i in 0 until n) {
                val mid = offset + i * 360f / n
                val half = 180f / n
                val a = polar(rBase, mid - half)
                val b = polar(rBase, mid + half)
                val tip = polar(rTip, mid)
                val c1 = polar(rTip * 0.95f, mid - half * 0.9f)
                val c2 = polar(rTip * 0.95f, mid + half * 0.9f)
                val p = Path().apply {
                    moveTo(a.x, a.y)
                    quadraticTo(c1.x, c1.y, tip.x, tip.y)
                    quadraticTo(c2.x, c2.y, b.x, b.y)
                }
                path(width, p)
                line(polar(rBase + 10f, mid), polar(rTip - 30f, mid), 2f)
            }
        }
        petals(8, 150f, 326f, -90f, 5f)
        petals(8, 110f, 236f, -67.5f, 4f)
        val star = Path()
        for (k in 0..32) {
            val p = polar(if (k % 2 == 0) 108f else 58f, -90f + k * 360f / 32f)
            if (k == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
        }
        path(3.5f, star)
        circle(c, 36f, 5f)
        dot(c, 10f)
    }
}
