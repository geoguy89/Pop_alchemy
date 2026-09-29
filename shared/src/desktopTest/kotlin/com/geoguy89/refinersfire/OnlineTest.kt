package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.net.JvmChatCrypto
import com.geoguy89.refinersfire.net.MatchGoal
import com.geoguy89.refinersfire.net.MatchPhase
import com.geoguy89.refinersfire.net.OkHttpTransport
import com.geoguy89.refinersfire.net.OnlineConfig
import com.geoguy89.refinersfire.net.Sealed
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import androidx.compose.ui.use

class OnlineTest {
    @Test
    fun chatCryptoRoundTripsAndRejectsTampering() {
        val a = JvmChatCrypto.newKeyPair()
        val b = JvmChatCrypto.newKeyPair()
        val sealed = JvmChatCrypto.seal(a.privateKey, b.publicKey, "idA", "idB", "Meet me on Board 5 🔥")
        assertEquals("Meet me on Board 5 🔥", JvmChatCrypto.open(b.privateKey, a.publicKey, "idA", "idB", sealed))
        assertNull("wrong direction fails", JvmChatCrypto.open(b.privateKey, a.publicKey, "idB", "idA", sealed))
        val flipped = sealed.ct.toCharArray().also { it[3] = if (it[3] == 'A') 'B' else 'A' }.concatToString()
        assertNull("tampered ciphertext fails", JvmChatCrypto.open(b.privateKey, a.publicKey, "idA", "idB", Sealed(sealed.nonce, flipped)))
        val eve = JvmChatCrypto.newKeyPair()
        assertNull("a third key can't read it", JvmChatCrypto.open(eve.privateKey, a.publicKey, "idA", "idB", sealed))
        assertEquals(JvmChatCrypto.safetyCode(a.publicKey, b.publicKey), JvmChatCrypto.safetyCode(b.publicKey, a.publicKey))
        assertNotEquals(JvmChatCrypto.safetyCode(a.publicKey, b.publicKey), JvmChatCrypto.safetyCode(a.publicKey, eve.publicKey))
    }

    @Test
    fun matchPlayersGetTheSamePieces() {
        val a = GameEngine.newMatch(Difficulty.EASY, 12345)
        val b = GameEngine.newMatch(Difficulty.EASY, 12345)
        assertEquals(Piece.Cornerstone, a.state.current)
        // Different moves, same sequence of pieces.
        a.play(GameEngine.index(0, 0)); b.play(GameEngine.index(7, 8))
        val seqA = mutableListOf<Piece>(); val seqB = mutableListOf<Piece>()
        repeat(15) {
            seqA += a.state.current; seqB += b.state.current
            a.discard(); b.discard()
            if (a.state.gameOver || b.state.gameOver) return@repeat
            a.play(a.validCells().firstOrNull() ?: return@repeat)
            b.play(b.validCells().lastOrNull() ?: return@repeat)
        }
        assertEquals(seqA.take(3), seqB.take(3))
        assertTrue((0 until 50).map { GameEngine.matchPiece(99, it, 1) } != (0 until 50).map { GameEngine.matchPiece(100, it, 1) })
    }

    // ---- Against a test server (skipped unless REFINERS_TEST_SERVER is set) ---------------------------------------

    private fun device(): GameViewModel =
        GameViewModel(Store(MemoryKeyValueStore()), SilentAudio, http = OkHttpTransport(), crypto = JvmChatCrypto).also { it.onAppForeground() }

    private var clock = 1_000_000_000L

