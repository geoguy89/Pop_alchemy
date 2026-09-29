package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.ROWS
import com.geoguy89.refinersfire.gfx.BoardFx
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.gfx.drawGoldTile
import com.geoguy89.refinersfire.gfx.drawLeadTile
import com.geoguy89.refinersfire.gfx.drawParticles
import com.geoguy89.refinersfire.gfx.drawPiece
import com.geoguy89.refinersfire.gfx.Reliefs
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Which relief is currently recorded in the board's plate layer. */
private class PlateKey { var key: Any? = null }

/** Frame thickness, in cells. */
private const val FRAME = 0.42f

class BoardGeom(val origin: Offset, val cell: Float) {
    val frame get() = cell * FRAME
    fun cellAt(p: Offset): Int {
        val c = ((p.x - origin.x) / cell).toInt()
        val r = ((p.y - origin.y) / cell).toInt()
        if (p.x < origin.x || p.y < origin.y || c !in 0 until COLS || r !in 0 until ROWS) return -1
        return GameEngine.index(r, c)
    }
    fun topLeft(i: Int) = origin + Offset((i % COLS) * cell, (i / COLS) * cell)
    fun center(i: Int) = topLeft(i) + Offset(cell / 2, cell / 2)

    companion object {
        fun of(size: Size): BoardGeom {
            val cell = min(size.width / (COLS + 2 * FRAME), size.height / (ROWS + 2 * FRAME))
            return BoardGeom(Offset((size.width - cell * COLS) / 2f, (size.height - cell * ROWS) / 2f), cell)
        }

        /** Width / height of the board including its frame. */
        const val ASPECT = (COLS + 2 * FRAME) / (ROWS + 2 * FRAME)
    }
}

@Composable
fun BoardView(vm: GameViewModel, modifier: Modifier = Modifier) {
    val tm = rememberTextMeasurer()
    val geomHolder = remember { arrayOf(BoardGeom(Offset.Zero, 1f)) }
    val reliefLayer = rememberGraphicsLayer()
    val plate = remember { PlateKey() }
    Canvas(
        modifier
            .semantics { contentDescription = "Refiner's Fire board" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val geom = geomHolder[0]
                    if (currentEvent.buttons.isSecondaryPressed) {
                        // Right-click anywhere on the board discards, as on the PC original.
                        vm.discard()
                        return@awaitEachGesture
                    }
                    vm.hoverIndex = geom.cellAt(down.position)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val change = ev.changes.firstOrNull { it.id == down.id } ?: break
                        if (ev.type == PointerEventType.Release || !change.pressed) {
                            val target = geom.cellAt(change.position)
                            vm.hoverIndex = -1
                            if (target >= 0) vm.tapCell(target)
                            break
                        }
                        vm.hoverIndex = geom.cellAt(change.position)
                    }
                    vm.hoverIndex = -1
                }
            },
    ) {
        val geom = BoardGeom.of(size)
        geomHolder[0] = geom
        val state = vm.displayState ?: return@Canvas
        val fx = vm.fx
        val t = fx.now
        translate(fx.shakeOffset().x * geom.cell, fx.shakeOffset().y * geom.cell) {
            drawFrame(geom, state, t, tm)
            drawTiles(geom, state, fx, t)
            // The engraved plate changes with every board, like the original. It is static, so it is
            // recorded once per board and size into a layer instead of being redrawn every frame.
            val design = Reliefs.forBoard(state.board)
            val plateSize = IntSize((geom.cell * COLS).toInt().coerceAtLeast(1), (geom.cell * ROWS).toInt().coerceAtLeast(1))
            val key = Triple(design, plateSize, Fonts.body)
            if (plate.key != key) {
                reliefLayer.record(this, layoutDirection, plateSize) {
                    scale(geom.cell / 100f, geom.cell / 100f, Offset.Zero) {
                        with(Reliefs) { drawRelief(design, tm, Fonts.body, alpha = 0.6f) }
                    }
                }
                plate.key = key
            }
            translate(geom.origin.x, geom.origin.y) {
                clipRect(0f, 0f, plateSize.width.toFloat(), plateSize.height.toFloat()) { drawLayer(reliefLayer) }
            }
            if (vm.boardInteractive && vm.hintCells.isNotEmpty()) drawHints(geom, vm.hintCells, t)
            drawPieces(geom, state, fx, t)
            drawGhosts(geom, fx, t)
            if (vm.boardInteractive) drawHover(geom, vm, state, t)
            drawInvalid(geom, fx, t)
            drawParticles(fx, geom.origin, geom.cell)
            drawFloatTexts(geom, fx, t, tm)
            val flash = t - fx.boardFlashStart
            if (flash in 0f..1.2f) {
                drawRect(Color.White, geom.origin, Size(geom.cell * COLS, geom.cell * ROWS), alpha = 0.7f * (1f - flash / 1.2f))
            }
            if (state.gameOver) drawRect(Color.Black, geom.origin, Size(geom.cell * COLS, geom.cell * ROWS), alpha = 0.45f)
        }
    }
}

