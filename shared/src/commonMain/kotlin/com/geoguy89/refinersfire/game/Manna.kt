package com.geoguy89.refinersfire.game

/**
 * Manna: one run a day, the same stones for everyone (seeded by the date), like the manna gathered fresh each morning
 * and gone by the next (Exodus 16). Average difficulty, Strategic rules, three boards; the first finished run of the
 * day is the one that counts.
 */
object Manna {
    const val BOARDS = 3
    val DIFFICULTY = Difficulty.AVERAGE

    /** The day's seed: the same on every device. Kept to 52 bits so it survives JSON unchanged. */
    fun seed(day: Long): Long {
        var z = day * -0x61c8864680b583ebL + 0x4d414e4e41L // "MANNA"
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return (z xor (z ushr 31)) and 0xFFFFFFFFFFFFFL
    }

    /** A fresh run for [day], recording its score stone by stone (for others to race as a ghost). */
    fun newRun(day: Long, ghost: List<Long>? = null, ghostName: String? = null): GameEngine {
        val seed = seed(day)
        val base = GameEngine.newGame(DIFFICULTY, GameMode.STRATEGIC, seed).state
        return GameEngine(
            base.copy(
                pieceSeed = seed, mannaDay = day, challengeBoards = BOARDS, timeline = emptyList(),
                ghost = ghost, ghostName = ghostName,
            ),
        )
    }

    /** Consecutive days gathered, ending today (or yesterday, if today's isn't gathered yet). */
    fun streak(days: Set<Long>, today: Long): Int {
        var d = if (today in days) today else today - 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }
}
