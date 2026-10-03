package com.geoguy89.refinersfire.game

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The twelve astrological glyphs, in the order they are introduced as the boards progress. */
@Serializable
enum class Glyph {
    CARNELIAN,
    EMERALD,
    TURQUOISE,
    LAPIS,
    JACINTH,
    PERIDOT,
    MOONSTONE,
    AGATE,
    AMETHYST,
    BERYL,
    ONYX,
    JASPER,
}

/** Stone colours, in the order they are introduced as the boards progress. */
@Serializable
enum class StoneColor(val displayName: String) {
    GREEN("Green"),
    RED("Red"),
    MAGENTA("Violet"),
    BLUE("Blue"),
    YELLOW("Yellow"),
    CYAN("Cyan"),
    ORANGE("Orange"),
    WHITE("White"),
}

@Serializable
sealed interface Piece {
    @Serializable
    @SerialName("stone")
    data class Stone(val glyph: Glyph, val color: StoneColor) : Piece

    /** The stone block: a wild card that matches anything. */
    @Serializable
    @SerialName("cornerstone")
    data object Cornerstone : Piece

    /** Hammer and crossbones: removes one piece of the player's choice from the board. */
    @Serializable
    @SerialName("hammer")
    data object Hammer : Piece
}

@Serializable
enum class Difficulty(val displayName: String, val startBoard: Int, val scoreMultiplier: Int) {
    EASY("Easy", 1, 1),
    AVERAGE("Average", 6, 2),
    HARD("Hard", 11, 4),
}

@Serializable
enum class GameMode(val displayName: String, val scoreMultiplier: Int, val blurb: String) {
    STRATEGIC("Strategic", 1, "No clock. Take all the time you need."),
    TIME_TRIAL("Time Trial", 2, "Place each stone before the hourglass runs out. Scores x2."),
    /** The forge holds a single level: one bad discard too many ends the game. */
    IRON_FORGE("Iron Forge", 3, "The forge holds just one level, so a second discard ends it. Scores x3."),
    /** The next three stones are shown, so placements can be planned. */
    FORESIGHT("Foresight", 1, "See the next three stones coming and plan ahead."),
}

/** Why a forge level is lit, so the forge can show each cause in its own colour. */
@Serializable
enum class ForgeSource { DISCARD, MISS, HINT, STOKE }

/** How many glyphs and colours can appear on a given board. */
data class BoardConfig(val glyphs: Int, val colors: Int) {
    companion object {
        fun forBoard(board: Int): BoardConfig {
            val b = board.coerceAtLeast(1)
            val glyphs = (4 + (3 * b + 3) / 4).coerceAtMost(Glyph.entries.size)
            val colors = (3 + (b - 1) / 3).coerceAtMost(StoneColor.entries.size)
            return BoardConfig(glyphs, colors)
        }
    }
}

object Ranks {
    /** Minimum score for each rank, ascending. */
    val table: List<Pair<Long, String>> = listOf(
        0L to "Dross",
        400L to "Raw Ore",
        700L to "Ore Gatherer",
        1000L to "Apprentice Smelter",
        1500L to "Apprentice Smith",
        2000L to "Senior Apprentice",
        2500L to "Bellows Keeper",
        3000L to "Furnace Tender",
        3500L to "Crucible Keeper",
        4500L to "Journeyman",
        5000L to "Silversmith",
        6000L to "Goldsmith 3rd Class",
        7000L to "Goldsmith 2nd Class",
        8000L to "Goldsmith 1st Class",
        10000L to "Master Goldsmith",
        12000L to "Refiner 3rd Class",
        14000L to "Refiner 2nd Class",
        16000L to "Refiner 1st Class",
        20000L to "Master Refiner",
        25000L to "Refiner of Silver",
        30000L to "Refiner of Gold",
        40000L to "Pure Gold",
    )

    fun rankFor(score: Long): String = table.last { score >= it.first }.second

    /** The next rank and the score needed to reach it, or null at the top rank. */
    fun nextRank(score: Long): Pair<Long, String>? = table.firstOrNull { it.first > score }
}
