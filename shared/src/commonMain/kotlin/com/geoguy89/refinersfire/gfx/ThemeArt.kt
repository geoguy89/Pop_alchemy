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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.Piece
import kotlin.math.sin

// Drawing for the Temple and Future themes. The Modern theme uses the original functions in Art.kt.

private fun round(w: Float) = Stroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Deterministic 0..1 noise from a seed. */
private fun hash01(seed: Int, k: Int): Float {
    var h = seed * 374761393 + k * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

// ---- Backdrops -------------------------------------------------------------------------------------------------------

fun DrawScope.drawBackdrop(theme: ThemeId) = when (theme) {
    ThemeId.TEMPLE -> drawTempleWall()
    ThemeId.FUTURE -> drawNeonGrid()
    ThemeId.MODERN -> Unit
}

/** Rough stone blocks in staggered courses, lit by two torches. */
private fun DrawScope.drawTempleWall() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1A1714), Color(0xFF0C0B0A), Color(0xFF14110E))))
    val courseH = (size.minDimension / 9f).coerceAtLeast(40f)
    val blockW = courseH * 1.9f
    var row = 0
    var y = 0f
    while (y < size.height) {
        val shift = if (row % 2 == 0) 0f else blockW / 2f
        var x = -shift
        var col = 0
        while (x < size.width) {
            val seed = row * 97 + col
            val shade = 0.10f + 0.10f * hash01(seed, 1)
            val tl = Offset(x + 2f, y + 2f)
            val sz = Size(blockW - 4f, courseH - 4f)
            drawRoundRect(lerp(Color(0xFF1B1916), Color(0xFF4A453D), shade * 2.2f), tl, sz, CornerRadius(courseH * 0.08f))
            drawRect(Color.White, tl, Size(sz.width, 2f), alpha = 0.05f)
            drawRect(Color.Black, tl + Offset(0f, sz.height - 3f), Size(sz.width, 3f), alpha = 0.35f)
            if (hash01(seed, 2) > 0.7f) {
                val cx = tl.x + sz.width * (0.2f + 0.6f * hash01(seed, 3))
                drawLine(Color.Black, Offset(cx, tl.y + sz.height * 0.15f), Offset(cx + sz.width * 0.08f, tl.y + sz.height * 0.6f), 1.5f, alpha = 0.4f)
            }
            x += blockW
            col++
        }
        y += courseH
        row++
    }
    for (fx in listOf(0.08f, 0.92f)) {
        val c = Offset(size.width * fx, size.height * 0.2f)
        drawCircle(Brush.radialGradient(listOf(Color(0x66FF9A30), Color(0x22FF6A10), Color.Transparent), c, size.maxDimension * 0.35f), size.maxDimension * 0.35f, c)
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0xCC000000)),
            center = Offset(size.width * 0.5f, size.height * 0.5f),
            radius = size.maxDimension * 0.75f,
        ),
    )
}

/** A dark horizon with a perspective grid floor and faint scanlines. */
private fun DrawScope.drawNeonGrid() {
    drawRect(Brush.verticalGradient(listOf(Color(0xFF070A18), Color(0xFF0B0F24), Color(0xFF05060C))))
    val horizon = size.height * 0.55f
    drawRect(
        Brush.verticalGradient(listOf(Color.Transparent, Color(0x33B03CFF), Color(0x2238E8FF), Color.Transparent), horizon - size.height * 0.12f, horizon + size.height * 0.05f),
        Offset(0f, horizon - size.height * 0.12f), Size(size.width, size.height * 0.17f),
    )
    val vanish = Offset(size.width / 2f, horizon)
    val grid = Color(0xFF38E8FF)
    for (i in -12..12) {
        val bx = size.width / 2f + i * size.width / 10f
        drawLine(grid, vanish, Offset(bx, size.height), 1.2f, alpha = 0.16f)
    }
    var k = 1f
    while (true) {
        val y = horizon + (size.height - horizon) * (k * k) / 64f
        if (y > size.height) break
        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.2f, alpha = 0.10f + 0.1f * (y - horizon) / (size.height - horizon))
        k += 1f
    }
    var y = 0f
    while (y < size.height) {
        drawLine(Color.Black, Offset(0f, y), Offset(size.width, y), 1f, alpha = 0.18f)
        y += 4f
    }
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color(0xAA000000)),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.maxDimension * 0.8f,
        ),
    )
}

// ---- Stones -----------------------------------------------------------------------------------------------------------

