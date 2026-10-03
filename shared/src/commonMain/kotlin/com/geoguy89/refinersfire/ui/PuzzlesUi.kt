package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Puzzles
import com.geoguy89.refinersfire.gfx.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A five-pointed star, filled when earned. */
private fun DrawScope.star(center: Offset, r: Float, filled: Boolean) {
    val p = Path()
    for (k in 0 until 10) {
        val a = (-PI / 2 + k * PI / 5).toFloat()
        val rad = if (k % 2 == 0) r else r * 0.45f
        val x = center.x + cos(a) * rad
        val y = center.y + sin(a) * rad
        if (k == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    if (filled) {
        drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFF1A8), Color(0xFFF2C14E), Color(0xFFB07A1C)), center.y - r, center.y + r))
        drawPath(p, Color(0xFF6E4E1C), style = Stroke(r * 0.12f))
    } else {
        drawPath(p, Color.Black.copy(alpha = 0.35f))
        drawPath(p, Palette.brass.copy(alpha = 0.5f), style = Stroke(r * 0.1f))
    }
}

/** [earned] of three stars. */
@Composable
fun StarRow(earned: Int, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.width(size * 3.2f).height(size).semantics { contentDescription = "$earned of 3 stars" }) {
        val r = this.size.height / 2f
        for (k in 0 until 3) star(Offset(r + k * (this.size.width - 2 * r) / 2f, r), r * 0.95f, k < earned)
    }
}

/** The puzzle book: sixty puzzles, opening a few at a time, with the stars earned on each. */
@Composable
fun PuzzlesPanel(vm: GameViewModel) {
    GamePanel("Puzzles", vm::pop, maxWidth = 620.dp) {
        Text(
            "Each puzzle sets a board and a fixed run of stones. Clear the lines it asks for before the stones run out. " +
                "Three stars for a solve with no stone melted and no hint.",
            style = bodyStyle(13.sp, dim()), textAlign = TextAlign.Center,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarRow(3, 18.dp)
            Text("${vm.totalPuzzleStars} of ${Puzzles.COUNT * 3}", style = bodyStyle(15.sp, Palette.goldLight, bold = true))
        }
        for (row in (1..Puzzles.COUNT).chunked(5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                for (id in row) PuzzleTile(vm, id, Modifier.weight(1f))
            }
        }
        BrassButton("Close", vm::pop, Modifier.fillMaxWidth(), dark = true)
    }
}

@Composable
private fun PuzzleTile(vm: GameViewModel, id: Int, modifier: Modifier) {
    val open = vm.puzzleUnlocked(id)
    val stars = vm.puzzleStars[id] ?: 0
    Box(
        modifier.aspectRatio(0.9f)
            .then(if (open) Modifier.clickable { vm.startPuzzle(id) } else Modifier)
            .semantics { contentDescription = if (open) "Puzzle $id, ${Puzzles.names[id - 1]}, $stars stars" else "Puzzle $id, locked" }
            .drawBehind {
                val r = CornerRadius(10.dp.toPx())
                val face = when {
                    !open -> listOf(Color(0xFF2A2018), Color(0xFF15100B))
                    stars > 0 -> listOf(Palette.brassLight, Palette.brass, Palette.brassDark)
                    else -> listOf(Color(0xFF5A4630), Color(0xFF33261A))
                }
                drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(1f, 3f), size = size, cornerRadius = r)
                drawRoundRect(Brush.verticalGradient(face), cornerRadius = r)
                drawRoundRect(Color.White.copy(alpha = if (open) 0.35f else 0.1f), cornerRadius = r, style = Stroke(1.2f))
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$id",
                style = bodyStyle(18.sp, if (stars > 0) Palette.ink else if (open) Palette.goldLight else Palette.parchment.copy(alpha = 0.3f), bold = true),
            )
            if (open) StarRow(stars, 11.dp)
        }
    }
}

/** A puzzle has ended. */
@Composable
fun PuzzleDonePanel(vm: GameViewModel, o: Overlay.PuzzleDone) {
    GamePanel(if (o.solved) "Puzzle Solved" else "Not This Time", null) {
        Text("${o.id}. ${Puzzles.names[o.id - 1]}", style = bodyStyle(16.sp, Palette.goldLight, bold = true), textAlign = TextAlign.Center)
        if (o.solved) {
            StarRow(o.stars, 34.dp)
            Text(
                when (o.stars) {
                    3 -> "Flawless: no stone melted, no hint."
                    2 -> "One melt or hint short of three stars."
                    else -> "Try it with fewer melts and hints for more stars."
                },
                style = bodyStyle(14.sp), textAlign = TextAlign.Center,
            )
        } else {
            Text(o.reason, style = bodyStyle(15.sp), textAlign = TextAlign.Center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Retry", vm::retryPuzzle, Modifier.weight(1f), dark = o.solved)
            if (o.solved && o.id < Puzzles.COUNT) BrassButton("Next", vm::nextPuzzle, Modifier.weight(1f))
        }
        BrassButton("All Puzzles", vm::leavePuzzle, Modifier.fillMaxWidth(), dark = true)
    }
}

/** In play: which puzzle, and how it's going. */
@Composable
fun PuzzleLabel(state: GameState) {
    val id = state.puzzleId ?: return
    Text("Puzzle $id · ${Puzzles.names[id - 1]}", style = bodyStyle(12.sp, Palette.goldLight, bold = true), maxLines = 1)
    Text(
        "Clear ${state.puzzleTarget} ${if (state.puzzleTarget == 1) "line" else "lines"} · ${state.puzzleLines} done · " +
            "${state.puzzleStonesLeft} ${if (state.puzzleStonesLeft == 1) "stone" else "stones"} left",
        style = bodyStyle(12.sp, Palette.hint, bold = true), maxLines = 1,
    )
}
