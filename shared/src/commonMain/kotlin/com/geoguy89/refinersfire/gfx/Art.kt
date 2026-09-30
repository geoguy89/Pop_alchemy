package com.geoguy89.refinersfire.gfx

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.Glyph
import com.geoguy89.refinersfire.game.Piece
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val roundStroke = { w: Float -> Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round) }

/** Draws [block] in a 0..100 coordinate space mapped onto the square at [center] with side [size]. */
inline fun DrawScope.inUnitBox(center: Offset, size: Float, block: DrawScope.() -> Unit) {
    val s = size / 100f
    withTransform({
        translate(center.x - size / 2f, center.y - size / 2f)
        scale(s, s, pivot = Offset.Zero)
    }) { block() }
}

/** Draw any piece centred at [center] filling roughly a cell of side [size]. */
fun DrawScope.drawPiece(piece: Piece, center: Offset, size: Float, alpha: Float = 1f, glow: Float = 0f, time: Float = 0f) {
    when (piece) {
        is Piece.Stone -> drawStone(piece, center, size, alpha, glow)
        Piece.Cornerstone -> drawCornerstone(center, size, alpha, glow)
        Piece.Hammer -> drawHammer(center, size, alpha, glow, time)
    }
}

fun DrawScope.drawStone(stone: Piece.Stone, center: Offset, size: Float, alpha: Float = 1f, glow: Float = 0f) {
    val path = GlyphPaths.path(stone.glyph)
    val main = Palette.pieceColor(stone.color)
    val dark = Palette.pieceDark(stone.color)
    val light = Palette.pieceLight(stone.color)
    // Glyphs are drawn at 78% of the cell so strokes don't touch neighbours.
    inUnitBox(center, size * 0.78f) {
        when (Palette.theme) {
            ThemeId.TEMPLE -> { drawStoneTemple(path, main, dark, alpha, glow); return@inUnitBox }
            ThemeId.FUTURE -> { drawStoneFuture(path, main, alpha, glow); return@inUnitBox }
            ThemeId.MODERN -> Unit
        }
        if (glow > 0f) {
            drawPath(path, main, alpha = 0.10f * glow * alpha, style = roundStroke(34f))
            drawPath(path, main, alpha = 0.16f * glow * alpha, style = roundStroke(24f))
        }
        translate(2.2f, 3.2f) { drawPath(path, Color.Black, alpha = 0.45f * alpha, style = roundStroke(15f)) }
        drawPath(path, dark, alpha = alpha, style = roundStroke(15f))
        drawPath(
            path,
            Brush.linearGradient(listOf(light, main, main, lerp(main, dark, 0.35f)), Offset(0f, 0f), Offset(100f, 100f)),
            alpha = alpha,
            style = roundStroke(10.5f),
        )
        translate(-1.3f, -1.6f) { drawPath(path, Color.White, alpha = 0.55f * alpha, style = roundStroke(2.8f)) }
    }
}

/** The Cornerstone's engraving: a plain cross. */
private val cornerstoneMark: Path by lazy {
    Path().apply {
        moveTo(50f, 30f); lineTo(50f, 70f)
        moveTo(37f, 43f); lineTo(63f, 43f)
    }
}

fun DrawScope.drawCornerstone(center: Offset, size: Float, alpha: Float = 1f, glow: Float = 0f) {
    inUnitBox(center, size) {
        if (Palette.theme == ThemeId.FUTURE) { drawCornerstoneFuture(alpha, glow); return@inUnitBox }
        if (glow > 0f) {
            drawRoundRect(Color.White, Offset(2f, 2f), Size(96f, 96f), CornerRadius(10f), alpha = 0.18f * glow * alpha)
        }
        val o0 = 9f; val o1 = 91f; val i0 = 27f; val i1 = 73f
        drawRoundRect(Color.Black, Offset(o0 + 2f, o0 + 3f), Size(o1 - o0, o1 - o0), CornerRadius(4f), alpha = 0.45f * alpha)
        fun quad(a: Offset, b: Offset, c: Offset, d: Offset, color: Color) {
            val p = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); lineTo(d.x, d.y); close() }
            drawPath(p, color, alpha = alpha)
        }
        val otl = Offset(o0, o0); val otr = Offset(o1, o0); val obl = Offset(o0, o1); val obr = Offset(o1, o1)
        val itl = Offset(i0, i0); val itr = Offset(i1, i0); val ibl = Offset(i0, i1); val ibr = Offset(i1, i1)
        quad(otl, otr, itr, itl, Color(0xFFE4E7EA))
        quad(otl, itl, ibl, obl, Color(0xFFB4B9BF))
        quad(otr, obr, ibr, itr, Color(0xFF70767D))
        quad(obl, ibl, ibr, obr, Color(0xFF50555B))
        drawRect(
            Brush.linearGradient(listOf(Color(0xFFD2D6DA), Color(0xFF9A9FA5)), itl, ibr),
            topLeft = itl, size = Size(i1 - i0, i1 - i0), alpha = alpha,
        )
        drawPath(cornerstoneMark, Color(0xFF4A4E54), alpha = 0.6f * alpha, style = Stroke(4f, cap = StrokeCap.Round))
        translate(-0.9f, -0.9f) { drawPath(cornerstoneMark, Color.White, alpha = 0.4f * alpha, style = Stroke(2f, cap = StrokeCap.Round)) }
        drawRect(Color(0xFF2B2E33), otl, Size(o1 - o0, o1 - o0), alpha = alpha, style = Stroke(2f))
    }
}

