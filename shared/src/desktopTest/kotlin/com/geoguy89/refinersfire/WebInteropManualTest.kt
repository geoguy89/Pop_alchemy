package com.geoguy89.refinersfire

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
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * A desktop player against a person playing the web version in a browser (run by hand: set WEB_CODE to the web
 * player's friend code and REFINERS_TEST_SERVER). Progress is written to build/web-interop.log.
 */
class WebInteropManualTest {
    private var clock = 5_000_000_000L
    private val log = File("build/web-interop.log").apply { writeText("") }
    private fun say(s: String) { log.appendText("$s\n"); println(s) }

    private fun until(vm: GameViewModel, seconds: Int, what: String, done: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            clock += 50_000_000L
            vm.onFrame(clock)
            if (done()) { say("OK: $what"); return true }
            Thread.sleep(50)
        }
        say("TIMED OUT: $what")
        return false
    }

    @Test
    fun desktopMeetsTheWebVersion() {
        val code = System.getenv("WEB_CODE")
        val server = System.getenv("REFINERS_TEST_SERVER")
        assumeTrue(code != null && server != null)
        OnlineConfig.baseUrl = server!!
        val desk = GameViewModel(Store(MemoryKeyValueStore()), SilentAudio, http = OkHttpTransport(), crypto = JvmChatCrypto).also { it.onAppForeground() }
        desk.choosePlayerName("Desk")
        desk.openFriends()
        until(desk, 20, "desktop account") { desk.online.account != null }
        desk.addFriendByCode(code!!)
        say("WAITING: accept Desk's friend request on the web")
        if (!until(desk, 120, "friends with the web player") { desk.online.friends.any { it.publicKey != null } }) return
        val web = desk.online.friends.first()
        desk.sendChat(web, "Hello web, from the desktop!")
        say("SENT: chat")
        // The web player answers in chat; the desktop must decrypt it.
        say("WAITING: reply in chat from the web")
        until(desk, 120, "reply decrypted on desktop") { desk.store().loadChat(web.playerId).any { !it.fromMe && !it.failed } }
        desk.store().loadChat(web.playerId).filter { !it.fromMe }.forEach { say("WEB SAID: ${it.text} (failed=${it.failed})") }
        desk.pop()
        desk.challenge(Rival(web.playerId, web.name), Difficulty.AVERAGE, MatchGoal("time", 5), GameMode.STRATEGIC)
        say("WAITING: accept the 1v1 on the web")
        if (!until(desk, 120, "match playing") { desk.match?.phase == MatchPhase.PLAYING }) return
        say("WAITING: place stones on the web")
        until(desk, 120, "web player's moves arrive") { (desk.match?.opp?.score ?: 0) > 0 }
        repeat(4) { desk.validCellsForTest().firstOrNull()?.let(desk::tapCell) ?: desk.discard() }
        until(desk, 5, "desktop moved") { true }
        say("DESKTOP SCORE: ${desk.state?.score}  WEB SCORE: ${desk.match?.opp?.score}")
        say("WAITING: 20s for the web to see desktop's score")
        until(desk, 20, "pause") { false }
        desk.leaveMatch()
        say("DONE")
    }
}

/** Test access to a view model's storage. */
private fun GameViewModel.store(): Store = GameViewModel::class.java.getDeclaredField("store").apply { isAccessible = true }.get(this) as Store
