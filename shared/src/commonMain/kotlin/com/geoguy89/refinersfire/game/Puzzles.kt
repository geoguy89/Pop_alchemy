package com.geoguy89.refinersfire.game

/**
 * A puzzle: a position partway through a game, a fixed run of stones dealt in order, and a number of lines to clear
 * with them. Every puzzle is solvable by construction: it's made by playing a careful solver through those very
 * stones, and the target is what the solver achieved.
 */
data class Puzzle(
    val id: Int,
    val name: String,
    /** The board number the position is set on (it decides how many shapes and colours appear). */
    val board: Int,
    val cells: List<Piece?>,
    val gold: List<Boolean>,
    val pieces: List<Piece>,
    val target: Int,
    /** One way to solve it: the square for each stone in turn (null = melt it). Used to prove it can be done. */
    val solution: List<Int?> = emptyList(),
) {
    /** A fresh attempt at this puzzle. */
    fun start(): GameEngine {
        val base = GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, seed = id.toLong()).state
        return GameEngine(
            base.copy(
                board = board, cells = cells, gold = gold, current = pieces.first(), pieceIndex = 1,
                puzzleId = id, puzzlePieces = pieces, puzzleTarget = target, puzzleStartLines = 0,
            ),
        )
    }
}

object Puzzles {
    const val COUNT = 60

    /** Each puzzle is named for a moment in Scripture, from the first light to the new Jerusalem. */
    val names = listOf(
        "Let There Be Light", "The Firmament", "Seed-Bearing Plants", "Lights in the Heavens", "Every Living Creature",
        "In Our Image", "The Seventh Day", "The Garden", "Four Rivers", "The Ark",
        "Forty Days of Rain", "The Rainbow", "Stars of the Sky", "The Ram in the Thicket", "Jacob's Ladder",
        "Coat of Many Colours", "Seven Fat Years", "The Burning Bush", "Ten Plagues", "Passover",
        "The Red Sea", "Water from the Rock", "Tablets of Stone", "The Tabernacle", "The Bronze Serpent",
        "Crossing the Jordan", "Walls of Jericho", "Gideon's Three Hundred", "Samson's Riddle", "Ruth's Gleaning",
        "Hannah's Prayer", "Five Smooth Stones", "David's Harp", "Solomon's Temple", "The Queen of Sheba",
        "Fire on Carmel", "The Still Small Voice", "Chariot of Fire", "The Widow's Oil", "Nehemiah's Wall",
        "Esther's Crown", "Job's Patience", "The Lord Is My Shepherd", "Wisdom's House", "Isaiah's Coal",
        "The Potter's House", "The Fiery Furnace", "The Lions' Den", "Jonah's Vine", "The Refiner's Fire",
        "The Star in the East", "Water into Wine", "Loaves and Fishes", "Walking on Water", "The Mustard Seed",
        "The Lost Sheep", "The Prodigal's Return", "The Empty Tomb", "Tongues of Fire", "The New Jerusalem",
    )

    private val cache = HashMap<Int, Puzzle>()

    /** Puzzle [id] (1..[COUNT]); the same on every device. */
    fun get(id: Int): Puzzle = cache.getOrPut(id) { build(id) }

    /**
     * Stars for a solved puzzle: three with no stone melted and no hint, two with at most one of either, one otherwise.
     */
    fun stars(discards: Int, hints: Int): Int = when {
        discards == 0 && hints == 0 -> 3
        discards + hints <= 1 -> 2
        else -> 1
    }

    // ---- making them ----------------------------------------------------------------------------------------------

    /** Stones in the run, and the fewest lines the solver must manage, both growing with the puzzle number. */
    private fun runLength(id: Int) = 5 + id / 6
    private fun minLines(id: Int) = 1 + id / 20
    private fun boardFor(id: Int) = (1 + (id - 1) / 5).coerceAtMost(12)

