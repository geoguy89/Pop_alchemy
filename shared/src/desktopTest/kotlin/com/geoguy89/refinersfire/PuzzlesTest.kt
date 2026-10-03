package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.Puzzles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzlesTest {
    @Test
    fun everyPuzzleIsSolvableWithItsOwnStones() {
        val started = System.currentTimeMillis()
        for (id in 1..Puzzles.COUNT) {
            val p = Puzzles.get(id)
            assertEquals(p.pieces.size, p.solution.size)
            val e = p.start()
            for ((k, move) in p.solution.withIndex()) {
                assertEquals("puzzle $id stone $k is the one dealt", p.pieces[k], e.state.current)
                if (move == null) e.discard() else assertTrue("puzzle $id move $k legal", e.play(move).isNotEmpty())
            }
            assertTrue("puzzle $id: the solution reaches the target", e.state.puzzleLines >= p.target)
            assertTrue("puzzle $id: every stone used", e.state.puzzleExhausted)
            assertFalse(e.state.gameOver)
        }
        val ms = System.currentTimeMillis() - started
        println("Built and solved ${Puzzles.COUNT} puzzles in $ms ms")
        // Targets climb: early puzzles ask for one line, late ones several.
        println((1..Puzzles.COUNT).joinToString { "${it}:${Puzzles.get(it).target}/${Puzzles.get(it).pieces.size}" })
        assertTrue(Puzzles.get(1).target >= 1)
        assertTrue(Puzzles.get(Puzzles.COUNT).target >= 3)
    }

    @Test
    fun puzzlesAreTheSameEverywhereAndNamedUniquely() {
        assertEquals(Puzzles.COUNT, Puzzles.names.toSet().size)
        val a = Puzzles.get(17)
        // A fresh build (bypassing the cache) gives exactly the same puzzle.
        val method = Puzzles::class.java.getDeclaredMethod("build", Int::class.javaPrimitiveType).apply { isAccessible = true }
        assertEquals(a, method.invoke(Puzzles, 17))
    }

    @Test
    fun theHandEmptiesWhenTheStonesRunOut() {
        val p = Puzzles.get(1)
        val e = p.start()
        assertEquals(p.pieces.size, e.state.puzzleStonesLeft)
        assertEquals(p.pieces.drop(1).take(3), e.upcoming(3))
        repeat(p.pieces.size) { e.discard().let { ev -> if (e.state.gameOver) return } }
        assertTrue(e.state.puzzleExhausted)
        assertEquals(0, e.state.puzzleStonesLeft)
        assertTrue("no more moves", e.discard().isEmpty() && e.validCells().isEmpty())
    }

    @Test
    fun starsRewardCleanSolves() {
        assertEquals(3, Puzzles.stars(0, 0))
        assertEquals(2, Puzzles.stars(1, 0))
        assertEquals(2, Puzzles.stars(0, 1))
        assertEquals(1, Puzzles.stars(1, 1))
        assertTrue(Piece.Cornerstone != Piece.Hammer)
    }
}