private fun DrawScope.drawFrame(g: BoardGeom, s: GameState, t: Float, tm: TextMeasurer) {
    val f = g.frame
    val tl = g.origin - Offset(f, f)
    val sz = Size(g.cell * COLS + 2 * f, g.cell * ROWS + 2 * f)
    drawRoundRect(Color.Black, tl + Offset(3f, 6f), sz, CornerRadius(f * 0.6f), alpha = 0.6f)
    drawRoundRect(
        Brush.linearGradient(Palette.colors.frame, tl, tl + Offset(sz.width, sz.height)),
        tl, sz, CornerRadius(f * 0.6f),
    )
    drawRoundRect(Color.White, tl, sz, CornerRadius(f * 0.6f), alpha = 0.25f, style = Stroke(2f))
    when (Palette.theme) {
        ThemeId.TEMPLE -> {
            // Iron corner brackets with square nails.
            val q = f * 0.16f
            for (corner in listOf(tl, tl + Offset(sz.width, 0f), tl + Offset(0f, sz.height), tl + Offset(sz.width, sz.height))) {
                val dir = Offset(if (corner.x > g.origin.x) -1f else 1f, if (corner.y > g.origin.y) -1f else 1f)
                val arm = f * 1.6f
                drawLine(Color(0xFF1A1714), corner + Offset(dir.x * f * 0.4f, dir.y * f * 0.4f), corner + Offset(dir.x * arm, dir.y * f * 0.4f), f * 0.34f)
                drawLine(Color(0xFF1A1714), corner + Offset(dir.x * f * 0.4f, dir.y * f * 0.4f), corner + Offset(dir.x * f * 0.4f, dir.y * arm), f * 0.34f)
                drawRect(Color(0xFF6A645A), corner + Offset(dir.x * f * 0.4f, dir.y * f * 0.4f) - Offset(q / 2, q / 2), Size(q, q))
            }
        }
        ThemeId.FUTURE -> {
            val pulse = 0.55f + 0.25f * sin(t * 1.6f)
            drawRoundRect(Palette.brass, tl, sz, CornerRadius(f * 0.6f), alpha = 0.18f * pulse, style = Stroke(f * 0.35f))
            drawRoundRect(Palette.brass, tl, sz, CornerRadius(f * 0.6f), alpha = pulse, style = Stroke(2f))
        }
        ThemeId.MODERN -> Unit
    }
    // Carved inner lip.
    val lip = f * 0.18f
    drawRect(Color.Black, g.origin - Offset(lip, lip), Size(g.cell * COLS + 2 * lip, g.cell * ROWS + 2 * lip), alpha = 0.55f)
    drawRect(Palette.brassDark, g.origin - Offset(lip * 0.5f, lip * 0.5f), Size(g.cell * COLS + lip, g.cell * ROWS + lip), style = Stroke(lip * 0.6f))
    // Gem sockets along the top: one lights up for every board completed this game.
    val sockets = 8
    val lit = s.boardsCleared.coerceAtMost(sockets)
    val span = g.cell * COLS * 0.8f
    val y = tl.y + f / 2f
    val r = f * 0.27f
    val modern = Palette.theme == ThemeId.MODERN
    val frameCols = Palette.colors.frame
    val socketBack = if (modern) Color(0xFF2A221A) else frameCols[2]
    val socketRing = if (modern) listOf(Color(0xFF9A8B74), Color(0xFF4A3F31)) else listOf(frameCols[0], frameCols[2])
    for (i in 0 until sockets) {
        val x = g.origin.x + g.cell * COLS * 0.1f + span * i / (sockets - 1)
        val c = Offset(x, y)
        drawCircle(socketBack, r * 1.25f, c)
        drawCircle(Brush.radialGradient(socketRing, c - Offset(r / 3, r / 3), r * 1.3f), r * 1.1f, c, style = Stroke(r * 0.3f))
        if (i < lit) {
            val gem = Palette.gems[i % Palette.gems.size]
            val pulse = 0.75f + 0.25f * sin(t * 2.5f + i)
            drawCircle(gem, r * 2.2f, c, alpha = 0.18f * pulse)
            drawCircle(Brush.radialGradient(listOf(Color.White, gem, lerp(gem, Color.Black, 0.5f)), c - Offset(r / 3, r / 3), r * 1.2f), r, c)
        } else {
            drawCircle(Color(0xFF15100C), r * 0.9f, c)
        }
    }
    // "Board N" plaque on the bottom edge, with the hourglass bar in Time Trial.
    val by = g.origin.y + g.cell * ROWS + f / 2f
    if (s.mode == GameMode.TIME_TRIAL) {
        val limit = GameEngine.pieceTimeLimit(s.mode, s.board).toFloat()
        val frac = (s.pieceTimeLeftMillis / limit).coerceIn(0f, 1f)
        val barH = f * 0.3f
        val bx = g.origin.x
        val bw = g.cell * COLS
        drawRoundRect(Color(0xFF1A120C), Offset(bx, by - barH / 2), Size(bw, barH), CornerRadius(barH / 2))
        val col = lerp(Palette.lava, Palette.gold, ((frac - 0.15f) / 0.5f).coerceIn(0f, 1f))
        val w = bw * frac
        drawRoundRect(Brush.verticalGradient(listOf(lerp(col, Color.White, 0.4f), col, lerp(col, Color.Black, 0.3f)), by - barH / 2, by + barH / 2), Offset(bx + (bw - w) / 2, by - barH / 2), Size(w, barH), CornerRadius(barH / 2))
    }
    val label = "Board ${s.board}"
    val style = TextStyle(fontFamily = Fonts.body, fontWeight = FontWeight.Bold, fontSize = (f * 0.42f / density / fontScale).sp, color = Palette.parchment)
    val m = tm.measure(label, style)
    val pw = m.size.width + f * 1.2f
    val ph = f * 0.72f
    val ptl = Offset(g.origin.x + g.cell * COLS / 2f - pw / 2, by - ph / 2)
    drawRoundRect(Color.Black, ptl + Offset(1f, 3f), Size(pw, ph), CornerRadius(ph / 2), alpha = 0.5f)
    val plaque = if (modern) listOf(Color(0xFF4A3A2A), Color(0xFF221810)) else Palette.colors.panel
    drawRoundRect(Brush.verticalGradient(plaque, ptl.y, ptl.y + ph), ptl, Size(pw, ph), CornerRadius(ph / 2))
    drawRoundRect(Palette.brass, ptl, Size(pw, ph), CornerRadius(ph / 2), style = Stroke(1.5f))
    drawText(m, topLeft = Offset(ptl.x + (pw - m.size.width) / 2, by - m.size.height / 2))
}

