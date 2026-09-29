package com.geoguy89.refinersfire

import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.geoguy89.refinersfire.audio.DesktopAudio
import com.geoguy89.refinersfire.data.FileKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.net.JvmChatCrypto
import com.geoguy89.refinersfire.net.OkHttpTransport
import com.geoguy89.refinersfire.net.OnlineConfig
import com.geoguy89.refinersfire.net.UdpLanTransport
import com.geoguy89.refinersfire.game.Glyph
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.StoneColor
import com.geoguy89.refinersfire.gfx.AppIcon
import com.geoguy89.refinersfire.ui.RefinersFireApp
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Screen

/** The window icon: the same flame and crucible as the Android launcher. */
private object AppWindowIcon : Painter() {
    override val intrinsicSize = Size(256f, 256f)
    override fun DrawScope.onDraw() = with(AppIcon) { drawAppIcon() }
}

fun main() = application {
    val vm = remember { run {
        // REFINERS_SERVER points a desktop build at a test server.
        System.getenv("REFINERS_SERVER")?.let { OnlineConfig.baseUrl = it }
        AppVersion.name = System.getProperty("refiners.version") ?: "dev"
        AppVersion.build = System.getProperty("refiners.build")?.toIntOrNull() ?: 0
        GameViewModel(Store(FileKeyValueStore.default()), DesktopAudio(), UdpLanTransport(), OkHttpTransport(), JvmChatCrypto)
    } }
    val state = rememberWindowState(size = DpSize(1280.dp, 860.dp), position = WindowPosition.Aligned(androidx.compose.ui.Alignment.Center))
    remember { vm.onAppForeground() }
    // Chats, challenges and friend requests while the window is in the background show as system notifications.
    val tray = androidx.compose.ui.window.rememberTrayState()
    Tray(icon = AppWindowIcon, state = tray, tooltip = "Refiner's Fire")
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.systemNotices.collect { (title, text) -> tray.sendNotification(androidx.compose.ui.window.Notification(title, text)) }
    }
    Window(
        onCloseRequest = {
            vm.onAppBackground()
            vm.dispose()
            exitApplication()
        },
        state = state,
        title = "Refiner's Fire",
        icon = AppWindowIcon,
        onPreviewKeyEvent = { e ->
            if (e.type != KeyEventType.KeyDown) return@Window false
            when (e.key) {
                Key.Escape -> vm.onBack()
                Key.Spacebar, Key.D, Key.Delete, Key.Backspace ->
                    if (vm.screen == Screen.GAME && vm.overlay == null) { vm.discard(); true } else false
                Key.H -> if (vm.screen == Screen.GAME && vm.overlay == null) { vm.useHint(); true } else false
                Key.F11 -> {
                    state.placement = if (state.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen) {
                        androidx.compose.ui.window.WindowPlacement.Floating
                    } else {
                        androidx.compose.ui.window.WindowPlacement.Fullscreen
                    }
                    true
                }
                else -> false
            }
        },
    ) {
        window.minimumSize = java.awt.Dimension(720, 560)
        androidx.compose.runtime.DisposableEffect(window) {
            val focus = object : java.awt.event.WindowFocusListener {
                override fun windowGainedFocus(e: java.awt.event.WindowEvent?) { vm.windowFocused = true }
                override fun windowLostFocus(e: java.awt.event.WindowEvent?) { vm.windowFocused = false }
            }
            window.addWindowFocusListener(focus)
            onDispose { window.removeWindowFocusListener(focus) }
        }
        RefinersFireApp(vm)
    }
}