/** Temple stones: painted into a carved groove, lit from the top left. */
internal fun DrawScope.drawStoneTemple(path: Path, main: Color, dark: Color, alpha: Float, glow: Float) {
    if (glow > 0f) drawPath(path, Palette.ember, alpha = 0.12f * glow * alpha, style = round(30f))
    translate(-1.6f, -1.8f) { drawPath(path, Color.Black, alpha = 0.55f * alpha, style = round(15f)) }
    translate(1.4f, 1.8f) { drawPath(path, Color.White, alpha = 0.16f * alpha, style = round(14f)) }
    drawPath(path, dark, alpha = alpha, style = round(13f))
    drawPath(path, lerp(main, Color(0xFF8A7050), 0.12f), alpha = alpha, style = round(9f))
    translate(0.8f, 1f) { drawPath(path, Color.Black, alpha = 0.22f * alpha, style = round(3f)) }
}

/** Future stones: neon tubes with a white-hot core. */
internal fun DrawScope.drawStoneFuture(path: Path, main: Color, alpha: Float, glow: Float) {
    drawPath(path, main, alpha = (0.10f + 0.12f * glow) * alpha, style = round(26f))
    drawPath(path, main, alpha = (0.22f + 0.15f * glow) * alpha, style = round(15f))
    drawPath(path, main, alpha = alpha, style = round(8f))
    drawPath(path, lerp(main, Color.White, 0.75f), alpha = alpha, style = round(3f))
}

/** Future wild: a prismatic data chip. */
internal fun DrawScope.drawCornerstoneFuture(alpha: Float, glow: Float) {
    val body = Path().apply {
        moveTo(22f, 10f); lineTo(78f, 10f); lineTo(90f, 22f); lineTo(90f, 78f)
        lineTo(78f, 90f); lineTo(22f, 90f); lineTo(10f, 78f); lineTo(10f, 22f); close()
    }
    val rainbow = Brush.sweepGradient(
        listOf(Color(0xFFFF3B4E), Color(0xFFFFEA3A), Color(0xFF39FF6A), Color(0xFF2EF6FF), Color(0xFF4C7DFF), Color(0xFFFF45F0), Color(0xFFFF3B4E)),
        Offset(50f, 50f),
    )
    if (glow > 0f) drawPath(body, Color.White, alpha = 0.15f * glow * alpha, style = round(18f))
    drawPath(body, Color(0xFF0A1422), alpha = alpha)
    drawPath(body, rainbow, alpha = 0.3f * alpha, style = round(12f))
    drawPath(body, rainbow, alpha = alpha, style = round(4f))
    for (k in 0..3) {
        val y = 30f + k * 13f
        drawLine(Color(0xFF2EF6FF), Offset(28f, y), Offset(72f, y), 2f, alpha = 0.35f * alpha)
    }
    drawRect(rainbow, Offset(38f, 38f), Size(24f, 24f), alpha = alpha)
    drawRect(Color.White, Offset(38f, 38f), Size(24f, 24f), alpha = 0.5f * alpha, style = Stroke(1.5f))
}

internal fun DrawScope.drawPieceGlowFuture(piece: Piece, alpha: Float) {
    if (piece is Piece.Hammer) drawCircle(Color(0xFF38E8FF), 46f, Offset(50f, 50f), alpha = 0.14f * alpha)
}

// ---- Tiles -----------------------------------------------------------------------------------------------------------

internal fun DrawScope.drawLeadTileTemple(topLeft: Offset, s: Float, seed: Int, alpha: Float) {
    val tint = hash01(seed, 7)
    val base = lerp(Palette.leadDark, Palette.leadLight, 0.25f + tint * 0.35f)
    drawRect(Color(0xFF15130F), topLeft, Size(s, s), alpha = alpha)
    val m = s * 0.045f
    val tl = topLeft + Offset(m, m)
    val sz = Size(s - 2 * m, s - 2 * m)
    drawRoundRect(
        Brush.linearGradient(listOf(lerp(base, Color.White, 0.08f), base, lerp(base, Color.Black, 0.3f)), tl, tl + Offset(sz.width, sz.height)),
        tl, sz, CornerRadius(s * 0.06f), alpha = alpha,
    )
    drawRect(Color.White, tl, Size(sz.width, s * 0.03f), alpha = 0.08f * alpha)
    // Cracks and chips.
    if (tint > 0.55f) {
        val a = tl + Offset(sz.width * hash01(seed, 1), 0f)
        val b = a + Offset(sz.width * 0.12f, sz.height * 0.35f)
        val c = b + Offset(-sz.width * 0.08f, sz.height * 0.25f)
        drawLine(Color.Black, a, b, s * 0.018f, alpha = 0.45f * alpha)
        drawLine(Color.Black, b, c, s * 0.014f, alpha = 0.4f * alpha)
    }
    repeat(4) { k ->
        val p = tl + Offset(sz.width * (0.1f + 0.8f * hash01(seed, 10 + k)), sz.height * (0.1f + 0.8f * hash01(seed, 20 + k)))
        drawCircle(Color.Black, s * (0.01f + 0.015f * hash01(seed, 30 + k)), p, alpha = 0.25f * alpha)
    }
}

