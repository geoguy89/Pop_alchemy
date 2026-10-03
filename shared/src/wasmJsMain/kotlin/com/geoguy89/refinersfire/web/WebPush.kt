package com.geoguy89.refinersfire.web

import com.geoguy89.refinersfire.net.PushRegistrar

// Web Push: notifications on an iPhone or iPad once the game is on the Home Screen (iOS 16.4+), and in desktop browsers.
// In a plain Safari tab there's no push, so the toggle is hidden and chats, challenges and requests show up in the game.

/** The game server's public push key (public by design: the browser needs it to subscribe). */
private const val VAPID_PUBLIC = "BGiERdXanq_7YqZI4jLYtNrkEQZqN3iQLXT523eN6J3g97H9lrSoYRFJuXSFkq6IAEx28VAcLF4HXiH3Fj4VzPs"

private fun jsPushSupported(): Boolean = js("'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window")

private fun jsPermission(): String = js("Notification.permission")

/** iOS only shows the permission prompt from a tap, so the request waits for the next one. */
private fun jsOnNextTap(action: () -> Unit): Unit = js(
    """{
        var fired = false;
        var run = function () {
            if (fired) return;
            fired = true;
            document.removeEventListener('touchend', run, true);
            document.removeEventListener('click', run, true);
            action();
        };
        document.addEventListener('touchend', run, true);
        document.addEventListener('click', run, true);
    }""",
)

private fun jsRequestPermission(done: (String) -> Unit): Unit = js(
    "{ Notification.requestPermission().then(function (p) { done(p); }).catch(function () { done('denied'); }); }",
)

private fun jsSubscribe(key: String, done: (String?) -> Unit): Unit = js(
    """{
        var raw = atob(key.replace(/-/g, '+').replace(/_/g, '/'));
        var bytes = new Uint8Array(raw.length);
        for (var i = 0; i < raw.length; i++) bytes[i] = raw.charCodeAt(i);
        navigator.serviceWorker.ready
          .then(function (reg) {
            return reg.pushManager.getSubscription().then(function (existing) {
              return existing || reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: bytes });
            });
          })
          .then(function (sub) { done(JSON.stringify(sub)); })
          .catch(function () { done(null); });
    }""",
)

object WebPush : PushRegistrar {
    override val supported: Boolean = jsPushSupported()

    override fun register(onToken: (String) -> Unit) {
        if (!supported) return
        val subscribe = { jsSubscribe(VAPID_PUBLIC) { sub -> if (sub != null) onToken("webpush:$sub") } }
        when (jsPermission()) {
            "granted" -> subscribe()
            "default" -> jsOnNextTap { jsRequestPermission { if (it == "granted") subscribe() } }
            // "denied": only the device's settings can turn it back on.
        }
    }
}
