package com.geoguy89.refinersfire.game

import kotlinx.serialization.Serializable

const val ROWS = 8
const val COLS = 9
const val FORGE_CAPACITY = 3

/** Small deterministic RNG (SplitMix64) whose whole state is one Long, so games can be saved and resumed. */
@Serializable
data class Rng(var state: Long) {
    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun nextInt(bound: Int): Int = ((nextLong() ushr 1) % bound).toInt()
}

@Serializable
data class GameState(
    val mode: GameMode,
    val difficulty: Difficulty,
    val board: Int,
    val cells: List<Piece?>,
    val gold: List<Boolean>,
    val current: Piece,
    val forge: Int = 0,
    /** Hints used on the current board: while any are, the forge can't cool below one level. */
    val hintsThisBoard: Int = 0,
    val score: Long = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val stonesPlaced: Int = 0,
    val linesCleared: Int = 0,
    val boardsCleared: Int = 0,
    val discards: Int = 0,
    val hintsUsed: Int = 0,
    val elapsedMillis: Long = 0,
    /** Time left to place the current piece (Time Trial only). */
    val pieceTimeLeftMillis: Long = 0,
    val gameOver: Boolean = false,
    val rng: Rng,
    /** 1v1 matches: both players draw from the same seeded sequence, so each gets the same pieces in the same order. */
    val matchSeed: Long? = null,
    val pieceIndex: Int = 0,
    /** Async challenge run: its id, the boards to clear, and the rival's name. */
    val challengeId: String? = null,
    val challengeBoards: Int = 0,
    val challengeRival: String? = null,
) {
    fun at(row: Int, col: Int): Piece? = cells[row * COLS + col]
    fun isGold(row: Int, col: Int): Boolean = gold[row * COLS + col]
    val goldCount: Int get() = gold.count { it }
    val boardEmpty: Boolean get() = cells.all { it == null }
    val rank: String get() = Ranks.rankFor(score)
    val multiplier: Int get() = GameEngine.multiplier(difficulty, mode)
}

/** A cleared line whose stones all share a symbol ([perfect]: and a colour too). */
data class LineBonus(val row: Int?, val col: Int?, val perfect: Boolean, val points: Long)

sealed interface GameEvent {
    data class Placed(val index: Int, val piece: Piece, val points: Long, val neighbors: Int) : GameEvent
    data class HammerUsed(val index: Int, val removed: Piece) : GameEvent
    data class LinesCleared(
        val rows: List<Int>,
        val cols: List<Int>,
        /** Pieces removed, keyed by cell index. */
        val removed: Map<Int, Piece>,
        val newlyGold: List<Int>,
        val points: Long,
        /** Same-symbol and same-symbol-and-colour lines in this clear. Included in [points]. */
        val bonuses: List<LineBonus> = emptyList(),
    ) : GameEvent
    data class BoardCleared(val completedBoard: Int, val points: Long, val stats: GameState) : GameEvent
    data class Discarded(val piece: Piece, val forgeLevel: Int, val timedOut: Boolean, val penalty: Long = 0) : GameEvent
    data class StreakMilestone(val streak: Int) : GameEvent
    data object ForgeEmptied : GameEvent
    data object GameOver : GameEvent
    data class NewPiece(val piece: Piece) : GameEvent
}

/**
 * The rules of Refiner's Fire. Holds the current [state] and mutates it in response to moves,
 * returning the events that happened so the UI can animate them.
 */
class GameEngine(state: GameState) {
    var state: GameState = state
        private set