internal fun DrawScope.drawGoldTileTemple(topLeft: Offset, s: Float, time: Float, phase: Float, alpha: Float) {
    drawRect(Color(0xFF2A1E08), topLeft, Size(s, s), alpha = alpha)
    val m = s * 0.045f
    val tl = topLeft + Offset(m, m)
    val sz = Size(s - 2 * m, s - 2 * m)
    // Torchlight flicker, different per tile.
    val flicker = 0.9f + 0.06f * sin(time * 7.3f + phase * 20f) + 0.04f * sin(time * 13.1f + phase * 7f)
    drawRoundRect(
        Brush.radialGradient(listOf(lerp(Palette.goldLight, Palette.gold, 0.3f), Palette.gold, Palette.goldDark), tl + Offset(sz.width * 0.3f, sz.height * 0.25f), s),
        tl, sz, CornerRadius(s * 0.06f), alpha = alpha * flicker,
    )
    // Hammered texture.
    val seed = (phase * 1000).toInt()
    repeat(6) { k ->
        val p = tl + Offset(sz.width * (0.12f + 0.76f * hash01(seed, k)), sz.height * (0.12f + 0.76f * hash01(seed, k + 9)))
        drawCircle(Palette.goldDark, s * 0.06f, p, alpha = 0.22f * alpha)
        drawCircle(Color.White, s * 0.025f, p - Offset(s * 0.015f, s * 0.015f), alpha = 0.18f * alpha)
    }
    drawRoundRect(Color.Black, tl, sz, CornerRadius(s * 0.06f), alpha = 0.3f * alpha, style = Stroke(s * 0.025f))
}

internal fun DrawScope.drawLeadTileFuture(topLeft: Offset, s: Float, seed: Int, alpha: Float) {
    val base = lerp(Palette.lead, Palette.leadLight, hash01(seed, 5) * 0.3f)
    drawRect(Brush.linearGradient(listOf(lerp(base, Color.White, 0.05f), base, Palette.leadDark), topLeft, topLeft + Offset(s, s)), topLeft, Size(s, s), alpha = alpha)
    val grid = Palette.brass
    drawRect(grid, topLeft, Size(s, s), alpha = 0.10f * alpha, style = Stroke(1f))
    val t = s * 0.16f
    val w = s * 0.03f
    for ((c, dx, dy) in listOf(Triple(topLeft, 1f, 1f), Triple(topLeft + Offset(s, 0f), -1f, 1f), Triple(topLeft + Offset(0f, s), 1f, -1f), Triple(topLeft + Offset(s, s), -1f, -1f))) {
        val o = c + Offset(dx * s * 0.08f, dy * s * 0.08f)
        drawLine(grid, o, o + Offset(dx * t, 0f), w, alpha = 0.25f * alpha)
        drawLine(grid, o, o + Offset(0f, dy * t), w, alpha = 0.25f * alpha)
    }
    drawCircle(grid, s * 0.02f, topLeft + Offset(s / 2, s / 2), alpha = 0.18f * alpha)
}

internal fun DrawScope.drawGoldTileFuture(topLeft: Offset, s: Float, time: Float, phase: Float, alpha: Float) {
    drawRect(
        Brush.linearGradient(listOf(Palette.goldLight, Palette.gold, Palette.goldDark), topLeft, topLeft + Offset(s, s)),
        topLeft, Size(s, s), alpha = alpha,
    )
    // Circuit traces.
    val trace = Color(0xFF7A4A00)
    val seed = (phase * 1000).toInt()
    val y1 = topLeft.y + s * (0.25f + 0.2f * hash01(seed, 1))
    val x1 = topLeft.x + s * (0.4f + 0.3f * hash01(seed, 2))
    drawLine(trace, Offset(topLeft.x, y1), Offset(x1, y1), s * 0.03f, alpha = 0.45f * alpha)
    drawLine(trace, Offset(x1, y1), Offset(x1 + s * 0.2f, y1 + s * 0.2f), s * 0.03f, alpha = 0.45f * alpha)
    drawLine(trace, Offset(x1 + s * 0.2f, y1 + s * 0.2f), Offset(x1 + s * 0.2f, topLeft.y + s), s * 0.03f, alpha = 0.45f * alpha)
    drawCircle(trace, s * 0.05f, Offset(x1, y1), alpha = 0.5f * alpha)
    drawRect(Color.White, topLeft, Size(s, s), alpha = 0.35f * alpha, style = Stroke(s * 0.035f))
    // A scan bar sweeps down every few seconds.
    val p = ((time * 0.3f + phase) % 2f) - 0.2f
    if (p in 0f..1f) {
        drawRect(Color.White, Offset(topLeft.x, topLeft.y + s * p), Size(s, s * 0.08f), alpha = 0.35f * alpha * (1f - p))
    }
}

