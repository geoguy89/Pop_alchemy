package com.geoguy89.refinersfire.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.ROWS
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.gfx.drawBrassPlate
import com.geoguy89.refinersfire.gfx.drawPiece
import com.geoguy89.refinersfire.net.Friend
import com.geoguy89.refinersfire.net.MatchGoal
import com.geoguy89.refinersfire.net.MatchType
import com.geoguy89.refinersfire.net.LeaderboardEntry
import com.geoguy89.refinersfire.net.Rival
import com.geoguy89.refinersfire.net.MatchPhase
import com.geoguy89.refinersfire.net.MatchSession

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.gold, unfocusedBorderColor = Palette.brassDark, cursorColor = Palette.goldLight,
    focusedLabelColor = Palette.goldLight, unfocusedLabelColor = Palette.parchment,
    focusedTextColor = Palette.parchment, unfocusedTextColor = Palette.parchment,
)

@Composable
private fun SectionTitle(text: String) =
    Text(text, style = bodyStyle(15.sp, Palette.goldLight, bold = true), modifier = Modifier.fillMaxWidth())

private fun dim() = Palette.parchment.copy(alpha = 0.7f)

// ---- Connect with Player ------------------------------------------------------------------------------------------

@Composable
fun FriendsPanel(vm: GameViewModel) {
    val online = vm.online
    val account = online.account
    val clipboard = LocalClipboardManager.current
    var code by rememberSaveable { mutableStateOf("") }
    var copied by rememberSaveable { mutableStateOf(false) }
    GamePanel("Connect with Player", vm::pop, maxWidth = 620.dp) {
        if (account == null) {
            Text(
                if (online.reachable) "Setting up your friend code..." else "Can't reach the game server. Check your connection and try again.",
                style = bodyStyle(14.sp), textAlign = TextAlign.Center,
            )
            if (!online.reachable) BrassButton("Try Again", { vm.pop(); vm.openFriends() }, Modifier.fillMaxWidth(), dark = true)
        } else {
            SectionTitle("Your friend code")
            Text(account.friendCode, style = titleStyle(26.sp, Palette.ledOrange), textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrassButton(if (copied) "Copied!" else "Copy Code", {
                    vm.click()
                    clipboard.setText(AnnotatedString("Play Refiner's Fire with me! My friend code is ${account.friendCode}"))
                    copied = true
                }, Modifier.weight(1f), fontSize = 14.sp)
            }
            Text("Send it to a friend. When they enter it, you'll both be asked to accept.", style = bodyStyle(12.sp, dim()), textAlign = TextAlign.Center)

            SectionTitle("Add a friend")
            OutlinedTextField(
                code, { code = it.uppercase().take(16) }, singleLine = true,
                label = { Text("Their friend code", style = bodyStyle(12.sp)) },
                textStyle = bodyStyle(16.sp, bold = true),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (code.isNotBlank()) { vm.addFriendByCode(code); code = "" } }),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            BrassButton("Send Request", { vm.click(); vm.addFriendByCode(code); code = "" }, Modifier.fillMaxWidth(), enabled = code.isNotBlank())

            if (online.incoming.isNotEmpty()) {
                SectionTitle("Friend requests")
                for (r in online.incoming) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(r.name, style = bodyStyle(15.sp, bold = true), modifier = Modifier.weight(1f), maxLines = 1)
                    SmallBrass("Accept", { vm.respondToRequest(r.id, true) }, Modifier.width(96.dp))
                    BrassButton("Deny", { vm.respondToRequest(r.id, false) }, Modifier.width(80.dp), dark = true, fontSize = 14.sp, minHeight = 38.dp)
                }
            }
            if (online.outgoing.isNotEmpty()) {
                SectionTitle("Waiting for them to accept")
                for (r in online.outgoing) Text(r.name, style = bodyStyle(14.sp, dim()), modifier = Modifier.fillMaxWidth())
            }

            ChallengesSection(vm)

            SectionTitle("Friends")
            if (online.friends.isEmpty()) {
                Text("No friends yet. Share your code, or add someone from the Global or Nearby Hall of Fame.", style = bodyStyle(13.sp, dim()), textAlign = TextAlign.Center)
            }
            val myBest = vm.highScores.maxOfOrNull { it.score } ?: 0
            for (f in online.friends.sortedWith(compareByDescending<Friend> { it.online }.thenBy { it.name.lowercase() })) FriendRow(vm, f, myBest)
        }
        BrassButton("Close", vm::pop, Modifier.fillMaxWidth())
    }
}

