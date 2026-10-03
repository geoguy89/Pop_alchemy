package com.geoguy89.refinersfire.web

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import com.geoguy89.refinersfire.AppVersion
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.net.OnlineConfig
import com.geoguy89.refinersfire.net.PureCrypto
import com.geoguy89.refinersfire.ui.GameViewModel
import com.geoguy89.refinersfire.ui.RefinersFireApp
import com.geoguy89.refinersfire.ui.Screen

private fun jsMeta(name: String): String? = js("{ var m = document.querySelector('meta[name=\"' + name + '\"]'); return m ? m.getAttribute('content') : null; }")

/** ?server=... points a test page at a local server (never used by the real site). */
private fun jsServerParam(): String? = js("{ try { return new URLSearchParams(location.search).get('server'); } catch (e) { return null; } }")

private fun jsOnVisibility(changed: (Boolean) -> Unit): Unit = js(
    """{
        document.addEventListener('visibilitychange', function () { changed(document.visibilityState === 'visible'); });
        window.addEventListener('pagehide', function () { changed(false); });
    }""",
)

/** Keyboard play on a computer: Escape for the menu, Space/D to melt, H for a hint (as in the Windows app). */
private fun jsOnKey(key: (String) -> Boolean): Unit = js(
    """{
        document.addEventListener('keydown', function (e) {
            var t = e.target; if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA')) return;
            if (key(e.key)) e.preventDefault();
        }, true);
    }""",
)

private fun jsHideSplash(): Unit = js("{ var s = document.getElementById('splash'); if (s) { s.style.opacity = '0'; setTimeout(function () { s.remove(); }, 400); } }")

/** The web version: the same game as the phones, drawn by Compose into the page. */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    AppVersion.name = jsMeta("rf-version") ?: "web"
    AppVersion.build = jsMeta("rf-build")?.toIntOrNull() ?: 0
    jsServerParam()?.takeIf { it.startsWith("http://localhost") || it.startsWith("http://127.0.0.1") }?.let { OnlineConfig.baseUrl = it }
    val vm = GameViewModel(
        Store(LocalStorageStore), WebAudio(),
        http = FetchTransport, crypto = PureCrypto, push = WebPush,
    )
    jsOnVisibility { visible -> if (visible) vm.onAppForeground() else vm.onAppBackground() }
    jsOnKey { k ->
        val inGame = vm.screen == Screen.GAME && vm.overlay == null
        when (k) {
            "Escape" -> vm.onBack()
            " ", "d", "D" -> if (inGame) { vm.discard(); true } else false
            "h", "H" -> if (inGame) { vm.useHint(); true } else false
            else -> false
        }
    }
    vm.onAppForeground()
    ComposeViewport("app") { RefinersFireApp(vm) }
    jsHideSplash()
}
