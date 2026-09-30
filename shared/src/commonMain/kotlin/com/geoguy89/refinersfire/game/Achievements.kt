package com.geoguy89.refinersfire.game

import kotlinx.serialization.Serializable

/** Lifetime counters that achievements are measured against. Stored on the device. */
@Serializable
data class LifetimeStats(
    val gamesStarted: Int = 0,
    val gamesFinished: Int = 0,
    val stonesPlaced: Long = 0,
    val cornerstonesPlaced: Int = 0,
    val fiftyPointPlacements: Int = 0,
    val linesCleared: Long = 0,
    val doubleClears: Int = 0,
    val symbolLines: Int = 0,
    val perfectLines: Int = 0,
    val boardsCleared: Int = 0,
    val cleanBoards: Int = 0,
    val timeTrialBoards: Int = 0,
    val hammersUsed: Int = 0,
    val discards: Int = 0,
    val hintsUsed: Int = 0,
    val forgeSaves: Int = 0,
    val bestScore: Long = 0,
    val bestStreak: Int = 0,
    val highestBoard: Int = 0,
    /** Most boards cleared in one game, per difficulty name. */
    val bestBoardsInGame: Map<String, Int> = emptyMap(),
    val fastestBoardMs: Long = 0,
    val bestBoardPoints: Long = 0,
    val noHintGameBoards: Int = 0,
    val zeroScoreGameOver: Boolean = false,
    val playMillis: Long = 0,
    val themesPlayed: Set<String> = emptySet(),
    val difficultiesCleared: Set<String> = emptySet(),
    val matchesPlayed: Int = 0,
    val matchesWon: Int = 0,
    val matchWinStreak: Int = 0,
    val bestMatchWinStreak: Int = 0,
    val raceWins: Int = 0,
    val timedWins: Int = 0,
    val perfectLineInMatch: Boolean = false,
    val friends: Int = 0,
    val chatsSent: Int = 0,
    val nearbyDevices: Int = 0,
    val sharedGlobally: Boolean = false,
    val asyncPlayed: Int = 0,
    val asyncWon: Int = 0,
)

enum class AchievementCategory(val title: String) {
    TRANSMUTATION("Refining"),
    BOARDS("Boards"),
    MASTERY("Mastery"),
    RANKS("Titles"),
    STREAKS("Streaks"),
    COMBOS("Combos"),
    FORGE("Forge"),
    CHALLENGES("Challenges"),
    RIVALS("Rivals"),
    DEDICATION("Dedication"),
}

/**
 * One achievement. [progress] returns how far along the player is, measured against [target]
 * (a yes/no achievement has target 1).
 */
class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val category: AchievementCategory,
    val target: Long,
    val progress: (LifetimeStats) -> Long,
) {
    fun unlocked(s: LifetimeStats) = progress(s) >= target
}

object Achievements {
    private fun flag(b: Boolean) = if (b) 1L else 0L

    private fun fmt(n: Long): String {
        val s = n.toString()
        return s.reversed().chunked(3).joinToString(",").reversed()
    }