@Composable
private fun FriendRow(vm: GameViewModel, f: Friend, myBest: Long) {
    val pending = vm.online.invites.firstOrNull { !it.incoming && it.playerId == f.playerId && it.status == "pending" }
    Column(Modifier.fillMaxWidth().drawBehind { drawRect(Color.Black, alpha = 0.18f) }.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FriendBadge(Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(f.name, style = bodyStyle(16.sp, bold = true), modifier = Modifier.weight(1f), maxLines = 1)
            Text("${f.wins}W ${f.losses}L" + if (f.ties > 0) " ${f.ties}T" else "", style = bodyStyle(12.sp, Palette.goldLight, bold = true))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (f.online) Color(0xFF3DDC6A) else Palette.stone, RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    f.online -> "Online now"
                    f.lastSeenAgoMs == null -> "Activity hidden"
                    else -> "Last seen ${ago(f.lastSeenAgoMs)}"
                },
                style = bodyStyle(12.sp, if (f.online) Color(0xFF3DDC6A) else dim(), bold = f.online),
            )
        }
        val rivalry = when {
            f.best == 0L -> "No high score yet"
            f.best > myBest -> "Best ${f.best} · beat it by ${f.best - myBest + 1}"
            f.best < myBest -> "Best ${f.best} · you lead by ${myBest - f.best}"
            else -> "Best ${f.best} · tied with you"
        }
        Text(rivalry, style = bodyStyle(12.sp, dim()))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pending != null) {
                SmallBrass("Waiting... (cancel)", { vm.cancelChallenge(pending.id) }, Modifier.weight(1f))
            } else {
                // A friend who hides their activity can't be seen online, so the challenge is always allowed; it pops up if they're playing.
                if (f.online || f.activityHidden) {
                    SmallBrass("Challenge 1v1", { vm.push(Overlay.Challenge(Rival(f.playerId, f.name, f.online, friend = true))) }, Modifier.weight(1f))
                } else {
                    SmallBrass("Poke", { vm.poke(f) }, Modifier.weight(1f))
                }
            }
            val unread = vm.unread[f.playerId] ?: 0
            SmallBrass(if (unread > 0) "Chat ($unread)" else "Chat", { vm.openChat(f) }, Modifier.weight(0.6f))
            BrassButton("Remove", { vm.removeFriend(f) }, Modifier.weight(0.6f), dark = true, fontSize = 13.sp, minHeight = 38.dp)
        }
        if (!f.online && pending == null) Text("Live matches need them online; Poke sends them a notification. A challenge they can play any time.", style = bodyStyle(11.sp, dim()))
        SmallBrass("Send Challenge", { vm.push(Overlay.AsyncSetup(Rival(f.playerId, f.name, f.online, friend = true))) }, Modifier.fillMaxWidth())
    }
}

