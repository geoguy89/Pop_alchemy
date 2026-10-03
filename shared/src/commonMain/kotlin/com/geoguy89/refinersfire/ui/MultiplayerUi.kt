package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.gfx.drawBrassPlate
import com.geoguy89.refinersfire.net.MatchPhase
import com.geoguy89.refinersfire.net.MatchSession

private val GATHERING_MODES = listOf(GameMode.STRATEGIC, GameMode.IRON_FORGE, GameMode.FORESIGHT)
private val GATHERING_MINUTES = listOf(5, 8, 12)

/** Pick 2-7 friends and the rules for a gathering. */
@Composable
fun GatheringSetupPanel(vm: GameViewModel) {
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var difficulty by rememberSaveable { mutableStateOf(Difficulty.AVERAGE) }
    var mode by rememberSaveable { mutableStateOf(GameMode.STRATEGIC) }
    var minutes by rememberSaveable { mutableStateOf(5) }
    GamePanel("Start a Gathering", vm::pop, maxWidth = 620.dp) {
        Text(
            "Invite 2 to 7 friends. Everyone plays the same stones at the same time, with live standings; the highest score " +
                "when time's up wins. You start it from the lobby once people are in.",
            style = bodyStyle(13.sp, dim()), textAlign = TextAlign.Center,
        )
        SectionTitle("Friends (${picked.size} of 7)")
        val friends = vm.online.friends.sortedWith(compareByDescending<com.geoguy89.refinersfire.net.Friend> { it.online }.thenBy { it.name.lowercase() })
        if (friends.size < 2) Text("You need at least two friends to gather. Add some from Friends & 1v1.", style = bodyStyle(13.sp, dim()), textAlign = TextAlign.Center)
        for (f in friends) {
            val on = f.playerId in picked
            Row(
                Modifier.fillMaxWidth().clickable {
                    vm.click()
                    picked = if (on) picked - f.playerId else if (picked.size < 7) picked + f.playerId else picked
                }.drawBehind {
                    if (on) drawRoundRect(Palette.gold.copy(alpha = 0.16f), cornerRadius = CornerRadius(8.dp.toPx()))
                }.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(18.dp).background(if (on) Palette.gold else Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    if (on) Text("✓", style = bodyStyle(12.sp, Palette.ink, bold = true))
                }
                Spacer(Modifier.width(10.dp))
                Text(f.name, style = bodyStyle(15.sp, bold = on), modifier = Modifier.weight(1f), maxLines = 1)
                Text(if (f.online) "Online" else "Offline", style = bodyStyle(11.sp, if (f.online) Color(0xFF3DDC6A) else dim()))
            }
        }
        SectionTitle("Difficulty")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (d in Difficulty.entries) BrassButton(d.displayName, { vm.click(); difficulty = d }, Modifier.weight(1f), dark = d != difficulty, fontSize = 14.sp, minHeight = 40.dp)
        }
        SectionTitle("Rules")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (m in GATHERING_MODES) BrassButton(m.displayName, { vm.click(); mode = m }, Modifier.weight(1f), dark = m != mode, fontSize = 13.sp, minHeight = 40.dp)
        }
        SectionTitle("Length")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (n in GATHERING_MINUTES) BrassButton("$n min", { vm.click(); minutes = n }, Modifier.weight(1f), dark = n != minutes, fontSize = 14.sp, minHeight = 40.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Back", vm::pop, Modifier.weight(1f), dark = true)
            BrassButton("Send Invites", { vm.createGathering(picked, difficulty, mode, minutes) }, Modifier.weight(1f), enabled = picked.size in 2..7)
        }
    }
}

