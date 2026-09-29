package com.geoguy89.refinersfire.gfx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.ROWS
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class ParticleKind { SPARK, GOLD_DUST, SMOKE, STAR }

/** Positions and velocities are in board cell units, so effects survive layout changes (fold / unfold). */
class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    var life: Float, val maxLife: Float,
    val size: Float, val color: Color, val kind: ParticleKind,
    val gravity: Float = 0f,
)

class FloatText(
    val text: String, val x: Float, val y: Float, val start: Float, val color: Color, val big: Boolean,
    val duration: Float = 1.4f,
    /** Text height in cells. */
    val height: Float = if (big) 0.55f else 0.34f,
    /** How far it drifts up over its life, in cells. */
    val rise: Float = 0.9f,
)

/** A piece that has left the board but is still animating away (line clear, hammer, board reset). */
class Ghost(val piece: Piece, val index: Int, val start: Float, val delay: Float, val dark: Boolean)

/**
 * All transient animation state for the board. Game logic never reads it; the view model feeds it events and
 * the board canvas reads it every frame.
 */
class BoardFx {
    /** Seconds since the effects clock started. Reading it in draw code subscribes to every frame. */
    var now by mutableFloatStateOf(0f)
        private set

    val particles = ArrayList<Particle>()
    val texts = ArrayList<FloatText>()
    val ghosts = ArrayList<Ghost>()
    val popStart = FloatArray(ROWS * COLS) { -10f }
    val transmuteStart = FloatArray(ROWS * COLS) { -10f }
    val revertStart = FloatArray(ROWS * COLS) { -10f }
    var invalidIndex = -1
    var invalidStart = -10f
    var shakeStart = -10f
    var shakeAmp = 0f
    var boardFlashStart = -10f
    private val rnd = Random(7)

    fun advance(t: Float) {
        val dt = (t - now).coerceIn(0f, 0.05f)
        now = t
        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vy += p.gravity * dt
            if (p.kind == ParticleKind.SMOKE) { p.vx *= 0.97f; p.vy *= 0.97f }
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        texts.removeAll { now - it.start > it.duration }
        ghosts.removeAll { now - it.start - it.delay > 0.8f }
    }

    private fun cx(index: Int) = index % COLS + 0.5f
    private fun cy(index: Int) = index / COLS + 0.5f

    fun burst(index: Int, color: Color, count: Int, speed: Float, kind: ParticleKind = ParticleKind.SPARK) {
        repeat(count) {
            val a = rnd.nextFloat() * 2 * PI.toFloat()
            val v = speed * (0.3f + rnd.nextFloat())
            val life = 0.4f + rnd.nextFloat() * 0.6f
            particles += Particle(
                cx(index), cy(index), cos(a) * v, sin(a) * v - speed * 0.3f, life, life,
                0.04f + rnd.nextFloat() * 0.06f, color, kind, gravity = if (kind == ParticleKind.SMOKE) -0.6f else 3.5f,
            )
        }
    }

    fun placed(index: Int, piece: Piece, points: Long) {
        popStart[index] = now
        val color = when (piece) {
            is Piece.Stone -> Palette.pieceColor(piece.color)
            else -> Color(0xFFE0E4E8)
        }
        burst(index, color, 14, 2.2f)
        burst(index, Color.White, 5, 1.5f, ParticleKind.STAR)
        if (points > 0) texts += FloatText("+$points", cx(index), cy(index) - 0.2f, now, Palette.goldLight, big = false)
    }

    fun invalid(index: Int) {
        invalidIndex = index
        invalidStart = now
    }

    fun linesCleared(originIndex: Int, removed: Map<Int, Piece>, newlyGold: List<Int>, points: Long, lines: Int) {
        val ox = cx(originIndex); val oy = cy(originIndex)
        for ((idx, piece) in removed) {
            val d = kotlin.math.hypot(cx(idx) - ox, cy(idx) - oy)
            ghosts += Ghost(piece, idx, now, d * 0.045f, dark = false)
            repeat(5) {
                val a = rnd.nextFloat() * 2 * PI.toFloat()
                val v = 1.2f + rnd.nextFloat() * 2f
                val life = 0.7f + rnd.nextFloat() * 0.7f
                particles += Particle(
                    cx(idx), cy(idx), cos(a) * v, sin(a) * v - 1.5f, life + d * 0.045f, life + d * 0.045f,
                    0.05f + rnd.nextFloat() * 0.05f, if (it % 2 == 0) Palette.goldLight else Palette.gold,
                    ParticleKind.GOLD_DUST, gravity = 2.2f,
                )
            }
        }
        for (idx in newlyGold) {
            val d = kotlin.math.hypot(cx(idx) - ox, cy(idx) - oy)
            transmuteStart[idx] = now + d * 0.045f
        }
        texts += FloatText("+$points", ox, oy - 0.4f, now, Color.White, big = true)
        if (lines > 1) { shakeStart = now; shakeAmp = 0.06f * lines }
    }