/** The Refiner's Hammer (the piece that knocks out any stone): a smith's hammer with a glowing, just-forged head. */
fun DrawScope.drawHammer(center: Offset, size: Float, alpha: Float = 1f, glow: Float = 0f, time: Float = 0f) {
    val wood = Color(0xFF8A5A2E)
    val woodDark = Color(0xFF4A2C14)
    val iron = Color(0xFF6E7378)
    val outline = Color(0xFF1C1410)
    inUnitBox(center, size * 0.92f) {
        val pulse = 0.5f + 0.5f * sin(time * 3f)
        if (glow > 0f) drawCircle(Palette.ember, 44f, Offset(50f, 50f), alpha = 0.18f * glow * alpha)
        rotate(-35f, Offset(50f, 50f)) {
            // Handle.
            drawRoundRect(outline, Offset(43f, 34f), Size(14f, 62f), CornerRadius(7f), alpha = alpha)
            drawRoundRect(Brush.horizontalGradient(listOf(wood, woodDark), 45f, 55f), Offset(45f, 36f), Size(10f, 58f), CornerRadius(5f), alpha = alpha)
            drawLine(Color.White, Offset(47.5f, 40f), Offset(47.5f, 90f), 1.4f, alpha = 0.25f * alpha)
            // Head: a flat face on one side, a peen on the other.
            val head = Path().apply {
                moveTo(16f, 16f); lineTo(70f, 16f); lineTo(88f, 22f); lineTo(88f, 34f); lineTo(70f, 40f); lineTo(16f, 40f); close()
            }
            drawPath(head, outline, alpha = alpha, style = Stroke(6f, join = StrokeJoin.Round))
            drawPath(head, Brush.verticalGradient(listOf(Color(0xFFB8BEC4), iron, Color(0xFF3A3E42)), 16f, 40f), alpha = alpha)
            drawRect(Color.White, Offset(18f, 18f), Size(50f, 3f), alpha = 0.35f * alpha)
            // The striking face still glows from the forge.
            drawRect(Palette.ember, Offset(12f, 16f), Size(7f, 24f), alpha = (0.55f + 0.35f * pulse) * alpha)
            drawCircle(Palette.ember, 10f, Offset(15f, 28f), alpha = 0.18f * pulse * alpha)
        }
    }
}

