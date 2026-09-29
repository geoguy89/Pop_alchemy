package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Settings
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.net.AppUpdater
import com.geoguy89.refinersfire.net.ReleaseFeed
import com.geoguy89.refinersfire.net.UpdateInfo
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateTest {
    @Test
    fun parsesARealGitHubRelease() {
        val body = javaClass.getResource("/github-latest-release.json")!!.readText()
        val info = ReleaseFeed.parse(body)
        assertNotNull(info)
        assertEquals("1.2.0", info!!.version)
        assertEquals(5, info.build)
        assertTrue(info.apkUrl.endsWith("/RefinersFire-1.2.0-android.apk"))
        assertNull("junk is ignored", ReleaseFeed.parse("{\"tag_name\":\"nightly\"}"))
    }

    private class FakeUpdater(override val installedBuild: Int, private val latest: UpdateInfo?) : AppUpdater {
        override val supported = true
        var installed: UpdateInfo? = null
        override fun latest(done: (UpdateInfo?) -> Unit) = done(latest)
        override fun install(info: UpdateInfo, progress: (Float) -> Unit, done: (String?) -> Unit) {
            progress(1f); installed = info; done(null)
        }
    }

    private fun vm(updater: AppUpdater, settings: Settings = Settings(playerName = "Alex", nameChosen = true)) =
        GameViewModel(Store(MemoryKeyValueStore()).also { it.saveSettings(settings) }, SilentAudio, updater = updater)

    private val newer = UpdateInfo("1.4.0", 20, "https://example.invalid/a.apk", 1, "")

    @Test
    fun offeredOnlyOnTheTitleScreenAndInstalls() {
        val up = FakeUpdater(installedBuild = 10, latest = newer)
        val vm = vm(up)
        vm.startNewGame(Difficulty.EASY, GameMode.STRATEGIC)
        vm.onAppForeground()
        var t = 1L
        repeat(5) { vm.onFrame(t); t += 50_000_000 }
        assertEquals(newer, vm.update)
        assertTrue("never interrupts a game", vm.overlay != Overlay.Update)
        vm.quitToTitle()
        repeat(3) { vm.onFrame(t); t += 50_000_000 }
        assertEquals(Overlay.Update, vm.overlay)
        vm.installUpdate()
        repeat(3) { vm.onFrame(t); t += 50_000_000 }
        assertEquals(newer, up.installed)
    }

    @Test
    fun checkNowReplacesOptionsOnTheTitleButNeverInterruptsAGame() {
        val atTitle = vm(FakeUpdater(installedBuild = 10, latest = newer), Settings(playerName = "Alex", nameChosen = true, checkUpdates = false))
        atTitle.push(Overlay.Options)
        atTitle.checkForUpdate(manual = true)
        atTitle.onFrame(1)
        assertEquals("shown in place of Options", listOf<Overlay>(Overlay.Update), atTitle.overlays)

        val inGame = vm(FakeUpdater(installedBuild = 10, latest = newer), Settings(playerName = "Alex", nameChosen = true, checkUpdates = false))
        inGame.startNewGame(Difficulty.EASY, GameMode.STRATEGIC)
        inGame.push(Overlay.Pause); inGame.push(Overlay.Options)
        inGame.checkForUpdate(manual = true)
        inGame.onFrame(1)
        assertTrue("game not interrupted", Overlay.Update !in inGame.overlays)
        assertTrue(inGame.notice?.contains("title screen") == true)
    }

    @Test
    fun notOfferedWhenCurrentOrSkipped() {
        val same = vm(FakeUpdater(installedBuild = 20, latest = newer))
        same.onAppForeground(); same.onFrame(1); same.onFrame(2)
        assertNull(same.update)

        val skipped = vm(FakeUpdater(installedBuild = 10, latest = newer), Settings(playerName = "Alex", nameChosen = true, skippedUpdateBuild = 20))
        skipped.onAppForeground(); skipped.onFrame(1); skipped.onFrame(2)
        assertNull("skipped build isn't offered", skipped.update)
        skipped.checkForUpdate(manual = true); skipped.onFrame(3)
        assertEquals("but Check Now still finds it", newer, skipped.update)
    }
}
