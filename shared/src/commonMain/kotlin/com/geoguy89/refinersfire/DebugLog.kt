package com.geoguy89.refinersfire

/** A short rolling log of what the app did recently (server calls, notices, games). Sent with bug reports. */
object DebugLog {
    private const val MAX_LINES = 300
    private val lines = ArrayDeque<String>()

    fun add(message: String) {
        val ms = epochMillis() % 86_400_000
        fun two(n: Long) = n.toString().padStart(2, '0')
        val stamp = "${two(ms / 3_600_000)}:${two(ms / 60_000 % 60)}:${two(ms / 1000 % 60)}"
        val line = "$stamp UTC  ${message.take(300)}"
        // Written from the UI thread (server replies are posted there).
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
    }

    fun dump(): String = lines.joinToString("\n")
}