fun DrawScope.drawLeadTile(topLeft: Offset, s: Float, seed: Int, alpha: Float = 1f) {
    when (Palette.theme) {
        ThemeId.TEMPLE -> return drawLeadTileTemple(topLeft, s, seed, alpha)
        ThemeId.FUTURE -> return drawLeadTileFuture(topLeft, s, seed, alpha)
        ThemeId.MODERN -> Unit
    }
    val tint = ((seed * 1103515245 + 12345) ushr 16 and 0xFF) / 255f
    val base = lerp(Palette.lead, Palette.leadLight, tint * 0.25f)
    drawRect(
        Brush.linearGradient(listOf(lerp(base, Color.White, 0.14f), base, Palette.leadDark), topLeft, topLeft + Offset(s, s)),
        topLeft, Size(s, s), alpha = alpha,
    )
    val b = s * 0.055f
    drawRect(Color.White, topLeft, Size(s, b), alpha = 0.16f * alpha)
    drawRect(Color.White, topLeft, Size(b, s), alpha = 0.12f * alpha)
    drawRect(Color.Black, topLeft + Offset(0f, s - b), Size(s, b), alpha = 0.30f * alpha)
    drawRect(Color.Black, topLeft + Offset(s - b, 0f), Size(b, s), alpha = 0.24f * alpha)
    // Pits and specks in the metal.
    var h = seed * 2654435761L.toInt()
    repeat(5) {
        h = h * 1664525 + 1013904223
        val x = ((h ushr 8) and 0xFF) / 255f
        h = h * 1664525 + 1013904223
        val y = ((h ushr 8) and 0xFF) / 255f
        val r = s * (0.012f + 0.02f * (((h ushr 20) and 0xF) / 15f))
        drawCircle(Color.Black, r, topLeft + Offset(s * (0.12f + 0.76f * x), s * (0.12f + 0.76f * y)), alpha = 0.18f * alpha)
    }
}

fun DrawScope.drawGoldTile(topLeft: Offset, s: Float, time: Float, phase: Float, alpha: Float = 1f) {
    when (Palette.theme) {
        ThemeId.TEMPLE -> return drawGoldTileTemple(topLeft, s, time, phase, alpha)
        ThemeId.FUTURE -> return drawGoldTileFuture(topLeft, s, time, phase, alpha)
        ThemeId.MODERN -> Unit
    }
    drawRect(
        Brush.linearGradient(listOf(Palette.goldLight, Palette.gold, Palette.goldDark), topLeft, topLeft + Offset(s, s)),
        topLeft, Size(s, s), alpha = alpha,
    )
    val b = s * 0.06f
    drawRect(Color.White, topLeft, Size(s, b), alpha = 0.45f * alpha)
    drawRect(Color.White, topLeft, Size(b, s), alpha = 0.35f * alpha)
    drawRect(Color(0xFF5A3A08), topLeft + Offset(0f, s - b), Size(s, b), alpha = 0.45f * alpha)
    drawRect(Color(0xFF5A3A08), topLeft + Offset(s - b, 0f), Size(b, s), alpha = 0.38f * alpha)
    // A glint sweeps across every few seconds.
    val p = ((time * 0.22f + phase) % 2.2f) - 0.4f
    if (p in -0.4f..1.4f) {
        val a = topLeft + Offset(s, s) * (p - 0.18f)
        val e = topLeft + Offset(s, s) * (p + 0.18f)
        drawRect(
            Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent), a, e),
            topLeft, Size(s, s), alpha = alpha,
        )
    }
}

/** The great engraved seal that sits beneath the stones, like the relief on the original board. */
fun DrawScope.drawSeal(center: Offset, radius: Float, time: Float, alpha: Float) {
    fun engraved(block: DrawScope.(Color, Float) -> Unit) {
        translate(radius * 0.006f, radius * 0.008f) { block(Color.Black, 0.5f * alpha) }
        translate(-radius * 0.004f, -radius * 0.005f) { block(Color.White, 0.32f * alpha) }
    }
    val w = radius * 0.012f
    engraved { c, a ->
        drawCircle(c, radius, center, alpha = a, style = Stroke(w * 1.6f))
        drawCircle(c, radius * 0.86f, center, alpha = a, style = Stroke(w))
        drawCircle(c, radius * 0.58f, center, alpha = a, style = Stroke(w))
        // Radiating ticks.
        for (i in 0 until 48) {
            val ang = (i * 2 * PI / 48).toFloat()
            val d = Offset(cos(ang), sin(ang))
            drawLine(c, center + d * radius * 0.86f, center + d * radius * (if (i % 4 == 0) 0.80f else 0.83f), w * 0.8f, alpha = a)
        }
    }
    drawSealCross(center, radius * 0.5f, alpha)
    // The twelve signs ring the seal, turning ever so slowly.
    val glyphSize = radius * 0.13f
    Glyph.entries.forEachIndexed { i, g ->
        val ang = (i * 2 * PI / 12 + time * 0.01).toFloat()
        val pos = center + Offset(cos(ang), sin(ang)) * radius * 0.93f
        rotate(ang * 180f / PI.toFloat() + 90f, pos) {
            inUnitBox(pos, glyphSize) {
                translate(1.5f, 2f) { drawPath(GlyphPaths.path(g), Color.Black, alpha = 0.45f * alpha, style = roundStroke(7f)) }
                drawPath(GlyphPaths.path(g), Color.White, alpha = 0.28f * alpha, style = roundStroke(5f))
            }
        }
    }
}

