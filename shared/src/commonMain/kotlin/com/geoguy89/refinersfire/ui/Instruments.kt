package com.geoguy89.refinersfire.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.ForgeSource
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.Ranks
import com.geoguy89.refinersfire.gfx.GlyphPaths
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.gfx.drawBrassPlate
import com.geoguy89.refinersfire.gfx.drawPiece
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun pieceName(p: Piece): String = when (p) {
    is Piece.Stone -> "${p.color.displayName} ${GlyphPaths.name(p.glyph)}"
    Piece.Cornerstone -> "Cornerstone"
    Piece.Hammer -> "Refiner's Hammer"
}

/** Foresight: the next stones in small wells, nearest first. Nothing is drawn in other modes. */
@Composable
fun NextStrip(vm: GameViewModel, modifier: Modifier = Modifier) {
    val next = vm.upcoming
    if (next.isEmpty()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Text("Next", style = bodyStyle(13.sp, Palette.goldLight, bold = true))
        next.forEachIndexed { i, piece ->
            Canvas(
                Modifier.size(if (i == 0) 44.dp else 36.dp)
                    .semantics { contentDescription = "Coming ${if (i == 0) "next" else "after that"}: ${pieceName(piece)}" },
            ) {
                val c = center
                val r = size.minDimension / 2f
                drawCircle(Brush.linearGradient(listOf(Palette.brassLight, Palette.brass, Palette.brassDark), c - Offset(r, r), c + Offset(r, r)), r, c)
                drawCircle(Brush.radialGradient(Palette.colors.well, c, r), r * 0.84f, c)
                drawPiece(piece, c, r * 1.3f, alpha = if (i == 0) 1f else 0.8f, time = vm.fx.now)
            }
        }
    }
}