/** Waiting for a 1v1 opponent, a co-op partner, or (for a gathering) everyone the host invited. */
@Composable
fun MatchLobbyPanel(vm: GameViewModel) {
    val m = vm.match ?: return
    if (!m.isGathering) {
        GamePanel(if (m.isCoop) "Co-op" else "1v1 Match", null) {
            val who = m.opponent?.name ?: if (m.isCoop) "your partner" else "your opponent"
            Text(
                when (m.phase) {
                    MatchPhase.CONNECTING -> "Connecting..."
                    else -> "Waiting for $who to join..."
                },
                style = bodyStyle(16.sp), textAlign = TextAlign.Center,
            )
            BrassButton("Leave", vm::leaveMatch, Modifier.fillMaxWidth(), dark = true)
        }
        return
    }
    GamePanel("Gathering", null) {
        val here = m.roster.count { it.connected }
        Text(
            if (m.phase == MatchPhase.CONNECTING) "Connecting to the lobby..." else "$here of ${m.roster.size} here",
            style = bodyStyle(15.sp, Palette.goldLight, bold = true),
        )
        for (p in m.roster) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (p.connected) Color(0xFF3DDC6A) else Palette.stone, RoundedCornerShape(50)))
            Spacer(Modifier.width(10.dp))
            Text(if (p.playerId == m.me) "You" else p.name, style = bodyStyle(15.sp, bold = p.connected), modifier = Modifier.weight(1f), maxLines = 1)
            if (p.playerId == m.host) Text("host", style = bodyStyle(11.sp, Palette.hint, bold = true))
        }
        if (m.isHost) {
            BrassButton("Start Now", vm::startGathering, Modifier.fillMaxWidth(), enabled = here >= 2)
            Text("It starts by itself once everyone's here.", style = bodyStyle(12.sp, dim()), textAlign = TextAlign.Center)
        } else {
            Text("Waiting for ${m.roster.firstOrNull { it.playerId == m.host }?.name ?: "the host"} to start...", style = bodyStyle(13.sp, dim()))
        }
        BrassButton("Leave", vm::leaveMatch, Modifier.fillMaxWidth(), dark = true)
    }
}

/** Above the board in a gathering: the top of the standings, with you always shown. */
@Composable
fun StandingsCard(vm: GameViewModel, m: MatchSession, modifier: Modifier = Modifier) {
    val mine = vm.state?.score ?: 0
    val names = m.roster.associate { it.playerId to it.name }
    val rows = (m.peers.map { (id, v) -> Triple(names[id] ?: "Refiner", v.score, v.over) } + Triple("You", mine, vm.state?.gameOver == true))
        .sortedByDescending { it.second }
    val myPlace = rows.indexOfFirst { it.first == "You" } + 1
    Column(
        modifier.drawBehind { drawBrassPlate(Offset.Zero, size, 12.dp.toPx(), rivets = false, dark = true) }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gathering · you're #$myPlace of ${rows.size}", style = bodyStyle(12.sp, Palette.hint, bold = true), modifier = Modifier.weight(1f), maxLines = 1)
            Text(clockText(m.secondsLeft), style = bodyStyle(13.sp, if (m.secondsLeft < 30) Palette.ember else Palette.parchment, bold = true))
        }
        val shown = rows.take(3).let { top -> if (top.none { it.first == "You" }) top.take(2) + rows.first { it.first == "You" } else top }
        for ((name, score, over) in shown) Row(verticalAlignment = Alignment.CenterVertically) {
            val place = rows.indexOfFirst { it.first == name } + 1
            Text("$place.", style = bodyStyle(12.sp, Palette.goldLight, bold = true), modifier = Modifier.width(22.dp))
            Text(name + if (over) " (out)" else "", style = bodyStyle(12.sp, if (name == "You") Palette.goldLight else Palette.parchment, bold = name == "You"),
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$score", style = bodyStyle(13.sp, Palette.ledOrange, bold = true))
        }
        if (m.reconnecting) Text("Connection lost, reconnecting...", style = bodyStyle(11.sp, Palette.ember))
    }
}

