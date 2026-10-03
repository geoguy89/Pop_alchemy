package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MannaResult
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Settings
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.Manna
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import com.geoguy89.refinersfire.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MannaTest {
    /** Play [e] by always taking the first legal square (or melting the stone), [n] times. */
    private fun play(e: GameEngine, n: Int) = repeat(n) {
        if (e.state.gameOver) return
        val v = e.validCells()
        if (v.isEmpty()) e.discard() else e.play(v.first())
    }

    @Test
    fun everyoneGetsTheSameStonesOnTheSameDay() {
        val a = Manna.newRun(20_000)
        val b = Manna.newRun(20_000)
        repeat(40) {
            assertEquals(a.state.current, b.state.current)
            play(a, 1); play(b, 1)
        }
        // A different day is a different run.
        val c = Manna.newRun(20_001)
        val d = Manna.newRun(20_000)
        val seqC = (0 until 20).map { play(c, 1); c.state.current }
        val seqD = (0 until 20).map { play(d, 1); d.state.current }
        assertNotEquals(seqC, seqD)
        assertTrue("seed fits 52 bits (JSON-safe)", Manna.seed(20_000) in 0L until (1L shl 52))
    }

    @Test
    fun theRunRecordsItsScoreStoneByStone() {
        val e = Manna.newRun(20_000)
        play(e, 12)
        val t = e.state.timeline!!
        assertEquals(12, t.size)
        assertEquals(e.state.score, t.last())
    }

    @Test
    fun streaksCountConsecutiveDays() {
        assertEquals(3, Manna.streak(setOf(10, 11, 12), 12))
        assertEquals("today not played yet: yesterday's streak still stands", 3, Manna.streak(setOf(10, 11, 12), 13))
        assertEquals(0, Manna.streak(setOf(10, 11), 13))
        assertEquals(1, Manna.streak(setOf(5, 13), 13))
    }

    private fun vm(store: Store = Store(MemoryKeyValueStore()).also { it.saveSettings(Settings(playerName = "Alex", nameChosen = true)) }) =
        GameViewModel(store, SilentAudio)

    @Test
    fun oneRunADayAndItsOwnSaveSlot() {
        val store = Store(MemoryKeyValueStore()).also { it.saveSettings(Settings(playerName = "Alex", nameChosen = true)) }
        val model = vm(store)
        model.playManna()
        assertEquals(Screen.GAME, model.screen)
        assertEquals(model.today, model.state!!.mannaDay)
        // Leaving keeps the run (separately from the normal saved game), and Continue resumes it.
        model.onAppBackground()
        assertEquals(model.today, store.loadMannaGame()?.mannaDay)
        assertNull("the single-player slot is untouched", store.loadGame())
        // Once the day's Manna is gathered, there's no second try.
        store.addMannaResult(MannaResult(model.today, 1234, 3))
        val again = vm(store)
        again.playManna()
        assertEquals(Screen.TITLE, again.screen)
        assertTrue(again.notice?.contains("tomorrow") == true)
    }

    @Test
    fun theRunEndsAfterThreeBoardsAndIsRecorded() {
        val store = Store(MemoryKeyValueStore()).also { it.saveSettings(Settings(playerName = "Alex", nameChosen = true)) }
        val model = vm(store)
        model.playManna()
        // Jump to the last board with one square left to fill, then fill it.
        val s = model.state!!
        val cells = MutableList(s.cells.size) { s.cells[it] }
        val gold = MutableList(s.gold.size) { true }
        gold[0] = false
        model.loadForPreview(s.copy(boardsCleared = Manna.BOARDS - 1, gold = gold, cells = cells, current = com.geoguy89.refinersfire.game.Piece.Cornerstone))
        // Fill row 0 so square 0 turns gold: put stones in the rest of the row first.
        val row = (1 until com.geoguy89.refinersfire.game.COLS).map { it }
        val withRow = model.state!!.copy(cells = model.state!!.cells.toMutableList().also { c -> row.forEach { c[it] = com.geoguy89.refinersfire.game.Piece.Cornerstone } })
        model.loadForPreview(withRow)
        model.tapCell(0)
        val done = model.overlay as? Overlay.MannaDone
        assertTrue("finished with the Manna result", done != null)
        assertEquals(Manna.BOARDS, done!!.result.boards)
        assertEquals(1, store.loadMannaHistory().size)
        assertEquals("waiting to reach the server", 1, store.loadPendingManna().size)
        assertNull(store.loadMannaGame())
        assertEquals(1, model.stats.mannaDays)
    }
}