/** The stone in hand, floating on a brass pedestal. */
@Composable
fun HandSlot(vm: GameViewModel, state: GameState, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = "Current stone: ${pieceName(state.current)}" },
        ) {
            val t = vm.fx.now
            val c = center
            val r = size.minDimension / 2f
            // Brass ring and velvet well.
            drawCircle(Color.Black, r * 0.98f, c + Offset(2f, 5f), alpha = 0.5f)
            drawCircle(Brush.linearGradient(listOf(Palette.brassLight, Palette.brass, Palette.brassDark), c - Offset(r, r), c + Offset(r, r)), r * 0.96f, c)
            drawCircle(Brush.radialGradient(Palette.colors.well, c - Offset(0f, r * 0.2f), r * 0.9f), r * 0.82f, c)
            drawCircle(Color.Black, r * 0.82f, c, alpha = 0.5f, style = Stroke(r * 0.05f))
            // Rotating ring of marks.
            for (k in 0 until 24) {
                val a = k * 2 * PI.toFloat() / 24 + t * 0.15f
                val d = Offset(cos(a), sin(a))
                drawLine(Palette.brassLight, c + d * r * 0.74f, c + d * r * (if (k % 3 == 0) 0.66f else 0.7f), r * 0.015f, alpha = 0.35f)
            }
            val arrive = ((t - vm.pieceArrivedAt) / 0.4f).coerceIn(0f, 1f)
            val ease = 1f + 2.7f * (arrive - 1f).let { it * it * it } + 1.7f * (arrive - 1f).let { it * it } // easeOutBack
            val bob = sin(t * 2.2f) * r * 0.03f
            val aura = when (val p = state.current) {
                is Piece.Stone -> Palette.pieceColor(p.color)
                Piece.Cornerstone -> Color.White
                Piece.Hammer -> Palette.ember
            }
            val pulse = 0.6f + 0.4f * sin(t * 3f)
            drawCircle(Brush.radialGradient(listOf(aura.copy(alpha = 0.35f * pulse), Color.Transparent), c, r * 0.7f), r * 0.7f, c)
            if (!state.gameOver) drawPiece(state.current, c + Offset(0f, bob), r * 1.25f * ease.coerceAtLeast(0f), glow = 1f - arrive * 0.6f, time = t)
        }
        if (showLabel) {
            FitText(pieceName(state.current), bodyStyle(13.sp, Palette.parchment, bold = true), Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Each cause of a forge level burns in its own colour: your discards in the theme's fire, a rival's stokes, a hint and
 * wrong guesses each different (Future's own fire is already blue and violet, so it gets other colours for those).
 * Returns the bright, middle and deep shades.
 */
fun forgeColors(source: ForgeSource): Triple<Color, Color, Color> {
    val future = Palette.theme == ThemeId.FUTURE
    return when (source) {
        ForgeSource.DISCARD -> Triple(Color(0xFFFFD27A), Palette.ember, Palette.lava)
        ForgeSource.STOKE -> if (future) Triple(Color(0xFFFFC2D8), Color(0xFFFF4D8D), Color(0xFF8A1040))
            else Triple(Color(0xFFB8E6FF), Color(0xFF4AA8FF), Color(0xFF1B3F9E))
        ForgeSource.HINT -> Triple(Color(0xFFD8FFD0), Color(0xFF5BE37A), Color(0xFF14632A))
        ForgeSource.MISS -> if (future) Triple(Color(0xFFFFE7B0), Color(0xFFFFB02E), Color(0xFF7A4A00))
            else Triple(Color(0xFFE6D2FF), Color(0xFFB07CFF), Color(0xFF4B1F99))
    }
}

/** A short name for what lit a forge level, for the legend and accessibility. */
fun forgeSourceName(source: ForgeSource) = when (source) {
    ForgeSource.DISCARD -> "discard"
    ForgeSource.STOKE -> "rival's stoke"
    ForgeSource.HINT -> "hint"
    ForgeSource.MISS -> "wrong guesses"
}

/** The forge: an egg-shaped crucible whose molten level rises with each discard. Tapping it discards. */
@Composable
fun Forge(vm: GameViewModel, state: GameState, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    val capacity = state.forgeCapacity
    val levels = state.forgeLevels
    val level by animateFloatAsState(state.forge.toFloat() / capacity, tween(700), label = "forge")
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .semantics {
                    val causes = levels.groupingBy { it }.eachCount().entries.joinToString { "${it.value} from ${forgeSourceName(it.key)}" }
                    contentDescription = "Forge ${state.forge} of $capacity" + (if (causes.isNotEmpty()) " ($causes)" else "") + ". Tap to discard."
                }
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { vm.discard() },
        ) {
            val t = vm.fx.now
            val w = size.width
            val h = size.height
            val egg = Rect(w * 0.1f, h * 0.04f, w * 0.9f, h * 0.9f)
            val inner = Rect(egg.left + w * 0.09f, egg.top + w * 0.09f, egg.right - w * 0.09f, egg.bottom - w * 0.09f)
            val danger = state.forge >= capacity
            // Base stand.
            drawRect(Brush.verticalGradient(listOf(Palette.brass, Palette.brassDark), h * 0.86f, h), Offset(w * 0.28f, h * 0.86f), Size(w * 0.44f, h * 0.12f))
            drawOval(Color.Black, egg.topLeft + Offset(2f, 5f), egg.size, alpha = 0.5f)
            drawOval(Brush.linearGradient(listOf(Palette.brassLight, Palette.brass, Palette.brassDark), egg.topLeft, egg.bottomRight), egg.topLeft, egg.size)
            if (danger) drawOval(Palette.lava, egg.topLeft, egg.size, alpha = 0.3f + 0.3f * sin(t * 6f), style = Stroke(w * 0.04f))
            drawOval(Palette.colors.crucible, inner.topLeft, inner.size)
            val clip = Path().apply { addOval(inner) }
            clipPath(clip) {
                // Hot glow at the bottom, always a little alive.
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55FF4010)), inner.top, inner.bottom), inner.topLeft, inner.size)
                // Molten metal.
                val surface = inner.bottom - inner.height * (0.06f + 0.9f * level)
                val lava = Path().apply {
                    moveTo(inner.left, inner.bottom)
                    val steps = 16
                    for (k in 0..steps) {
                        val x = inner.left + inner.width * k / steps
                        val y = surface + sin(k * 0.9f + t * 3f) * inner.height * 0.015f + sin(k * 2.1f - t * 2f) * inner.height * 0.01f
                        lineTo(x, y)
                    }
                    lineTo(inner.right, inner.bottom)
                    close()
                }
                drawPath(lava, Brush.verticalGradient(listOf(Color(0xFFFFD27A), Palette.ember, Palette.lava, Color(0xFF6A0E06)), surface, inner.bottom))
                // Each level that isn't one of your own discards burns in its cause's colour, in its own band.
                fun levelY(f: Float) = inner.bottom - inner.height * (0.06f + 0.9f * f / capacity)
                levels.forEachIndexed { k, source ->
                    if (source == ForgeSource.DISCARD) return@forEachIndexed
                    val (bright, main, deep) = forgeColors(source)
                    val bandTop = if (k == levels.lastIndex) surface - inner.height * 0.04f else levelY(k + 1f)
                    val bandBottom = if (k == 0) inner.bottom else levelY(k.toFloat())
                    clipRect(top = bandTop, bottom = bandBottom) {
                        drawPath(lava, Brush.verticalGradient(listOf(bright, main, deep, Color.Black.copy(alpha = 0.6f)), surface, inner.bottom))
                    }
                }
                // Crusty dark patches drifting on the melt.
                for (k in 0 until 7) {
                    val px = inner.left + inner.width * ((k * 0.37f + t * 0.03f * (1 + k % 3)) % 1f)
                    val py = surface + inner.height * (0.08f + 0.12f * ((k * 0.61f) % 1f))
                    if (py < inner.bottom) drawCircle(Color(0xFF5A1206), inner.width * (0.05f + 0.03f * (k % 3)), Offset(px, py), alpha = 0.55f)
                }
                // Rising embers.
                val emberCount = 6 + (level * 14).toInt()
                for (k in 0 until emberCount) {
                    val speed = 0.25f + (k % 5) * 0.07f
                    val prog = (t * speed + k * 0.173f) % 1f
                    val ex = inner.left + inner.width * (0.2f + 0.6f * ((k * 0.47f) % 1f)) + sin(t * 2f + k) * inner.width * 0.05f
                    val ey = surface - prog * inner.height * 0.6f
                    drawCircle(Color(0xFFFFB050), inner.width * 0.018f, Offset(ex, ey), alpha = (1f - prog) * 0.9f)
                }
                // Flare when a stone is thrown in.
                val flare = (t - vm.forgeFlareAt) / 0.9f
                if (flare in 0f..1f) {
                    drawRect(Brush.verticalGradient(listOf(Color(0xFFFFF0A0), Palette.ember, Color.Transparent), surface - inner.height * 0.5f * (1 - flare), inner.bottom), inner.topLeft, inner.size, alpha = 1f - flare)
                }
                // Steam when the forge is emptied by a cleared line.
                val cool = (t - vm.forgeCoolAt) / 1.3f
                if (cool in 0f..1f) {
                    for (k in 0 until 6) {
                        val sx = inner.left + inner.width * (0.2f + 0.12f * k)
                        val sy = inner.bottom - inner.height * (0.2f + cool * 0.8f) - k % 2 * inner.height * 0.05f
                        drawCircle(Color.White, inner.width * (0.08f + cool * 0.12f), Offset(sx, sy), alpha = 0.35f * (1f - cool))
                    }
                }
                // Glass-like highlight.
                drawOval(Color.White, inner.topLeft + Offset(inner.width * 0.18f, inner.height * 0.06f), Size(inner.width * 0.3f, inner.height * 0.16f), alpha = 0.08f)
            }
            drawOval(Color.Black, inner.topLeft, inner.size, alpha = 0.7f, style = Stroke(w * 0.02f))
            // Level lamps.
            for (k in 0 until capacity) {
                // Iron Forge's single lamp sits in the middle; three lamps climb the side.
                val ly = if (capacity == 1) egg.bottom - egg.height * 0.5f else egg.bottom - egg.height * (0.25f + 0.25f * k)
                val lc = Offset(egg.right + w * 0.02f, ly)
                val on = state.forge > k
                val (_, lamp, deep) = forgeColors(levels.getOrElse(k) { ForgeSource.DISCARD })
                drawCircle(Color(0xFF1C130C), w * 0.055f, lc)
                if (on) {
                    drawCircle(lamp, w * 0.1f, lc, alpha = 0.25f)
                    drawCircle(Brush.radialGradient(listOf(Color.White, lamp, deep), lc - Offset(w * 0.015f, w * 0.015f), w * 0.06f), w * 0.042f, lc)
                } else {
                    drawCircle(Color(0xFF3A2A1C), w * 0.038f, lc)
                }
            }
        }
        if (showLabel) {
            FitText(
                if (state.forge >= capacity) "Forge full!" else "Forge ${state.forge}/$capacity",
                bodyStyle(13.sp, if (state.forge >= capacity) Palette.ember else Palette.parchment, bold = true),
            )
        }
    }
}

