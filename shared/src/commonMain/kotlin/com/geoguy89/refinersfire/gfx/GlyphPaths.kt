package com.geoguy89.refinersfire.gfx

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.Glyph

/**
 * The piece shapes for each theme, as SVG path data in a 100x100 box. They are stroked (not filled), so each path
 * is a centre-line.
 */
object GlyphPaths {
    /**
     * Modern theme: the twelve stones of the high priest's breastplate (Exodus 28:17-20, NLT names), each drawn
     * as a different gem cut so pieces are told apart by shape. The first five appear from board 1, so they are the
     * most distinct outlines. Each slot keeps its [Glyph] identity; only the drawing and name change.
     */
    private val stones: Map<Glyph, Pair<String, String>> = mapOf(
        // Round brilliant with the crown line.
        Glyph.CARNELIAN to ("Carnelian" to "M16 50 A34 34 0 1 0 84 50 A34 34 0 1 0 16 50 M24 36 L76 36"),
        // Emerald cut: an octagon with its stepped table.
        Glyph.EMERALD to ("Emerald" to "M32 14 L68 14 L84 30 L84 70 L68 86 L32 86 L16 70 L16 30 Z M34 32 L66 32 L66 68 L34 68 Z"),
        // Cabochon: a polished dome.
        Glyph.TURQUOISE to ("Turquoise" to "M12 72 C12 22 88 22 88 72 Z M24 72 L76 72"),
        // Princess square with its facet cross.
        Glyph.LAPIS to ("Lapis Lazuli" to "M18 18 L82 18 L82 82 L18 82 Z M18 18 L82 82 M82 18 L18 82"),
        // Marquise: a pointed oval with its girdle.
        Glyph.JACINTH to ("Jacinth" to "M8 50 Q50 2 92 50 Q50 98 8 50 Z M8 50 L92 50"),
        // Upright oval.
        Glyph.PERIDOT to ("Peridot" to "M26 50 A24 36 0 1 0 74 50 A24 36 0 1 0 26 50 M34 30 L66 30"),
        // Pear drop.
        Glyph.MOONSTONE to ("Moonstone" to "M50 8 C62 30 82 48 82 64 A32 30 0 0 1 18 64 C18 48 38 30 50 8 Z"),
        // Banded hexagon, like a cut agate.
        Glyph.AGATE to ("Agate" to "M50 10 L85 30 L85 70 L50 90 L15 70 L15 30 Z M22 44 Q36 34 50 44 Q64 54 78 44 M22 60 Q36 50 50 60 Q64 70 78 60"),
        // Crystal point.
        Glyph.AMETHYST to ("Amethyst" to "M50 6 L74 30 L74 92 L26 92 L26 30 Z M26 30 L74 30 M50 6 L50 92"),
        // Baguette.
        Glyph.BERYL to ("Beryl" to "M32 8 L68 8 L68 92 L32 92 Z M32 22 L68 22 M32 78 L68 78"),
        // Kite (shield) cut.
        Glyph.ONYX to ("Onyx" to "M50 6 L88 36 L50 94 L12 36 Z M12 36 L88 36"),
        // Trillion.
        Glyph.JASPER to ("Jasper" to "M50 12 L90 84 L10 84 Z M50 42 L68 74 L32 74 Z"),
    )

