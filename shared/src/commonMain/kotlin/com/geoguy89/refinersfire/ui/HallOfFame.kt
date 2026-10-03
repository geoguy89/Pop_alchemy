package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.formatDate
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.Ranks
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.net.BoardKey
import com.geoguy89.refinersfire.net.LeaderboardEntry

/** A row of small toggle buttons; [selected] is highlighted. */
@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, slots: Int = options.size, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        for (o in options) {
            BrassButton(label(o), { onSelect(o) }, Modifier.weight(1f), dark = o != selected, fontSize = 12.sp, minHeight = 34.dp)
        }
        // Keep a short last row's buttons the same width as the rows above.
        repeat(slots - options.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Gold, silver and bronze. */
private val medals = listOf(
    listOf(Color(0xFFFFF1A8), Color(0xFFF2C14E), Color(0xFF9A6A12)),
    listOf(Color(0xFFF4F6F8), Color(0xFFB8C0C8), Color(0xFF5E666E)),
    listOf(Color(0xFFF6CFA0), Color(0xFFC98A4A), Color(0xFF6E3E14)),
)

@Composable
fun HallOfFamePanel(vm: GameViewModel) {
    var tab by rememberSaveable { mutableStateOf(HallTab.GLOBAL) }
    var difficulty by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var mode by rememberSaveable { mutableStateOf<GameMode?>(null) }
    var week by rememberSaveable { mutableStateOf(false) }
    var showBests by rememberSaveable { mutableStateOf(true) }
    // Each Global slice is fetched as it's chosen; the server sends it narrowed, with this player's rank.
    LaunchedEffect(tab, difficulty, mode, week) {
        if (tab == HallTab.GLOBAL) vm.online.fetchLeaderboard(BoardKey(difficulty, mode, week))
    }
    GamePanel("Hall of Fame", vm::pop, maxWidth = 660.dp) {
        YourBests(vm, expanded = showBests) { vm.click(); showBests = !showBests }
        Chips(HallTab.entries, tab, { it.label }) { vm.click(); tab = it }
        Chips(listOf<Difficulty?>(null) + Difficulty.entries, difficulty, { it?.displayName ?: "All Levels" }) { vm.click(); difficulty = it }
        // Five modes don't fit one row on a phone: three, then two.
        for (row in (listOf<GameMode?>(null) + GameMode.entries).chunked(3)) {
            Chips(row, mode, { it?.displayName ?: "All Modes" }, slots = 3) { vm.click(); mode = it }
        }
        Chips(listOf(false, true), week, { if (it) "This Week" else "All Time" }) { vm.click(); week = it }
        val rows = vm.hallRows(tab, difficulty, mode, week)
        if (rows.isEmpty()) {
            Text(
                when {
                    tab == HallTab.GLOBAL && !vm.online.reachable -> "Can't reach the game server right now."
                    week && (difficulty != null || mode != null) -> "No scores in this table yet this week. Be the first!"
                    week -> "Nobody's set a score this week yet. Be the first!"
                    difficulty != null || mode != null -> "No scores in this table yet. Try another, or play one!"
                    tab == HallTab.GLOBAL -> "No one is on the Global board yet. Set Share Scores to Global in Options to put yours up."
                    tab == HallTab.FRIENDS -> "Add friends from Friends & 1v1 to see their best here."
                    tab == HallTab.NEARBY -> "Nobody nearby yet. Scores appear when someone plays on the same Wi-Fi."
                    else -> "No names are yet inscribed here.\nWill yours be the first?"
                },
                style = bodyStyle(), textAlign = TextAlign.Center,
            )
        }
        // On the Global board, tap a player for their card: add them, or challenge them.
        val board = vm.online.boards[BoardKey(difficulty, mode, week)]?.players ?: vm.online.leaderboard
        fun entryFor(row: HallRow): LeaderboardEntry? =
            if (tab == HallTab.GLOBAL) board.firstOrNull { it.playerId == row.playerId } ?: vm.online.leaderboard.firstOrNull { it.playerId == row.playerId } else null
        if (rows.isNotEmpty()) Podium(vm, rows.take(3), ::entryFor)
        rows.drop(3).forEachIndexed { i, row -> HallRowLine(vm, tab, i + 4, row, entryFor(row)) }
        // When the player isn't in the list shown, pin where they stand.
        val standing = vm.hallStanding(tab, difficulty, mode, week)
        if (standing != null && rows.none { it.isMe }) {
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    drawRoundRect(Palette.gold.copy(alpha = 0.12f), cornerRadius = CornerRadius(10.dp.toPx()))
                    drawRoundRect(Palette.gold.copy(alpha = 0.5f), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.5f))
                }.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${standing.rank}", style = bodyStyle(16.sp, Palette.goldLight, bold = true), modifier = Modifier.width(54.dp))
                Column(Modifier.weight(1f)) {
                    Text("You", style = bodyStyle(16.sp, bold = true))
                    Text("of ${standing.total} ${if (standing.total == 1) "player" else "players"}", style = bodyStyle(11.sp, dim()))
                }
                Text("${standing.best}", style = bodyStyle(18.sp, Palette.ledOrange, bold = true))
            }
        }
        if (tab == HallTab.NEARBY && vm.peers.any { it.direct }) {
            val n = vm.peers.count { it.direct }
            Text("Scores from $n nearby ${if (n == 1) "device" else "devices"}.", style = bodyStyle(12.sp, dim()), textAlign = TextAlign.Center)
        }
        if (week) Text("Weeks start Monday at midnight (UTC).", style = bodyStyle(11.sp, dim()), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (tab == HallTab.NEARBY && vm.peers.isNotEmpty()) BrassButton("Forget Nearby", vm::forgetNearbyScores, Modifier.weight(1f), dark = true, fontSize = 14.sp)
            BrassButton("Close", vm::pop, Modifier.weight(1f))
        }
    }
}