    /** Snapshot a device's screen, as the renders in DesktopRenderTest do. */
    /** Desktop window by default; [phone] renders a Pixel-sized 1080x2400 screen at 2.625x. */
    private fun shot(vm: GameViewModel, name: String, phone: Boolean = false) {
        val (w, h, d) = if (phone) Triple(1080, 2400, 2.625f) else Triple(1280, 860, 1.25f)
        androidx.compose.ui.ImageComposeScene(w, h, androidx.compose.ui.unit.Density(d)) {
            com.geoguy89.refinersfire.ui.RefinersFireApp(vm)
        }.use { scene ->
            var t = clock
            repeat(12) { scene.render(t); t += 40_000_000L }
            clock = t
            java.io.File("build/screenshots").mkdirs()
            java.io.File("build/screenshots/$name.png").writeBytes(scene.render(t).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
        }
    }

    /** Run both devices' frames in real time until [done], or fail after [seconds]. */
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

    @Test
    fun anAccountTheServerDoesNotKnowIsReplaced() {
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue("set REFINERS_TEST_SERVER to run", server != null)
        OnlineConfig.baseUrl = server!!
        // As if the game had registered with a server that has since been replaced.
        val stale = com.geoguy89.refinersfire.net.Account("0123456789abcdef0123456789abcdef", "notARealSecretNotARealSecret", "AAAA-AAAA-AAAA", "Zed")
        val store = Store(MemoryKeyValueStore()).also {
            it.saveSettings(com.geoguy89.refinersfire.data.Settings(playerName = "Zed", nameChosen = true))
            it.saveAccount(stale)
        }
        val vm = GameViewModel(store, SilentAudio, http = OkHttpTransport(), crypto = JvmChatCrypto).also { it.onAppForeground() }
        until(vm, what = "re-registered") { vm.online.account != null && vm.online.account != stale }
        assertEquals("Zed", vm.online.account!!.name)
        assertEquals(vm.online.account, store.loadAccount())
    }

    @Test
    fun friendsChatAndAMatchEndToEnd() {
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue("set REFINERS_TEST_SERVER to run", server != null)
        OnlineConfig.baseUrl = server!!
        val ann = device(); val bob = device()
        assertTrue(ann.choosePlayerName("Ann"))
        assertTrue(bob.choosePlayerName("Bob"))

        ann.openFriends(); bob.openFriends()
        until(ann, bob, what = "accounts") { ann.online.account != null && bob.online.account != null }
        assertNotEquals(ann.online.account!!.friendCode, bob.online.account!!.friendCode)

        ann.addFriendByCode(bob.online.account!!.friendCode.lowercase())
        until(ann, bob, what = "request arrives") { bob.online.incoming.isNotEmpty() }
        bob.respondToRequest(bob.online.incoming.first().id, true)
        until(ann, bob, what = "friends, with keys") {
            ann.online.friends.any { it.name == "Bob" && it.publicKey != null } && bob.online.friends.any { it.name == "Ann" && it.publicKey != null }
        }

        val bobAsFriend = ann.online.friends.first()
        val annAsFriend = bob.online.friends.first()
        assertEquals("both see the same safety code", ann.safetyCode(bobAsFriend), bob.safetyCode(annAsFriend))
        ann.sendChat(bobAsFriend, "Ready to lose?")
        until(ann, bob, what = "chat arrives") { (bob.unread[annAsFriend.playerId] ?: 0) > 0 }
        bob.openChat(annAsFriend)
        assertEquals("Ready to lose?", bob.chatLines.last().text)
        assertTrue(!bob.chatLines.last().failed)

        bob.pop()
        shot(ann, "online_friends")
        bob.openChat(annAsFriend)
        shot(bob, "online_chat", phone = true)
        bob.pop()
        ann.pop()
        ann.push(Overlay.Challenge(com.geoguy89.refinersfire.net.Rival(bobAsFriend.playerId, bobAsFriend.name)))
        shot(ann, "online_challenge")
        ann.pop()
        ann.challenge(com.geoguy89.refinersfire.net.Rival(bobAsFriend.playerId, bobAsFriend.name), Difficulty.AVERAGE, MatchGoal("stoke", 5))
        until(ann, bob, what = "challenge arrives") { bob.online.invites.any { it.incoming && it.status == "pending" } }
        shot(bob, "online_invite_banner", phone = true)
        val invite = bob.online.invites.first { it.incoming }
        assertEquals(Difficulty.AVERAGE, invite.difficulty)
        bob.respondToChallenge(invite.id, true)
        until(ann, bob, what = "both in the match") {
            ann.match?.phase in setOf(MatchPhase.COUNTDOWN, MatchPhase.PLAYING) && bob.match?.phase in setOf(MatchPhase.COUNTDOWN, MatchPhase.PLAYING)
        }
        assertEquals("same difficulty on both", ann.state!!.difficulty, bob.state!!.difficulty)
        assertEquals(6, ann.state!!.board)
        assertEquals("same seed", ann.state!!.matchSeed, bob.state!!.matchSeed)

        until(ann, bob, what = "countdown over") { ann.match?.phase == MatchPhase.PLAYING && bob.match?.phase == MatchPhase.PLAYING }
        ann.tapCell(GameEngine.index(3, 3))
        val annScore = ann.state!!.score
        until(ann, bob, what = "Bob sees Ann's move") { bob.match?.opp?.score == annScore }
        // Stoke Duel: Ann's cleared line raises Bob's forge, and Ann sees it on his board.
        val bobForge = bob.state!!.forge
        ann.match!!.stoke(1)
        until(ann, bob, what = "Bob is stoked") { bob.state!!.forge == bobForge + 1 }
        until(ann, bob, what = "Ann sees Bob's forge rise") { ann.match?.opp?.forge == bobForge + 1 }
        shot(bob, "online_match_desktop")
        shot(bob, "online_match_phone", phone = true)

        // The test server's match lasts 5 seconds.
        until(ann, bob, seconds = 20, what = "match ends") { ann.overlay == Overlay.MatchOver && bob.overlay == Overlay.MatchOver }
        shot(ann, "online_match_over")
        assertEquals(true, ann.match!!.result!!.won)
        assertEquals(false, bob.match!!.result!!.won)
        ann.leaveMatch()
        assertNull(ann.match)
        assertNull("matches never overwrite the saved single-player game", ann.savedGame?.matchSeed)
        until(ann, bob, what = "record updated") { ann.online.friends.firstOrNull()?.wins == 1 }

        // Async challenge: Ann plays first, Bob replies later with the same run.
        ann.startChallenge(com.geoguy89.refinersfire.net.Rival(ann.online.friends.first().playerId, ann.online.friends.first().name), Difficulty.EASY, 3)
        until(ann, bob, what = "Ann's run starts") { ann.state?.challengeId != null }
        val seed = ann.state!!.matchSeed
        ann.tapCell(GameEngine.index(4, 4))
        var guard = 0
        while (ann.state?.challengeId != null && ann.overlay !is Overlay.AsyncDone && guard++ < 20) ann.discard()
        assertTrue(ann.overlay is Overlay.AsyncDone)
        assertTrue((ann.overlay as Overlay.AsyncDone).sent)
        until(ann, bob, what = "Bob's move") { bob.ourMoves.isNotEmpty() }
        assertTrue("Bob is told once", bob.notice?.contains("challenged you") == true)
        val c = bob.ourMoves.first()
        bob.playChallenge(c)
        assertEquals("same run", seed, bob.state!!.matchSeed)
        // Bob never closed the earlier match result: the finished match must not lock the challenge board.
        bob.tapCell(GameEngine.index(0, 0))
        assertEquals("the move counts", 1, bob.state!!.stonesPlaced)
        val midRun = bob.state!!
        bob.quitToTitle()
        assertEquals("the unfinished run is kept", c.id, bob.savedChallenge?.challengeId)
        bob.resumeChallenge()
        assertEquals(midRun.cells, bob.state!!.cells)
        guard = 0
        while (bob.overlay !is Overlay.AsyncDone && guard++ < 20) bob.discard()
        assertTrue(bob.overlay is Overlay.AsyncDone)
        bob.closeChallengeResult()
        until(ann, bob, what = "both see the result") {
            ann.online.asyncChallenges.any { it.id == c.id && it.status == "done" } && bob.online.asyncChallenges.any { it.id == c.id && it.status == "done" }
        }
        assertNull("single-player save untouched by challenges", ann.savedGame?.challengeId)

        // Hide activity: Ann sees Bob's activity as hidden, never online.
        bob.setHideActivity(true)
        until(ann, bob, what = "Bob's activity hidden") { ann.online.friends.firstOrNull()?.activityHidden == true }
        assertEquals(false, ann.online.friends.first().online)
        assertNull(ann.online.friends.first().lastSeenAgoMs)

        // Global: Cat isn't a friend, but she and Ann are both Global, so Ann can find her and challenge her.
        val cat = device()
        assertTrue(cat.choosePlayerName("Cat"))
        cat.setShareMode(com.geoguy89.refinersfire.ui.ShareMode.PLUS)
        ann.setShareMode(com.geoguy89.refinersfire.ui.ShareMode.PLUS)
        until(ann, bob, cat, what = "Cat registered") { cat.online.account != null }
        val catId = cat.online.account!!.playerId
        cat.online.pushScores(listOf(com.geoguy89.refinersfire.data.HighScore("Cat", 7777, "", 4, Difficulty.EASY, com.geoguy89.refinersfire.game.GameMode.STRATEGIC, 1)), true)
        ann.openHallOfFame()
        until(ann, bob, cat, what = "Cat on the leaderboard") { ann.online.fetchLeaderboard(); ann.online.leaderboard.any { it.playerId == catId } }
        val catEntry = ann.online.leaderboard.first { it.playerId == catId }
        assertTrue(!ann.isFriend(catId) && ann.canChallenge(catEntry))
        shot(ann, "online_global_board")
        ann.push(Overlay.PlayerCard(catEntry))
        shot(ann, "online_player_card", phone = true)
        ann.pop()
        ann.pop()
        ann.startChallenge(ann.rivalOf(catEntry), Difficulty.EASY, 1)
        until(ann, bob, cat, what = "stranger challenge starts") { ann.state?.challengeRival == "Cat" }
    }
}
