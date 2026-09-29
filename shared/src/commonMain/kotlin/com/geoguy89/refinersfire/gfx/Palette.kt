package com.geoguy89.refinersfire.gfx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.StoneColor

/** Every colour a theme defines. Field names follow the Modern theme (brass, wood, ...). */
class ThemeColors(
    val night: Color,
    val wood: Color,
    val woodLight: Color,
    val stone: Color,
    val stoneDark: Color,
    val brass: Color,
    val brassLight: Color,
    val brassDark: Color,
    val parchment: Color,
    val ink: Color,
    val ember: Color,
    val lava: Color,
    val ledOrange: Color,
    val ledRed: Color,
    val leadLight: Color,
    val lead: Color,
    val leadDark: Color,
    val goldLight: Color,
    val gold: Color,
    val goldDark: Color,
    val hint: Color,
    val invalid: Color,
    /** Board frame gradient, light to dark. */
    val frame: List<Color>,
    /** Hand-slot well, centre to edge. */
    val well: List<Color>,
    /** Modal panel inset, top to bottom. */
    val panel: List<Color>,
    /** Score display background. */
    val screen: Color,
    /** Forge crucible interior. */
    val crucible: Color,
    val pieceColors: Map<StoneColor, Color>,
    val pieceDarkMix: Float = 0.62f,
    val gems: List<Color>,
)

private val defaultGems = listOf(
    Color(0xFFFF3B30), Color(0xFFFF9500), Color(0xFFFFD60A), Color(0xFF34C759),
    Color(0xFF30B0FF), Color(0xFF5856D6), Color(0xFFAF52DE), Color(0xFFFFFFFF),
)

private val modern = ThemeColors(
    night = Color(0xFF140E0A),
    wood = Color(0xFF2B1D12),
    woodLight = Color(0xFF4A3220),
    stone = Color(0xFF5E5242),
    stoneDark = Color(0xFF2E271F),
    brass = Color(0xFFC89B45),
    brassLight = Color(0xFFF4DA8C),
    brassDark = Color(0xFF6E4E1C),
    parchment = Color(0xFFF1E4C0),
    ink = Color(0xFF2A1A0C),
    ember = Color(0xFFFF7A1A),
    lava = Color(0xFFE2361B),
    ledOrange = Color(0xFFFFB02E),
    ledRed = Color(0xFFE8322C),
    leadLight = Color(0xFF878A7C),
    lead = Color(0xFF686C60),
    leadDark = Color(0xFF45483F),
    goldLight = Color(0xFFFFF3B0),
    gold = Color(0xFFF2C14E),
    goldDark = Color(0xFFB07A1C),
    hint = Color(0xFF7CFF9A),
    invalid = Color(0xFFFF4A3A),
    frame = listOf(Color(0xFF8A7A62), Color(0xFF5E5242), Color(0xFF3A3024)),
    well = listOf(Color(0xFF3B1F3A), Color(0xFF140A12)),
    panel = listOf(Color(0xFF33231A), Color(0xFF1A110B)),
    screen = Color(0xFF120A06),
    crucible = Color(0xFF120806),
    pieceColors = mapOf(
        StoneColor.GREEN to Color(0xFF34D24A),
        StoneColor.RED to Color(0xFFEA2E28),
        StoneColor.MAGENTA to Color(0xFFD640E6),
        StoneColor.BLUE to Color(0xFF3B6BFF),
        StoneColor.YELLOW to Color(0xFFFFDA1F),
        StoneColor.CYAN to Color(0xFF22E0E0),
        StoneColor.ORANGE to Color(0xFFFF8A1F),
        StoneColor.WHITE to Color(0xFFF7F7F2),
    ),
    gems = defaultGems,
)

private val temple = ThemeColors(
    night = Color(0xFF0C0B0A),
    wood = Color(0xFF221D18),
    woodLight = Color(0xFF3A322A),
    stone = Color(0xFF5A564E),
    stoneDark = Color(0xFF2A2724),
    brass = Color(0xFF9C8660),
    brassLight = Color(0xFFCDB98E),
    brassDark = Color(0xFF4A3D28),
    parchment = Color(0xFFE8D9AE),
    ink = Color(0xFF1C140A),
    ember = Color(0xFFFF8A2A),
    lava = Color(0xFFD8401C),
    ledOrange = Color(0xFFFFC266),
    ledRed = Color(0xFFD0582C),
    leadLight = Color(0xFF6E6A62),
    lead = Color(0xFF524E47),
    leadDark = Color(0xFF34312C),
    goldLight = Color(0xFFFBE7A6),
    gold = Color(0xFFD8A83C),
    goldDark = Color(0xFF8A6418),
    hint = Color(0xFF7DFFC4),
    invalid = Color(0xFFD0402A),
    frame = listOf(Color(0xFF5C5750), Color(0xFF3C3934), Color(0xFF1E1C19)),
    well = listOf(Color(0xFF2A2320), Color(0xFF0C0A09)),
    panel = listOf(Color(0xFF2C2620), Color(0xFF141110)),
    screen = Color(0xFF0E0B08),
    crucible = Color(0xFF100806),
    pieceColors = mapOf(
        StoneColor.GREEN to Color(0xFF4DB04A),
        StoneColor.RED to Color(0xFFC8352C),
        StoneColor.MAGENTA to Color(0xFFB04CC0),
        StoneColor.BLUE to Color(0xFF3F6AD8),
        StoneColor.YELLOW to Color(0xFFE8C62A),
        StoneColor.CYAN to Color(0xFF3CC4BE),
        StoneColor.ORANGE to Color(0xFFE07E26),
        StoneColor.WHITE to Color(0xFFEDE6D2),
    ),
    pieceDarkMix = 0.7f,
    gems = listOf(
        Color(0xFFC0392B), Color(0xFFD9822B), Color(0xFFE5C04A), Color(0xFF4E9A48),
        Color(0xFF3F7FBF), Color(0xFF5B4FA8), Color(0xFF9150A8), Color(0xFFEDE6D2),
    ),
)