    companion object {
        const val CORNERSTONE_CHANCE = 40 // one in N
        const val HAMMER_CHANCE = 38 // one in N
        const val MIN_PIECES_FOR_HAMMER = 4
        const val STREAK_MILESTONE = 10

        /** Base points for a placement, indexed by neighbour count (1-4). */
        private val LEAD_POINTS = longArrayOf(5, 5, 15, 30, 50)
        private val GOLD_POINTS = longArrayOf(1, 1, 2, 3, 4)
        const val LINE_POINTS = 50L
        const val SYMBOL_LINE_BONUS = 250L
        const val PERFECT_LINE_BONUS = 1000L
        const val BOARD_POINTS = 500L
        const val DISCARD_PENALTY = 10L
        const val HINT_PENALTY = 25L

        fun newGame(difficulty: Difficulty, mode: GameMode, seed: Long = kotlin.random.Random.nextLong()): GameEngine {
            val board = difficulty.startBoard
            return GameEngine(
                GameState(
                    mode = mode,
                    difficulty = difficulty,
                    board = board,
                    cells = List(ROWS * COLS) { null },
                    gold = List(ROWS * COLS) { false },
                    current = Piece.Cornerstone,
                    pieceTimeLeftMillis = pieceTimeLimit(mode, board),
                    rng = Rng(seed),
                ),
            )
        }

        /** A 1v1 match: same seed on both devices, Strategic rules, starting board by difficulty. */
        fun newMatch(difficulty: Difficulty, seed: Long): GameEngine {
            val base = newGame(difficulty, GameMode.STRATEGIC, seed).state
            return GameEngine(base.copy(matchSeed = seed))
        }

        /** The [index]th piece of a match's sequence. Depends only on the seed, the index and the board number. */
        fun matchPiece(seed: Long, index: Int, board: Int): Piece {
            val rng = Rng(seed xor (index.toLong() * -0x61c8864680b583ebL))
            rng.nextLong()
            if (rng.nextInt(HAMMER_CHANCE) == 0) return Piece.Hammer
            if (rng.nextInt(CORNERSTONE_CHANCE) == 0) return Piece.Cornerstone
            val cfg = BoardConfig.forBoard(board)
            return Piece.Stone(Glyph.entries[rng.nextInt(cfg.glyphs)], StoneColor.entries[rng.nextInt(cfg.colors)])
        }

        fun pieceTimeLimit(mode: GameMode, board: Int): Long =
            if (mode == GameMode.TIME_TRIAL) (16_000L - (board - 1) * 600L).coerceAtLeast(6_000L) else 0L

        /** Easy x1, Average x2, Hard x4; Time Trial doubles on top. */
        fun multiplier(difficulty: Difficulty, mode: GameMode): Int = difficulty.scoreMultiplier * mode.scoreMultiplier

        /** Base points for placing on a lead or gold square touching [neighbors] pieces. */
        fun placementPoints(onGold: Boolean, neighbors: Int): Long =
            (if (onGold) GOLD_POINTS else LEAD_POINTS)[neighbors.coerceIn(1, 4)]

        fun index(row: Int, col: Int) = row * COLS + col
        fun rowOf(index: Int) = index / COLS
        fun colOf(index: Int) = index % COLS

        fun neighbors(index: Int): List<Int> {
            val r = rowOf(index)
            val c = colOf(index)
            return buildList {
                if (r > 0) add(index(r - 1, c))
                if (r < ROWS - 1) add(index(r + 1, c))
                if (c > 0) add(index(r, c - 1))
                if (c < COLS - 1) add(index(r, c + 1))
            }
        }

        fun matches(a: Piece, b: Piece): Boolean = when {
            a is Piece.Cornerstone || b is Piece.Cornerstone -> true
            a is Piece.Stone && b is Piece.Stone -> a.color == b.color || a.glyph == b.glyph
            else -> false
        }
    }

    /** Whether [piece] may legally be played on the cell at [index]. */
    fun canPlay(piece: Piece, index: Int, s: GameState = state): Boolean {
        if (s.gameOver) return false
        val occupant = s.cells[index]
        if (piece is Piece.Hammer) return occupant != null
        if (occupant != null) return false
        if (s.boardEmpty) return true
        val adjacent = neighbors(index).mapNotNull { s.cells[it] }
        if (adjacent.isEmpty()) return false
        return adjacent.all { matches(piece, it) }
    }

    fun validCells(piece: Piece = state.current): List<Int> =
        (0 until ROWS * COLS).filter { canPlay(piece, it) }

    fun hasAnyMove(): Boolean = (0 until ROWS * COLS).any { canPlay(state.current, it) }

