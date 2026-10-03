package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.formatDay
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Manna
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.net.MannaBoard
import kotlin.math.abs

/** Today's Manna: what it is, your streak, the way in, and how everyone's doing today. */
@Composable
fun MannaPanel(vm: GameViewModel) {
    val today = vm.today
    val board = vm.online.manna?.takeIf { it.day == today }
    val done = vm.todaysManna
    GamePanel("Today's Manna", vm::pop) {
        Text(formatDay(today), style = bodyStyle(16.sp, Palette.goldLight, bold = true))
        Text(
            "Everyone in the world gets the same stones today: ${Manna.DIFFICULTY.displayName}, ${Manna.BOARDS} boards, and " +
                "one try. Like the manna in the wilderness, it's gathered fresh each morning and gone by the next.",
            style = bodyStyle(13.sp, dim()), textAlign = TextAlign.Center,
        )
        StreakLine(vm)
        when {
            done != null -> {
                Text("You gathered ${done.score}", style = bodyStyle(22.sp, Palette.ledOrange, bold = true))
                if (board?.rank != null) Text("#${board.rank} of ${board.total} today", style = bodyStyle(14.sp, Palette.parchment, bold = true))
                Text("Come back tomorrow for more.", style = bodyStyle(13.sp, dim()))
            }
            vm.savedManna?.mannaDay == today -> BrassButton("Continue Today's Manna", vm::playManna, Modifier.fillMaxWidth())
            else -> {
                BrassButton("Gather Today's Manna", vm::playManna, Modifier.fillMaxWidth())
                board?.ghost?.let { Text("You'll race ${it.name}'s run as you go.", style = bodyStyle(12.sp, Palette.hint, bold = true)) }
            }
        }
        MannaStandings(vm, board)
        BrassButton("Close", vm::pop, Modifier.fillMaxWidth(), dark = true)
    }
}

@Composable
private fun StreakLine(vm: GameViewModel) {
    val streak = vm.mannaStreak
    val days = vm.mannaHistory.size
    Text(
        when {
            days == 0 -> "Your first day of Manna awaits."
            streak > 0 -> "$streak-day streak · gathered on $days ${if (days == 1) "day" else "days"}"
            else -> "Gathered on $days ${if (days == 1) "day" else "days"} · start a new streak today"
        },
        style = bodyStyle(13.sp, Palette.parchment, bold = true), textAlign = TextAlign.Center,
    )
}

/** Friends first (with you among them), then the top of everyone who shares globally. */
@Composable
private fun MannaStandings(vm: GameViewModel, board: MannaBoard?) {
    if (board == null) {
        Text(if (vm.online.reachable) "Loading today's standings..." else "Can't reach the game server right now.", style = bodyStyle(12.sp, dim()))
        return
    }
    val me = vm.online.account?.playerId
    val mine = board.mine
    val friends = (board.friends.map { Triple(it.name, it.score, false) } + listOfNotNull(mine?.let { Triple("You", it.score, true) }))
        .sortedByDescending { it.second }
    SectionTitle("Friends today")
    if (friends.isEmpty()) {
        Text("None of your friends have gathered today's Manna yet.", style = bodyStyle(12.sp, dim()), textAlign = TextAlign.Center)
    } else {
        friends.take(10).forEachIndexed { i, (name, score, isMe) -> StandingRow(i + 1, name, score, isMe) }
    }
    if (board.top.isNotEmpty()) {
        SectionTitle("Everyone today (${board.total})")
        board.top.take(10).forEachIndexed { i, e -> StandingRow(i + 1, if (e.playerId == me) "You" else e.name, e.score, e.playerId == me) }
    }
}

@Composable
private fun StandingRow(place: Int, name: String, score: Long, isMe: Boolean) {
    Row(
        Modifier.fillMaxWidth().then(
            if (isMe) Modifier.drawBehind { drawRoundRect(Palette.gold.copy(alpha = 0.12f), cornerRadius = CornerRadius(8.dp.toPx())) } else Modifier,
        ).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$place.", style = bodyStyle(14.sp, Palette.goldLight, bold = true), modifier = Modifier.width(32.dp))
        Text(name, style = bodyStyle(14.sp, bold = isMe), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$score", style = bodyStyle(15.sp, Palette.ledOrange, bold = true))
    }
}

/** A Manna run just ended: the score, the streak, and how it stands so far today. */
@Composable
fun MannaDonePanel(vm: GameViewModel, o: Overlay.MannaDone) {
    val board = vm.online.manna?.takeIf { it.day == o.result.day }
    GamePanel("Manna Gathered", null) {
        Text("Your score", style = bodyStyle(14.sp, dim()))
        Text("${o.result.score}", style = bodyStyle(34.sp, Palette.ledOrange, bold = true))
        Text(
            if (o.result.boards >= Manna.BOARDS) "All ${Manna.BOARDS} boards refined!" else "${o.result.boards} of ${Manna.BOARDS} boards refined",
            style = bodyStyle(14.sp, Palette.parchment, bold = true),
        )
        Text(
            if (o.streak > 1) "${o.streak}-day streak. See you tomorrow!" else "Come back tomorrow to start a streak.",
            style = bodyStyle(14.sp, Palette.hint, bold = true), textAlign = TextAlign.Center,
        )
        if (board?.rank != null && board.mine != null) Text("#${board.rank} of ${board.total} so far today", style = bodyStyle(13.sp, Palette.parchment))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Standings", { vm.closeChallengeResult(); vm.openManna() }, Modifier.weight(1f), dark = true)
            BrassButton("Done", vm::closeChallengeResult, Modifier.weight(1f))
        }
    }
}

/** Racing someone's recorded run: where they were after the same number of stones, and how you compare. */
@Composable
fun GhostLine(state: GameState) {
    val ghost = state.ghost?.takeIf { it.isNotEmpty() } ?: return
    val stones = state.timeline?.size ?: 0
    val theirs = if (stones == 0) 0L else ghost.getOrElse(stones - 1) { ghost.last() }
    val diff = state.score - theirs
    val finished = stones > ghost.size
    Column {
        Text(
            "${state.ghostName ?: "Rival"}'s run: $theirs" + if (finished) " (done)" else "",
            style = bodyStyle(12.sp, Palette.parchment.copy(alpha = 0.85f), bold = true), maxLines = 1,
        )
        Text(
            when {
                diff > 0 -> "You're ahead by $diff"
                diff < 0 -> "You're behind by ${abs(diff)}"
                else -> "Neck and neck"
            },
            style = bodyStyle(12.sp, if (diff >= 0) Palette.hint else Palette.ember, bold = true), maxLines = 1,
        )
    }
}

/** A thin gold-edged card for title-screen extras. */
internal fun Modifier.goldEdge(): Modifier = drawBehind {
    drawRoundRect(Color.Black.copy(alpha = 0.25f), cornerRadius = CornerRadius(12.dp.toPx()))
    drawRoundRect(Palette.gold.copy(alpha = 0.45f), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5f))
}
