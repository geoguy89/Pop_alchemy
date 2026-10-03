package com.geoguy89.refinersfire.web

import com.geoguy89.refinersfire.data.KeyValueStore
import com.geoguy89.refinersfire.net.HttpTransport
import com.geoguy89.refinersfire.net.LiveSocket
import com.geoguy89.refinersfire.net.SocketListener

// ---- Storage: the browser's localStorage (kept per site; a home-screen app has its own) --------------------------------

private fun lsGet(key: String): String? = js("{ try { return localStorage.getItem(key); } catch (e) { return null; } }")
private fun lsSet(key: String, value: String): Unit = js("{ try { localStorage.setItem(key, value); } catch (e) {} }")
private fun lsRemove(key: String): Unit = js("{ try { localStorage.removeItem(key); } catch (e) {} }")

object LocalStorageStore : KeyValueStore {
    private const val PREFIX = "rf."
    override fun get(key: String): String? = lsGet(PREFIX + key)
    override fun put(key: String, value: String?) = if (value == null) lsRemove(PREFIX + key) else lsSet(PREFIX + key, value)
}

// ---- HTTP and WebSockets: fetch, and the browser's WebSocket ------------------------------------------------------------

private fun jsFetch(method: String, url: String, body: String?, headersJson: String, done: (Int, String?) -> Unit): Unit = js(
    """{
        fetch(url, { method: method, headers: JSON.parse(headersJson), body: body, cache: 'no-store' })
          .then(function (r) { return r.text().then(function (t) { done(r.status, t); }); })
          .catch(function () { done(-1, null); });
    }""",
)

external interface JsSocket : JsAny

private fun jsOpenSocket(url: String, auth: String, onOpen: () -> Unit, onMessage: (String) -> Unit, onClose: (Boolean) -> Unit): JsSocket = js(
    """{
        var opened = false;
        // Browsers can't set headers on a WebSocket, so the sign-in rides along as a second subprotocol.
        var ws = auth ? new WebSocket(url, ['refinersfire', auth]) : new WebSocket(url);
        ws.onopen = function () { opened = true; onOpen(); };
        ws.onmessage = function (e) { if (typeof e.data === 'string') onMessage(e.data); };
        ws.onclose = function () { onClose(!opened); };
        return ws;
    }""",
)

private fun jsSend(ws: JsSocket, text: String): Unit = js("{ try { if (ws.readyState === 1) ws.send(text); } catch (e) {} }")
private fun jsClose(ws: JsSocket): Unit = js("{ try { ws.close(1000, 'bye'); } catch (e) {} }")

private fun jsonString(s: String): String = buildString {
    append('"')
    for (c in s) when (c) {
        '"' -> append("\\\"")
        '\\' -> append("\\\\")
        '\n' -> append("\\n")
        else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
    }
    append('"')
}

/** Requests through fetch; sockets through WebSocket. Callbacks arrive on the page's only thread. */
object FetchTransport : HttpTransport {
    override fun request(method: String, url: String, body: String?, headers: Map<String, String>, done: (Int, String?) -> Unit) {
        val json = headers.entries.joinToString(",", "{", "}") { (k, v) -> jsonString(k) + ":" + jsonString(v) }
        val payload = body ?: if (method == "POST") "{}" else null
        jsFetch(method, url, payload, json, done)
    }

    override fun openSocket(url: String, headers: Map<String, String>, listener: SocketListener): LiveSocket {
        // "Bearer <id>.<secret>" becomes the subprotocol "rf-auth.<id>.<secret>".
        val auth = headers["Authorization"]?.removePrefix("Bearer ")?.let { "rf-auth.$it" } ?: ""
        // A socket that never opened was refused (or the network is down); the match retries or gives up accordingly.
        val ws = jsOpenSocket(url, auth, { listener.onOpen() }, { listener.onMessage(it) }, { neverOpened -> listener.onClosed(refused = neverOpened) })
        return object : LiveSocket {
            override fun send(text: String) = jsSend(ws, text)
            override fun close() = jsClose(ws)
        }
    }
}