private fun DrawScope.drawTiles(g: BoardGeom, s: GameState, fx: BoardFx, t: Float) {
    val c = g.cell
    for (i in 0 until ROWS * COLS) {
        val tl = g.topLeft(i)
        val phase = ((i % COLS) + (i / COLS)) * 0.08f
        val revert = t - fx.revertStart[i]
        val trans = t - fx.transmuteStart[i]
        if (s.gold[i]) {
            when {
                trans < 0f && fx.transmuteStart[i] > t - 5f -> drawLeadTile(tl, c, i) // Waiting for the wave to arrive.
                trans in 0f..0.6f -> {
                    val p = trans / 0.6f
                    drawLeadTile(tl, c, i)
                    drawGoldTile(tl, c, t, phase, alpha = p)
                    drawRect(Color.White, tl, Size(c, c), alpha = 0.8f * sin(p * PI.toFloat()))
                }
                else -> drawGoldTile(tl, c, t, phase)
            }
        } else {
            drawLeadTile(tl, c, i)
            if (revert < 0f && fx.revertStart[i] > t - 5f) drawGoldTile(tl, c, t, phase)
            else if (revert in 0f..0.5f) drawGoldTile(tl, c, t, phase, alpha = 1f - revert / 0.5f)
        }
    }
}

/** Squares revealed by a paid hint: a bright pulsing ring. */
private fun DrawScope.drawHints(g: BoardGeom, cells: Set<Int>, t: Float) {
    val a = 0.55f + 0.35f * sin(t * 5f)
    for (i in cells) {
        val tl = g.topLeft(i)
        val inset = g.cell * 0.07f
        drawRoundRect(Palette.hint, tl + Offset(inset, inset), Size(g.cell - 2 * inset, g.cell - 2 * inset), CornerRadius(g.cell * 0.12f), alpha = 0.18f * a)
        drawRoundRect(Palette.hint, tl + Offset(inset, inset), Size(g.cell - 2 * inset, g.cell - 2 * inset), CornerRadius(g.cell * 0.12f), alpha = a, style = Stroke(g.cell * 0.06f))
    }
}

