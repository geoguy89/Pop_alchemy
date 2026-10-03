package com.geoguy89.refinersfire.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
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

/** A player in a gathering: whether they're connected yet. */
@Serializable
data class LobbyPlayer(val playerId: String, val name: String, val connected: Boolean = false)

/** One co-op move, as the server relays it to both players in order. */
@Serializable
data class CoopMove(val seq: Int, val kind: String, val index: Int = -1, val by: String, val auto: Boolean = false)

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
    val mode: GameMode? = null,
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
    // Gatherings
    val players: List<LobbyPlayer>? = null,
    val host: String? = null,
    val playerId: String? = null,
    val peers: Map<String, StateSnapshot>? = null,
    // Co-op
    val turn: String? = null,
    val turnEndsInMs: Long? = null,
    val seq: Int? = null,
    val kind: String? = null,
    val index: Int? = null,
    val by: String? = null,
    val auto: Boolean? = null,
    val moves: List<CoopMove>? = null,
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

data class MatchResult(
    val reason: String,
    val won: Boolean?,
    val myScore: Long,
    val theirScore: Long,
    /** Gatherings: everyone's final score, best first (names as the lobby knew them). */
    val standings: List<Pair<String, Long>> = emptyList(),
)

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
    private val onStart: (seed: Long, difficulty: Difficulty, mode: GameMode, resume: Boolean) -> Unit,
    /** The opponent just cleared a board: their new board number and score. */
    private val onOpponentCleared: (board: Int, score: Long) -> Unit = { _, _ -> },
    /** Stoke Duel: the opponent cleared a line and stoked us [levels] times. */
    private val onStoked: (levels: Int) -> Unit = {},
    /** Co-op: a move (by either player) to apply to the shared game, in order. */
    private val onCoopMove: (CoopMove) -> Unit = {},
    /** What kind of match we're joining, until the server says (a gathering shows its lobby straight away). */
    initialGoal: MatchGoal = MatchGoal(),
) {
    var goal by mutableStateOf(initialGoal)
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
    /** Strategic, Iron Forge or Foresight: both players play the same. */
    var mode by mutableStateOf(GameMode.STRATEGIC)
        private set
    val forgeCapacity: Int get() = if (mode == GameMode.IRON_FORGE) 1 else com.geoguy89.refinersfire.game.FORGE_CAPACITY
    /** Local clock times (seconds) when play starts and when the match ends. */
    var startsAt by mutableStateOf(0f)
        private set
    var endsAt by mutableStateOf(0f)
        private set
    var forfeitAt by mutableStateOf<Float?>(null)
        private set
    /** Gatherings: everyone invited (host first), who's here, and how the others are doing. */
    var roster by mutableStateOf<List<LobbyPlayer>>(emptyList())
        private set
    var host by mutableStateOf<String?>(null)
        private set
    var peers by mutableStateOf<Map<String, OpponentView>>(emptyMap())
        private set
    /** Co-op: whose turn it is and when it times out; moves applied so far (also the next move's number). */
    var turn by mutableStateOf<String?>(null)
        private set
    var turnEndsAt by mutableStateOf(0f)
        private set
    var coopApplied by mutableStateOf(0)
        private set
    /** Co-op: a move of ours is on its way to the server (don't send another until it's back). */
    var moveInFlight by mutableStateOf(false)
        private set
    val isCoop: Boolean get() = goal.coop
    val isGathering: Boolean get() = goal.gathering
    val isHost: Boolean get() = host == myId
    val isMyTurn: Boolean get() = isCoop && turn == myId
    val turnSecondsLeft: Int get() = ((turnEndsAt - now()).coerceAtLeast(0f) + 0.999f).toInt()
    val me: String get() = myId
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
            "lobby" -> {
                goal = MatchGoal("gathering", goal.value)
                roster = m.players ?: roster
                host = m.host ?: host
                if (phase == MatchPhase.CONNECTING) phase = MatchPhase.WAITING
            }
            "peer" -> {
                val id = m.playerId ?: return
                val old = peers[id] ?: OpponentView()
                peers = peers + (id to OpponentView(
                    score = m.score ?: old.score, board = m.board ?: old.board, cleared = m.cleared ?: old.cleared, forge = m.forge ?: old.forge,
                    over = m.over ?: old.over, connected = m.connected ?: old.connected,
                ))
            }
            "move" -> {
                val mv = CoopMove(m.seq ?: return, m.kind ?: return, m.index ?: -1, m.by ?: return, m.auto == true)
                turn = m.turn ?: turn
                turnEndsAt = now() + (m.turnEndsInMs ?: 0) / 1000f
                deliver(mv)
            }
            "start", "resume" -> {
                opponent = m.opponent ?: opponent
                difficulty = m.difficulty ?: difficulty
                mode = m.mode ?: mode
                if (m.goalType != null) goal = MatchGoal(m.goalType, m.goalValue ?: goal.value)
                val startIn = (m.startInMs ?: 0) / 1000f
                val remaining = (m.remainingMs ?: m.durationMs ?: 0) / 1000f
                startsAt = now() + startIn
                endsAt = startsAt + remaining
                m.opp?.let { opp = it.toView() }
                m.players?.let { list -> roster = list.map { p -> roster.firstOrNull { it.playerId == p.playerId }?.copy(name = p.name) ?: p } }
                m.peers?.let { map -> peers = map.mapValues { (_, v) -> v.toView() } }
                if (m.turn != null) {
                    turn = m.turn
                    turnEndsAt = now() + (m.turnEndsInMs ?: 0) / 1000f
                }
                forfeitAt = null
                if (!started) {
                    started = true
                    onStart(m.seed ?: 0, difficulty, mode, m.t == "resume")
                }
                // A reconnect brings every co-op move; apply the ones we missed.
                m.moves?.forEach { deliver(it) }
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
                val names = roster.associate { it.playerId to it.name } + listOfNotNull(opponent?.let { it.playerId to it.name })
                val standings = scores?.keys?.map { id -> (if (id == myId) "You" else names[id] ?: "Refiner") to scoreOf(id) }?.sortedByDescending { it.second } ?: emptyList()
                result = MatchResult(
                    reason = m.reason ?: "time",
                    won = m.winner?.let { it == myId },
                    myScore = scoreOf(myId),
                    theirScore = if (isGathering) standings.firstOrNull { it.first != "You" }?.second ?: 0 else scoreOf(opponent?.playerId),
                    standings = standings,
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

    /** Co-op: hand a move to the game in order, once each. */
    private fun deliver(mv: CoopMove) {
        if (mv.seq != coopApplied) return // Already applied (or out of order: the next resume brings the rest).
        coopApplied++
        if (mv.by == myId) moveInFlight = false
        onCoopMove(mv)
    }

    /** Co-op: ask to make a move on our turn. It's applied when the server relays it back (to both of us). */
    fun sendMove(kind: String, index: Int = -1) {
        if (!isMyTurn || moveInFlight || phase != MatchPhase.PLAYING) return
        moveInFlight = true
        socket?.send("""{"t":"move","seq":$coopApplied,"kind":"$kind","index":$index}""")
    }

    /** Gathering host: start now with whoever's here. */
    fun go() {
        if (isGathering && isHost && phase == MatchPhase.WAITING) socket?.send("""{"t":"go"}""")
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
