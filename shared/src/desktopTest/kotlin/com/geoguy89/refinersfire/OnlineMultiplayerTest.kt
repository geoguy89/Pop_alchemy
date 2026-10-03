package com.geoguy89.refinersfire

import androidx.compose.ui.use
import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.net.JvmChatCrypto
import com.geoguy89.refinersfire.net.MatchGoal
import com.geoguy89.refinersfire.net.MatchPhase
import com.geoguy89.refinersfire.net.OkHttpTransport
import com.geoguy89.refinersfire.net.OnlineConfig
import com.geoguy89.refinersfire.net.Rival
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Co-op and gatherings through the real app, against a short-timer test server (REFINERS_TEST_SERVER). */
class OnlineMultiplayerTest {
    private var clock = 3_000_000_000L

    private fun device(): GameViewModel =
        GameViewModel(Store(MemoryKeyValueStore()), SilentAudio, http = OkHttpTransport(), crypto = JvmChatCrypto).also { it.onAppForeground() }

    private fun until(vararg vms: GameViewModel, seconds: Int = 20, what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            clock += 50_000_000L
            vms.forEach { it.onFrame(clock) }
            if (done()) return
            Thread.sleep(50)
        }
        throw AssertionError("timed out: $what")
    }

    private fun shot(vm: GameViewModel, name: String) {
        repeat(60) { clock += 50_000_000L; vm.onFrame(clock) }
        vm.dismissNotice()
        androidx.compose.ui.ImageComposeScene(824, 1830, androidx.compose.ui.unit.Density(2f)) {
            com.geoguy89.refinersfire.ui.RefinersFireApp(vm)
        }.use { scene ->
            var t = clock
            repeat(12) { scene.render(t); t += 40_000_000L }
            clock = t
            java.io.File("build/screenshots").mkdirs()
            java.io.File("build/screenshots/$name.png").writeBytes(scene.render(t).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
    }

    private fun befriend(a: GameViewModel, b: GameViewModel) {
        a.addFriendByCode(b.online.account!!.friendCode)
        until(a, b, what = "request") { b.online.incoming.isNotEmpty() }
        b.respondToRequest(b.online.incoming.first().id, true)
        until(a, b, what = "friends") { a.online.friends.any { it.playerId == b.online.account!!.playerId } && b.online.friends.any { it.playerId == a.online.account!!.playerId } }
    }

    private fun named(name: String): GameViewModel = device().also { assertTrue(it.choosePlayerName(name)); it.openFriends() }

    private fun sameGame(a: GameViewModel, b: GameViewModel) =
        a.state != null && a.state!!.cells == b.state!!.cells && a.state!!.score == b.state!!.score && a.state!!.forge == b.state!!.forge &&
            a.state!!.current == b.state!!.current

    @Test
    fun coopTwoPlayersOneBoard() {
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue("set REFINERS_TEST_SERVER to run", server != null)
        OnlineConfig.baseUrl = server!!
        val ann = named("Ann"); val bob = named("Bob")
        until(ann, bob, what = "accounts") { ann.online.account != null && bob.online.account != null }
        befriend(ann, bob)
        ann.challenge(Rival(bob.online.account!!.playerId, "Bob"), Difficulty.AVERAGE, MatchGoal("coop", 0), GameMode.FORESIGHT)
        until(ann, bob, what = "co-op invite") { bob.online.invites.any { it.incoming && it.goal.coop } }
        bob.respondToChallenge(bob.online.invites.first { it.incoming }.id, true)
        until(ann, bob, seconds = 30, what = "playing") { ann.match?.phase == MatchPhase.PLAYING && bob.match?.phase == MatchPhase.PLAYING }
        assertTrue("Ann invited, so she goes first", ann.match!!.isMyTurn)
        assertTrue(!bob.match!!.isMyTurn)
        assertEquals("hints are shared in co-op", true, ann.hintLabel.startsWith("Hint"))

        // Bob can't move on Ann's turn.
        val before = bob.state!!.cells
        bob.tapCell(bob.validCellsForTest().first())
        until(ann, bob, seconds = 2, what = "nothing") { true }
        assertEquals(before, bob.state!!.cells)

        // Take turns: whoever's turn it is places a stone (or melts one); both boards stay identical.
        repeat(8) { k ->
            val mover = if (ann.match!!.isMyTurn) ann else bob
            val applied = mover.match!!.coopApplied
            val cell = mover.validCellsForTest().firstOrNull()
            if (cell != null) mover.tapCell(cell) else mover.discard()
            until(ann, bob, what = "move $k reached both") { ann.match!!.coopApplied > applied && bob.match!!.coopApplied > applied }
            until(ann, bob, seconds = 3, what = "same game after move $k") { sameGame(ann, bob) }
        }
        shot(ann, "coop_phone")

        // Ann leaves: it's over for both, with the score they made together.
        val score = ann.state!!.score
        ann.leaveMatch()
        until(bob, seconds = 15, what = "Bob sees it end") { bob.overlay == Overlay.MatchOver }
        assertEquals(score, bob.match!!.result!!.myScore)
        shot(bob, "coop_over_phone")
        bob.leaveMatch()
        until(ann, bob, seconds = 15, what = "best together recorded") {
            ann.online.sync()
            (ann.online.friends.firstOrNull()?.coopBest ?: 0) == score || score == 0L
        }
    }

    @Test
    fun aGatheringOfThree() {
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue("set REFINERS_TEST_SERVER to run", server != null)
        OnlineConfig.baseUrl = server!!
        val ann = named("Ann"); val bob = named("Bob"); val cat = named("Cat")
        until(ann, bob, cat, what = "accounts") { listOf(ann, bob, cat).all { it.online.account != null } }
        befriend(ann, bob); befriend(ann, cat)
        ann.pop(); bob.pop(); cat.pop()

        ann.createGathering(listOf(bob.online.account!!.playerId, cat.online.account!!.playerId), Difficulty.HARD, GameMode.IRON_FORGE, 5)
        until(ann, what = "Ann in the lobby") { ann.match?.isGathering == true && ann.match?.roster?.size == 3 }
        shot(ann, "gathering_lobby_phone")
        until(ann, bob, cat, what = "invites") { bob.online.gatherings.any { it.myStatus == "invited" } && cat.online.gatherings.any { it.myStatus == "invited" } }
        shot(bob, "gathering_invite_phone")
        bob.respondToGathering(bob.online.gatherings.first().id, true)
        cat.respondToGathering(cat.online.gatherings.first().id, true)
        // Everyone's here, so it starts by itself.
        until(ann, bob, cat, seconds = 30, what = "all playing") { listOf(ann, bob, cat).all { it.match?.phase == MatchPhase.PLAYING } }
        assertEquals(ann.state!!.current, cat.state!!.current)
        assertEquals("No Hints Here", bob.hintLabel)
        assertEquals(1, bob.state!!.forgeCapacity)
        // Play a few stones each; everyone's standings fill in.
        repeat(4) {
            for (vm in listOf(ann, bob, cat)) vm.validCellsForTest().firstOrNull()?.let(vm::tapCell) ?: vm.discard()
        }
        until(ann, bob, cat, what = "standings") { ann.match!!.peers.size == 2 && ann.match!!.peers.values.all { it.score > 0 } }
        shot(ann, "gathering_play_phone")
        // The short test clock runs out: standings for all three.
        until(ann, bob, cat, seconds = 30, what = "gathering over") { listOf(ann, bob, cat).all { it.overlay == Overlay.MatchOver } }
        assertEquals(3, ann.match!!.result!!.standings.size)
        shot(ann, "gathering_over_phone")
    }
}