    /** Temple theme: signs of the faith, simple enough to read at a glance. */
    private val temple: Map<Glyph, Pair<String, String>> = mapOf(
        // The ichthys: two arcs crossing into a tail.
        Glyph.CARNELIAN to ("Fish" to "M8 50 C32 18 70 20 92 64 M8 50 C32 82 70 80 92 36"),
        Glyph.EMERALD to ("Crown" to "M14 80 L18 30 L36 54 L50 20 L64 54 L82 30 L86 80 Z"),
        Glyph.TURQUOISE to ("Anchor" to "M50 22 L50 88 M32 34 L68 34 M16 60 C18 84 42 90 50 88 C58 90 82 84 84 60 M42 16 A8 8 0 1 0 58 16 A8 8 0 1 0 42 16"),
        // Two tablets of stone.
        Glyph.LAPIS to ("Tablets" to "M12 88 L12 32 C12 14 46 14 46 32 L46 88 Z M54 88 L54 32 C54 14 88 14 88 32 L88 88 Z"),
        Glyph.JACINTH to ("Key" to "M8 50 A14 14 0 1 0 36 50 A14 14 0 1 0 8 50 M36 50 L90 50 M76 50 L76 66 M90 50 L90 64"),
        // Oil lamp with its flame.
        Glyph.PERIDOT to ("Lamp" to "M10 66 C10 50 68 48 78 58 L94 50 L86 68 C72 82 16 82 10 66 Z M44 46 C38 36 44 24 50 16 C56 26 56 36 50 46"),
        Glyph.MOONSTONE to ("Wheat" to "M50 94 L50 12 M50 28 C40 24 36 16 38 10 M50 28 C60 24 64 16 62 10 M50 46 C38 42 34 32 36 26 M50 46 C62 42 66 32 64 26 M50 64 C38 60 34 50 36 44 M50 64 C62 60 66 50 64 44"),
        Glyph.AGATE to ("Olive Branch" to "M14 88 C38 66 60 42 88 14 M34 70 C24 62 24 50 32 44 C38 52 40 62 34 70 M52 52 C62 44 74 44 80 50 C72 58 60 58 52 52 M60 36 C54 26 56 16 64 12 C68 20 66 30 60 36"),
        Glyph.AMETHYST to ("Harp" to "M22 90 L22 12 C60 12 84 44 84 90 Z M36 28 L36 90 M50 32 L50 90 M64 44 L64 90"),
        Glyph.BERYL to ("Scroll" to "M26 14 L26 86 M74 14 L74 86 M26 26 L74 26 M26 74 L74 74 M18 14 L34 14 M66 14 L82 14 M18 86 L34 86 M66 86 L82 86 M36 42 L64 42 M36 54 L64 54"),
        // The shofar: a ram's horn.
        Glyph.ONYX to ("Shofar" to "M10 78 C24 88 46 84 60 66 C72 50 76 30 90 14 M16 62 C28 72 44 68 54 56 C64 42 70 24 80 10 M10 78 L16 62 M90 14 L80 10"),
        // The star of Bethlehem.
        Glyph.JASPER to ("Star" to "M50 6 L50 94 M6 50 L94 50 M24 24 L76 76 M76 24 L24 76 M40 50 A10 10 0 1 0 60 50 A10 10 0 1 0 40 50"),
    )

    /** Future theme: geometric shapes. */
    private val shapes: Map<Glyph, Pair<String, String>> = mapOf(
        Glyph.CARNELIAN to ("Hexagon" to "M50 10 L85 30 L85 70 L50 90 L15 70 L15 30 Z"),
        Glyph.EMERALD to ("Ring" to "M16 50 A34 34 0 1 0 84 50 A34 34 0 1 0 16 50 M36 50 A14 14 0 1 0 64 50 A14 14 0 1 0 36 50"),
        Glyph.TURQUOISE to ("Bolt" to "M58 8 L26 54 L48 54 L40 92 L74 44 L52 44 Z"),
        Glyph.LAPIS to ("Delta" to "M50 12 L88 82 L12 82 Z M50 46 L50 66"),
        Glyph.JACINTH to ("Plus" to "M50 12 L50 88 M12 50 L88 50"),
        Glyph.PERIDOT to ("Diamond" to "M50 8 L88 50 L50 92 L12 50 Z"),
        Glyph.MOONSTONE to ("Chevron" to "M14 22 L44 50 L14 78 M50 22 L80 50 L50 78"),
        Glyph.AGATE to ("Wave" to "M8 50 Q29 6 50 50 Q71 94 92 50"),
        Glyph.AMETHYST to ("Orbit" to "M12 50 A38 16 0 1 0 88 50 A38 16 0 1 0 12 50 M31 17 A38 16 60 1 0 69 83 A38 16 60 1 0 31 17"),
        Glyph.BERYL to ("Grid" to "M36 14 L36 86 M64 14 L64 86 M14 36 L86 36 M14 64 L86 64"),
        Glyph.ONYX to ("Star" to "M50 14 L60 40 L88 42 L66 59 L74 86 L50 71 L26 86 L34 59 L12 42 L40 40 Z"),
        Glyph.JASPER to ("Arrow" to "M50 84 L50 16 M26 40 L50 16 L74 40 M26 88 L74 88"),
    )

