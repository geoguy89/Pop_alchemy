package com.geoguy89.refinersfire.net

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit

/** HTTP and WebSockets for Android and desktop. Callbacks arrive on OkHttp's threads. */
class OkHttpTransport : HttpTransport {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json".toMediaType()

    override fun request(method: String, url: String, body: String?, headers: Map<String, String>, done: (Int, String?) -> Unit) {
        val req = try {
            Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
                method(method, body?.toRequestBody(jsonType) ?: if (method == "POST") "{}".toRequestBody(jsonType) else null)
            }.build()
        } catch (e: IllegalArgumentException) {
            done(-1, null)
            return
        }
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = done(-1, null)
            override fun onResponse(call: Call, response: Response) = response.use { done(it.code, it.body?.string()) }
        })
    }

    override fun openSocket(url: String, headers: Map<String, String>, listener: SocketListener): LiveSocket {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        var opened = false
        val ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened = true
                listener.onOpen()
            }
            override fun onMessage(webSocket: WebSocket, text: String) = listener.onMessage(text)
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = listener.onClosed(refused = false)
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // A non-101 answer to the handshake (403 not in the match, 410 finished) is a refusal, not a network blip.
                listener.onClosed(refused = !opened && response != null && response.code in 400..499)
            }
        })
        return object : LiveSocket {
            override fun send(text: String) { ws.send(text) }
            override fun close() { ws.close(1000, "bye") }
        }
    }
}