@Composable
fun ChallengePanel(vm: GameViewModel, friend: Rival) {
    var difficulty by rememberSaveable { mutableStateOf(vm.settings.difficulty) }
    var type by rememberSaveable { mutableStateOf(MatchType.TIMED) }
    var value by rememberSaveable { mutableStateOf(MatchType.TIMED.default) }
    val goal = MatchGoal(type.id, if (value in type.values) value else type.default)
    GamePanel("Challenge ${friend.name}", vm::pop) {
        Text("You both play the same difficulty and get the same pieces in the same order.", style = bodyStyle(14.sp), textAlign = TextAlign.Center)
        SectionTitle("Difficulty")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (d in Difficulty.entries) BrassButton(d.displayName, { vm.click(); difficulty = d }, Modifier.weight(1f), dark = d != difficulty, fontSize = 15.sp)
        }
        SectionTitle("Match type")
        for (row in MatchType.entries.chunked(3)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (t in row) BrassButton(t.title, { vm.click(); type = t; value = t.default }, Modifier.weight(1f), dark = t != type, fontSize = 13.sp, minHeight = 40.dp)
            repeat(3 - row.size) { Box(Modifier.weight(1f)) }
        }
        Text(type.blurb, style = bodyStyle(12.sp, dim()), textAlign = TextAlign.Center)
        if (type.values.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (v in type.values) {
                val label = MatchGoal(type.id, v).valueLabel(difficulty)
                BrassButton(label, { vm.click(); value = v }, Modifier.weight(1f), dark = v != goal.value, fontSize = 13.sp, minHeight = 38.dp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Back", vm::pop, Modifier.weight(1f), dark = true)
            BrassButton("Send Challenge", { vm.challenge(friend, difficulty, goal) }, Modifier.weight(1f))
        }
    }
}

/** Marks someone you've connected with. A green disc with a white check. */
@Composable
fun FriendBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        drawCircle(Color(0xFF2FA84F), r, center)
        drawCircle(Color.White, r, center, alpha = 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.12f))
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(center.x - r * 0.45f, center.y + r * 0.02f)
            lineTo(center.x - r * 0.12f, center.y + r * 0.35f)
            lineTo(center.x + r * 0.48f, center.y - r * 0.3f)
        }
        drawPath(p, Color.White, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.24f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

private fun ago(ms: Long): String {
    val m = ms / 60_000
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} days ago"
    }
}

// ---- Global leaderboard player card --------------------------------------------------------------------------------

@Composable
fun PlayerCardPanel(vm: GameViewModel, e: LeaderboardEntry) {
    val rival = vm.rivalOf(e)
    val rank = vm.online.leaderboard.indexOfFirst { it.playerId == e.playerId } + 1
    GamePanel(e.name, vm::pop) {
        if (rival.friend) Row(verticalAlignment = Alignment.CenterVertically) {
            FriendBadge(Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text("Friend", style = bodyStyle(14.sp, Palette.hint, bold = true))
        }
        if (rank > 0) Text("#$rank on the Global board", style = bodyStyle(15.sp, Palette.goldLight, bold = true))
        Text("${e.best}", style = bodyStyle(32.sp, Palette.ledOrange, bold = true))
        Text("${com.geoguy89.refinersfire.game.Ranks.rankFor(e.best)} · Board ${e.board} · ${e.difficulty.displayName}", style = bodyStyle(13.sp, dim()))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(if (e.online) Color(0xFF3DDC6A) else Palette.stone, RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text(if (e.online) "Online now" else "Not online", style = bodyStyle(13.sp, if (e.online) Color(0xFF3DDC6A) else dim(), bold = e.online))
        }
        val isMe = e.playerId == vm.online.account?.playerId
        if (!isMe) {
            if (vm.canBefriend(HallRow(e.toHighScore(), null, e.playerId, false))) {
                BrassButton("Add Friend", { vm.addFriend(e.playerId) }, Modifier.fillMaxWidth(), dark = true)
            } else if (!rival.friend) {
                Text("Friend request sent.", style = bodyStyle(12.sp, dim()))
            }
            if (vm.canChallenge(e)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallBrass("Live Match", { vm.pop(); vm.push(Overlay.Challenge(rival)) }, Modifier.weight(1f), enabled = e.online)
                    SmallBrass("Send Challenge", { vm.pop(); vm.push(Overlay.AsyncSetup(rival)) }, Modifier.weight(1f))
                }
                if (!e.online) Text("Live matches need them online. A challenge they can play any time.", style = bodyStyle(11.sp, dim()), textAlign = TextAlign.Center)
            } else {
                Text("To challenge players who aren't friends, set Share Scores to Global in Options.", style = bodyStyle(12.sp, dim()), textAlign = TextAlign.Center)
            }
        }
        BrassButton("Close", vm::pop, Modifier.fillMaxWidth())
    }
}

// ---- Async challenges ---------------------------------------------------------------------------------------------

