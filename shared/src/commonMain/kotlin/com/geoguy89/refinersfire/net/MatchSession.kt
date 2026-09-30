package com.geoguy89.refinersfire.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Glyph
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.game.ROWS
import com.geoguy89.refinersfire.game.StoneColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class MatchPhase { CONNECTING, WAITING, COUNTDOWN, PLAYING, OUT, ENDED, FAILED }

/** What we know about the opponent's game, as last reported. */
data class OpponentView(
    val score: Long = 0,
    val board: Int = 1,
    val cleared: Int = 0,
    val forge: Int = 0,
    val over: Boolean = false,
    val connected: Boolean = true,
    val cells: List<Int> = emptyList(),
    val gold: String = "",
)

@Serializable
data class PlayerRef(val playerId: String, val name: String)

@Serializable
private data class StateMsg(
    val t: String = "state",
    val score: Long,
    val board: Int,
    val forge: Int,
    val placed: Int,
    val over: Boolean,
    val cells: List<Int>,
    val gold: String,
    val cleared: Int,
)

@Serializable
private data class ServerMsg(
    val t: String,
    val seed: Long? = null,
    val difficulty: Difficulty? = null,
    val goalType: String? = null,
    val goalValue: Int? = null,
    val cleared: Int? = null,
    val startInMs: Long? = null,
    val remainingMs: Long? = null,
    val durationMs: Long? = null,
    val opponent: PlayerRef? = null,
    val score: Long? = null,
    val board: Int? = null,
    val forge: Int? = null,
    val over: Boolean? = null,
    val connected: Boolean? = null,
    val cells: List<Int>? = null,
    val gold: String? = null,
    val reason: String? = null,
    val winner: String? = null,
    val scores: JsonObject? = null,
    val opp: StateSnapshot? = null,
    val you: StateSnapshot? = null,
    val forfeitInMs: Long? = null,
    val n: Int? = null,
)

@Serializable
private data class StateSnapshot(
    val score: Long = 0,
    val board: Int = 1,
    val cleared: Int = 0,
    val forge: Int = 0,
    val over: Boolean = false,
    val connected: Boolean = true,
    val cells: List<Int> = emptyList(),
    val gold: String = "",
)

data class MatchResult(val reason: String, val won: Boolean?, val myScore: Long, val theirScore: Long)

/**
 * One live 1v1 match over the relay. Socket events arrive through [post] on the UI thread. The clock is kept
 * locally from the durations the server sends, in seconds of [now].
 */