// ---- Plates (buttons, panels, gauges) --------------------------------------------------------------------------------

/** Iron-bound plate with square rivets. */
internal fun DrawScope.drawIronPlate(topLeft: Offset, size: Size, corner: Float, rivets: Boolean, dark: Boolean) {
    val c0 = if (dark) Color(0xFF3A342C) else Palette.brassLight
    val c1 = if (dark) Color(0xFF26221D) else Palette.brass
    val c2 = if (dark) Color(0xFF12100D) else Palette.brassDark
    val r = corner.coerceAtMost(size.minDimension * 0.22f)
    drawRoundRect(Color.Black, topLeft + Offset(2f, 4f), size, CornerRadius(r), alpha = 0.6f)
    drawRoundRect(Brush.verticalGradient(listOf(c0, c1, c2), topLeft.y, topLeft.y + size.height), topLeft, size, CornerRadius(r))
    // Hammered speckle.
    for (k in 0 until 14) {
        val p = topLeft + Offset(size.width * hash01(k, 3), size.height * hash01(k, 4))
        drawCircle(Color.Black, size.minDimension * 0.02f, p, alpha = 0.12f)
    }
    drawRoundRect(Color.Black, topLeft, size, CornerRadius(r), alpha = 0.6f, style = Stroke(2f))
    drawRoundRect(Color.White, topLeft + Offset(1.5f, 1.5f), Size(size.width - 3f, size.height - 3f), CornerRadius(r), alpha = if (dark) 0.05f else 0.2f, style = Stroke(1f))
    if (rivets) {
        val q = (size.minDimension * 0.06f).coerceIn(2f, 6f)
        val m = q * 2.4f
        for (p in listOf(Offset(m, m), Offset(size.width - m, m), Offset(m, size.height - m), Offset(size.width - m, size.height - m))) {
            drawRect(Color(0xFF1A1714), topLeft + p - Offset(q, q), Size(q * 2, q * 2))
            drawRect(Color(0xFF6A645A), topLeft + p - Offset(q * 0.7f, q * 0.7f), Size(q * 1.2f, q * 1.2f))
        }
    }
}

/** Chamfered glass plate with a neon edge. */
internal fun DrawScope.drawGlassPlate(topLeft: Offset, size: Size, corner: Float, rivets: Boolean, dark: Boolean) {
    val ch = corner.coerceAtMost(size.minDimension * 0.35f).coerceAtLeast(4f)
    val p = Path().apply {
        moveTo(topLeft.x + ch, topLeft.y); lineTo(topLeft.x + size.width, topLeft.y)
        lineTo(topLeft.x + size.width, topLeft.y + size.height - ch); lineTo(topLeft.x + size.width - ch, topLeft.y + size.height)
        lineTo(topLeft.x, topLeft.y + size.height); lineTo(topLeft.x, topLeft.y + ch); close()
    }
    val edge = Palette.brass
    if (dark) {
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFF12233A), Color(0xFF070E1A)), topLeft.y, topLeft.y + size.height))
        drawPath(p, edge, alpha = 0.55f, style = Stroke(1.5f))
    } else {
        drawPath(p, Brush.verticalGradient(listOf(Palette.brassLight, Palette.brass, Palette.brassDark), topLeft.y, topLeft.y + size.height))
        drawPath(p, Color.White, alpha = 0.6f, style = Stroke(1.5f))
    }
    drawPath(p, edge, alpha = 0.18f, style = Stroke(6f))
    if (rivets) {
        val w = (size.minDimension * 0.05f).coerceIn(2f, 5f)
        drawLine(edge, topLeft + Offset(ch + w * 3, w * 1.5f), topLeft + Offset(ch + w * 12, w * 1.5f), w * 0.6f, alpha = 0.8f)
        drawLine(edge, topLeft + Offset(size.width - ch - w * 12, size.height - w * 1.5f), topLeft + Offset(size.width - ch - w * 3, size.height - w * 1.5f), w * 0.6f, alpha = 0.8f)
    }
}