    /** Place the current piece (or use the hammer) on [index]. Returns an empty list if the move is illegal. */
    fun play(index: Int): List<GameEvent> {
        val s = state
        val piece = s.current
        if (!canPlay(piece, index)) return emptyList()
        val events = mutableListOf<GameEvent>()
        val cells = s.cells.toMutableList()
        var score = s.score
        var streak = s.streak
        var stonesPlaced = s.stonesPlaced

        if (piece is Piece.Hammer) {
            val removed = cells[index]!!
            cells[index] = null
            events += GameEvent.HammerUsed(index, removed)
        } else {
            val neighborCount = neighbors(index).count { cells[it] != null }
            cells[index] = piece
            val pts = placementPoints(s.gold[index], neighborCount) * s.multiplier
            score += pts
            streak += 1
            stonesPlaced += 1
            events += GameEvent.Placed(index, piece, pts, neighborCount)
            if (streak % STREAK_MILESTONE == 0) events += GameEvent.StreakMilestone(streak)
        }
        val forgeFloor = if (s.hintsThisBoard > 0) 1 else 0
        var forge = (s.forge - 1).coerceAtLeast(minOf(forgeFloor, s.forge))

        // Completed rows and columns.
        val fullRows = (0 until ROWS).filter { r -> (0 until COLS).all { c -> cells[index(r, c)] != null } }
        val fullCols = (0 until COLS).filter { c -> (0 until ROWS).all { r -> cells[index(r, c)] != null } }
        val gold = s.gold.toMutableList()
        var linesCleared = s.linesCleared
        if (fullRows.isNotEmpty() || fullCols.isNotEmpty()) {
            val clearIdx = buildSet {
                fullRows.forEach { r -> (0 until COLS).forEach { c -> add(index(r, c)) } }
                fullCols.forEach { c -> (0 until ROWS).forEach { r -> add(index(r, c)) } }
            }.sorted()
            val removed = clearIdx.associateWith { cells[it]!! }
            val newlyGold = clearIdx.filter { !gold[it] }
            clearIdx.forEach { cells[it] = null; gold[it] = true }
            val lines = fullRows.size + fullCols.size
            val bonuses = fullRows.mapNotNull { r -> lineBonus((0 until COLS).map { removed.getValue(index(r, it)) }, r, null, s.multiplier) } +
                fullCols.mapNotNull { c -> lineBonus((0 until ROWS).map { removed.getValue(index(it, c)) }, null, c, s.multiplier) }
            val pts = LINE_POINTS * lines * s.multiplier + bonuses.sumOf { it.points }
            score += pts
            linesCleared += lines
            events += GameEvent.LinesCleared(fullRows, fullCols, removed, newlyGold, pts, bonuses)
            // A cleared line empties the forge -- unless it finished the board, which only lowers it one level.
            if (!gold.all { it }) {
                if (forge > forgeFloor) events += GameEvent.ForgeEmptied
                forge = minOf(forge, forgeFloor)
            }
        }

        var next = s.copy(
            cells = cells,
            gold = gold,
            forge = forge,
            score = score,
            streak = streak,
            bestStreak = maxOf(s.bestStreak, streak),
            stonesPlaced = stonesPlaced,
            linesCleared = linesCleared,
        )

        if (gold.all { it }) {
            val pts = BOARD_POINTS * s.multiplier
            val completed = next.copy(score = next.score + pts, boardsCleared = next.boardsCleared + 1)
            events += GameEvent.BoardCleared(s.board, pts, completed)
            next = completed.copy(
                board = s.board + 1,
                hintsThisBoard = 0,
                cells = List(ROWS * COLS) { null },
                gold = List(ROWS * COLS) { false },
            )
        }

        next = withNextPiece(next).copy(pieceTimeLeftMillis = pieceTimeLimit(next.mode, next.board))
        state = next
        events += GameEvent.NewPiece(next.current)
        return events
    }

    /**
     * Throw the current piece into the forge. If the forge is already full, the game ends.
     * A voluntary discard costs points; a Time Trial timeout fills the forge without the penalty.
     * Trashing the hammer costs no points and keeps the streak, but still fills the forge.
     */
    fun discard(timedOut: Boolean = false): List<GameEvent> {
        val s = state
        if (s.gameOver) return emptyList()
        val streak = if (s.current is Piece.Hammer && !timedOut) s.streak else 0
        if (s.forge >= FORGE_CAPACITY) {
            state = s.copy(gameOver = true, streak = streak, discards = s.discards + 1)
            return listOf(GameEvent.Discarded(s.current, FORGE_CAPACITY + 1, timedOut), GameEvent.GameOver)
        }
        val penalty = if (timedOut || s.current is Piece.Hammer) 0L else minOf(DISCARD_PENALTY * s.multiplier, s.score)
        val next = s.copy(forge = s.forge + 1, streak = streak, discards = s.discards + 1, score = s.score - penalty)
        val withPiece = withNextPiece(next).copy(pieceTimeLeftMillis = pieceTimeLimit(s.mode, s.board))
        state = withPiece
        return listOf(GameEvent.Discarded(s.current, withPiece.forge, timedOut, penalty), GameEvent.NewPiece(withPiece.current))
    }

