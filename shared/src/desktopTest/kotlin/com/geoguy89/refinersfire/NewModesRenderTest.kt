package com.geoguy89.refinersfire

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.HighScore
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.ForgeSource
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.Ranks
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import com.geoguy89.refinersfire.ui.RefinersFireApp
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Screens added with the new modes: the Hall of Fame, New Game, Foresight, Iron Forge and the forge's colours. */
class NewModesRenderTest {
    private fun named() = Store(MemoryKeyValueStore()).also {
        it.saveSettings(com.geoguy89.refinersfire.data.Settings(playerName = "Alex", nameChosen = true))
    }

    private fun render(name: String, w: Int, h: Int, store: Store = named(), setup: (GameViewModel) -> Unit) {
        val vm = GameViewModel(store, SilentAudio)
        setup(vm)
        // Phone shots (twice 412 x 915) lay out at a real phone's 412 dp width; desktop shots as the Windows app.
        val density = if (w == 412 * 2) 2f else 1.25f
        ImageComposeScene(w, h, Density(density)) { RefinersFireApp(vm) }.use { scene ->
            var t = 0L
            repeat(40) { scene.render(t); t += 40_000_000L }
            val bytes = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/screenshots").mkdirs()
            File("build/screenshots/$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 10_000)
        }
    }

    private fun hs(name: String, score: Long, d: Difficulty, m: GameMode, t: Long = epochMillis()) =
        HighScore(name, score, Ranks.rankFor(score), 7, d, m, t)

    @Test
    fun hallOfFame() {
        val store = named()
        store.addHighScore(hs("Alex", 8200, Difficulty.AVERAGE, GameMode.STRATEGIC))
        store.addHighScore(hs("Alex", 3100, Difficulty.EASY, GameMode.TIME_TRIAL))
        store.addHighScore(hs("Alex", 12400, Difficulty.HARD, GameMode.IRON_FORGE))
        store.addHighScore(hs("Alex", 2600, Difficulty.EASY, GameMode.FORESIGHT))
        store.mergePeer("0123456789abcdef", listOf(hs("Sam", 9400, Difficulty.AVERAGE, GameMode.STRATEGIC), hs("Sam", 2500, Difficulty.EASY, GameMode.STRATEGIC)), 1L, open = true)
        store.mergePeer("fedcba9876543210", listOf(hs("Jordan", 5600, Difficulty.HARD, GameMode.TIME_TRIAL)), 2L, open = true)
        store.mergePeer("00112233445566aa", listOf(hs("Riley", 4100, Difficulty.EASY, GameMode.FORESIGHT)), 3L, open = true)
        render("hall_of_fame_phone", 412 * 2, 915 * 2, store) { it.push(Overlay.HighScores) }
        render("hall_of_fame_desktop", 1280, 1000, store) { it.push(Overlay.HighScores) }
    }

    @Test
    fun newGameScreen() = render("new_game_phone", 412 * 2, 915 * 2) { it.push(Overlay.NewGame) }

    private fun played(mode: GameMode): com.geoguy89.refinersfire.game.GameState {
        val e = GameEngine.newGame(Difficulty.EASY, mode, 5)
        e.play(GameEngine.index(3, 4))
        repeat(18) { e.validCells().let { v -> if (v.isEmpty()) e.discard() else e.play(v[(it * 13) % v.size]) } }
        return e.state
    }

    @Test
    fun foresight() = render("foresight_phone", 412 * 2, 915 * 2) { it.loadForPreview(played(GameMode.FORESIGHT)) }

    @Test
    fun ironForge() = render("iron_forge_phone", 412 * 2, 915 * 2) {
        it.loadForPreview(played(GameMode.IRON_FORGE).copy(forge = 1, forgeSources = listOf(ForgeSource.DISCARD)))
    }

    @Test
    fun puzzles() {
        val store = named()
        store.savePuzzleStars(mapOf(1 to 3, 2 to 2, 3 to 3, 4 to 1, 5 to 3, 6 to 2))
        render("puzzles_phone", 412 * 2, 915 * 2, store) { it.push(Overlay.Puzzles) }
        render("puzzle_play_phone", 412 * 2, 915 * 2, store) { it.startPuzzle(7) }
        render("puzzle_done_phone", 412 * 2, 915 * 2, store) {
            it.startPuzzle(3)
            it.push(Overlay.PuzzleDone(3, true, 2, "Solved!"))
        }
    }

    @Test
    fun newThemesOnAPhone() {
        for (theme in listOf(ThemeId.GARDEN, ThemeId.STARLIGHT)) {
            val n = theme.name.lowercase()
            render("theme_${n}_title_phone", 412 * 2, 915 * 2) { it.setTheme(theme) }
            render("theme_${n}_game_phone", 412 * 2, 915 * 2) { it.setTheme(theme); it.loadForPreview(played(GameMode.STRATEGIC)) }
        }
        com.geoguy89.refinersfire.gfx.Palette.theme = ThemeId.MODERN
    }

    @Test
    fun forgeColours() {
        val s = played(GameMode.STRATEGIC).copy(forge = 3, stoked = 1, forgeSources = listOf(ForgeSource.STOKE, ForgeSource.HINT, ForgeSource.MISS))
        render("forge_colours_phone", 412 * 2, 915 * 2) { it.loadForPreview(s) }
        render("forge_colours_desktop", 1280, 860) { it.loadForPreview(s) }
        render("forge_colours_future", 1280, 860) { it.setTheme(ThemeId.FUTURE); it.loadForPreview(s) }
        com.geoguy89.refinersfire.gfx.Palette.theme = ThemeId.MODERN
    }
}
