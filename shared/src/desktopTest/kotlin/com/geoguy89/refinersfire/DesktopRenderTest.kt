package com.geoguy89.refinersfire

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.ui.RefinersFireApp
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import com.geoguy89.refinersfire.gfx.Palette
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Renders the desktop (Skia) build of the UI offscreen, as the Windows app will draw it. */
class DesktopRenderTest {
    private fun named() = Store(MemoryKeyValueStore()).also {
        it.saveSettings(com.geoguy89.refinersfire.data.Settings(playerName = "Alex", nameChosen = true))
    }

    private fun render(name: String, w: Int, h: Int, store: Store = named(), setup: (GameViewModel) -> Unit) {
        val vm = GameViewModel(store, SilentAudio)
        setup(vm)
        ImageComposeScene(w, h, Density(1.25f)) { RefinersFireApp(vm) }.use { scene ->
            var t = 0L
            repeat(40) { scene.render(t); t += 40_000_000L }
            val img = scene.render(t)
            val bytes = img.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/screenshots").mkdirs()
            File("build/screenshots/$name.png").writeBytes(bytes)
            assertTrue(bytes.size > 10_000)
        }
    }

    @Test
    fun titleWindow() = render("desktop_title", 1280, 860) {}

    private fun setupGame(vm: GameViewModel) {
        val e = GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, 5)
        e.play(GameEngine.index(3, 4))
        repeat(25) { e.validCells().let { v -> if (v.isEmpty()) e.discard() else e.play(v[(it * 13) % v.size]) } }
        val s = e.state
        vm.loadForPreview(s.copy(gold = List(s.gold.size) { i -> i / 9 == 6 || i % 9 == 2 }, forge = 1, score = 1234))
    }

    @Test
    fun gameWindow() = render("desktop_game", 1280, 860, setup = ::setupGame)

    @Test
    fun everyTheme() {
        for (theme in ThemeId.entries) {
            val n = theme.name.lowercase()
            render("theme_${n}_title", 1280, 860) { it.setTheme(theme) }
            render("theme_${n}_game", 1280, 860) { it.setTheme(theme); setupGame(it) }
            render("theme_${n}_phone_hint", 412, 915) { it.setTheme(theme); setupGame(it); it.useHint() }
            render("theme_${n}_options", 1280, 860) { it.setTheme(theme); it.push(Overlay.Options) }
        }
        Palette.theme = ThemeId.MODERN
    }

    @Test
    fun hallOfFameWithNearbyScores() {
        val store = named()
        fun hs(name: String, score: Long) = com.geoguy89.refinersfire.data.HighScore(
            name, score, com.geoguy89.refinersfire.game.Ranks.rankFor(score), 7, Difficulty.AVERAGE, GameMode.STRATEGIC, 1_790_000_000_000L,
        )
        store.addHighScore(hs("Alex", 8200)); store.addHighScore(hs("Alex", 3100))
        store.mergePeer("0123456789abcdef", listOf(hs("Sam", 9400), hs("Sam", 2500)), 1L)
        store.mergePeer("fedcba9876543210", listOf(hs("Jordan", 5600)), 2L)
        render("hall_of_fame_nearby", 1280, 860, store) { it.push(com.geoguy89.refinersfire.ui.Overlay.HighScores) }
    }

    @Test
    fun achievementsAndOptions() {
        val store = named()
        store.saveStats(com.geoguy89.refinersfire.game.LifetimeStats(stonesPlaced = 1840, linesCleared = 120, boardsCleared = 14, bestScore = 5200, bestStreak = 23, highestBoard = 9))
        render("achievements", 1280, 860, store) { vm ->
            vm.startNewGame(Difficulty.EASY, GameMode.STRATEGIC)
            vm.quitToTitle()
            vm.openAchievements()
        }
        render("options_share", 412, 915) { it.push(com.geoguy89.refinersfire.ui.Overlay.Options) }
        render("title_phone", 412, 915) {}
        render("name_first_launch", 1280, 860, Store(MemoryKeyValueStore())) {}
        render("name_first_launch_phone", 412, 915, Store(MemoryKeyValueStore())) {}
    }

    @Test
    fun piecesAndSets() {
        // Every piece in each set, plus the Cornerstone and the Hammer, on one board.
        for (set in ThemeId.entries) render("pieces_${set.name.lowercase()}", 1280, 860) { vm ->
            vm.setTheme(set)
            val base = GameEngine.newGame(Difficulty.HARD, GameMode.STRATEGIC, 3).state
            val cells = base.cells.toMutableList()
            com.geoguy89.refinersfire.game.Glyph.entries.forEachIndexed { i, g ->
                cells[GameEngine.index(1 + (i / 6) * 2, (i % 6) + 1)] = com.geoguy89.refinersfire.game.Piece.Stone(g, com.geoguy89.refinersfire.game.StoneColor.entries[i % 8])
            }
            cells[GameEngine.index(6, 2)] = com.geoguy89.refinersfire.game.Piece.Cornerstone
            cells[GameEngine.index(6, 5)] = com.geoguy89.refinersfire.game.Piece.Hammer
            vm.loadForPreview(base.copy(cells = cells, current = com.geoguy89.refinersfire.game.Piece.Hammer))
        }
        // Temple theme with the breastplate stones chosen as pieces.
        render("pieces_temple_with_stones", 1280, 860) { vm ->
            vm.setTheme(ThemeId.TEMPLE)
            vm.updateSettings(vm.settings.copy(pieceSet = ThemeId.MODERN))
            setupGame(vm)
        }
        Palette.pieceSet = null
        Palette.theme = ThemeId.MODERN
    }

    @Test
    fun perfectLine() = render("bonus_perfect_line", 1280, 860) { vm ->
        val base = GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, 5).state
        val stone = com.geoguy89.refinersfire.game.Piece.Stone(com.geoguy89.refinersfire.game.Glyph.TURQUOISE, com.geoguy89.refinersfire.game.StoneColor.RED)
        val cells = base.cells.toMutableList().also { c -> for (col in 0 until 8) c[GameEngine.index(3, col)] = stone }
        vm.loadForPreview(base.copy(cells = cells, current = stone))
        vm.tapCell(GameEngine.index(3, 8))
    }
}
