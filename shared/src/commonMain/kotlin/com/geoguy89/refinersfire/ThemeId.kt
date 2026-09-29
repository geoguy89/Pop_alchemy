package com.geoguy89.refinersfire

import kotlinx.serialization.Serializable

/** The visual and audio themes. MODERN is the original look. */
@Serializable
enum class ThemeId(val displayName: String, val blurb: String) {
    MODERN("Modern", "Brass, velvet and the twelve stones of the breastplate."),
    // Kept as TEMPLE so saved settings still load; shown as the Temple theme.
    TEMPLE("Temple", "Lamplit stone and signs of the faith."),
    FUTURE("Future", "Neon circuitry and clean geometric shapes."),
}
