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
import com.geoguy89.refinersfire.gfx.Reliefs
import com.geoguy89.refinersfire.ui.RefinersFireApp
import com.geoguy89.refinersfire.ui.GameViewModel
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Test
import androidx.compose.foundation.layout.fillMaxSize
import java.io.File

/** Renders one board for each engraved relief to shared/build/screenshots/relief_N.png. */
class ReliefRenderTest {
    @Test
    fun everyReliefRenders() {
        for (i in 0 until Reliefs.count) {
            val vm = GameViewModel(Store(MemoryKeyValueStore()).also { it.saveSettings(com.geoguy89.refinersfire.data.Settings(playerName = "Alex", nameChosen = true)) }, SilentAudio)
            val s = GameEngine.newGame(Difficulty.EASY, GameMode.STRATEGIC, 3).state
            // A few gold columns so the relief can be seen over both metals.
            vm.loadForPreview(s.copy(board = i + 1, gold = List(s.gold.size) { k -> k % 9 in setOf(1, 2, 6) || k / 9 == 5 }))
            ImageComposeScene(1100, 720, Density(1f)) { RefinersFireApp(vm) }.use { scene ->
                var t = 0L
                repeat(30) { scene.render(t); t += 40_000_000L }
                val bytes = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
                File("build/screenshots").mkdirs()
                File("build/screenshots/relief_${i + 1}.png").writeBytes(bytes)
            }
        }
    }

    @Test
    fun appIcon() {
        java.io.File("build/icons").mkdirs()
        for (px in listOf(16, 24, 32, 48, 64, 128, 256)) {
            androidx.compose.ui.ImageComposeScene(px, px) {
                androidx.compose.foundation.Canvas(androidx.compose.ui.Modifier.fillMaxSize()) { with(com.geoguy89.refinersfire.gfx.AppIcon) { drawAppIcon() } }
            }.use { scene ->
                java.io.File("build/icons/icon_$px.png").writeBytes(scene.render().encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)!!.bytes)
            }
        }
    }
}
