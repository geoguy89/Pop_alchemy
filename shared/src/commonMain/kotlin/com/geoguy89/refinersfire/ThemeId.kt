package com.geoguy89.refinersfire

import kotlinx.serialization.Serializable

/** The visual and audio themes. MODERN is the original look. */
@Serializable
enum class ThemeId(val displayName: String, val blurb: String) {
    MODERN("Modern", "Brass, velvet and the twelve stones of the breastplate."),
    // Kept as TEMPLE so saved settings still load; shown as the Temple theme.
    TEMPLE("Temple", "Lamplit stone and signs of the faith."),
    FUTURE("Future", "Neon circuitry and clean geometric shapes."),
    GARDEN("Garden", "Morning light on an olive grove, and the plants of Scripture."),
    /** Unlocked by gathering the daily Manna on seven days ("count the stars", Genesis 15:5). */
    STARLIGHT("Starlight", "A night sky full of stars, as promised to Abraham."),
}