@Composable
private fun ChallengesSection(vm: GameViewModel) {
    val all = vm.online.asyncChallenges
    val yourMove = all.filter { it.ourMove }
    val unfinished = all.filter { it.ourRunUnfinished }
    val waiting = all.filter { !it.incoming && it.status == "waiting" }
    val results = all.filter { it.status == "done" }.take(5)
    if (yourMove.isEmpty() && unfinished.isEmpty() && waiting.isEmpty() && results.isEmpty()) return
    SectionTitle("Challenges")
    for (c in yourMove) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Your move vs ${c.name}", style = bodyStyle(14.sp, Palette.goldLight, bold = true), maxLines = 1)
            Text("Beat ${c.theirScore ?: 0} · ${c.difficulty.displayName} · ${c.boards} ${if (c.boards == 1) "board" else "boards"}", style = bodyStyle(12.sp, dim()))
        }
        SmallBrass("Play", { vm.playChallenge(c) }, Modifier.width(80.dp))
        BrassButton("Decline", { vm.declineChallenge(c) }, Modifier.width(92.dp), dark = true, fontSize = 13.sp, minHeight = 38.dp)
    }
    for (c in unfinished) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Finish your run vs ${c.name}", style = bodyStyle(14.sp, bold = true), modifier = Modifier.weight(1f), maxLines = 1)
        SmallBrass("Continue", { vm.playChallenge(c) }, Modifier.width(110.dp))
    }
    for (c in waiting) Text("Waiting for ${c.name} to beat your ${c.myScore ?: 0}", style = bodyStyle(13.sp, dim()), modifier = Modifier.fillMaxWidth())
    for (c in results) {
        val (text, color) = when (c.winner) {
            null -> "Tied with ${c.name} · ${c.myScore}" to Palette.parchment
            c.playerId -> "Lost to ${c.name} · ${c.myScore} vs ${c.theirScore}${if (c.myScore == c.theirScore) " (on time)" else ""}" to Palette.ember
            else -> "Beat ${c.name} · ${c.myScore} vs ${c.theirScore}${if (c.myScore == c.theirScore) " (on time)" else ""}" to Palette.hint
        }
        Text(text, style = bodyStyle(13.sp, color, bold = true), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun AsyncSetupPanel(vm: GameViewModel, friend: Rival) {
    var difficulty by rememberSaveable { mutableStateOf(vm.settings.difficulty) }
    var boards by rememberSaveable { mutableStateOf(3) }
    GamePanel("Challenge ${friend.name}", vm::pop) {
        Text(
            "You play first. ${friend.name} then plays the exact same run (same pieces, same order) whenever they can. " +
                "The run ends when the boards are cleared or the forge overflows. Higher score wins.",
            style = bodyStyle(14.sp), textAlign = TextAlign.Center,
        )
        SectionTitle("Difficulty")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (d in Difficulty.entries) BrassButton(d.displayName, { vm.click(); difficulty = d }, Modifier.weight(1f), dark = d != difficulty, fontSize = 15.sp)
        }
        SectionTitle("Length")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (n in listOf(1, 3, 5)) BrassButton("$n ${if (n == 1) "board" else "boards"}", { vm.click(); boards = n }, Modifier.weight(1f), dark = n != boards, fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Back", vm::pop, Modifier.weight(1f), dark = true)
            BrassButton("Play My Run", { vm.startChallenge(friend, difficulty, boards) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun AsyncDonePanel(vm: GameViewModel, o: Overlay.AsyncDone) {
    val title = when {
        o.sent -> "Challenge Sent"
        o.won == true -> "You Win!"
        o.won == false -> "${o.rival} Wins"
        else -> "Run Complete"
    }
    GamePanel(title, null) {
        Text("Your score", style = bodyStyle(14.sp, dim()))
        Text("${o.score}", style = bodyStyle(34.sp, Palette.ledOrange, bold = true))
        Text(
            when {
                o.sent -> "${o.rival} has a week to beat it. You'll hear how it went next time you open the game."
                o.theirScore != null && o.won == true -> "You beat ${o.rival}'s ${o.theirScore}!"
                o.theirScore != null && o.won == false -> "${o.rival}'s ${o.theirScore} holds. Send a rematch?"
                else -> "Your result is saved."
            },
            style = bodyStyle(15.sp), textAlign = TextAlign.Center,
        )
        val friend = o.rivalId?.let { Rival(it, o.rival) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!o.sent && friend != null) BrassButton("Rematch", { vm.closeChallengeResult(); vm.push(Overlay.AsyncSetup(friend)) }, Modifier.weight(1f))
            BrassButton("Done", vm::closeChallengeResult, Modifier.weight(1f), dark = !o.sent && friend != null)
        }
    }
}

// ---- Match overlays -----------------------------------------------------------------------------------------------

@Composable
fun MatchLobbyPanel(vm: GameViewModel) {
    val m = vm.match ?: return
    GamePanel("1v1 Match", null) {
        val who = m.opponent?.name ?: "your opponent"
        Text(
            when (m.phase) {
                MatchPhase.CONNECTING -> "Connecting to the match..."
                else -> "Waiting for $who to join..."
            },
            style = bodyStyle(16.sp), textAlign = TextAlign.Center,
        )
        BrassButton("Leave", vm::leaveMatch, Modifier.fillMaxWidth(), dark = true)
    }
}

@Composable
fun ConfirmLeaveMatchPanel(vm: GameViewModel) {
    GamePanel("Leave the Match?", vm::pop) {
        Text("Leaving now forfeits the match to ${vm.match?.opponent?.name ?: "your opponent"}.", style = bodyStyle(), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Keep Playing", vm::pop, Modifier.weight(1f))
            BrassButton("Forfeit", vm::leaveMatch, Modifier.weight(1f), dark = true)
        }
    }
}

@Composable
fun MatchOverPanel(vm: GameViewModel) {
    val m = vm.match ?: return
    val r = m.result ?: return
    val who = m.opponent?.name ?: "Opponent"
    val title = when (r.won) { true -> "Victory!"; false -> "Defeat"; null -> "A Draw" }
    GamePanel(title, null) {
        Text(
            when (r.reason) {
                "race", "points" -> if (r.won == true) "You reached the goal first!" else "$who reached the goal first."
                "out" -> if (r.won == true) "$who's forge overflowed." else "Your forge overflowed."
                "forfeit" -> if (r.won == true) "$who left the match." else "You left the match."
                "both_out" -> "Both forges overflowed."
                "abandoned" -> "The match was abandoned."
                else -> "Time's up!"
            },
            style = bodyStyle(15.sp), textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            for ((name, score) in listOf("You" to r.myScore, who to r.theirScore)) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(name, style = bodyStyle(14.sp, dim(), bold = true))
                Text("$score", style = bodyStyle(26.sp, Palette.ledOrange, bold = true))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BrassButton("Rematch", vm::rematch, Modifier.weight(1f))
            BrassButton("Done", vm::leaveMatch, Modifier.weight(1f), dark = true)
        }
    }
}

// ---- In-game pieces -----------------------------------------------------------------------------------------------

private fun clock(seconds: Int) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** The opponent at a glance: name, score, how they're doing and the clock. Their board is behind Peek. */
@Composable
fun OpponentCard(vm: GameViewModel, m: MatchSession, modifier: Modifier = Modifier) {
    val o = m.opp
    val t = vm.fx.now
    Column(
        modifier.drawBehind {
            drawBrassPlate(Offset.Zero, size, 12.dp.toPx(), rivets = false, dark = true)
        }.padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.opponent?.name ?: "Opponent", style = bodyStyle(14.sp, Palette.goldLight, bold = true), maxLines = 1, modifier = Modifier.weight(1f))
            // Races and Survival have only a long safety cap, so the clock is shown for timed types.
            if (m.goal.timed) Text(clock(m.secondsLeft), style = bodyStyle(14.sp, if (m.secondsLeft < 30) Palette.ember else Palette.parchment, bold = true))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${o.score}", style = bodyStyle(18.sp, Palette.ledOrange, bold = true), modifier = Modifier.weight(1f))
            val status = when {
                !o.connected -> m.forfeitAt?.let { "Reconnecting ${((it - t).coerceAtLeast(0f)).toInt()}s" } ?: "Reconnecting"
                o.over -> "Out"
                else -> "Board ${o.board} · Forge ${o.forge}/3"
            }
            Text(status, style = bodyStyle(11.sp, if (!o.connected || o.over) Palette.ember else dim()))
        }
        Text(m.goal.label(m.difficulty), style = bodyStyle(11.sp, Palette.hint, bold = true), maxLines = 1)
        SmallBrass("Peek", { vm.push(Overlay.Peek) }, Modifier.fillMaxWidth())
        if (vm.state?.gameOver == true) Text("Your forge overflowed. Watch ${m.opponent?.name ?: "them"} finish...", style = bodyStyle(11.sp, Palette.ember), textAlign = TextAlign.Center)
        if (m.reconnecting) Text("Connection lost, reconnecting...", style = bodyStyle(11.sp, Palette.ember), textAlign = TextAlign.Center)
    }
}

/** Peek: the opponent's board, over your own until you close it. */
@Composable
fun PeekPanel(vm: GameViewModel) {
    val m = vm.match ?: return
    val o = m.opp
    GamePanel(m.opponent?.name ?: "Opponent", vm::pop, maxWidth = 620.dp) {
        Text("${o.score} · Board ${o.board} · Forge ${o.forge}/3" + if (o.over) " · Out" else "", style = bodyStyle(14.sp, Palette.goldLight, bold = true))
        val t = vm.fx.now
        Canvas(Modifier.fillMaxWidth().aspectRatio(COLS / ROWS.toFloat())) {
            val cell = size.width / COLS
            for (i in 0 until ROWS * COLS) {
                val tl = Offset((i % COLS) * cell, (i / COLS) * cell)
                val gold = o.gold.getOrNull(i) == '1'
                drawRect(if (gold) Palette.gold else Palette.lead, tl, Size(cell - 1f, cell - 1f))
                MatchSession.decodeCell(o.cells.getOrElse(i) { MatchSession.EMPTY })?.let { p ->
                    drawPiece(p, tl + Offset(cell / 2, cell / 2), cell, time = t)
                }
            }
        }
        BrassButton("Back to My Board", vm::pop, Modifier.fillMaxWidth())
    }
}

/** Chat with the opponent during a match (friends only), along the bottom of the screen. */
@Composable
fun MatchChatButton(vm: GameViewModel, modifier: Modifier = Modifier) {
    val m = vm.match ?: return
    val friend = m.opponent?.let { vm.chatFriend(it.playerId) } ?: return
    val n = vm.unread[friend.playerId] ?: 0
    SmallBrass(if (n > 0) "Chat with ${friend.name} ($n)" else "Chat with ${friend.name}", { vm.openChat(friend) }, modifier)
}

/** Big 3-2-1 over the board before a match starts. */
@Composable
fun MatchCountdown(vm: GameViewModel) {
    val m = vm.match ?: return
    if (m.phase != MatchPhase.COUNTDOWN) return
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("vs ${m.opponent?.name ?: ""}", style = bodyStyle(20.sp, Palette.parchment, bold = true))
            Text("${m.countdown.coerceAtLeast(1)}", style = titleStyle(96.sp))
        }
    }
}

// ---- Notifications ------------------------------------------------------------------------------------------------

/** Friend requests and challenges, shown over whatever screen is up. */
@Composable
fun IncomingBanner(vm: GameViewModel) {
    val online = vm.online
    val invite = online.invites.firstOrNull { it.incoming && it.status == "pending" }
    val request = online.incoming.firstOrNull()
    if (vm.match != null || (invite == null && request == null)) return
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(10.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().clickable(enabled = false) {}.drawBehind {
                drawBrassPlate(Offset.Zero, size, 14.dp.toPx(), rivets = true, dark = true)
            }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (invite != null) {
                val article = if (invite.difficulty.displayName.first().lowercaseChar() in "aeiou") "an" else "a"
                Text("${invite.name} challenges you to $article ${invite.difficulty.displayName} 1v1!", style = bodyStyle(15.sp, Palette.goldLight, bold = true), textAlign = TextAlign.Center)
                Text(invite.goal.label(invite.difficulty), style = bodyStyle(13.sp, Palette.parchment))
                if (vm.screen == Screen.GAME) Text("Your current game is saved.", style = bodyStyle(12.sp, dim()))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallBrass("Accept", { vm.respondToChallenge(invite.id, true) }, Modifier.weight(1f))
                    BrassButton("Decline", { vm.respondToChallenge(invite.id, false) }, Modifier.weight(1f), dark = true, fontSize = 14.sp, minHeight = 38.dp)
                }
            } else if (request != null) {
                Text("${request.name} wants to connect with you.", style = bodyStyle(15.sp, Palette.goldLight, bold = true), textAlign = TextAlign.Center)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmallBrass("Accept", { vm.respondToRequest(request.id, true) }, Modifier.weight(1f))
                    BrassButton("Deny", { vm.respondToRequest(request.id, false) }, Modifier.weight(1f), dark = true, fontSize = 14.sp, minHeight = 38.dp)
                }
            }
        }
    }
}

