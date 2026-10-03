package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Achievements
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.LifetimeStats
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    @Test
    fun between100And200WithUniqueIdsAndNames() {
        val all = Achievements.all
        println("Achievements: ${all.size}")
        assertTrue(all.size in 100..200)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertEquals("names are unique", all.size, all.map { it.name }.toSet().size)
        assertTrue("nothing unlocked on a fresh install", Achievements.newlyUnlocked(LifetimeStats(), emptySet()).isEmpty())
    }

    @Test
    fun tiersUnlockInOrder() {
        val s = LifetimeStats(stonesPlaced = 260, bestScore = 1_600)
        val ids = Achievements.newlyUnlocked(s, emptySet()).map { it.id }.toSet()
        assertTrue(ids.containsAll(listOf("stones-10", "stones-100", "stones-250", "rank-400", "rank-1500")))
        assertTrue("stones-500" !in ids && "rank-2000" !in ids)
        assertTrue("already unlocked are not repeated", Achievements.newlyUnlocked(s, ids).isEmpty())
    }

    @Test
    fun unlocksWaitForAPauseAndPersist() {
        val kv = MemoryKeyValueStore()
        val vm = GameViewModel(Store(kv), SilentAudio)
        vm.startNewGame(Difficulty.EASY, GameMode.STRATEGIC)
        var t = 1L
        fun frame() { vm.onFrame(t); t += 50_000_000L }
        // Place pieces until "First Steps" (10 stones) unlocks.
        var guard = 0
        while ("stones-10" !in vm.unlocked && guard++ < 400) {
            val s = vm.state ?: break
            if (vm.overlay != null) { vm.continueAfterBoard(); frame(); continue }
            val cell = vm.validCellsForTest().firstOrNull()
            if (cell != null) vm.tapCell(cell) else vm.discard()
            frame()
        }
        assertTrue("stones-10 should be unlocked after placing 10 stones", "stones-10" in vm.unlocked)
        repeat(40) { frame() }
        assertNull("no announcement while playing", vm.achievementNote)
        vm.quitToTitle()
        frame()
        assertTrue(vm.screen == Screen.TITLE)
        assertTrue("announced at the title", vm.achievementNote?.contains("chievement") == true)
        // A fresh view model over the same storage still has it.
        assertTrue("stones-10" in GameViewModel(Store(kv), SilentAudio).unlocked)
    }

    @Test
    fun playerNameIsRequiredAndUsedForTheHallOfFame() {
        val vm = GameViewModel(Store(MemoryKeyValueStore()), SilentAudio)
        assertTrue("asked on first launch", vm.needsName)
        assertTrue("blank is refused", !vm.choosePlayerName("   "))
        assertTrue(vm.needsName)
        assertTrue(vm.choosePlayerName("  Travis the Great  "))
        assertEquals("Travis the Great", vm.settings.playerName)
        assertTrue(!vm.needsName)
        assertEquals("cut to 18", "ABCDEFGHIJKLMNOPQR", GameViewModel.cleanName("ABCDEFGHIJKLMNOPQRSTUV"))
        // Lose a game quickly and inscribe: the entry carries the chosen name.
        val base = com.geoguy89.refinersfire.game.GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, 1).state
        vm.loadForPreview(base.copy(score = 750, forge = 3))
        vm.discard() // Forge already full: game over, and the final melt costs nothing.
        assertTrue(vm.overlay is com.geoguy89.refinersfire.ui.Overlay.GameOver)
        vm.finishGameOver(true)
        assertEquals("Travis the Great", vm.highScores.single().name)
    }

    @Test
    fun leavingTheAppSavesAndReturnsToTheTitle() {
        val kv = MemoryKeyValueStore()
        val vm = GameViewModel(Store(kv).also { it.saveSettings(com.geoguy89.refinersfire.data.Settings(playerName = "Alex", nameChosen = true)) }, SilentAudio)
        vm.startNewGame(Difficulty.EASY, GameMode.TIME_TRIAL)
        vm.tapCell(com.geoguy89.refinersfire.game.GameEngine.index(3, 3))
        val placed = vm.state!!.cells
        vm.onAppBackground()
        assertEquals(Screen.TITLE, vm.screen)
        assertEquals("saved for Continue", placed, vm.savedGame?.cells)
        vm.onAppForeground()
        vm.resumeSavedGame()
        assertEquals(placed, vm.state!!.cells)
    }
}