    /** A message across the middle of the board (penalties, streaks, bonuses). */
    fun banner(text: String, color: Color, height: Float = 0.5f, y: Float = ROWS / 2f, duration: Float = 1.8f) {
        texts += FloatText(text, COLS / 2f, y, now, color, big = true, duration = duration, height = height, rise = 0.5f)
    }

    /** Fireworks along a cleared line that matched on symbol ([perfect]: symbol and colour). */
    fun lineBonus(row: Int?, col: Int?, perfect: Boolean, color: Color, points: Long, bannerY: Float) {
        val cells = if (row != null) (0 until COLS).map { row * COLS + it } else (0 until ROWS).map { it * COLS + col!! }
        for (i in cells) {
            burst(i, color, if (perfect) 26 else 12, if (perfect) 4.2f else 2.6f)
            burst(i, Color.White, if (perfect) 10 else 4, 3f, ParticleKind.STAR)
        }
        if (perfect) {
            boardFlashStart = now
            shakeStart = now; shakeAmp = 0.22f
            banner("PERFECT!", androidx.compose.ui.graphics.lerp(color, Color.White, 0.25f), height = 0.95f, y = bannerY, duration = 2.6f)
            banner("Pure gold +$points", Palette.goldLight, height = 0.45f, y = bannerY + 1f, duration = 2.6f)
        } else {
            shakeStart = now; shakeAmp = maxOf(shakeAmp, 0.08f)
            banner("Symbol line! +$points", color, height = 0.46f, y = bannerY, duration = 2f)
        }
    }

    fun hammer(index: Int, removed: Piece) {
        ghosts += Ghost(removed, index, now, 0f, dark = true)
        burst(index, Color(0xFF8A847A), 14, 1.2f, ParticleKind.SMOKE)
        burst(index, Palette.ember, 16, 2.6f)
        burst(index, Color.White, 6, 2f, ParticleKind.STAR)
        shakeStart = now; shakeAmp = 0.05f
    }

    fun boardComplete() {
        boardFlashStart = now
        for (i in 0 until ROWS * COLS) {
            if (rnd.nextFloat() < 0.5f) {
                val life = 1f + rnd.nextFloat()
                particles += Particle(
                    cx(i), cy(i), (rnd.nextFloat() - 0.5f) * 2f, -1f - rnd.nextFloat() * 2f, life, life,
                    0.05f + rnd.nextFloat() * 0.07f, if (i % 3 == 0) Color.White else Palette.goldLight,
                    ParticleKind.STAR, gravity = 1f,
                )
            }
        }
    }

    /** The board turns back to lead in a wave from the centre, and leftover stones fade away. */
    fun boardReset(leftovers: Map<Int, Piece>) {
        for (i in 0 until ROWS * COLS) {
            val d = kotlin.math.hypot(cx(i) - COLS / 2f, cy(i) - ROWS / 2f)
            revertStart[i] = now + d * 0.06f
            transmuteStart[i] = -10f
            popStart[i] = -10f
        }
        for ((idx, p) in leftovers) ghosts += Ghost(p, idx, now, 0f, dark = false)
    }

    fun reset() {
        particles.clear(); texts.clear(); ghosts.clear()
        popStart.fill(-10f); transmuteStart.fill(-10f); revertStart.fill(-10f)
        invalidIndex = -1
    }

    fun shakeOffset(): Offset {
        val t = now - shakeStart
        if (t > 0.4f || t < 0f) return Offset.Zero
        val a = shakeAmp * (1f - t / 0.4f)
        return Offset(sin(t * 90f) * a, cos(t * 70f) * a)
    }
}

/** Draw particles and floating text; [cell] is the size of one board cell in pixels. */
fun DrawScope.drawParticles(fx: BoardFx, origin: Offset, cell: Float) {
    for (p in fx.particles) {
        val f = (p.life / p.maxLife).coerceIn(0f, 1f)
        val pos = origin + Offset(p.x * cell, p.y * cell)
        val r = p.size * cell
        when (p.kind) {
            ParticleKind.SPARK, ParticleKind.GOLD_DUST -> {
                drawCircle(p.color, r * 2.2f, pos, alpha = 0.18f * f)
                drawCircle(p.color, r, pos, alpha = f)
            }
            ParticleKind.STAR -> {
                val l = r * 2.4f * f
                drawLine(p.color, pos - Offset(l, 0f), pos + Offset(l, 0f), r * 0.35f, alpha = f)
                drawLine(p.color, pos - Offset(0f, l), pos + Offset(0f, l), r * 0.35f, alpha = f)
                drawCircle(Color.White, r * 0.6f, pos, alpha = f)
            }
            ParticleKind.SMOKE -> drawCircle(p.color, r * (3f - 1.5f * f), pos, alpha = 0.45f * f)
        }
    }
}