private fun DrawScope.drawPieces(g: BoardGeom, s: GameState, fx: BoardFx, t: Float) {
    for (i in 0 until ROWS * COLS) {
        val piece = s.cells[i] ?: continue
        val p = ((t - fx.popStart[i]) / 0.35f).coerceIn(0f, 1f)
        val scale = if (p < 1f) 1f + 0.35f * sin(p * PI.toFloat()) * (1f - p) + (1f - p) * 0.1f else 1f
        val glow = if (p < 1f) 1f - p else 0f
        drawPiece(piece, g.center(i), g.cell * scale, alpha = (0.4f + p * 2f).coerceAtMost(1f), glow = glow, time = t)
    }
}

private fun DrawScope.drawGhosts(g: BoardGeom, fx: BoardFx, t: Float) {
    for (gh in fx.ghosts) {
        val local = t - gh.start - gh.delay
        if (local < 0f) {
            drawPiece(gh.piece, g.center(gh.index), g.cell, time = t)
            continue
        }
        val p = (local / 0.6f).coerceIn(0f, 1f)
        if (gh.dark) {
            drawPiece(gh.piece, g.center(gh.index), g.cell * (1f - 0.6f * p), alpha = 1f - p, time = t)
        } else {
            drawPiece(gh.piece, g.center(gh.index), g.cell * (1f + 0.5f * p), alpha = 1f - p, glow = 1f, time = t)
        }
    }
}

private fun DrawScope.drawHover(g: BoardGeom, vm: GameViewModel, s: GameState, t: Float) {
    val i = vm.hoverIndex
    if (i < 0) return
    // Neutral cursor: it shows where the stone will land, never whether the square is legal.
    val tl = g.topLeft(i)
    drawRect(Palette.parchment, tl, Size(g.cell, g.cell), alpha = 0.10f)
    drawRect(Palette.parchment, tl, Size(g.cell, g.cell), alpha = 0.6f, style = Stroke(g.cell * 0.04f))
    if (s.current is Piece.Hammer) {
        if (s.cells[i] != null) drawPiece(Piece.Hammer, g.center(i), g.cell * 0.8f, alpha = 0.6f, time = t)
    } else if (s.cells[i] == null) {
        drawPiece(s.current, g.center(i), g.cell, alpha = 0.55f, time = t)
    }
}

private fun DrawScope.drawInvalid(g: BoardGeom, fx: BoardFx, t: Float) {
    val i = fx.invalidIndex
    if (i < 0) return
    val p = (t - fx.invalidStart) / 0.45f
    if (p !in 0f..1f) return
    val dx = sin(p * 40f) * g.cell * 0.06f * (1f - p)
    val tl = g.topLeft(i) + Offset(dx, 0f)
    drawRect(Palette.invalid, tl, Size(g.cell, g.cell), alpha = 0.45f * (1f - p))
    drawRect(Palette.invalid, tl, Size(g.cell, g.cell), alpha = 1f - p, style = Stroke(g.cell * 0.07f))
}

private fun DrawScope.drawFloatTexts(g: BoardGeom, fx: BoardFx, t: Float, tm: TextMeasurer) {
    for (ft in fx.texts) {
        val p = ((t - ft.start) / ft.duration).coerceIn(0f, 1f)
        val px = g.cell * ft.height * (1f + 0.2f * (1f - p))
        val style = TextStyle(
            fontFamily = Fonts.body, fontWeight = FontWeight.Bold, fontSize = (px / density / fontScale).sp, color = ft.color,
            shadow = androidx.compose.ui.graphics.Shadow(Color.Black, Offset(2f, 3f), 4f),
        )
        val m = tm.measure(ft.text, style)
        val pos = g.origin + Offset(ft.x * g.cell, (ft.y - p * ft.rise) * g.cell) - Offset(m.size.width / 2f, m.size.height / 2f)
        drawText(m, topLeft = pos, alpha = (1f - p * p).coerceIn(0f, 1f))
    }
}