    /**
     * Bonus for a cleared line: every stone the same symbol, or the same symbol and colour. Wild stones count as a
     * match; a line needs at least two stones to qualify. Colour alone earns nothing.
     */
    private fun lineBonus(line: List<Piece>, row: Int?, col: Int?, multiplier: Int): LineBonus? {
        val stones = line.filterIsInstance<Piece.Stone>()
        if (stones.size < 2 || line.any { it is Piece.Hammer }) return null
        if (stones.any { it.glyph != stones[0].glyph }) return null
        val perfect = stones.all { it.color == stones[0].color }
        return LineBonus(row, col, perfect, (if (perfect) PERFECT_LINE_BONUS else SYMBOL_LINE_BONUS) * multiplier)
    }

    /** Stoke Duel: the opponent's cleared line raises our forge (never past full; it can't end the game by itself). */
    fun stoke(levels: Int): Int {
        val s = state
        if (s.gameOver) return 0
        val next = (s.forge + levels).coerceAtMost(FORGE_CAPACITY)
        state = s.copy(forge = next)
        return next - s.forge
    }

    fun hintCost(): Long = HINT_PENALTY * state.multiplier

    /** Reveal the legal squares for the current piece, at a cost. Returns the squares (possibly none). */
    fun useHint(): List<Int> {
        val s = state
        if (s.gameOver) return emptyList()
        // A hint also stokes the forge: one level for the first on a board, two for the second, and so on (never past
        // full). Until the board is cleared the forge can't cool below one level.
        state = s.copy(
            score = s.score - minOf(hintCost(), s.score),
            hintsUsed = s.hintsUsed + 1,
            forge = (s.forge + s.hintsThisBoard + 1).coerceAtMost(FORGE_CAPACITY),
            hintsThisBoard = s.hintsThisBoard + 1,
        )
        return validCells()
    }

    /** Advance clocks. In Time Trial, returns discard events if the current piece ran out of time. */
    fun tick(deltaMillis: Long): List<GameEvent> {
        val s = state
        if (s.gameOver) return emptyList()
        var next = s.copy(elapsedMillis = s.elapsedMillis + deltaMillis)
        if (s.mode == GameMode.TIME_TRIAL) {
            next = next.copy(pieceTimeLeftMillis = (s.pieceTimeLeftMillis - deltaMillis).coerceAtLeast(0))
            state = next
            if (next.pieceTimeLeftMillis == 0L) return discard(timedOut = true)
        }
        state = next
        return emptyList()
    }

    private fun withNextPiece(s: GameState): GameState {
        val seed = s.matchSeed ?: return s.copy(current = drawPiece(s))
        // An empty board always gets the stone, without using up a piece of the shared sequence.
        if (s.boardEmpty) return s.copy(current = Piece.Cornerstone)
        return s.copy(current = matchPiece(seed, s.pieceIndex, s.board), pieceIndex = s.pieceIndex + 1)
    }

    private fun drawPiece(s: GameState): Piece {
        val rng = s.rng
        // A fresh or accidentally emptied board always starts with the stone.
        if (s.boardEmpty) return Piece.Cornerstone
        val pieceCount = s.cells.count { it != null }
        if (pieceCount >= MIN_PIECES_FOR_HAMMER && rng.nextInt(HAMMER_CHANCE) == 0) return Piece.Hammer
        if (rng.nextInt(CORNERSTONE_CHANCE) == 0) return Piece.Cornerstone
        val cfg = BoardConfig.forBoard(s.board)
        return Piece.Stone(Glyph.entries[rng.nextInt(cfg.glyphs)], StoneColor.entries[rng.nextInt(cfg.colors)])
    }
}
