package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.data.HighScore
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.ForgeSource
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.GameEngine.Companion.index
import com.geoguy89.refinersfire.game.GameEvent
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.Glyph
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.StoneColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {
    private val redLapis = Piece.Stone(Glyph.LAPIS, StoneColor.RED)

    private fun engine(mode: GameMode, current: Piece = redLapis, forge: Int = 0): GameEngine {
        val base = GameEngine.newGame(Difficulty.EASY, mode, seed = 7).state
        val cells = base.cells.toMutableList().also { it[index(3, 3)] = redLapis }
        return GameEngine(base.copy(cells = cells, current = current, forge = forge))
    }

    @Test
    fun ironForgeHoldsOneLevel() {
        val e = engine(GameMode.IRON_FORGE)
        assertEquals(1, e.state.forgeCapacity)
        e.discard()
        assertEquals(1, e.state.forge)
        assertFalse(e.state.gameOver)
        val events = e.discard()
        assertTrue("a second discard ends it", e.state.gameOver)
        assertTrue(events.any { it is GameEvent.GameOver })
        assertEquals("Iron Forge scores x3", 3, GameEngine.multiplier(Difficulty.EASY, GameMode.IRON_FORGE))
    }

    @Test
    fun foresightShowsExactlyTheStonesThatArrive() {
        val e = GameEngine.newGame(Difficulty.EASY, GameMode.FORESIGHT, seed = 99)
        assertTrue("Foresight games are seeded", e.state.pieceSeed != null)
        // The opening Cornerstone goes anywhere on the empty board; after that the queue must come true, piece by piece.
        e.play(index(4, 4))
        repeat(25) {
            val promised = e.upcoming(3)
            assertEquals(3, promised.size)
            // Placing (or discarding) the current stone must bring exactly the promised next one.
            val before = e.state.board
            val legal = e.validCells()
            if (legal.isNotEmpty()) e.play(legal.first()) else e.discard()
            if (e.state.gameOver) return
            // A fresh board always opens with the Cornerstone, outside the sequence; the queue is unchanged by it.
            if (e.state.board != before) {
                assertEquals(Piece.Cornerstone, e.state.current)
                return@repeat
            }
            assertEquals(promised.first(), e.state.current)
        }
    }

    @Test
    fun otherModesShowNothingComing() {
        assertTrue(GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, seed = 1).upcoming().isEmpty())
    }

    @Test
    fun forgeRemembersWhatLitEachLevel() {
        val e = engine(GameMode.STRATEGIC)
        e.discard()
        assertEquals(listOf(ForgeSource.DISCARD), e.state.forgeLevels)
        // A hint on this board adds one level and holds it.
        GameEngine(e.state.copy(current = redLapis)).let { h ->
            h.useHint()
            assertEquals(listOf(ForgeSource.DISCARD, ForgeSource.HINT), h.state.forgeLevels)
            // Placing cools the discard (the top level free to cool), not the held hint level.
            h.play(h.validCells().first())
            assertEquals(listOf(ForgeSource.HINT), h.state.forgeLevels)
        }
        // Two wrong guesses add a level of their own.
        val m = engine(GameMode.STRATEGIC)
        m.registerMiss(); m.registerMiss()
        assertEquals(listOf(ForgeSource.MISS), m.state.forgeLevels)
        // A rival's stoke stays while placements cool the rest.
        val s = engine(GameMode.STRATEGIC)
        s.discard()
        s.stoke(1)
        assertEquals(listOf(ForgeSource.DISCARD, ForgeSource.STOKE), s.state.forgeLevels)
        GameEngine(s.state.copy(current = redLapis)).let { p ->
            p.play(p.validCells().first())
            assertEquals(listOf(ForgeSource.STOKE), p.state.forgeLevels)
        }
    }

    @Test
    fun oldSavesWithoutSourcesCountAsDiscards() {
        val e = engine(GameMode.STRATEGIC, forge = 2)
        assertEquals(listOf(ForgeSource.DISCARD, ForgeSource.DISCARD), e.state.forgeLevels)
    }

    @Test
    fun matchesCanBePlayedUnderOtherRules() {
        val iron = GameEngine.newMatch(Difficulty.AVERAGE, 1234L, GameMode.IRON_FORGE)
        assertEquals(1, iron.state.forgeCapacity)
        val fore = GameEngine.newMatch(Difficulty.AVERAGE, 1234L, GameMode.FORESIGHT)
        assertEquals(3, fore.upcoming().size)
        // Both players in a Foresight match see the same queue.
        assertEquals(fore.upcoming(), GameEngine.newMatch(Difficulty.AVERAGE, 1234L, GameMode.FORESIGHT).upcoming())
    }

    private fun score(points: Long, d: Difficulty, m: GameMode, t: Long) = HighScore("Ann", points, "Dross", 1, d, m, t)

    @Test
    fun eachDifficultyAndModeKeepsItsOwnTable() {
        val store = Store(MemoryKeyValueStore())
        val now = epochMillis()
        // Twelve big Hard scores must not push out a small Easy one.
        repeat(12) { store.addHighScore(score(10_000L + it, Difficulty.HARD, GameMode.STRATEGIC, now)) }
        store.addHighScore(score(50, Difficulty.EASY, GameMode.FORESIGHT, now))
        val all = store.loadHighScores()
        assertEquals(10, all.count { it.difficulty == Difficulty.HARD })
        assertTrue(all.any { it.difficulty == Difficulty.EASY && it.mode == GameMode.FORESIGHT })
        assertTrue("an empty table always qualifies", store.qualifies(5, Difficulty.AVERAGE, GameMode.IRON_FORGE))
        assertFalse("too low for a full table", store.qualifies(100, Difficulty.HARD, GameMode.STRATEGIC))
    }

    @Test
    fun thisWeeksBestIsKeptAndUploadedEvenWhenItIsntATopTen() {
        val store = Store(MemoryKeyValueStore())
        val now = epochMillis()
        val lastMonth = now - 30L * 86_400_000
        repeat(10) { store.addHighScore(score(9_000L + it, Difficulty.EASY, GameMode.STRATEGIC, lastMonth)) }
        assertTrue("beats this week's best (there isn't one yet)", store.qualifies(400, Difficulty.EASY, GameMode.STRATEGIC))
        store.addHighScore(score(400, Difficulty.EASY, GameMode.STRATEGIC, now))
        assertEquals(listOf(400L), store.loadWeekScores().map { it.score })
        assertTrue(store.scoresForUpload().any { it.score == 400L })
        assertEquals("best five per table, plus the week's best", 6, store.scoresForUpload().size)
    }

    @Test
    fun weeksStartOnMondayUtcLikeTheServer() {
        // 2026-10-05 is a Monday; any moment that week maps to its 00:00 UTC.
        val monday = 1_791_158_400_000L
        assertEquals(monday, Store.weekStart(monday))
        assertEquals(monday, Store.weekStart(monday + 6L * 86_400_000 + 86_399_999))
        assertEquals(monday - 7L * 86_400_000, Store.weekStart(monday - 1))
    }
}