private val future = ThemeColors(
    night = Color(0xFF05070F),
    wood = Color(0xFF0B1220),
    woodLight = Color(0xFF16233A),
    stone = Color(0xFF334255),
    stoneDark = Color(0xFF141C28),
    brass = Color(0xFF36C8F0),
    brassLight = Color(0xFFA8EEFF),
    brassDark = Color(0xFF0B4660),
    parchment = Color(0xFFD6F2FF),
    ink = Color(0xFF02101A),
    ember = Color(0xFF38E8FF),
    lava = Color(0xFFB03CFF),
    ledOrange = Color(0xFF45FFB5),
    ledRed = Color(0xFFFF4D8D),
    leadLight = Color(0xFF2E3A4A),
    lead = Color(0xFF1E2835),
    leadDark = Color(0xFF111821),
    goldLight = Color(0xFFFFF0B8),
    gold = Color(0xFFFFC23D),
    goldDark = Color(0xFFB0700C),
    hint = Color(0xFF45FFB5),
    invalid = Color(0xFFFF3D6E),
    frame = listOf(Color(0xFF26344A), Color(0xFF151F2E), Color(0xFF0A0F18)),
    well = listOf(Color(0xFF0E2238), Color(0xFF03070E)),
    panel = listOf(Color(0xFF0E1828), Color(0xFF060A12)),
    screen = Color(0xFF020A10),
    crucible = Color(0xFF03060C),
    pieceColors = mapOf(
        StoneColor.GREEN to Color(0xFF39FF6A),
        StoneColor.RED to Color(0xFFFF3B4E),
        StoneColor.MAGENTA to Color(0xFFFF45F0),
        StoneColor.BLUE to Color(0xFF4C7DFF),
        StoneColor.YELLOW to Color(0xFFFFEA3A),
        StoneColor.CYAN to Color(0xFF2EF6FF),
        StoneColor.ORANGE to Color(0xFFFF9A2E),
        StoneColor.WHITE to Color(0xFFF4FBFF),
    ),
    pieceDarkMix = 0.75f,
    gems = listOf(
        Color(0xFFFF3B4E), Color(0xFFFF9A2E), Color(0xFFFFEA3A), Color(0xFF39FF6A),
        Color(0xFF2EF6FF), Color(0xFF4C7DFF), Color(0xFFFF45F0), Color(0xFFFFFFFF),
    ),
)

/** The current theme's colours. Reads are snapshot state, so a theme change redraws everything. */
object Palette {
    var theme by mutableStateOf(ThemeId.MODERN)

    /** Piece shapes from another theme, or null to match [theme]. */
    var pieceSet by mutableStateOf<ThemeId?>(null)

    /** The theme whose piece shapes are drawn. */
    val pieces: ThemeId get() = pieceSet ?: theme

    val colors: ThemeColors
        get() = when (theme) {
            ThemeId.MODERN -> modern
            ThemeId.TEMPLE -> temple
            ThemeId.FUTURE -> future
        }

    val night get() = colors.night
    val wood get() = colors.wood
    val woodLight get() = colors.woodLight
    val stone get() = colors.stone
    val stoneDark get() = colors.stoneDark
    val brass get() = colors.brass
    val brassLight get() = colors.brassLight
    val brassDark get() = colors.brassDark
    val parchment get() = colors.parchment
    val ink get() = colors.ink
    val ember get() = colors.ember
    val lava get() = colors.lava
    val ledOrange get() = colors.ledOrange
    val ledRed get() = colors.ledRed

    val leadLight get() = colors.leadLight
    val lead get() = colors.lead
    val leadDark get() = colors.leadDark
    val goldLight get() = colors.goldLight
    val gold get() = colors.gold
    val goldDark get() = colors.goldDark

    val hint get() = colors.hint
    val invalid get() = colors.invalid

    fun pieceColor(c: StoneColor): Color = colors.pieceColors.getValue(c)
    fun pieceDark(c: StoneColor): Color = lerp(pieceColor(c), Color.Black, colors.pieceDarkMix)
    fun pieceLight(c: StoneColor): Color = lerp(pieceColor(c), Color.White, 0.6f)

    /** Gem colours for the "boards cleared" lights above the board. */
    val gems get() = colors.gems
}
