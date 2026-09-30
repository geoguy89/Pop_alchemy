package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.GameEngine.Companion.index
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameEvent
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Glyph
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.ROWS
import com.geoguy89.refinersfire.game.Ranks
import com.geoguy89.refinersfire.game.StoneColor
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val redLapis = Piece.Stone(Glyph.LAPIS, StoneColor.RED)
    private val redTurquoise = Piece.Stone(Glyph.TURQUOISE, StoneColor.RED)
    private val blueLapis = Piece.Stone(Glyph.LAPIS, StoneColor.BLUE)
    private val greenCarnelian = Piece.Stone(Glyph.CARNELIAN, StoneColor.GREEN)

    private fun engineWith(
        current: Piece,
        forge: Int = 0,
        difficulty: Difficulty = Difficulty.EASY,
        mode: GameMode = GameMode.STRATEGIC,
        score: Long = 0,
        setup: (MutableList<Piece?>, MutableList<Boolean>) -> Unit = { _, _ -> },
    ): GameEngine {
        val base = GameEngine.newGame(difficulty, mode, seed = 42).state
        val cells = base.cells.toMutableList()
        val gold = base.gold.toMutableList()
        setup(cells, gold)
        return GameEngine(base.copy(cells = cells, gold = gold, current = current, forge = forge, score = score))
    }

    private fun placedPoints(events: List<GameEvent>) = events.filterIsInstance<GameEvent.Placed>().single().points

    /** A full row except its last square, alternating Lapis and Turquoise so no symbol bonus applies. */
    private fun mixedRow(c: MutableList<Piece?>, row: Int, skipCol: Int = COLS - 1) {
        for (col in 0 until COLS) if (col != skipCol) c[index(row, col)] = if (col % 2 == 0) redLapis else redTurquoise
    }

    @Test
    fun placementPointsDependOnLeadOrGoldAndNeighbours() {
        for ((n, expected) in listOf(1 to 5L, 2 to 15L, 3 to 30L, 4 to 50L)) {
            val e = engineWith(redTurquoise) { c, _ -> GameEngine.neighbors(index(3, 3)).take(n).forEach { c[it] = redLapis } }
            assertEquals("lead, $n neighbours", expected, placedPoints(e.play(index(3, 3))))
        }
        for (n in 1..4) {
            val e = engineWith(redTurquoise) { c, g ->
                g[index(3, 3)] = true
                GameEngine.neighbors(index(3, 3)).take(n).forEach { c[it] = redLapis }
            }
            assertEquals("gold, $n neighbours", n.toLong(), placedPoints(e.play(index(3, 3))))
        }
    }

    @Test
    fun multipliersStack() {
        assertEquals(1, GameEngine.multiplier(Difficulty.EASY, GameMode.STRATEGIC))
        assertEquals(2, GameEngine.multiplier(Difficulty.AVERAGE, GameMode.STRATEGIC))
        assertEquals(4, GameEngine.multiplier(Difficulty.HARD, GameMode.STRATEGIC))
        assertEquals(8, GameEngine.multiplier(Difficulty.HARD, GameMode.TIME_TRIAL))
        val e = engineWith(redTurquoise, difficulty = Difficulty.HARD, mode = GameMode.TIME_TRIAL) { c, _ ->
            GameEngine.neighbors(index(3, 3)).forEach { c[it] = redLapis }
        }
        assertEquals(400L, placedPoints(e.play(index(3, 3))))
    }

    @Test
    fun lineAndBoardPoints() {
        val e = engineWith(redTurquoise) { c, _ -> mixedRow(c, 2) }
        val ev = e.play(index(2, COLS - 1))
        assertEquals(50L, ev.filterIsInstance<GameEvent.LinesCleared>().single().points)
        assertEquals(55L, e.state.score)
        val both = engineWith(Piece.Cornerstone) { c, _ ->
            mixedRow(c, 5, skipCol = 4)
            for (row in 0 until ROWS) if (row != 5) c[index(row, 4)] = if (row % 2 == 0) redLapis else redTurquoise
        }
        assertEquals(100L, both.play(index(5, 4)).filterIsInstance<GameEvent.LinesCleared>().single().points)
        val board = engineWith(redTurquoise) { c, g ->
            for (i in g.indices) g[i] = true
            for (col in 0 until COLS) g[index(7, col)] = false
            mixedRow(c, 7)
        }
        assertEquals(500L, board.play(index(7, COLS - 1)).filterIsInstance<GameEvent.BoardCleared>().single().points)
    }

    @Test
    fun sameSymbolLineEarnsBonusAndSameColourToo() {
        val sym = engineWith(redLapis) { c, _ -> for (col in 0 until COLS - 1) c[index(2, col)] = if (col % 2 == 0) redLapis else blueLapis }
        val symClear = sym.play(index(2, COLS - 1)).filterIsInstance<GameEvent.LinesCleared>().single()
        assertFalse(symClear.bonuses.single().perfect)
        assertEquals(50L + GameEngine.SYMBOL_LINE_BONUS, symClear.points)

        val perfect = engineWith(redLapis) { c, _ -> for (col in 0 until COLS - 1) c[index(2, col)] = redLapis }
        val pClear = perfect.play(index(2, COLS - 1)).filterIsInstance<GameEvent.LinesCleared>().single()
        assertTrue(pClear.bonuses.single().perfect)
        assertEquals(50L + GameEngine.PERFECT_LINE_BONUS, pClear.points)

        val colourOnly = engineWith(redTurquoise) { c, _ -> mixedRow(c, 2) }
        assertTrue(colourOnly.play(index(2, COLS - 1)).filterIsInstance<GameEvent.LinesCleared>().single().bonuses.isEmpty())

        val withWild = engineWith(redLapis) { c, _ -> for (col in 0 until COLS - 1) c[index(2, col)] = if (col == 3) Piece.Cornerstone else redLapis }
        assertTrue(withWild.play(index(2, COLS - 1)).filterIsInstance<GameEvent.LinesCleared>().single().bonuses.single().perfect)
    }

    @Test
    fun discardCostsPointsButNeverGoesNegative() {
        val e = engineWith(redTurquoise, score = 100, difficulty = Difficulty.AVERAGE)
        val ev = e.discard().filterIsInstance<GameEvent.Discarded>().single()
        assertEquals(GameEngine.DISCARD_PENALTY * 2, ev.penalty)
        assertEquals(100 - GameEngine.DISCARD_PENALTY * 2, e.state.score)
        val poor = engineWith(redTurquoise, score = 3)
        poor.discard()
        assertEquals(0L, poor.state.score)
    }

    @Test
    fun hintRevealsLegalSquaresForAFee() {
        val e = engineWith(redTurquoise, score = 100) { c, _ -> c[index(3, 3)] = redLapis }
        val cells = e.useHint()
        assertEquals(e.validCells(), cells)
        assertEquals(4, cells.size)
        assertEquals("hints cost no points", 100L, e.state.score)
        assertEquals(1, e.state.hintsUsed)
    }

    @Test
    fun stokingPastAFullForgeEndsTheGame() {
        val e = engineWith(redLapis, forge = 3)
        e.stoke(1)
        assertTrue(e.state.gameOver)
    }

    @Test
    fun wrongPlacementsStokeTheForgeEveryTwoTries() {
        // A stone far from the only occupied neighbor has nowhere legal to go there.
        val e = engineWith(redLapis) { c, _ -> c[index(3, 3)] = blueLapis }
        assertTrue("illegal placement", e.play(index(0, 0)).isEmpty())
        assertFalse("first wrong try: no penalty yet", e.registerMiss())
        assertEquals(0, e.state.forge)
        assertTrue("second wrong try: stokes the forge", e.registerMiss())
        assertEquals(1, e.state.forge)
    }

    @Test
    fun stokedLevelsOnlyCoolByClearingLines() {
        // Row 0 is one stone short of full.
        var e = engineWith(Piece.Cornerstone) { c, _ -> for (col in 1 until COLS) c[index(0, col)] = redLapis }
        assertEquals(2, e.stoke(2))
        e.play(index(4, 4)) // no line: stoked levels stay
        assertEquals("placing doesn't cool a stoke", 2, e.state.forge)
        e = GameEngine(e.state.copy(current = Piece.Cornerstone))
        val events = e.play(index(0, 0)) // clears row 0
        val clear = events.filterIsInstance<GameEvent.LinesCleared>().single()
        assertTrue("a clear while stoked doesn't stoke back", clear.coolsStoke)
        assertEquals("one line cools one stoked level", 1, e.state.forge)
        assertEquals(1, e.state.stoked)
    }

    @Test
    fun ownDiscardsStillCoolOnPlacementButNotBelowTheStoke() {
        var e = engineWith(redLapis, forge = 1) { c, _ -> c[index(3, 3)] = redTurquoise }
        e.stoke(1)
        assertEquals(2, e.state.forge)
        e.play(index(3, 4))
        assertEquals(1, e.state.forge)
        e = GameEngine(e.state.copy(current = Piece.Cornerstone))
        e.play(index(3, 5))
        assertEquals(1, e.state.forge)
    }

    @Test
    fun hintsStokeTheForgeUntilTheBoardIsCleared() {
        var e = engineWith(Piece.Cornerstone)
        e.useHint()
        assertEquals("first hint on a board: one level", 1, e.state.forge)
        e.play(index(4, 4))
        assertEquals("placing doesn't cool it below one", 1, e.state.forge)
        e = GameEngine(e.state.copy(current = Piece.Cornerstone))
        e.useHint()
        assertEquals("second hint: two more", 3, e.state.forge)
        assertTrue("no third hint on a board", e.useHint().isEmpty() && e.state.hintsThisBoard == 2)
        e = GameEngine(e.state.copy(forge = 1, current = Piece.Cornerstone))
        e.play(e.validCells().first())
        assertEquals("still held until the board is cleared", 1, e.state.forge)
        e = GameEngine(e.state.copy(hintsThisBoard = 0, current = Piece.Cornerstone))
        e.play(e.validCells().first())
        assertEquals("released on a new board", 0, e.state.forge)
    }

    @Test
    fun streakMilestonesEveryTenAndHammerTrashKeepsStreak() {
        var e = engineWith(Piece.Cornerstone)
        e.play(index(4, 4))
        var milestones = 0
        repeat(19) {
            // Always hand it a wild stone so there is a legal square.
            e = GameEngine(e.state.copy(current = Piece.Cornerstone))
            milestones += e.play(e.validCells().first()).count { it is GameEvent.StreakMilestone }
        }
        assertEquals(20, e.state.streak)
        assertEquals(2, milestones)
        val hammer = GameEngine(e.state.copy(current = Piece.Hammer, score = 500, forge = 0))
        val trashed = hammer.discard().filterIsInstance<GameEvent.Discarded>().single()
        assertEquals(20, hammer.state.streak)
        assertEquals("no penalty for the hammer", 0L, trashed.penalty)
        assertEquals(500L, hammer.state.score)
        assertEquals("but it still fills the forge", 1, hammer.state.forge)
        hammer.discard()
        assertEquals(0, hammer.state.streak)
    }

    @Test
    fun newGameStartsWithWildAtChosenBoard() {
        val e = GameEngine.newGame(Difficulty.HARD, GameMode.STRATEGIC, 1)
        assertEquals(11, e.state.board)
        assertEquals(Piece.Cornerstone, e.state.current)
        assertEquals(ROWS * COLS, e.validCells().size)
    }

    @Test
    fun runeMustTouchAnotherRune() {
        val e = engineWith(redTurquoise) { c, _ -> c[index(3, 3)] = redLapis }
        assertTrue(e.canPlay(redTurquoise, index(3, 4)))
        assertTrue(e.canPlay(redTurquoise, index(2, 3)))
        assertFalse("diagonals don't count", e.canPlay(redTurquoise, index(2, 2)))
        assertFalse(e.canPlay(redTurquoise, index(0, 0)))
        assertFalse("occupied", e.canPlay(redTurquoise, index(3, 3)))
    }

    @Test
    fun allNeighboursMustMatchColourOrShape() {
        val e = engineWith(redTurquoise) { c, _ ->
            c[index(3, 3)] = redLapis
            c[index(3, 5)] = greenCarnelian
        }
        assertFalse(e.canPlay(redTurquoise, index(3, 4)))
        assertTrue(e.canPlay(blueLapis, index(2, 3)))
        assertFalse(e.canPlay(blueLapis, index(3, 4)))
    }

    @Test
    fun wildMatchesEverythingBothWays() {
        val e = engineWith(redTurquoise) { c, _ ->
            c[index(3, 3)] = Piece.Cornerstone
            c[index(3, 5)] = redLapis
        }
        assertTrue(e.canPlay(redTurquoise, index(3, 4)))
        assertTrue(e.canPlay(greenCarnelian, index(2, 3)))
        assertFalse(e.canPlay(greenCarnelian, index(3, 4)))
        assertTrue(e.canPlay(Piece.Cornerstone, index(3, 6)))
        assertFalse("wild still needs a neighbour", e.canPlay(Piece.Cornerstone, index(7, 8)))
    }

    @Test
    fun hammerRemovesAPieceAndLowersForge() {
        val e = engineWith(Piece.Hammer, forge = 2) { c, _ -> c[index(1, 1)] = redLapis }
        assertFalse(e.canPlay(Piece.Hammer, index(0, 0)))
        val events = e.play(index(1, 1))
        assertTrue(events.first() is GameEvent.HammerUsed)
        assertEquals(null, e.state.cells[index(1, 1)])
        assertEquals(1, e.state.forge)
    }

    @Test
    fun placingLowersForgeAndDiscardFillsIt() {
        val e = engineWith(redTurquoise, forge = 2) { c, _ -> c[index(0, 0)] = redLapis }
        e.play(index(0, 1))
        assertEquals(1, e.state.forge)
        e.discard(); e.discard()
        assertEquals(3, e.state.forge)
        assertFalse(e.state.gameOver)
        val events = e.discard()
        assertTrue(e.state.gameOver)
        assertTrue(events.contains(GameEvent.GameOver))
    }

    @Test
    fun fillingARowClearsItToGoldAndEmptiesForge() {
        val e = engineWith(redTurquoise, forge = 3) { c, _ ->
            for (col in 0 until COLS - 1) c[index(2, col)] = redLapis
            c[index(3, 0)] = blueLapis
        }
        val events = e.play(index(2, COLS - 1))
        val cleared = events.filterIsInstance<GameEvent.LinesCleared>().single()
        assertEquals(listOf(2), cleared.rows)
        assertEquals(COLS, cleared.newlyGold.size)
        assertEquals(0, e.state.forge)
        assertTrue((0 until COLS).all { e.state.isGold(2, it) && e.state.at(2, it) == null })
        assertEquals(blueLapis, e.state.at(3, 0))
        assertTrue(events.contains(GameEvent.ForgeEmptied))
    }

    @Test
    fun rowAndColumnClearTogether() {
        val e = engineWith(Piece.Cornerstone) { c, _ ->
            for (col in 0 until COLS) if (col != 4) c[index(5, col)] = redLapis
            for (row in 0 until ROWS) if (row != 5) c[index(row, 4)] = redLapis
        }
        val cleared = e.play(index(5, 4)).filterIsInstance<GameEvent.LinesCleared>().single()
        assertEquals(listOf(5), cleared.rows)
        assertEquals(listOf(4), cleared.cols)
        assertEquals(ROWS + COLS - 1, cleared.removed.size)
    }

    @Test
    fun emptiedBoardGivesAWild() {
        val e = engineWith(redTurquoise) { c, _ -> for (col in 0 until COLS - 1) c[index(0, col)] = redLapis }
        e.play(index(0, COLS - 1))
        assertEquals(Piece.Cornerstone, e.state.current)
    }

    @Test
    fun completingBoardAdvancesAndOnlyLowersForgeOneLevel() {
        val e = engineWith(redTurquoise, forge = 3) { c, g ->
            for (i in g.indices) g[i] = true
            for (col in 0 until COLS) g[index(7, col)] = false
            for (col in 0 until COLS - 1) c[index(7, col)] = redLapis
        }
        val events = e.play(index(7, COLS - 1))
        assertTrue(events.any { it is GameEvent.BoardCleared })
        assertEquals(2, e.state.board)
        assertEquals(2, e.state.forge)
        assertEquals(1, e.state.boardsCleared)
        assertTrue(e.state.gold.none { it })
        assertTrue(e.state.boardEmpty)
        assertEquals(Piece.Cornerstone, e.state.current)
    }

    @Test
    fun streakTracksPlacementsWithoutDiscard() {
        val e = engineWith(Piece.Cornerstone)
        e.play(index(4, 4))
        repeat(3) {
            val cell = e.validCells().first()
            e.play(cell)
        }
        assertEquals(4, e.state.bestStreak)
        e.discard()
        assertEquals(0, e.state.streak)
        assertEquals(4, e.state.bestStreak)
    }

    @Test
    fun stokingRaisesTheForgeAndOverflowEndsTheGame() {
        val e = engineWith(redTurquoise, forge = 1)
        assertEquals(2, e.stoke(2))
        assertEquals(3, e.state.forge)
        assertFalse(e.state.gameOver)
        e.stoke(1)
        assertTrue("stoked past the top", e.state.gameOver)
    }

    @Test
    fun timeTrialDiscardsWhenTimeRunsOut() {
        val e = GameEngine.newGame(Difficulty.EASY, GameMode.TIME_TRIAL, 3)
        val limit = e.state.pieceTimeLeftMillis
        assertTrue(limit > 0)
        assertTrue(e.tick(limit - 1).isEmpty())
        val events = e.tick(10)
        val d = events.first() as GameEvent.Discarded
        assertTrue(d.timedOut)
        assertEquals("a timeout fills the forge without the penalty", 0L, d.penalty)
        assertEquals(1, e.state.forge)
    }

    @Test
    fun randomGamesNeverBreakInvariants() {
        repeat(20) { seed ->
            val e = GameEngine.newGame(Difficulty.entries[seed % 3], GameMode.STRATEGIC, seed.toLong())
            var moves = 0
            while (!e.state.gameOver && moves < 3000) {
                val valid = e.validCells()
                if (valid.isEmpty()) e.discard() else e.play(valid[(moves * 7919) % valid.size])
                assertTrue(e.state.forge in 0..3)
                moves++
            }
        }
    }

    @Test
    fun stateRoundTripsThroughJson() {
        val e = GameEngine.newGame(Difficulty.AVERAGE, GameMode.STRATEGIC, 99)
        e.play(index(3, 3))
        repeat(10) { e.validCells().firstOrNull()?.let(e::play) ?: e.discard() }
        val json = Json.encodeToString(GameState.serializer(), e.state)
        val restored = Json.decodeFromString(GameState.serializer(), json)
        assertEquals(e.state, restored)
        // Both continue identically.
        val other = GameEngine(restored)
        e.discard(); other.discard()
        assertEquals(e.state.current, other.state.current)
    }

    @Test
    fun ranks() {
        assertEquals("Dross", Ranks.rankFor(0))
        assertEquals("Dross", Ranks.rankFor(399))
        assertEquals("Raw Ore", Ranks.rankFor(400))
        assertEquals("Crucible Keeper", Ranks.rankFor(4499))
        assertEquals("Pure Gold", Ranks.rankFor(123456))
        assertEquals(1000L to "Apprentice Smelter", Ranks.nextRank(700))
        assertEquals(null, Ranks.nextRank(40000))
    }
}