    /** Garden theme: plants of Scripture, each a distinct silhouette. */
    private val plants: Map<Glyph, Pair<String, String>> = mapOf(
        Glyph.CARNELIAN to ("Pomegranate" to "M50 34 C30 34 18 50 20 66 C22 84 38 92 50 92 C62 92 78 84 80 66 C82 50 70 34 50 34 Z M38 34 L34 16 L44 24 L50 12 L56 24 L66 16 L62 34"),
        Glyph.EMERALD to ("Fig" to "M50 18 C50 12 54 8 60 6 M50 18 C30 30 22 56 30 74 C36 88 64 88 70 74 C78 56 70 30 50 18 Z M42 70 L46 64 M56 72 L58 64"),
        Glyph.TURQUOISE to ("Grapes" to "M30 34 A8 8 0 1 0 46 34 A8 8 0 1 0 30 34 M46 34 A8 8 0 1 0 62 34 A8 8 0 1 0 46 34 M62 34 A8 8 0 1 0 78 34 A8 8 0 1 0 62 34 M38 50 A8 8 0 1 0 54 50 A8 8 0 1 0 38 50 M54 50 A8 8 0 1 0 70 50 A8 8 0 1 0 54 50 M46 66 A8 8 0 1 0 62 66 A8 8 0 1 0 46 66 M54 26 L56 10 M56 14 C64 6 76 8 80 16"),
        Glyph.LAPIS to ("Lily" to "M50 92 L50 58 M32 22 L32 40 C32 52 40 58 50 58 C60 58 68 52 68 40 L68 22 L59 32 L50 18 L41 32 Z M50 76 C42 68 30 68 24 74 M50 82 C58 74 70 74 76 80"),
        Glyph.JACINTH to ("Barley" to "M50 94 L50 32 M50 40 L38 30 M50 40 L62 30 M50 54 L36 44 M50 54 L64 44 M50 68 L38 58 M50 68 L62 58 M50 32 L50 8 M38 30 L30 14 M62 30 L70 14"),
        Glyph.PERIDOT to ("Almond Blossom" to "M37 28 A13 13 0 1 0 63 28 A13 13 0 1 0 37 28 M58 43 A13 13 0 1 0 84 43 A13 13 0 1 0 58 43 M50 68 A13 13 0 1 0 76 68 A13 13 0 1 0 50 68 M24 68 A13 13 0 1 0 50 68 A13 13 0 1 0 24 68 M16 43 A13 13 0 1 0 42 43 A13 13 0 1 0 16 43 M44 50 A6 6 0 1 0 56 50 A6 6 0 1 0 44 50"),
        Glyph.MOONSTONE to ("Palm" to "M46 94 C50 74 52 54 50 38 M50 38 C40 26 26 24 14 30 M50 38 C42 22 44 12 50 6 M50 38 C60 24 74 22 86 30 M50 38 C36 40 26 50 22 62 M50 38 C64 40 74 50 78 62"),
        Glyph.AGATE to ("Cedar" to "M50 8 L66 32 L58 32 L74 54 L62 54 L80 78 L20 78 L38 54 L26 54 L42 32 L34 32 Z M50 78 L50 94"),
        Glyph.AMETHYST to ("Vine" to "M10 60 C26 40 42 80 58 60 C70 44 82 50 90 40 M26 50 C18 40 22 28 32 26 C36 36 34 46 26 50 M58 60 C66 70 64 82 54 86 C50 76 52 66 58 60 M82 46 C86 54 82 62 74 62"),
        Glyph.BERYL to ("Hyssop" to "M50 94 L50 34 M44 28 A6 6 0 1 0 56 28 A6 6 0 1 0 44 28 M50 52 L36 42 M30 40 A6 6 0 1 0 42 40 A6 6 0 1 0 30 40 M50 60 L64 48 M58 46 A6 6 0 1 0 70 46 A6 6 0 1 0 58 46 M50 74 L38 66 M50 80 L62 72"),
        Glyph.ONYX to ("Rose of Sharon" to "M50 50 C56 46 58 54 52 58 C42 62 38 50 44 42 C52 32 68 40 66 54 C64 70 44 74 34 64 C22 52 30 30 48 26 C68 22 82 40 78 58 M40 76 L34 92 M60 76 L66 92"),
        Glyph.JASPER to ("Acorn" to "M50 8 L50 22 M26 40 C26 26 74 26 74 40 Z M26 40 L74 40 M32 40 C32 66 42 84 50 90 C58 84 68 66 68 40"),
    )

    private val cache = HashMap<Pair<ThemeId, Glyph>, Path>()

    private fun source(theme: ThemeId, glyph: Glyph): String = when (theme) {
        ThemeId.MODERN -> stones.getValue(glyph).second
        ThemeId.TEMPLE -> temple.getValue(glyph).second
        ThemeId.FUTURE -> shapes.getValue(glyph).second
        ThemeId.GARDEN -> plants.getValue(glyph).second
        ThemeId.STARLIGHT -> stones.getValue(glyph).second
    }

    /** Path in 0..100 coordinates, in the current theme's glyph set. */
    fun path(glyph: Glyph, theme: ThemeId = Palette.pieces): Path =
        cache.getOrPut(theme to glyph) { PathParser().parsePathString(source(theme, glyph)).toPath() }

    /** The glyph's name in the current theme. */
    fun name(glyph: Glyph, theme: ThemeId = Palette.pieces): String = when (theme) {
        ThemeId.MODERN -> stones.getValue(glyph).first
        ThemeId.TEMPLE -> temple.getValue(glyph).first
        ThemeId.FUTURE -> shapes.getValue(glyph).first
        ThemeId.GARDEN -> plants.getValue(glyph).first
        ThemeId.STARLIGHT -> stones.getValue(glyph).first
    }
}
