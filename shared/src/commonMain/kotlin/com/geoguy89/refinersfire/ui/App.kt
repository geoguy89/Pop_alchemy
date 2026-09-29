package com.geoguy89.refinersfire.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.geoguy89.refinersfire.gfx.Palette

/** The whole game UI, shared by the Android and desktop apps. */
@Composable
fun RefinersFireApp(vm: GameViewModel, posture: Posture = Posture(), onHaptic: (Haptic) -> Unit = {}) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Palette.gold, surface = Palette.wood, background = Palette.night)) {
        Fonts.ProvideFonts {
            LaunchedEffect(Unit) {
                while (true) withFrameNanos(vm::onFrame)
            }
            LaunchedEffect(Unit) {
                vm.haptics.collect { h -> if (vm.settings.haptics) onHaptic(h) }
            }
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(vm.screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { screen ->
                    when (screen) {
                        Screen.TITLE -> TitleScreen(vm)
                        Screen.GAME -> GameScreen(vm, posture)
                    }
                }
                MatchCountdown(vm)
                OverlayHost(vm)
                IncomingBanner(vm)
                NoticeToast(vm)
                AchievementNote(vm)
                // Asked once, before anything else; nothing behind it can be used until a name is chosen.
                if (vm.needsName) NamePanel(vm, firstTime = true)
            }
        }
    }
}
