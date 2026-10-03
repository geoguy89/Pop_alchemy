package com.geoguy89.refinersfire

import androidx.compose.ui.use
import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.net.BoardKey
import com.geoguy89.refinersfire.net.JvmChatCrypto
import com.geoguy89.refinersfire.net.MatchGoal
import com.geoguy89.refinersfire.net.OkHttpTransport
import com.geoguy89.refinersfire.net.OnlineConfig
import com.geoguy89.refinersfire.net.Rival
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import com.geoguy89.refinersfire.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The newer online features end to end, against a test server (skipped unless REFINERS_TEST_SERVER is set). */
class OnlineFeaturesTest {
    private var clock = 2_000_000_000L

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

    /** Let pop-ups (notices, achievement banners) clear before a picture. */
    private fun quiet(vm: GameViewModel) {
        repeat(240) { clock += 50_000_000L; vm.onFrame(clock) }
        vm.dismissNotice()
    }

    private fun shot(vm: GameViewModel, name: String) {
        quiet(vm)
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

    private fun friends(a: GameViewModel, b: GameViewModel) {
        a.openFriends(); b.openFriends()
        until(a, b, what = "accounts") { a.online.account != null && b.online.account != null }
        a.addFriendByCode(b.online.account!!.friendCode)
        until(a, b, what = "request") { b.online.incoming.isNotEmpty() }
        b.respondToRequest(b.online.incoming.first().id, true)
        until(a, b, what = "friends") { a.online.friends.isNotEmpty() && b.online.friends.isNotEmpty() }
        a.pop(); b.pop()
    }

    /** Finish the current Manna run by playing the first legal square (or melting) until it ends. */
    private fun finishManna(vm: GameViewModel) {
        var guard = 0
        while (vm.overlay !is Overlay.MannaDone && guard++ < 2000) {
            if (vm.overlay is Overlay.BoardComplete) { vm.continueAfterBoard(); continue }
            val cell = vm.validCellsForTest().firstOrNull()
            if (cell != null) vm.tapCell(cell) else vm.discard()
        }
        assertTrue("the run finished", vm.overlay is Overlay.MannaDone)
    }

    @Test
    fun mannaStandingsAndAGhostToRace() {
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue("set REFINERS_TEST_SERVER to run", server != null)
        OnlineConfig.baseUrl = server!!
        val ann = device(); val bob = device()
        assertTrue(ann.choosePlayerName("Ann")); assertTrue(bob.choosePlayerName("Bob"))
        friends(ann, bob)

        // Bob gathers today's Manna first.
        bob.openManna()
        until(bob, what = "Bob's board") { bob.online.manna != null }
        bob.playManna()
        assertEquals(Screen.GAME, bob.screen)
        finishManna(bob)
        val bobScore = (bob.overlay as Overlay.MannaDone).result.score
        until(bob, what = "Bob's result delivered") { bob.online.manna?.mine != null }
        assertEquals(bobScore, bob.online.manna!!.mine!!.score)
        shot(bob, "manna_done_phone")

        // Ann sees Bob on today's board, with his run to race.
        ann.openManna()
        until(ann, what = "Ann's board shows Bob") { ann.online.manna?.friends?.any { it.name == "Bob" } == true }
        assertNotNull(ann.online.manna!!.ghost)
        shot(ann, "manna_panel_phone")
        ann.playManna()
        assertEquals("Bob", ann.state!!.ghostName)
        repeat(6) { ann.validCellsForTest().firstOrNull()?.let(ann::tapCell) ?: ann.discard() }
        shot(ann, "manna_ghost_phone")
    }

    @Test
    fun globalSlicesAndAnIronForgeMatch() {
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue("set REFINERS_TEST_SERVER to run", server != null)
        OnlineConfig.baseUrl = server!!
        val cat = device(); val dan = device()
        assertTrue(cat.choosePlayerName("Cat")); assertTrue(dan.choosePlayerName("Dan"))
        friends(cat, dan)

        // A Global slice comes back narrowed, with Cat's own standing.
        val key = BoardKey(Difficulty.HARD, GameMode.IRON_FORGE, week = true)
        cat.online.pushScores(listOf(com.geoguy89.refinersfire.data.HighScore("Cat", 77_000, "x", 12, Difficulty.HARD, GameMode.IRON_FORGE, epochMillis())), true)
        until(cat, what = "uploaded") { true }
        Thread.sleep(500)
        cat.online.fetchLeaderboard(key)
        until(cat, what = "slice") { cat.online.boards[key]?.me != null }
        assertEquals(77_000L, cat.online.boards[key]!!.me!!.best)
        assertTrue(cat.online.boards[key]!!.players.all { it.difficulty == Difficulty.HARD && it.mode == GameMode.IRON_FORGE })

        // A live match under Iron Forge rules: both players get a one-level forge.
        cat.challenge(Rival(dan.online.account!!.playerId, "Dan"), Difficulty.HARD, MatchGoal("time", 5), GameMode.IRON_FORGE)
        until(cat, dan, what = "invite arrives") { dan.online.invites.any { it.incoming && it.status == "pending" } }
        assertEquals(GameMode.IRON_FORGE, dan.online.invites.first { it.incoming }.mode)
        dan.respondToChallenge(dan.online.invites.first { it.incoming }.id, true)
        until(cat, dan, seconds = 30, what = "both playing") { cat.state?.matchSeed != null && dan.state?.matchSeed != null }
        assertEquals(1, cat.state!!.forgeCapacity)
        assertEquals(1, dan.state!!.forgeCapacity)
        assertEquals("No Hints in 1v1", cat.hintLabel)
        cat.leaveMatch(); dan.leaveMatch()
    }
}