@Composable
fun NoticeToast(vm: GameViewModel) {
    val text = vm.notice ?: return
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.widthIn(max = 520.dp).drawBehind { drawBrassPlate(Offset.Zero, size, 12.dp.toPx(), rivets = false, dark = true) }
                .clickable { vm.dismissNotice() }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = bodyStyle(14.sp, Palette.parchment), modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(12.dp))
            if (vm.noticeChatFrom != null) {
                Text("Read", style = bodyStyle(14.sp, Palette.goldLight, bold = true), modifier = Modifier.clickable { vm.openNoticeChat() })
                Spacer(Modifier.width(16.dp))
            }
            Text("OK", style = bodyStyle(14.sp, Palette.goldLight, bold = true))
        }
    }
}

// ---- Encrypted chat -------------------------------------------------------------------------------------------------

@Composable
fun ChatPanel(vm: GameViewModel, friendId: String) {
    val friend = vm.chatFriend(friendId)
    var draft by rememberSaveable(friendId) { mutableStateOf("") }
    var showCode by rememberSaveable { mutableStateOf(false) }
    GamePanel(friend?.name ?: "Chat", vm::pop, maxWidth = 560.dp) {
        if (friend == null) {
            Text("They're no longer on your friends list.", style = bodyStyle())
        } else {
            Text("End-to-end encrypted: only you and ${friend.name} can read these.", style = bodyStyle(11.sp, Palette.hint, bold = true), textAlign = TextAlign.Center)
            val lines = vm.chatLines.takeLast(60)
            if (lines.isEmpty()) Text("Say hello!", style = bodyStyle(13.sp, dim()))
            for (l in lines) Row(Modifier.fillMaxWidth(), horizontalArrangement = if (l.fromMe) Arrangement.End else Arrangement.Start) {
                Text(
                    l.text + if (l.failed && l.fromMe) "  (not sent)" else "",
                    style = bodyStyle(14.sp, if (l.failed) Palette.ember else Palette.parchment),
                    modifier = Modifier.widthIn(max = 380.dp)
                        .background(if (l.fromMe) Palette.brassDark.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            val send = { vm.sendChat(friend, draft); draft = "" }
            OutlinedTextField(
                draft, { draft = it.take(com.geoguy89.refinersfire.net.CHAT_MAX_CHARS) },
                label = { Text("Message", style = bodyStyle(12.sp)) },
                textStyle = bodyStyle(15.sp), colors = fieldColors(), maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrassButton("Safety Code", { vm.click(); showCode = !showCode }, Modifier.weight(1f), dark = true, fontSize = 13.sp, minHeight = 38.dp)
                SmallBrass("Send", send, Modifier.weight(1f), enabled = draft.isNotBlank())
            }
            if (showCode) {
                val code = vm.safetyCode(friend)
                Text(
                    if (code == null) "${friend.name} hasn't opened the new version yet."
                    else "$code\nIf ${friend.name} sees the same number, nobody can be listening in.",
                    style = bodyStyle(13.sp, Palette.goldLight), textAlign = TextAlign.Center,
                )
            }
        }
        BrassButton("Close", vm::pop, Modifier.fillMaxWidth())
    }
}