/** Brass score plate with glowing LED numerals, like the original's red "SCORE" gauge. */
@Composable
fun ScorePlaque(state: GameState, modifier: Modifier = Modifier, digitsSize: TextUnit = 30.sp, showRank: Boolean = true) {
    val animated by animateFloatAsState(state.score.toFloat(), tween(600), label = "score")
    Box(
        modifier
            .drawBehind {
                drawBrassPlate(Offset.Zero, size, 14.dp.toPx(), rivets = true)
                val inset = 10.dp.toPx()
                drawRoundRect(Palette.colors.screen, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text("SCORE", style = bodyStyle(11.sp, Palette.ledRed, bold = true))
            Text(
                animated.toLong().toString(),
                style = bodyStyle(digitsSize, Palette.ledOrange, bold = true).copy(
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                    shadow = Shadow(Palette.ember, Offset.Zero, 14f),
                ),
                maxLines = 1,
            )
            if (showRank) {
                Text(state.rank, style = bodyStyle(12.sp, Palette.goldLight, bold = true), maxLines = 1)
                val next = Ranks.nextRank(state.score)
                val streak = if (state.streak > 0) " · Streak ${state.streak}" else ""
                if (next != null) Text("Next at ${next.first}$streak", style = bodyStyle(10.sp, Palette.parchment.copy(alpha = 0.7f)), maxLines = 1)
                else if (streak.isNotEmpty()) Text("Streak ${state.streak}", style = bodyStyle(10.sp, Palette.parchment.copy(alpha = 0.7f)), maxLines = 1)
            }
        }
    }
}

/** The "Refiner's Fire" logotype with a slow gold shimmer. */
@Composable
fun Logo(vm: GameViewModel, size: TextUnit, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val t = vm.fx.now
        val shimmer = 0.5f + 0.5f * sin(t * 1.3f)
        FitText(
            "Refiner's Fire",
            titleStyle(size, lerp(Palette.gold, Palette.goldLight, shimmer)).copy(
                shadow = Shadow(lerp(Color.Black, Palette.ember, 0.3f * shimmer), Offset(2f, 4f), 10f),
            ),
        )
    }
}

/** Little helper so layouts can make a tappable brass icon button with a glyph label. */
@Composable
fun SmallBrass(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    BrassButton(label, onClick, modifier, enabled = enabled, fontSize = 14.sp, minHeight = 38.dp)