    val all: List<Achievement> = buildList {
        fun tiers(
            prefix: String, cat: AchievementCategory, targets: List<Long>, names: List<String>,
            describe: (String) -> String, progress: (LifetimeStats) -> Long,
        ) {
            require(targets.size == names.size)
            targets.forEachIndexed { i, t -> add(Achievement("$prefix-$t", names[i], describe(fmt(t)), cat, t, progress)) }
        }
        fun one(id: String, name: String, desc: String, cat: AchievementCategory, test: (LifetimeStats) -> Boolean) =
            add(Achievement(id, name, desc, cat, 1) { flag(test(it)) })

        val T = AchievementCategory.TRANSMUTATION
        tiers("stones", T, listOf(10, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000),
            listOf("First Steps", "Stone Setter", "Centurion", "Steady Hand", "Gem Setter", "Thousandfold", "Stonemason", "Jeweler", "Ten Thousand Stones", "Faithful in Much", "Mountain Mover", "Temple Builder"),
            { "Place $it stones" }) { it.stonesPlaced }
        tiers("lines", T, listOf(1, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000),
            listOf("Lead to Gold", "Gilded", "Gold Leaf", "Goldsmith", "Hundred Lines", "Refined Silver", "Golden Age", "River of Gold", "Streets of Gold", "Gates of Pearl", "New Jerusalem"),
            { "Clear $it lines" }) { it.linesCleared }
        tiers("cornerstones", T, listOf(1, 25, 100), listOf("Cornerstone", "Living Stones", "Chief Cornerstone"),
            { "Place $it Cornerstones" }) { it.cornerstonesPlaced.toLong() }
        tiers("fifty", T, listOf(1, 10, 50, 100, 500), listOf("Surrounded", "Keystone", "Mortar and Pestle", "Architect", "Master Builder"),
            { "Place a stone on lead touching four stones $it times" }) { it.fiftyPointPlacements.toLong() }

        val B = AchievementCategory.BOARDS
        tiers("boards", B, listOf(1, 5, 10, 25, 50, 100, 250, 500, 1_000),
            listOf("The Good Work", "Five Plates", "Ten Plates", "Well Seasoned", "Half a Hundred", "Plate Hundred", "Gilded Hall", "Five Hundred Lamps", "A Thousand Generations"),
            { "Complete $it boards" }) { it.boardsCleared.toLong() }
        tiers("reach", B, listOf(3, 5, 8, 10, 12, 15, 20, 25, 30, 40),
            listOf("Deeper", "Further In", "Eighth Day", "Double Digits", "Twelve Tribes", "Fifteen Steps", "Twenty Gates", "Onward and Upward", "Thirtyfold", "Forty Years"),
            { "Reach board $it" }) { it.highestBoard.toLong() }
        for ((d, label) in listOf("EASY" to "Easy", "AVERAGE" to "Average", "HARD" to "Hard")) {
            tiers("run-$d", B, listOf(3, 5, 10), listOf("$label Hat Trick", "$label Handful", "$label Marathon"),
                { "Complete $it boards in one $label game" }) { (it.bestBoardsInGame[d] ?: 0).toLong() }
        }
        tiers("tt", B, listOf(1, 10, 50, 250), listOf("Against the Sand", "Hourglass Keeper", "Master of Time", "Redeeming the Time"),
            { "Complete $it boards in Time Trial" }) { it.timeTrialBoards.toLong() }

        val M = AchievementCategory.MASTERY
        one("avg-board", "Average No More", "Complete a board on Average", M) { "AVERAGE" in it.difficultiesCleared }
        one("hard-board", "Hard as Lead", "Complete a board on Hard", M) { "HARD" in it.difficultiesCleared }
        tiers("clean", M, listOf(1, 5, 25, 100, 250), listOf("Waste Not", "Frugal Refiner", "Nothing to Spare", "Perfect Economy", "Good Steward"),
            { "Complete $it boards without a discard" }) { it.cleanBoards.toLong() }
        one("fast-120", "Quicksilver", "Complete a board in under 2 minutes", M) { it.fastestBoardMs in 1..120_000 }
        one("fast-60", "Swift as Eagles", "Complete a board in under 1 minute", M) { it.fastestBoardMs in 1..60_000 }
        one("fast-40", "Twinkling of an Eye", "Complete a board in under 40 seconds", M) { it.fastestBoardMs in 1..40_000 }
        tiers("board-pts", M, listOf(1_000, 2_500, 5_000, 10_000), listOf("Gold Rush", "Mother Lode", "Treasure in Heaven", "Pearl of Great Price"),
            { "Score $it points on a single board" }) { it.bestBoardPoints }
        one("no-hint", "Unaided", "Reach board 5 in a game without using a hint", M) { it.noHintGameBoards >= 4 }
        one("no-hint-10", "Walk by Faith", "Reach board 10 in a game without using a hint", M) { it.noHintGameBoards >= 9 }

        val R = AchievementCategory.RANKS
        Ranks.table.drop(1).forEach { (score, rank) ->
            add(Achievement("rank-$score", rank, "Score ${fmt(score)} in one game", R, score) { it.bestScore })
        }

        val S = AchievementCategory.STREAKS
        tiers("streak", S, listOf(10, 20, 30, 50, 75, 100, 150, 200), listOf("On a Roll", "In the Flow", "Unbroken", "Fifty Strong", "Relentless", "The Hundred", "Steadfast", "Immovable"),
            { "Place $it stones in a row without a discard" }) { it.bestStreak.toLong() }

        val C = AchievementCategory.COMBOS
        tiers("double", C, listOf(1, 10, 25, 100, 250), listOf("Crossroads", "Intersection", "Grand Cross", "Master of Crosses", "Four Corners"),
            { "Clear a row and a column at once $it times" }) { it.doubleClears.toLong() }
        tiers("symbol", C, listOf(1, 5, 10, 25, 50, 100), listOf("Kindred Stones", "Sympathy", "Resonance", "Harmony", "Choir of Stones", "One Accord"),
            { "Clear $it lines of one symbol" }) { it.symbolLines.toLong() }
        tiers("perfect", C, listOf(1, 5, 10, 25, 50, 100), listOf("Perfectly Refined", "Flawless", "Immaculate", "Tried and True", "Seven Times Purified", "Pure Gold Throughout"),
            { "Clear $it lines of one symbol and one colour" }) { it.perfectLines.toLong() }

        val F = AchievementCategory.FORGE
        tiers("discards", F, listOf(1, 100, 500), listOf("Into the Fire", "Stoker", "Forgemaster"),
            { "Melt $it stones in the forge" }) { it.discards.toLong() }
        tiers("saves", F, listOf(1, 10, 25, 50), listOf("Close Call", "Brinkmanship", "Fireproof", "Through the Furnace"),
            { "Clear a line with the forge full, $it times" }) { it.forgeSaves.toLong() }
        tiers("hammers", F, listOf(1, 10, 50, 100, 250), listOf("First Strike", "Hammer and Anvil", "Stonebreaker", "Refiner's Hand", "Breaker of Rocks"),
            { "Use the Refiner's Hammer $it times" }) { it.hammersUsed.toLong() }
        tiers("hints", F, listOf(1, 25), listOf("A Little Help", "Well Advised"),
            { "Use $it hints" }) { it.hintsUsed.toLong() }
        one("all-dross", "All Dross", "End a game with no points at all", F) { it.zeroScoreGameOver }

        val H = AchievementCategory.CHALLENGES
        one("theme-temple", "Into the Temple", "Play a game in the Temple theme", H) { "TEMPLE" in it.themesPlayed }
        one("theme-future", "Back to the Future", "Play a game in the Future theme", H) { "FUTURE" in it.themesPlayed }
        one("theme-all", "Well Travelled", "Play a game in every theme", H) { it.themesPlayed.size >= 3 }
        tiers("nearby", H, listOf(1, 5, 10), listOf("Good Neighbour", "Block Party", "Love Thy Neighbour"),
            { "Swap scores with $it nearby devices" }) { it.nearbyDevices.toLong() }

        val V = AchievementCategory.RIVALS
        tiers("friends", V, listOf(1, 5, 10, 25), listOf("Kindred Spirit", "Fellowship", "Guild Master", "Great Cloud of Witnesses"),
            { "Have $it friends" }) { it.friends.toLong() }
        tiers("chats", V, listOf(1, 100), listOf("Whisper", "Correspondent"),
            { "Send $it chat messages" }) { it.chatsSent.toLong() }
        tiers("matches", V, listOf(1, 10, 50, 100), listOf("Duelist", "Regular Rival", "Arena Veteran", "Iron Sharpens Iron"),
            { "Play $it 1v1 matches" }) { it.matchesPlayed.toLong() }
        tiers("wins", V, listOf(1, 5, 10, 25, 50, 100), listOf("First Victory", "Contender", "Champion", "Victor", "Undisputed", "More Than a Conqueror"),
            { "Win $it 1v1 matches" }) { it.matchesWon.toLong() }
        tiers("win-streak", V, listOf(3, 5, 10), listOf("Hot Hand", "Unstoppable", "Invincible"),
            { "Win $it matches in a row" }) { it.bestMatchWinStreak.toLong() }
        one("race-win", "Photo Finish", "Win a Race match", V) { it.raceWins > 0 }
        one("timed-win", "Beat the Clock", "Win a Timed match", V) { it.timedWins > 0 }
        one("match-perfect", "Showboat", "Clear a perfect line during a 1v1 match", V) { it.perfectLineInMatch }
        tiers("async", V, listOf(1, 10), listOf("Pen Pal", "Correspondent Chess"),
            { "Finish $it challenge runs" }) { it.asyncPlayed.toLong() }
        tiers("async-won", V, listOf(1, 5, 25), listOf("Return to Sender", "Postmaster General", "Epistle Writer"),
            { "Win $it challenges" }) { it.asyncWon.toLong() }

        val D = AchievementCategory.DEDICATION
        tiers("games", D, listOf(1, 5, 10, 25, 50, 100, 250, 500, 1_000), listOf("Newcomer", "Returning", "Regular", "Devotee", "Diligent", "Centenarian", "Faithful", "Persevering", "Good and Faithful Servant"),
            { "Start $it games" }) { it.gamesStarted.toLong() }
        tiers("hours", D, listOf(1, 5, 10, 24, 50, 100, 250), listOf("An Hour at the Bench", "Afternoon Study", "Long Nights", "Full Day", "Life's Work", "Pillar in the Temple", "Finished the Race"),
            { "Play for $it hours in total" }) { it.playMillis / 3_600_000 }
    }

    init {
        check(all.map { it.id }.toSet().size == all.size) { "duplicate achievement id" }
    }

    fun byId(id: String) = all.firstOrNull { it.id == id }

    /** Achievements now satisfied that aren't in [unlocked] yet. */
    fun newlyUnlocked(stats: LifetimeStats, unlocked: Set<String>): List<Achievement> =
        all.filter { it.id !in unlocked && it.unlocked(stats) }
}