class MatchSession(
    val matchId: String,
    private val myId: String,
    private val online: OnlineService,
    private val post: (() -> Unit) -> Unit,
    private val now: () -> Float,
    /** Seed and difficulty arrive with "start" (or "resume"); the view model builds the engine from them. */
    private val onStart: (seed: Long, difficulty: Difficulty, resume: Boolean) -> Unit,
    /** The opponent just cleared a board: their new board number and score. */
    private val onOpponentCleared: (board: Int, score: Long) -> Unit = { _, _ -> },
    /** Stoke Duel: the opponent cleared a line and stoked us [levels] times. */
    private val onStoked: (levels: Int) -> Unit = {},
) {
    var goal by mutableStateOf(MatchGoal())
        private set
    var phase by mutableStateOf(MatchPhase.CONNECTING)
        private set
    var opponent by mutableStateOf<PlayerRef?>(null)
        private set
    var opp by mutableStateOf(OpponentView())
        private set
    var result by mutableStateOf<MatchResult?>(null)
        private set
    var difficulty by mutableStateOf(Difficulty.EASY)
        private set
    /** Local clock times (seconds) when play starts and when the match ends. */
    var startsAt by mutableStateOf(0f)
        private set
    var endsAt by mutableStateOf(0f)
        private set
    var forfeitAt by mutableStateOf<Float?>(null)
        private set
    private var lastSent: StateMsg? = null
    private var started = false

    private var socket: LiveSocket? = null
    private var reconnectAt: Float? = null
    private var attempts = 0
    private var leaving = false
    /** True while the connection is down and being retried. */
    var reconnecting by mutableStateOf(false)
        private set

    init {
        open()
    }

    private fun open() {
        reconnectAt = null
        socket = online.openMatchSocket(matchId, object : SocketListener {
            override fun onOpen() = post { attempts = 0; reconnecting = false }
            override fun onMessage(text: String) = post { handle(text) }
            override fun onClosed(refused: Boolean) = post { dropped(refused) }
        })
    }

    private fun dropped(refused: Boolean) {
        if (phase == MatchPhase.ENDED || leaving) return
        // A refused handshake means the match is gone; otherwise keep trying for as long as the server would wait.
        if (refused && !started || attempts >= MAX_RETRIES) {
            phase = MatchPhase.FAILED
            reconnecting = false
            return
        }
        attempts++
        reconnecting = true
        reconnectAt = now() + RETRY_SECONDS
    }

    val secondsLeft: Int get() = ((endsAt - now()).coerceAtLeast(0f)).toInt()
    val countdown: Int get() = ((startsAt - now()).coerceAtLeast(0f) + 0.999f).toInt()

    /** Called every frame: moves from countdown to play when the clock says so. */
    fun tick() {
        if (phase == MatchPhase.COUNTDOWN && now() >= startsAt) phase = MatchPhase.PLAYING
        reconnectAt?.let { if (now() >= it) open() }
    }

    private fun handle(text: String) {
        val m = runCatching { wireJson.decodeFromString(ServerMsg.serializer(), text) }.getOrNull() ?: return
        when (m.t) {
            "waiting" -> {
                opponent = m.opponent
                if (phase == MatchPhase.CONNECTING) phase = MatchPhase.WAITING
            }
            "start", "resume" -> {
                opponent = m.opponent ?: opponent
                difficulty = m.difficulty ?: difficulty
                if (m.goalType != null) goal = MatchGoal(m.goalType, m.goalValue ?: goal.value)
                val startIn = (m.startInMs ?: 0) / 1000f
                val remaining = (m.remainingMs ?: m.durationMs ?: 0) / 1000f
                startsAt = now() + startIn
                endsAt = startsAt + remaining
                m.opp?.let { opp = it.toView() }
                forfeitAt = null
                if (!started) {
                    started = true
                    onStart(m.seed ?: 0, difficulty, m.t == "resume")
                }
                phase = if (m.you?.over == true) MatchPhase.OUT else if (startIn > 0) MatchPhase.COUNTDOWN else MatchPhase.PLAYING
                // A resumed connection may have missed our last move.
                lastSent?.let { socket?.send(wireJson.encodeToString(StateMsg.serializer(), it)) }
            }
            "opp" -> {
                val before = opp
                opp = OpponentView(
                    score = m.score ?: opp.score, board = m.board ?: opp.board, cleared = m.cleared ?: opp.cleared, forge = m.forge ?: opp.forge,
                    over = m.over ?: opp.over, connected = m.connected ?: true, cells = m.cells ?: opp.cells, gold = m.gold ?: opp.gold,
                )
                if (opp.cleared > before.cleared) onOpponentCleared(opp.board - 1, opp.score)
            }
            "stoked" -> onStoked((m.n ?: 1).coerceIn(1, 2))
            "opp_left" -> {
                opp = opp.copy(connected = false)
                forfeitAt = now() + (m.forfeitInMs ?: 45_000) / 1000f
            }
            "opp_back" -> {
                opp = opp.copy(connected = true)
                forfeitAt = null
            }
            "end" -> {
                val scores = m.scores
                fun scoreOf(id: String?) = id?.let { scores?.get(it)?.jsonPrimitive?.content?.toLongOrNull() } ?: 0L
                result = MatchResult(
                    reason = m.reason ?: "time",
                    won = m.winner?.let { it == myId },
                    myScore = scoreOf(myId),
                    theirScore = scoreOf(opponent?.playerId),
                )
                phase = MatchPhase.ENDED
                reconnecting = false
                socket?.close()
            }
        }
    }

    /** Report our board after every move. Unchanged states are not resent. */
    fun report(s: GameState) {
        if (!started) return
        val msg = StateMsg(
            score = s.score, board = s.board, forge = s.forge, placed = s.stonesPlaced, over = s.gameOver,
            cells = s.cells.map(::encodeCell), gold = s.gold.joinToString("") { if (it) "1" else "0" },
            cleared = s.boardsCleared,
        )
        if (msg == lastSent) return
        lastSent = msg
        socket?.send(wireJson.encodeToString(StateMsg.serializer(), msg))
        if (s.gameOver && phase == MatchPhase.PLAYING) phase = MatchPhase.OUT
    }

    /** Stoke Duel: tell the server we cleared a line worth [levels] stokes. */
    fun stoke(levels: Int) {
        if (!goal.kind.equals(MatchType.STOKE) || phase != MatchPhase.PLAYING) return
        socket?.send("""{"t":"stoke","n":${levels.coerceIn(1, 2)}}""")
    }

    fun leave() {
        leaving = true
        reconnectAt = null
        // Leaving a live match resigns it: the opponent wins straight away rather than after the reconnect window.
        if (phase != MatchPhase.ENDED) socket?.send("""{"t":"resign"}""")
        socket?.close()
        if (phase != MatchPhase.ENDED) phase = MatchPhase.FAILED
    }

    private fun StateSnapshot.toView() = OpponentView(score, board, cleared, forge, over, connected, cells, gold)

    companion object {
        private const val MAX_RETRIES = 12
        private const val RETRY_SECONDS = 3f
        const val EMPTY = -1
        const val CORNERSTONE = -2
        const val HAMMER = -3

        fun encodeCell(p: Piece?): Int = when (p) {
            null -> EMPTY
            Piece.Cornerstone -> CORNERSTONE
            Piece.Hammer -> HAMMER
            is Piece.Stone -> p.glyph.ordinal * 8 + p.color.ordinal
        }

        fun decodeCell(v: Int): Piece? = when {
            v == CORNERSTONE -> Piece.Cornerstone
            v == HAMMER -> Piece.Hammer
            v < 0 || v >= Glyph.entries.size * 8 -> null
            else -> Piece.Stone(Glyph.entries[v / 8], StoneColor.entries[(v % 8).coerceAtMost(StoneColor.entries.size - 1)])
        }

        const val CELLS = ROWS * COLS
    }
}