    private fun build(id: Int): Puzzle {
        require(id in 1..COUNT)
        // Try seeds in a fixed order until one makes a good puzzle; the first is usually fine.
        for (attempt in 0 until 400) {
            val seed = id.toLong() * 7919L + attempt * 104_729L
            candidate(id, seed)?.let { return it }
        }
        error("no puzzle for $id")
    }

    private fun candidate(id: Int, seed: Long): Puzzle? {
        val rng = Rng(seed xor 0x5A5A5A5AL)
        val board = boardFor(id)
        val empty = GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, seed).state
        var e = GameEngine(empty.copy(board = board, matchSeed = seed, current = Piece.Cornerstone))
        // Set the scene: build a busy board without finishing any line, so several lines are nearly complete.
        val setup = 18 + id / 2
        repeat(setup) {
            val s = e.state
            if (s.current is Piece.Hammer) { e.discard(); return@repeat }
            val legal = e.validCells().filter { !completesLine(s, it) }
            if (legal.isEmpty()) { e.discard(); return@repeat }
            // Pack stones close together (more neighbours), with a little randomness for variety.
            val best = legal.maxBy { neighbours(s, it) * 10 + rowColFill(s, it) + rng.nextInt(7) }
            e.play(best)
        }
        val scene = e.state
        if (scene.cells.count { it != null } < 12) return null
        // Some squares already refined, as in a game that's been going a while.
        val gold = scene.gold.toMutableList()
        repeat(1 + rng.nextInt(3)) {
            if (rng.nextInt(2) == 0) { val r = rng.nextInt(ROWS); for (c in 0 until COLS) gold[GameEngine.index(r, c)] = true }
            else { val c = rng.nextInt(COLS); for (r in 0 until ROWS) gold[GameEngine.index(r, c)] = true }
        }
        if (gold.all { it }) return null
        // The run: the solver plays the next stones, aiming to finish lines.
        val run = runLength(id)
        val pieces = ArrayList<Piece>()
        val moves = ArrayList<Int?>()
        e = GameEngine(scene.copy(gold = gold, forge = 0, forgeSources = emptyList(), linesCleared = 0, stoked = 0, hintsThisBoard = 0))
        var discards = 0
        repeat(run) {
            val s = e.state
            if (s.gameOver) return null
            pieces += s.current
            val legal = e.validCells()
            if (s.current is Piece.Hammer || legal.isEmpty()) {
                discards++
                moves += null
                e.discard()
            } else {
                val best = legal.maxBy { (if (completesLine(s, it)) 1000 else 0) + rowColFill(s, it) * 3 + neighbours(s, it) + rng.nextInt(3) }
                moves += best
                e.play(best)
            }
        }
        val after = e.state
        // A board finished mid-run resets everything: not a fair puzzle. Nor is one that overflows the forge.
        if (after.board != board || after.gameOver || discards > 1) return null
        if (after.linesCleared < minLines(id)) return null
        return Puzzle(id, names[id - 1], board, scene.cells, gold, pieces, target = after.linesCleared, solution = moves)
    }

    private fun completesLine(s: GameState, index: Int): Boolean {
        val r = GameEngine.rowOf(index)
        val c = GameEngine.colOf(index)
        val rowFull = (0 until COLS).all { k -> k == c || s.cells[GameEngine.index(r, k)] != null }
        val colFull = (0 until ROWS).all { k -> k == r || s.cells[GameEngine.index(k, c)] != null }
        return rowFull || colFull
    }

    private fun neighbours(s: GameState, index: Int) = GameEngine.neighbors(index).count { s.cells[it] != null }

    /** How full this square's row and column already are (the fuller, the nearer to a cleared line). */
    private fun rowColFill(s: GameState, index: Int): Int {
        val r = GameEngine.rowOf(index)
        val c = GameEngine.colOf(index)
        val row = (0 until COLS).count { s.cells[GameEngine.index(r, it)] != null }
        val col = (0 until ROWS).count { s.cells[GameEngine.index(it, c)] != null }
        return maxOf(row * ROWS / COLS, col)
    }
}
