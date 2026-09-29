package com.geoguy89.refinersfire

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameEvent
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.ui.RefinersFireApp
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Overlay
import com.geoguy89.refinersfire.ui.Posture
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real UI on the JVM and saves PNGs to app/build/screenshots, so the art and the adaptive layouts can
 * be checked without a device.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** A believable mid-game position: some gold, a scattering of stones. */
    private fun midGame(mode: GameMode = GameMode.STRATEGIC, moves: Int = 70, boardEvent: Boolean = false): Pair<GameState, GameEvent.BoardCleared?> {
        val e = GameEngine.newGame(Difficulty.AVERAGE, mode, seed = 11)
        var cleared: GameEvent.BoardCleared? = null
        var i = 0
        while (i < moves && !e.state.gameOver) {
            val valid = e.validCells()
            // Prefer squares that touch the most stones, like a sensible player.
            val best = valid.maxByOrNull { idx -> GameEngine.neighbors(idx).count { e.state.cells[it] != null } * 10 + (idx * 31 % 7) }
            val events = if (best == null) e.discard() else e.play(best)
            events.filterIsInstance<GameEvent.BoardCleared>().firstOrNull()?.let { cleared = it; if (boardEvent) return e.state to it }
            if (e.state.forge == 3) e.play(e.validCells().firstOrNull() ?: break)
            i++
        }
        return e.state to cleared
    }

    private fun shoot(name: String, posture: Posture = Posture(), setup: (GameViewModel) -> Unit) {
        val vm = GameViewModel(Store(MemoryKeyValueStore()), SilentAudio)
        setup(vm)
        compose.mainClock.autoAdvance = false
        compose.setContent { RefinersFireApp(vm, posture) }
        compose.mainClock.advanceTimeBy(1_600)
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "w411dp-h923dp-xxhdpi")
    fun title_phone() = shoot("title_phone") {}

    @Test
    @Config(qualifiers = "w791dp-h820dp-xxhdpi")
    fun title_fold_unfolded() = shoot("title_fold_unfolded") {}

    @Test
    @Config(qualifiers = "w411dp-h923dp-xxhdpi")
    fun game_phone() = shoot("game_phone") { it.loadForPreview(midGame().first) }

    @Test
    @Config(qualifiers = "w791dp-h820dp-xxhdpi")
    fun game_fold_unfolded() = shoot("game_fold_unfolded") { it.loadForPreview(midGame(GameMode.TIME_TRIAL, 90).first) }

    @Test
    @Config(qualifiers = "w915dp-h411dp-land-xxhdpi")
    fun game_phone_landscape() = shoot("game_phone_landscape") { it.loadForPreview(midGame(moves = 40).first) }

    @Test
    @Config(qualifiers = "w882dp-h851dp-xhdpi")
    fun game_fold_tabletop() = shoot("game_fold_tabletop", Posture(tabletopHingeY = 425.dp)) { it.loadForPreview(midGame(moves = 120).first) }

    @Test
    @Config(qualifiers = "w411dp-h923dp-xxhdpi")
    fun new_game_panel() = shoot("new_game_panel") { it.loadForPreview(midGame().first, Overlay.NewGame) }

    @Test
    @Config(qualifiers = "w851dp-h882dp-xhdpi")
    fun board_complete() = shoot("board_complete") {
        val s = midGame().first
        val done = s.copy(gold = List(s.gold.size) { true }, boardsCleared = 3, score = 5230, bestStreak = 41, elapsedMillis = 1_234_000)
        it.loadForPreview(s.copy(board = s.board + 1), Overlay.BoardComplete(GameEvent.BoardCleared(s.board, 1250, done)))
    }

    @Test
    @Config(qualifiers = "w411dp-h923dp-xxhdpi")
    fun partly_gold() = shoot("partly_gold") {
        val s = midGame(moves = 30).first
        val gold = List(s.gold.size) { i -> i / 9 in setOf(2, 5) || i % 9 in setOf(1, 7) }
        it.loadForPreview(s.copy(gold = gold, forge = 1, boardsCleared = 2, current = com.geoguy89.refinersfire.game.Piece.Hammer))
    }

    @Test
    @Config(qualifiers = "w411dp-h923dp-xxhdpi")
    fun how_to_play() = shoot("how_to_play") { it.loadForPreview(midGame().first, Overlay.HowToPlay) }
}