/** Your best score in every table at a glance, with your furthest board and longest streak. */
@Composable
private fun YourBests(vm: GameViewModel, expanded: Boolean, toggle: () -> Unit) {
    val bests = vm.bestsByTable()
    Column(
        Modifier.fillMaxWidth().drawBehind {
            drawRoundRect(Color.Black.copy(alpha = 0.25f), cornerRadius = CornerRadius(12.dp.toPx()))
            drawRoundRect(Palette.brass.copy(alpha = 0.45f), cornerRadius = CornerRadius(12.dp.toPx()), style = Stroke(1.5f))
        }.padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable(onClick = toggle), verticalAlignment = Alignment.CenterVertically) {
            Text("Your Bests", style = bodyStyle(15.sp, Palette.goldLight, bold = true), modifier = Modifier.weight(1f))
            Text(if (expanded) "Hide" else "Show", style = bodyStyle(12.sp, dim(), bold = true))
        }
        if (!expanded) return@Column
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.3f))
            for (d in Difficulty.entries) Text(d.displayName, style = bodyStyle(11.sp, dim(), bold = true), textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        for (m in GameMode.entries) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(m.displayName, style = bodyStyle(12.sp, Palette.parchment, bold = true), modifier = Modifier.weight(1.3f), maxLines = 1)
                for (d in Difficulty.entries) {
                    val b = bests[d to m]?.score
                    Text(
                        b?.toString() ?: "—",
                        style = bodyStyle(13.sp, if (b != null) Palette.ledOrange else dim(), bold = b != null),
                        textAlign = TextAlign.End, modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        val s = vm.stats
        Text(
            "Furthest board ${s.highestBoard.coerceAtLeast(1)} · Longest streak ${s.bestStreak} · Games ${s.gamesStarted}",
            style = bodyStyle(11.sp, dim()), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

/** The top three on plates of gold, silver and bronze: second, first, third, with first standing tallest. */
@Composable
private fun Podium(vm: GameViewModel, top: List<HallRow>, entryFor: (HallRow) -> LeaderboardEntry?) {
    val order = listOf(1, 0, 2).filter { it < top.size }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        for (place in order) {
            val row = top[place]
            val entry = entryFor(row)
            val colors = medals[place]
            val height: Dp = when (place) { 0 -> 118.dp; 1 -> 100.dp; else -> 88.dp }
            Box(
                Modifier.weight(1f).height(height)
                    .then(if (entry != null) Modifier.clickable { vm.click(); vm.push(Overlay.PlayerCard(entry)) } else Modifier)
                    .drawBehind {
                        val r = CornerRadius(12.dp.toPx())
                        drawRoundRect(Color.Black.copy(alpha = 0.45f), topLeft = Offset(2f, 4f), size = size, cornerRadius = r)
                        drawRoundRect(Brush.verticalGradient(colors, 0f, size.height), cornerRadius = r)
                        drawRoundRect(Color.White.copy(alpha = 0.5f), cornerRadius = r, style = Stroke(1.5f))
                        // The place number sits in a disc at the top.
                        drawCircle(Color.Black.copy(alpha = 0.25f), 13.dp.toPx(), Offset(size.width / 2, 18.dp.toPx()))
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${place + 1}", style = bodyStyle(16.sp, Color.White, bold = true))
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vm.isFriend(row.playerId)) { FriendBadge(Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                        Text(
                            if (row.isMe) "You" else row.score.name, style = bodyStyle(13.sp, Color(0xFF231505), bold = true),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text("${row.score.score}", style = bodyStyle(16.sp, Color(0xFF231505), bold = true), maxLines = 1)
                    Text(row.score.mode.displayName, style = bodyStyle(10.sp, Color(0xFF231505).copy(alpha = 0.7f)), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HallRowLine(vm: GameViewModel, tab: HallTab, place: Int, row: HallRow, entry: LeaderboardEntry?) {
    val h = row.score
    Row(
        Modifier.fillMaxWidth().then(if (entry != null) Modifier.clickable { vm.click(); vm.push(Overlay.PlayerCard(entry)) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$place.", style = bodyStyle(16.sp, Palette.goldLight, bold = true), modifier = Modifier.width(38.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val friend = vm.isFriend(row.playerId)
                if (friend) {
                    FriendBadge(Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(h.name, style = bodyStyle(16.sp, bold = true), maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                val tag = if (row.isMe && tab != HallTab.MINE) "you" else row.tag?.takeIf { !friend }
                if (tag != null) Text("  · $tag", style = bodyStyle(11.sp, Palette.hint, bold = true), maxLines = 1)
            }
            Text(
                "${Ranks.rankFor(h.score)} · Board ${h.board} · ${h.difficulty.displayName} ${h.mode.displayName} · ${formatDate(h.epochMillis)}",
                style = bodyStyle(11.sp, dim()), maxLines = 2,
            )
        }
        if (entry == null && vm.canBefriend(row)) {
            BrassButton("+ Friend", { vm.addFriend(row.playerId!!) }, Modifier.width(92.dp), dark = true, fontSize = 12.sp, minHeight = 32.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text("${h.score}", style = bodyStyle(18.sp, Palette.ledOrange, bold = true))
    }
}