/** Above the board in co-op: whose turn it is (with its clock), your partner, and what you're aiming for together. */
@Composable
fun CoopCard(vm: GameViewModel, m: MatchSession, modifier: Modifier = Modifier) {
    val partner = m.opponent?.name ?: "Your partner"
    val best = m.opponent?.let { vm.chatFriend(it.playerId) }?.coopBest ?: 0
    Column(
        modifier.drawBehind { drawBrassPlate(Offset.Zero, size, 12.dp.toPx(), rivets = false, dark = true) }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("Co-op with $partner", style = bodyStyle(13.sp, Palette.goldLight, bold = true), maxLines = 1)
        val mine = m.isMyTurn
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (mine) "Your turn" else "$partner's turn",
                style = bodyStyle(16.sp, if (mine) Palette.hint else Palette.parchment, bold = true), modifier = Modifier.weight(1f), maxLines = 1,
            )
            if (m.phase == MatchPhase.PLAYING) Text("${m.turnSecondsLeft}s", style = bodyStyle(14.sp, if (m.turnSecondsLeft <= 8) Palette.ember else Palette.parchment, bold = true))
        }
        Text(if (best > 0) "Best together: $best" else "One board, one forge: make it last.", style = bodyStyle(11.sp, dim()), maxLines = 1)
        if (!m.opp.connected) Text(m.forfeitAt?.let { "$partner is reconnecting..." } ?: "$partner is reconnecting...", style = bodyStyle(11.sp, Palette.ember))
        if (m.reconnecting) Text("Connection lost, reconnecting...", style = bodyStyle(11.sp, Palette.ember))
    }
}

/** The card above the board in any live match. */
@Composable
fun MatchCard(vm: GameViewModel, m: MatchSession, modifier: Modifier = Modifier) = when {
    m.isGathering -> StandingsCard(vm, m, modifier)
    m.isCoop -> CoopCard(vm, m, modifier)
    else -> OpponentCard(vm, m, modifier)
}

private fun clockText(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** A gathering or co-op game has ended. */
@Composable
fun GroupResultPanel(vm: GameViewModel, m: MatchSession) {
    val r = m.result ?: return
    if (m.isCoop) {
        GamePanel("Well Played, Together", null) {
            Text(
                when (r.reason) {
                    "coop_over" -> "The shared forge overflowed."
                    "partner_left" -> "The game ended when a player left."
                    else -> "Time's up!"
                },
                style = bodyStyle(15.sp), textAlign = TextAlign.Center,
            )
            Text("You scored", style = bodyStyle(14.sp, dim()))
            Text("${r.myScore}", style = bodyStyle(34.sp, Palette.ledOrange, bold = true))
            val best = m.opponent?.let { vm.chatFriend(it.playerId) }?.coopBest ?: 0
            if (r.myScore > best && best > 0) Text("A new best together!", style = bodyStyle(14.sp, Palette.hint, bold = true))
            else if (best > 0) Text("Best together: $best", style = bodyStyle(13.sp, dim()))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrassButton("Play Again", vm::rematch, Modifier.weight(1f))
                BrassButton("Done", vm::leaveMatch, Modifier.weight(1f), dark = true)
            }
        }
        return
    }
    GamePanel(if (r.won == true) "You Win the Gathering!" else "Gathering Over", null) {
        Text(if (r.reason == "time") "Time's up!" else "Everyone's out.", style = bodyStyle(15.sp), textAlign = TextAlign.Center)
        r.standings.forEachIndexed { i, (name, score) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}.", style = bodyStyle(16.sp, Palette.goldLight, bold = true), modifier = Modifier.width(34.dp))
                Text(name, style = bodyStyle(16.sp, bold = name == "You"), modifier = Modifier.weight(1f), maxLines = 1)
                Text("$score", style = bodyStyle(18.sp, Palette.ledOrange, bold = true))
            }
        }
        BrassButton("Done", vm::leaveMatch, Modifier.fillMaxWidth())
    }
}
