package com.geoguy89.refinersfire

import android.app.Application
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.geoguy89.refinersfire.audio.AudioEngine
import com.geoguy89.refinersfire.data.PrefsKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.net.JvmChatCrypto
import com.geoguy89.refinersfire.net.OkHttpTransport
import com.geoguy89.refinersfire.net.UdpLanTransport
import com.geoguy89.refinersfire.ui.RefinersFireApp
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.Haptic
import com.geoguy89.refinersfire.ui.Posture
import com.geoguy89.refinersfire.ui.Screen
import kotlinx.coroutines.flow.map

/** Keeps the game alive across configuration changes such as folding and unfolding. */
class GameHolder(app: Application) : AndroidViewModel(app) {
    init {
        val info = app.packageManager.getPackageInfo(app.packageName, 0)
        AppVersion.name = info.versionName ?: "?"
        AppVersion.build = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    }

    // Some devices drop Wi-Fi broadcasts unless a multicast lock is held; it is held only while sharing is active.
    private val multicastLock = (app.applicationContext.getSystemService(android.content.Context.WIFI_SERVICE) as android.net.wifi.WifiManager)
        .createMulticastLock("refinersfire-lan").apply { setReferenceCounted(false) }
    val game = GameViewModel(
        Store(PrefsKeyValueStore(app)),
        AudioEngine(app),
        UdpLanTransport(onActive = { on -> if (on) multicastLock.acquire() else if (multicastLock.isHeld) multicastLock.release() }),
        OkHttpTransport(),
        JvmChatCrypto,
        AndroidUpdater(app),
        AndroidPush.registrar(app),
    )
    override fun onCleared() = game.dispose()
}

class MainActivity : ComponentActivity() {
    private val holder: GameHolder by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm = holder.game
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val notificationPermission = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
        AndroidPush.askPermission = {
            if (Build.VERSION.SDK_INT >= 33) runOnUiThread { notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        }
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { AndroidPush.visible = true; vm.onAppForeground() }
                Lifecycle.Event.ON_STOP -> { AndroidPush.visible = false; vm.onAppBackground() }
                else -> Unit
            }
        })

        // Track the hinge of foldables so the board never straddles the fold when half-open.
        val folds = WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map { info ->
            info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.takeIf { it.state == FoldingFeature.State.HALF_OPENED }
        }

        setContent {
            val fold by folds.collectAsState(initial = null)
            val density = LocalDensity.current
            val posture = fold?.let { f ->
                with(density) {
                    if (f.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                        Posture(tabletopHingeY = f.bounds.top.toDp(), hingeThickness = f.bounds.height().toDp())
                    } else {
                        Posture(bookHingeX = f.bounds.left.toDp(), hingeThickness = f.bounds.width().toDp())
                    }
                }
            } ?: Posture()
            val view = LocalView.current
            // Don't let the screen sleep mid-game; the title screen may still time out as usual.
            // Android drops this automatically whenever the app is in the background.
            val playing = vm.screen == Screen.GAME
            SideEffect { view.keepScreenOn = playing }
            BackHandler(enabled = vm.screen == Screen.GAME || vm.overlay != null) { vm.onBack() }
            RefinersFireApp(vm, posture, onHaptic = view::haptic)
        }
    }
}

private fun View.haptic(h: Haptic) {
    val constant = when (h) {
        Haptic.TICK -> HapticFeedbackConstants.CLOCK_TICK
        Haptic.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Haptic.REJECT -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Haptic.HEAVY -> HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}