/** A raised cross at the heart of the seal: drop shadow, bevelled faces, and a lit upper edge. */
private fun DrawScope.drawSealCross(center: Offset, half: Float, alpha: Float) {
    val bar = half * 0.28f
    val armY = center.y - half * 0.32f
    val armHalf = half * 0.68f
    fun cross(o: Offset = Offset.Zero, grow: Float = 0f) = Path().apply {
        val l = center.x - bar / 2 - grow + o.x; val r = center.x + bar / 2 + grow + o.x
        val t = center.y - half - grow + o.y; val b = center.y + half + grow + o.y
        val al = center.x - armHalf - grow + o.x; val ar = center.x + armHalf + grow + o.x
        val at = armY - bar / 2 - grow + o.y; val ab = armY + bar / 2 + grow + o.y
        moveTo(l, t); lineTo(r, t); lineTo(r, at); lineTo(ar, at); lineTo(ar, ab); lineTo(r, ab)
        lineTo(r, b); lineTo(l, b); lineTo(l, ab); lineTo(al, ab); lineTo(al, at); lineTo(l, at); close()
    }
    val depth = half * 0.06f
    // Shadow, then the side walls (stacked offsets), then the face.
    drawPath(cross(Offset(depth * 1.6f, depth * 2.2f), depth * 0.4f), Color.Black, alpha = 0.45f * alpha)
    for (i in 4 downTo 1) drawPath(cross(Offset(depth * i / 4f, depth * i / 4f)), Color(0xFF2A2320), alpha = 0.9f * alpha)
    drawPath(
        cross(),
        Brush.linearGradient(
            listOf(Color(0xFFF1E3C0), Color(0xFFC9A96B), Color(0xFF8A6A3A)),
            Offset(center.x - armHalf, center.y - half), Offset(center.x + armHalf, center.y + half),
        ),
        alpha = 0.85f * alpha,
    )
    // Inner bevel line and a bright upper-left edge.
    drawPath(cross(grow = -bar * 0.18f), Color.Black, alpha = 0.18f * alpha, style = Stroke(half * 0.012f))
    drawPath(cross(Offset(-half * 0.006f, -half * 0.006f)), Color.White, alpha = 0.35f * alpha, style = Stroke(half * 0.014f))
}

/** A brass plate with bevel and optional corner rivets. */
fun DrawScope.drawBrassPlate(topLeft: Offset, size: Size, corner: Float, rivets: Boolean = true, dark: Boolean = false) {
    when (Palette.theme) {
        ThemeId.TEMPLE -> return drawIronPlate(topLeft, size, corner, rivets, dark)
        ThemeId.FUTURE -> return drawGlassPlate(topLeft, size, corner, rivets, dark)
        ThemeId.MODERN -> Unit
    }
    val c0 = if (dark) Color(0xFF3A2A18) else Palette.brassLight
    val c1 = if (dark) Color(0xFF261A0F) else Palette.brass
    val c2 = if (dark) Color(0xFF150E08) else Palette.brassDark
    drawRoundRect(Color.Black, topLeft + Offset(2f, 4f), size, CornerRadius(corner), alpha = 0.5f)
    drawRoundRect(Brush.verticalGradient(listOf(c0, c1, c2), topLeft.y, topLeft.y + size.height), topLeft, size, CornerRadius(corner))
    val inset = size.minDimension * 0.08f
    drawRoundRect(
        Color.Black, topLeft + Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
        CornerRadius((corner - inset).coerceAtLeast(0f)), alpha = 0.18f, style = Stroke(1.5f),
    )
    drawRoundRect(Color.White, topLeft, size, CornerRadius(corner), alpha = if (dark) 0.08f else 0.35f, style = Stroke(1.5f))
    if (rivets) {
        val r = (size.minDimension * 0.06f).coerceIn(2f, 7f)
        val m = r * 2.2f
        for (p in listOf(
            Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m),
        )) {
            drawCircle(Brush.radialGradient(listOf(Color.White, Palette.brass, Palette.brassDark), topLeft + p - Offset(r / 3, r / 3), r * 1.4f), r, topLeft + p)
        }
    }
}
