package com.geoguy89.refinersfire.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.refinersfire.audio.AudioPlayer
import com.geoguy89.refinersfire.epochMillis
import com.geoguy89.refinersfire.audio.Sfx
import com.geoguy89.refinersfire.data.HighScore
import com.geoguy89.refinersfire.data.Settings
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Achievement
import com.geoguy89.refinersfire.game.Achievements
import com.geoguy89.refinersfire.game.GameEngine
import com.geoguy89.refinersfire.game.LifetimeStats
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.FORGE_CAPACITY
import com.geoguy89.refinersfire.game.GameEvent
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.Piece
import com.geoguy89.refinersfire.gfx.BoardFx
import com.geoguy89.refinersfire.gfx.Palette
import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.game.COLS
import com.geoguy89.refinersfire.game.ROWS
import com.geoguy89.refinersfire.net.CHAT_MAX_CHARS
import com.geoguy89.refinersfire.net.ChatCrypto
import com.geoguy89.refinersfire.net.ChatLine
import com.geoguy89.refinersfire.net.AsyncChallenge
import com.geoguy89.refinersfire.net.AsyncSubmitBody
import com.geoguy89.refinersfire.net.Friend
import com.geoguy89.refinersfire.net.LeaderboardEntry
import com.geoguy89.refinersfire.net.Rival
import com.geoguy89.refinersfire.net.NoChatCrypto
import com.geoguy89.refinersfire.net.Sealed
import com.geoguy89.refinersfire.net.AppUpdater
import com.geoguy89.refinersfire.net.NoUpdater
import com.geoguy89.refinersfire.net.UpdateInfo
import com.geoguy89.refinersfire.net.HttpTransport
import com.geoguy89.refinersfire.net.LanProtocol
import com.geoguy89.refinersfire.net.LanTransport
import com.geoguy89.refinersfire.net.MatchGoal
import com.geoguy89.refinersfire.net.MatchPhase
import com.geoguy89.refinersfire.net.MatchSession
import com.geoguy89.refinersfire.net.NoHttp
import com.geoguy89.refinersfire.net.NoLanTransport
import com.geoguy89.refinersfire.net.OnlineService
import com.geoguy89.refinersfire.net.NoPush
import com.geoguy89.refinersfire.net.PushRegistrar
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

enum class Screen { TITLE, GAME }

enum class Haptic { TICK, CONFIRM, REJECT, HEAVY }

sealed interface Overlay {
    data object NewGame : Overlay
    data object Options : Overlay
    data object HighScores : Overlay
    data object HowToPlay : Overlay
    data object Pause : Overlay
    data object ConfirmQuit : Overlay
    data class BoardComplete(val event: GameEvent.BoardCleared) : Overlay
    data class GameOver(val final: GameState, val qualifies: Boolean) : Overlay
    data object Friends : Overlay
    data class Challenge(val rival: Rival) : Overlay
    data class PlayerCard(val entry: LeaderboardEntry) : Overlay
    data object MatchLobby : Overlay
    data object ConfirmLeaveMatch : Overlay
    data object MatchOver : Overlay
    data class Chat(val friendId: String) : Overlay
    data object Achievements : Overlay
    data object ChangeName : Overlay
    data object Update : Overlay
    data object Peek : Overlay
    data class AsyncSetup(val rival: Rival) : Overlay
    /** A challenge run just ended: [sent] for the challenger, otherwise the outcome for the friend. */
    data class AsyncDone(val rival: String, val rivalId: String?, val score: Long, val theirScore: Long?, val sent: Boolean, val won: Boolean?) : Overlay
}

/** Local = nearby only, own scores only. Plus = Local+: pass on open scores and list yours on the global board. */
enum class ShareMode(val label: String) { OFF("Off"), LOCAL("Local"), PLUS("Global") }

enum class HallTab(val label: String) { GLOBAL("Global"), FRIENDS("Friends"), NEARBY("Nearby"), MINE("Mine") }

/** One Hall of Fame line: the score, where it came from, and who to befriend (if anyone). */
data class HallRow(val score: HighScore, val tag: String?, val playerId: String?, val isMe: Boolean)

/** All game-session state and actions. Platform code owns its lifetime and calls [dispose] at the end. */
class GameViewModel(
    private val store: Store,
    val audio: AudioPlayer,
    private val lan: LanTransport = NoLanTransport,
    http: HttpTransport = NoHttp,
    private val crypto: ChatCrypto = NoChatCrypto,
    val updater: AppUpdater = NoUpdater,
    val push: PushRegistrar = NoPush,
) {
    val fx = BoardFx()

    var settings by mutableStateOf(store.loadSettings())
        private set
    var screen by mutableStateOf(Screen.TITLE)
        private set
    /** A stack, so Options can open on top of Pause and close back to it. */
    var overlays by mutableStateOf(listOf<Overlay>())
        private set
    val overlay: Overlay? get() = overlays.lastOrNull()

    private var engine: GameEngine? = null
    var state by mutableStateOf<GameState?>(null)
        private set
    var savedGame by mutableStateOf(store.loadGame())
        private set
    /** An unfinished async challenge run, resumable from the title screen. */
    var savedChallenge by mutableStateOf(store.loadChallengeGame())
        private set
    private var announced = store.loadAnnounced()
    var highScores by mutableStateOf(store.loadHighScores())
        private set
    /** Scores received from other devices on the local network. */
    var peers by mutableStateOf(store.loadPeers())
        private set

    private val deviceId = store.deviceId()
    /** Datagrams arrive on a network thread; they are applied on the next frame. */
    private val inbox = Channel<ByteArray>(64, BufferOverflow.DROP_OLDEST)
    private var lanActive = false
    private var lastBroadcast = -100f

    /** Network callbacks (HTTP, match socket) queue work here; it runs on the UI thread at the next frame. */
    private val tasks = Channel<() -> Unit>(Channel.UNLIMITED)
    private val post: (() -> Unit) -> Unit = { tasks.trySend(it) }

    val online = OnlineService(http, post, store.loadAccount(), store::saveAccount).also { svc ->
        svc.friends = store.loadFriends()
    }
    private var foreground = false
    private var lastSync = -100f
    private var scoresPushed = false

    /** The 1v1 match in progress, if any. */
    var match by mutableStateOf<MatchSession?>(null)
        private set
    private val joinedMatches = HashSet<String>()
    /** Open conversation (loaded from storage when a chat is opened) and unread counts per friend. */
    var chatLines by mutableStateOf<List<ChatLine>>(emptyList())
        private set
    var unread by mutableStateOf(store.loadUnread())
        private set
    private var keyPublished = false
    private var pushRegistered = false
    private var trayAnnounced = setOf<String>()

    /** False while the desktop window is in the background; notices then go to the system tray. */
    var windowFocused = true
    /** Notices for the desktop tray (title, text) when the window isn't focused. */
    val systemNotices = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    // Achievements: counters, what's unlocked, and a queue of unlocks waiting for a quiet moment to be mentioned.
    var stats by mutableStateOf(store.loadStats())
        private set
    var unlocked by mutableStateOf(store.loadAchievements())
        private set
    var unseenAchievements by mutableStateOf(store.loadUnseenAchievements())
        private set
    private var pendingAnnounce = listOf<Achievement>()
    /** The one-line "achievement unlocked" note currently showing, and when it goes away. */
    var achievementNote by mutableStateOf<String?>(null)
        private set
    private var achievementNoteUntil = 0f
    private var unsavedPlayMs = 0L
    private var boardStartMs = 0L
    private var boardStartDiscards = 0
    private var boardStartScore = 0L

    /** A newer release, once found. Offered on the title screen only. */
    var update by mutableStateOf<UpdateInfo?>(null)
        private set
    /** Download progress 0..1 while an update is downloading, else null. */
    var updateProgress by mutableStateOf<Float?>(null)
        private set
    private var updateChecked = false
    private var updateOffered = false

    /** A short message for the player ("Request sent", errors). Cleared by [dismissNotice]. */
    var notice by mutableStateOf<String?>(null)
        private set

    /** Hovered cell while a finger is on the board, or -1. */
    var hoverIndex by mutableIntStateOf(-1)
    /** Effect clock times for the hand slot and forge. */
    var pieceArrivedAt by mutableFloatStateOf(-10f)
        private set
    var forgeFlareAt by mutableFloatStateOf(-10f)
        private set
    var forgeCoolAt by mutableFloatStateOf(-10f)
        private set
    /** Squares revealed by the hint for the current piece. */
    var hintCells by mutableStateOf<Set<Int>>(emptySet())
        private set
    /** The hint was used on this piece (so it is not charged twice). */
    var hintShown by mutableStateOf(false)
        private set
    /** A hint found no legal square for this piece: the discard button pulses. */
    var noMoves by mutableStateOf(false)
        private set

    private val _haptics = MutableSharedFlow<Haptic>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val haptics: SharedFlow<Haptic> = _haptics

    private var lastFrameNanos = 0L
    private var clockStartNanos = 0L
    private var lastTickSecond = -1L

    init {
        audio.sfxVolume = settings.sfxVolume
        audio.musicVolume = settings.musicVolume
        Palette.theme = settings.theme
        Palette.pieceSet = settings.pieceSet
        audio.setTheme(settings.theme)
        online.onAccountRejected = {
            store.clearAccount()
            store.saveFriends(emptyList())
            online.friends = emptyList()
            keyPublished = false
            // Make a fresh account straight away and put our scores back up.
            online.ensureAccount(settings.playerName) { online.pushScores(highScores, settings.sharePlus); online.sync() }
        }
        online.onSynced = { r ->
            store.saveFriends(r.friends)
            // Keep the server in step with the privacy switch (it may have been changed while offline).
            if (r.me.hideActivity != settings.hideActivity) online.setHideActivity(settings.hideActivity)
            if (r.friends.size > stats.friends) bump { it.copy(friends = r.friends.size) }
            publishChatKey(r.me.publicKey)
            registerPush()
            // Desktop: new challenges and friend requests while the window is in the background.
            if (!windowFocused) {
                r.invites.filter { it.incoming && it.status == "pending" && it.id !in trayAnnounced }.forEach {
                    systemNotices.tryEmit("Refiner's Fire" to "${it.name} challenges you to a 1v1")
                }
                r.incoming.filter { it.id !in trayAnnounced }.forEach { systemNotices.tryEmit("Refiner's Fire" to "${it.name} wants to connect") }
            }
            trayAnnounced = trayAnnounced + r.invites.map { it.id } + r.incoming.map { it.id }
            receiveChat(r.messages)
            announceChallenges(r.asyncChallenges)
            retryPendingResults()
            if (!scoresPushed) {
                scoresPushed = true
                online.pushScores(highScores, settings.sharePlus)
            }
            // Our challenge was accepted: join the match.
            r.invites.firstOrNull { !it.incoming && it.status == "accepted" && it.matchId != null && it.ageMs < 5 * 60_000 && it.matchId !in joinedMatches }
                ?.let { joinMatch(it.matchId!!) }
        }
    }

    /** The board shown on screen: while the "board complete" panel is up we keep showing the finished gold board. */
    val displayState: GameState?
        get() = (overlay as? Overlay.BoardComplete)?.event?.stats ?: state

    val boardInteractive: Boolean
        get() = screen == Screen.GAME && overlay == null && state?.gameOver == false &&
            (match == null || match?.phase == MatchPhase.PLAYING)

    // ---- Navigation ---------------------------------------------------------------------------------------------

    fun push(o: Overlay) { click(); overlays = overlays + o }

    fun pop() {
        click()
        overlays = overlays.dropLast(1)
    }

    fun onBack(): Boolean = when {
        overlay is Overlay.MatchOver -> { leaveMatch(); true }
        overlay is Overlay.MatchLobby -> { leaveMatch(); true }
        overlay == null && match != null -> { push(Overlay.ConfirmLeaveMatch); true }
        overlay is Overlay.BoardComplete -> { continueAfterBoard(); true }
        overlay is Overlay.GameOver -> { finishGameOver(false); true }
        overlay != null -> { pop(); true }
        screen == Screen.GAME -> { push(Overlay.Pause); true }
        else -> false
    }

    fun startNewGame(difficulty: Difficulty, mode: GameMode) {
        click()
        updateSettings(settings.copy(difficulty = difficulty, mode = mode))
        val e = GameEngine.newGame(difficulty, mode)
        adopt(e)
        countGameStart()
        overlays = emptyList()
        screen = Screen.GAME
        persist()
    }

    fun resumeSavedGame() {
        val saved = savedGame ?: return
        click()
        adopt(GameEngine(saved))
        overlays = emptyList()
        screen = Screen.GAME
    }

    /** Jump straight into a prepared position (used by screenshot tests). */
    fun loadForPreview(s: GameState, overlay: Overlay? = null) {
        adopt(GameEngine(s))
        overlays = listOfNotNull(overlay)
        screen = Screen.GAME
    }

    /** A finished (or failed) live match must not keep the board locked once another game starts. */
    private fun dropFinishedMatch() {
        val m = match ?: return
        if (m.phase == MatchPhase.ENDED || m.phase == MatchPhase.FAILED) {
            m.leave()
            match = null
        }
    }

    private fun adopt(e: GameEngine) {
        if (e.state.matchSeed == null || e.state.challengeId != null) dropFinishedMatch()
        engine = e
        state = e.state
        fx.reset()
        pieceArrivedAt = fx.now
        markBoardStart(e.state)
        hintShown = false
        hintCells = emptySet()
        noMoves = false
    }

    fun quitToTitle() {
        click()
        toTitle()
    }

    private fun toTitle() {
        persist()
        overlays = emptyList()
        screen = Screen.TITLE
        engine = null
        state = null
        savedGame = store.loadGame()
        savedChallenge = store.loadChallengeGame()
    }

    // ---- Play ---------------------------------------------------------------------------------------------------

    fun tapCell(index: Int) {
        val e = engine ?: return
        if (!boardInteractive) return
        val events = e.play(index)
        if (events.isEmpty()) {
            fx.invalid(index)
            audio.play(Sfx.INVALID)
            _haptics.tryEmit(Haptic.REJECT)
            return
        }
        handle(events, origin = index)
    }

    fun discard() {
        val e = engine ?: return
        if (!boardInteractive) return
        handle(e.discard(), origin = -1)
    }

    /** The hint is useless on an empty board (every square is legal) and is charged once per piece. */
    val hintAvailable: Boolean
        get() = boardInteractive && !hintShown && state?.boardEmpty == false

    val hintCost: Long get() = engine?.hintCost() ?: 0L

    fun useHint() {
        val e = engine ?: return
        if (!hintAvailable) return
        val cost = minOf(e.hintCost(), e.state.score)
        val cells = e.useHint()
        bump { it.copy(hintsUsed = it.hintsUsed + 1) }
        hintShown = true
        hintCells = cells.toSet()
        noMoves = cells.isEmpty()
        audio.play(Sfx.HINT)
        _haptics.tryEmit(Haptic.TICK)
        val msg = if (cells.isEmpty()) "No home for this stone" else "Hint"
        fx.banner(if (cost > 0) "$msg  -$cost" else msg, if (cells.isEmpty()) Palette.ember else Palette.hint, height = 0.42f, y = ROWS - 0.8f)
        state = e.state
        persist()
    }

    private fun handle(events: List<GameEvent>, origin: Int) {
        val e = engine ?: return
        val before = state
        track(events, before, e.state)
        for (ev in events) when (ev) {
            is GameEvent.Placed -> {
                fx.placed(ev.index, ev.piece, ev.points)
                audio.play(if (ev.piece is Piece.Cornerstone) Sfx.CORNERSTONE_PLACE else Sfx.place(ev.neighbors))
                _haptics.tryEmit(Haptic.TICK)
            }
            is GameEvent.HammerUsed -> {
                fx.hammer(ev.index, ev.removed)
                audio.play(Sfx.HAMMER_USE)
                _haptics.tryEmit(Haptic.HEAVY)
            }
            is GameEvent.LinesCleared -> {
                val lines = ev.rows.size + ev.cols.size
                // Stoke Duel: each line stokes the rival once; a symbol or perfect line stokes twice.
                match?.stoke(if (ev.bonuses.isNotEmpty()) 2 else 1)
                fx.linesCleared(origin, ev.removed, ev.newlyGold, ev.points, lines)
                audio.play(if (lines > 1) Sfx.MULTI_LINE else Sfx.LINE_CLEAR)
                _haptics.tryEmit(Haptic.CONFIRM)
                ev.bonuses.forEachIndexed { k, b ->
                    val sample = ev.removed.entries.firstOrNull { (idx, p) ->
                        p is Piece.Stone && (b.row == idx / COLS || b.col == idx % COLS)
                    }?.value as? Piece.Stone
                    val color = sample?.let { Palette.pieceColor(it.color) } ?: Palette.goldLight
                    fx.lineBonus(b.row, b.col, b.perfect, color, b.points, bannerY = 2.2f + k * 2f)
                    audio.play(if (b.perfect) Sfx.PERFECT_LINE else Sfx.SYMBOL_LINE)
                    _haptics.tryEmit(Haptic.HEAVY)
                }
            }
            GameEvent.ForgeEmptied -> {
                forgeCoolAt = fx.now
                audio.play(Sfx.FORGE_COOL, 0.8f)
            }
            is GameEvent.BoardCleared -> {
                fx.boardComplete()
                audio.play(Sfx.BOARD_CLEAR)
                _haptics.tryEmit(Haptic.HEAVY)
                // A challenge run ends when its boards are cleared.
                if (ev.stats.challengeId != null && ev.stats.boardsCleared >= ev.stats.challengeBoards) finishChallengeRun(ev.stats)
                else overlays = overlays + Overlay.BoardComplete(ev)
            }
            is GameEvent.Discarded -> {
                forgeFlareAt = fx.now
                audio.play(Sfx.DISCARD)
                _haptics.tryEmit(Haptic.HEAVY)
                if (ev.forgeLevel == FORGE_CAPACITY) audio.play(Sfx.FORGE_WARNING, 0.9f)
                when {
                    ev.timedOut -> fx.banner("Time's up!", Palette.ember, height = 0.42f, y = ROWS - 0.8f)
                    ev.penalty > 0 -> fx.banner("Melted  -${ev.penalty}", Palette.invalid, height = 0.42f, y = ROWS - 0.8f)
                }
            }
            is GameEvent.StreakMilestone -> {
                fx.banner("Streak ${ev.streak}!", Palette.goldLight, height = 0.5f, y = 1.2f)
                audio.play(Sfx.STREAK, 0.8f)
            }
            is GameEvent.NewPiece -> {
                pieceArrivedAt = fx.now
                hintShown = false
                hintCells = emptySet()
                noMoves = false
                lastTickSecond = -1
                when (ev.piece) {
                    Piece.Hammer -> audio.play(Sfx.HAMMER_APPEAR, 0.8f)
                    Piece.Cornerstone -> if (e.state.cells.any { it != null }) audio.play(Sfx.CORNERSTONE_APPEAR, 0.8f)
                    else -> Unit
                }
            }
            GameEvent.GameOver -> {
                audio.play(Sfx.GAME_OVER)
                // In a match you're out but the match goes on; the result comes from the server.
                if (e.state.challengeId != null) finishChallengeRun(e.state)
                else if (match == null) overlays = overlays + Overlay.GameOver(e.state, store.qualifies(e.state.score))
            }
        }
        state = e.state
        match?.report(e.state)
        persist()
    }

    fun continueAfterBoard() {
        val o = overlay as? Overlay.BoardComplete ?: return
        click()
        val leftovers = o.event.stats.cells.withIndex().filter { it.value != null }.associate { it.index to it.value!! }
        fx.boardReset(leftovers)
        overlays = overlays.dropLast(1)
        pieceArrivedAt = fx.now
    }

    /** Record the score under the player's name (when [inscribe] and it qualifies) and go back to the title. */
    fun finishGameOver(inscribe: Boolean) {
        val o = overlay as? Overlay.GameOver ?: return
        val name = if (inscribe) settings.playerName else null
        if (name != null && o.qualifies) {
            val clean = name
            store.addHighScore(
                HighScore(clean, o.final.score, o.final.rank, o.final.board, o.final.difficulty, o.final.mode, epochMillis()),
            )
            highScores = store.loadHighScores()
            lastBroadcast = -100f
            online.pushScores(highScores, settings.sharePlus)
        }
        store.saveGame(null)
        savedGame = null
        engine = null
        state = null
        overlays = if (name != null && o.qualifies) listOf(Overlay.HighScores) else emptyList()
        screen = Screen.TITLE
    }

    /** Called every animation frame by the UI. */
    fun onFrame(frameNanos: Long) {
        if (clockStartNanos == 0L) clockStartNanos = frameNanos
        val dtMs = if (lastFrameNanos == 0L) 0L else ((frameNanos - lastFrameNanos) / 1_000_000L).coerceIn(0L, 100L)
        lastFrameNanos = frameNanos
        fx.advance((frameNanos - clockStartNanos) / 1e9f)
        while (true) (tasks.tryReceive().getOrNull() ?: break).invoke()
        pumpLan()
        pumpOnline()
        pumpAchievementNote()
        val e = engine ?: return
        if (!boardInteractive) return
        countPlayTime(dtMs)
        val events = e.tick(dtMs)
        if (events.isNotEmpty()) {
            handle(events, -1)
        } else if (e.state.mode == GameMode.TIME_TRIAL || e.state.elapsedMillis / 1000 != (state?.elapsedMillis ?: 0) / 1000) {
            // Publish at most once a second in Strategic mode; Time Trial needs every frame for its hourglass.
            state = e.state
            if (e.state.mode == GameMode.TIME_TRIAL) {
                val sec = e.state.pieceTimeLeftMillis / 1000
                if (e.state.pieceTimeLeftMillis < 4000 && sec != lastTickSecond) {
                    lastTickSecond = sec
                    audio.play(Sfx.TICK)
                }
            }
        }
    }

    // ---- Local network score sharing ----------------------------------------------------------------------------

    private fun startLan() {
        if (lanActive) return
        lanActive = true
        lastBroadcast = -100f
        lan.start { inbox.trySend(it) }
    }

    private fun stopLan() {
        if (!lanActive) return
        lanActive = false
        lan.stop()
    }

    /** Apply received packets and re-announce our scores every few seconds. Runs on the UI thread. */
    private fun pumpLan() {
        if (!lanActive) return
        val plus = settings.sharePlus
        while (true) {
            val bytes = inbox.tryReceive().getOrNull() ?: break
            val update = LanProtocol.decode(bytes, deviceId) ?: continue
            val now = epochMillis()
            peers = store.mergePeer(update.deviceId, update.scores, now, update.playerId, direct = true, open = update.open)
            val met = peers.count { it.direct }
            if (met > stats.nearbyDevices) bump { it.copy(nearbyDevices = met) }
            // Scores a Local+ neighbour passed on are only taken in Local+ mode, and only ones their owner opened.
            if (plus) for (f in update.forwarded) {
                if (f.deviceId == deviceId || f.scores.isEmpty()) continue
                peers = store.mergePeer(f.deviceId, f.scores, now, f.playerId, direct = false, open = true)
            }
        }
        if (fx.now - lastBroadcast >= BROADCAST_SECONDS) {
            lastBroadcast = fx.now
            // Local: only this device's own scores. Local+: also any open scores we know of.
            lan.send(LanProtocol.encode(deviceId, store.loadHighScores(), online.account?.playerId, plus, if (plus) peers else emptyList()))
        }
    }

    // ---- Online: friends, global board, matches -------------------------------------------------------------------

    val shareMode: ShareMode
        get() = when {
            !settings.lanShare -> ShareMode.OFF
            settings.sharePlus -> ShareMode.PLUS
            else -> ShareMode.LOCAL
        }

    fun setShareMode(mode: ShareMode) {
        click()
        updateSettings(settings.copy(lanShare = mode != ShareMode.OFF, sharePlus = mode == ShareMode.PLUS))
        if (mode == ShareMode.PLUS) {
            bump { it.copy(sharedGlobally = true) }
            online.ensureAccount(settings.playerName) { online.pushScores(highScores, true); online.sync() }
        } else if (online.account != null) {
            online.pushScores(highScores, false)
        }
    }

    private fun pumpOnline() {
        offerUpdate()
        match?.let { m ->
            m.tick()
            if (m.phase == MatchPhase.ENDED && overlay != Overlay.MatchOver) {
                val r = m.result
                bump {
                    val won = r?.won == true
                    val streak = if (won) it.matchWinStreak + 1 else 0
                    it.copy(
                        matchesPlayed = it.matchesPlayed + 1,
                        matchesWon = it.matchesWon + if (won) 1 else 0,
                        matchWinStreak = streak,
                        bestMatchWinStreak = maxOf(it.bestMatchWinStreak, streak),
                        raceWins = it.raceWins + if (won && m.goal.race) 1 else 0,
                        timedWins = it.timedWins + if (won && m.goal.timed) 1 else 0,
                    )
                }
                audio.play(if (r?.won == true) Sfx.BOARD_CLEAR else Sfx.GAME_OVER)
                overlays = listOf(Overlay.MatchOver)
            }
            if (m.phase == MatchPhase.FAILED && overlay != Overlay.MatchOver) {
                notice = if (engine == null) "Couldn't join the match." else "Lost the connection to the match."
                leaveMatch()
            }
        }
        if (!foreground || online.account == null) return
        val waiting = online.invites.any { !it.incoming && it.status == "pending" } || overlay == Overlay.Friends || overlay is Overlay.Chat
        val interval = if (waiting) 3f else if (screen == Screen.GAME) 15f else 8f
        if (fx.now - lastSync >= interval) {
            lastSync = fx.now
            online.sync()
        }
    }

    fun openHallOfFame() {
        push(Overlay.HighScores)
        online.fetchLeaderboard()
    }

    /** Hand the server this device's notification token once per session (or clear it when switched off). */
    private fun registerPush() {
        if (pushRegistered || !push.supported || online.account == null) return
        pushRegistered = true
        if (!settings.notifications) { online.setPushToken(""); return }
        push.register { token -> post { if (settings.notifications) online.setPushToken(token) } }
    }

    fun setNotifications(on: Boolean) {
        click()
        updateSettings(settings.copy(notifications = on))
        pushRegistered = false
        registerPush()
    }

    fun setHideActivity(hide: Boolean) {
        click()
        updateSettings(settings.copy(hideActivity = hide))
        online.setHideActivity(hide)
    }

    /** A leaderboard player as someone to challenge. */
    fun rivalOf(e: LeaderboardEntry) = Rival(e.playerId, e.name, e.online, isFriend(e.playerId))

    /** Strangers can only be challenged when you're in Global mode too (the server checks it). */
    fun canChallenge(e: LeaderboardEntry): Boolean = e.playerId != online.account?.playerId && (isFriend(e.playerId) || settings.sharePlus)

    fun openFriends() {
        push(Overlay.Friends)
        online.ensureAccount(settings.playerName) { online.sync() }
        lastSync = fx.now
    }

    fun addFriendByCode(code: String) {
        online.ensureAccount(settings.playerName) { online.addFriendByCode(code) { notice = it } }
    }

    fun addFriend(playerId: String) {
        click()
        online.ensureAccount(settings.playerName) { online.addFriend(playerId) { notice = it } }
    }

    fun respondToRequest(id: String, accept: Boolean) {
        click()
        online.respondToRequest(id, accept)
    }

    fun removeFriend(friend: Friend) {
        click()
        online.removeFriend(friend.playerId)
    }

    fun challenge(rival: Rival, difficulty: Difficulty, goal: MatchGoal) {
        click()
        online.invite(rival.playerId, difficulty, goal) { notice = it }
        overlays = overlays.filter { it !is Overlay.Challenge }
        lastSync = -100f
    }

    fun cancelChallenge(id: String) {
        click()
        online.cancelInvite(id)
    }

    fun respondToChallenge(id: String, accept: Boolean) {
        click()
        online.respondToInvite(id, accept, onMatch = ::joinMatch) { notice = it }
    }

    fun dismissNotice() { notice = null }

    // ---- Async challenges ---------------------------------------------------------------------------------------------

    /** Challenges waiting for our run. */
    val ourMoves: List<AsyncChallenge> get() = online.asyncChallenges.filter { it.ourMove }

    fun startChallenge(friend: Rival, difficulty: Difficulty, boards: Int) {
        click()
        dropFinishedMatch()
        if (match != null) { notice = "Finish your live match first."; return }
        online.createAsync(friend.playerId, difficulty, boards, onError = { notice = it }) { c ->
            beginChallengeRun(c.id, c.seed, c.difficulty, c.boards, friend.name)
        }
    }

    fun playChallenge(c: AsyncChallenge) {
        click()
        dropFinishedMatch()
        if (match != null) { notice = "Finish your live match first."; return }
        // Resuming our own unfinished run keeps its progress.
        val saved = store.loadChallengeGame()
        if (saved?.challengeId == c.id) {
            resumeChallenge()
            return
        }
        beginChallengeRun(c.id, c.seed, c.difficulty, c.boards, c.name)
    }

    fun declineChallenge(c: AsyncChallenge) {
        click()
        online.declineAsync(c.id)
    }

    private fun beginChallengeRun(id: String, seed: Long, difficulty: Difficulty, boards: Int, rival: String) {
        persist() // Keep the single-player game safe.
        val e = GameEngine.newMatch(difficulty, seed)
        adopt(GameEngine(e.state.copy(challengeId = id, challengeBoards = boards, challengeRival = rival)))
        overlays = emptyList()
        screen = Screen.GAME
        countGameStart()
        persist()
    }

    fun resumeChallenge() {
        val saved = store.loadChallengeGame() ?: return
        click()
        adopt(GameEngine(saved))
        overlays = emptyList()
        screen = Screen.GAME
    }

    /** The run is over (boards cleared or forge overflowed): send the result and show where things stand. */
    private fun finishChallengeRun(s: GameState) {
        val id = s.challengeId ?: return
        val result = AsyncSubmitBody(id, s.score, s.boardsCleared.coerceAtMost(s.challengeBoards), s.elapsedMillis)
        store.saveChallengeGame(null)
        savedChallenge = null
        store.savePendingResults(store.loadPendingResults().filter { it.id != id } + result)
        val c = online.asyncChallenges.firstOrNull { it.id == id }
        val rival = s.challengeRival ?: c?.name ?: "your friend"
        val target = c?.theirScore
        val won = if (c?.incoming == true && target != null) s.score > target else null
        bump { it.copy(asyncPlayed = it.asyncPlayed + 1, asyncWon = it.asyncWon + if (won == true) 1 else 0) }
        overlays = listOf(Overlay.AsyncDone(rival, c?.playerId, s.score, target, sent = c?.incoming != true, won = won))
        retryPendingResults()
    }

    private fun retryPendingResults() {
        for (r in store.loadPendingResults()) online.submitAsync(r) { delivered ->
            if (delivered) store.savePendingResults(store.loadPendingResults().filter { it.id != r.id })
        }
    }

    /** Leave the finished run for the title screen. */
    fun closeChallengeResult() {
        click()
        engine = null
        state = null
        overlays = emptyList()
        screen = Screen.TITLE
        savedGame = store.loadGame()
        lastSync = -100f
    }

    /** One quiet mention per new challenge and per result, not a popup each sync. */
    private fun announceChallenges(list: List<AsyncChallenge>) {
        val fresh = mutableListOf<String>()
        for (c in list) {
            val key = "${c.id}:${c.status}"
            if (key in announced) continue
            when {
                c.ourMove -> fresh += "${c.name} challenged you: beat ${c.theirScore ?: 0} (${c.difficulty.displayName}, ${c.boards} ${if (c.boards == 1) "board" else "boards"})"
                !c.incoming && c.status == "done" -> fresh += when (c.winner) {
                    null -> "${c.name} tied your challenge at ${c.myScore}"
                    // Equal scores are decided on boards cleared, then time.
                    c.playerId -> if (c.myScore == c.theirScore) "${c.name} edged you out on time (${c.theirScore} vs ${c.myScore})" else "${c.name} beat your ${c.myScore} with ${c.theirScore}"
                    else -> if (c.myScore == c.theirScore) "You edged out ${c.name} on time (${c.myScore} vs ${c.theirScore})" else "You beat ${c.name}: ${c.myScore} to ${c.theirScore}"
                }
                !c.incoming && c.status == "declined" -> fresh += "${c.name} declined your challenge"
                else -> {}
            }
            announced = announced + key
        }
        store.saveAnnounced(announced)
        if (fresh.isNotEmpty()) notice = if (fresh.size == 1) fresh[0] else "${fresh.size} challenge updates. See Friends & 1v1."
        if (fresh.isNotEmpty() && !windowFocused) systemNotices.tryEmit("Refiner's Fire" to (notice ?: fresh[0]))
    }

    // ---- Achievements -----------------------------------------------------------------------------------------------

    /** Update the lifetime counters and record anything that unlocks. Unlocks are announced later, never mid-play. */
    private fun bump(change: (LifetimeStats) -> LifetimeStats) {
        val next = change(stats)
        if (next == stats) return
        stats = next
        store.saveStats(next)
        val fresh = Achievements.newlyUnlocked(next, unlocked.keys)
        if (fresh.isEmpty()) return
        val now = epochMillis()
        unlocked = unlocked + fresh.associate { it.id to now }
        store.saveAchievements(unlocked)
        unseenAchievements = unseenAchievements + fresh.map { it.id }
        store.saveUnseenAchievements(unseenAchievements)
        pendingAnnounce = pendingAnnounce + fresh
    }

    private fun countGameStart() = bump {
        it.copy(gamesStarted = it.gamesStarted + 1, themesPlayed = it.themesPlayed + settings.theme.name)
    }

    private fun markBoardStart(s: GameState) {
        boardStartMs = s.elapsedMillis
        boardStartDiscards = s.discards
        boardStartScore = s.score
    }

    private fun countPlayTime(dtMs: Long) {
        unsavedPlayMs += dtMs
        if (unsavedPlayMs >= 15_000) {
            val add = unsavedPlayMs
            unsavedPlayMs = 0
            bump { it.copy(playMillis = it.playMillis + add) }
        }
    }

    private fun track(events: List<GameEvent>, before: GameState?, after: GameState) {
        val mult = after.multiplier.coerceAtLeast(1)
        bump { st ->
            var s = st
            for (ev in events) s = when (ev) {
                is GameEvent.Placed -> s.copy(
                    stonesPlaced = s.stonesPlaced + 1,
                    cornerstonesPlaced = s.cornerstonesPlaced + if (ev.piece is Piece.Cornerstone) 1 else 0,
                    fiftyPointPlacements = s.fiftyPointPlacements + if (ev.points / mult == 50L) 1 else 0,
                )
                is GameEvent.HammerUsed -> s.copy(hammersUsed = s.hammersUsed + 1)
                is GameEvent.LinesCleared -> {
                    val perfect = ev.bonuses.count { it.perfect }
                    s.copy(
                        linesCleared = s.linesCleared + ev.rows.size + ev.cols.size,
                        doubleClears = s.doubleClears + if (ev.rows.isNotEmpty() && ev.cols.isNotEmpty()) 1 else 0,
                        symbolLines = s.symbolLines + ev.bonuses.count { !it.perfect },
                        perfectLines = s.perfectLines + perfect,
                        perfectLineInMatch = s.perfectLineInMatch || (perfect > 0 && after.matchSeed != null),
                        forgeSaves = s.forgeSaves + if (before?.forge == FORGE_CAPACITY) 1 else 0,
                    )
                }
                is GameEvent.BoardCleared -> {
                    val g = ev.stats
                    val boardMs = g.elapsedMillis - boardStartMs
                    val points = g.score - boardStartScore
                    val clean = g.discards == boardStartDiscards
                    markBoardStart(g)
                    val d = g.difficulty.name
                    s.copy(
                        boardsCleared = s.boardsCleared + 1,
                        cleanBoards = s.cleanBoards + if (clean) 1 else 0,
                        timeTrialBoards = s.timeTrialBoards + if (g.mode == GameMode.TIME_TRIAL) 1 else 0,
                        difficultiesCleared = s.difficultiesCleared + d,
                        bestBoardsInGame = s.bestBoardsInGame + (d to maxOf(s.bestBoardsInGame[d] ?: 0, g.boardsCleared)),
                        fastestBoardMs = if (boardMs > 0 && (s.fastestBoardMs == 0L || boardMs < s.fastestBoardMs)) boardMs else s.fastestBoardMs,
                        bestBoardPoints = maxOf(s.bestBoardPoints, points),
                        noHintGameBoards = if (g.hintsUsed == 0) maxOf(s.noHintGameBoards, g.boardsCleared) else s.noHintGameBoards,
                    )
                }
                is GameEvent.Discarded -> s.copy(discards = s.discards + 1)
                GameEvent.GameOver -> s.copy(
                    gamesFinished = s.gamesFinished + 1,
                    zeroScoreGameOver = s.zeroScoreGameOver || after.score == 0L,
                )
                else -> s
            }
            s.copy(
                bestScore = maxOf(s.bestScore, after.score),
                bestStreak = maxOf(s.bestStreak, after.bestStreak),
                highestBoard = maxOf(s.highestBoard, after.board),
            )
        }
    }

    /** Only at a pause (title, board complete, game or match over) and only one line, however many unlocked. */
    private fun pumpAchievementNote() {
        if (achievementNote != null && fx.now > achievementNoteUntil) achievementNote = null
        if (pendingAnnounce.isEmpty() || achievementNote != null) return
        val quiet = screen == Screen.TITLE || overlay is Overlay.BoardComplete || overlay is Overlay.GameOver || overlay == Overlay.MatchOver
        if (!quiet) return
        val list = pendingAnnounce
        pendingAnnounce = emptyList()
        achievementNote = if (list.size == 1) "Achievement unlocked: ${list[0].name}" else "${list.size} achievements unlocked"
        achievementNoteUntil = fx.now + 4.5f
        audio.play(Sfx.STREAK, 0.5f)
    }

    fun openAchievements() {
        achievementNote = null
        push(Overlay.Achievements)
    }

    /** Called when the Achievements panel closes: everything shown is now seen. */
    fun markAchievementsSeen() {
        unseenAchievements = emptySet()
        store.saveUnseenAchievements(emptySet())
    }

    // ---- Encrypted chat ---------------------------------------------------------------------------------------------

    private fun chatKey() = store.loadChatKey() ?: crypto.newKeyPair().also(store::saveChatKey)

    /** Make sure the server has our current public key (the private key never leaves the device). */
    private fun publishChatKey(onServer: String?) {
        if (keyPublished || crypto === NoChatCrypto) return
        val key = chatKey()
        keyPublished = true
        if (onServer != key.publicKey) online.publishKey(key.publicKey)
    }

    private fun receiveChat(messages: List<com.geoguy89.refinersfire.net.InboundMessage>) {
        val me = online.account ?: return
        if (messages.isEmpty()) return
        val key = chatKey()
        val open = (overlay as? Overlay.Chat)?.friendId
        val counts = unread.toMutableMap()
        for ((from, batch) in messages.groupBy { it.from }) {
            val friend = online.friends.firstOrNull { it.playerId == from } ?: continue
            val theirKey = friend.publicKey ?: continue
            val lines = batch.map { m ->
                val text = crypto.open(key.privateKey, theirKey, from, me.playerId, Sealed(m.nonce, m.ct))
                ChatLine(fromMe = false, text = text ?: "(This message couldn't be decrypted.)", t = m.t, failed = text == null)
            }
            val all = store.loadChat(from) + lines
            store.saveChat(from, all)
            if (from == open) chatLines = all.takeLast(Store.MAX_CHAT_LINES)
            else counts[from] = (counts[from] ?: 0) + lines.size
        }
        unread = counts
        store.saveUnread(counts)
        if (open == null) audio.play(Sfx.HINT, 0.6f)
        if (!windowFocused) for (from in messages.map { it.from }.distinct()) {
            online.friends.firstOrNull { it.playerId == from }?.let { systemNotices.tryEmit("Refiner's Fire" to "New message from ${it.name}") }
        }
    }

    fun openChat(friend: Friend) {
        click()
        chatLines = store.loadChat(friend.playerId)
        unread = unread - friend.playerId
        store.saveUnread(unread)
        overlays = overlays + Overlay.Chat(friend.playerId)
        lastSync = -100f
    }

    fun chatFriend(id: String): Friend? = online.friends.firstOrNull { it.playerId == id }

    /** The number both friends can compare to be sure the chat is private. */
    fun safetyCode(friend: Friend): String? {
        val theirs = friend.publicKey ?: return null
        return crypto.safetyCode(chatKey().publicKey, theirs)
    }

    fun sendChat(friend: Friend, text: String) {
        val me = online.account ?: return
        val clean = text.trim().take(CHAT_MAX_CHARS)
        if (clean.isEmpty()) return
        val theirKey = friend.publicKey
        if (theirKey == null) {
            notice = "${friend.name} needs to open the new version of the game once before you can chat."
            return
        }
        val sealed = crypto.seal(chatKey().privateKey, theirKey, me.playerId, friend.playerId, clean)
        val line = ChatLine(fromMe = true, text = clean, t = epochMillis())
        bump { it.copy(chatsSent = it.chatsSent + 1) }
        val all = store.loadChat(friend.playerId) + line
        store.saveChat(friend.playerId, all)
        chatLines = all.takeLast(Store.MAX_CHAT_LINES)
        online.sendChat(friend.playerId, sealed) { err ->
            val marked = store.loadChat(friend.playerId).map { if (it == line) it.copy(failed = true) else it }
            store.saveChat(friend.playerId, marked)
            if ((overlay as? Overlay.Chat)?.friendId == friend.playerId) chatLines = marked
            notice = err
        }
    }

    val totalUnread: Int get() = unread.values.sum()

    private fun joinMatch(matchId: String) {
        val me = online.account ?: return
        if (match != null || !joinedMatches.add(matchId)) return
        persist() // Keep any single-player game safe; it resumes from the title afterwards.
        match = MatchSession(
            matchId, me.playerId, online, post, { fx.now },
            onStart = { seed, difficulty, _ ->
                adopt(GameEngine.newMatch(difficulty, seed))
                overlays = emptyList()
                screen = Screen.GAME
                countGameStart()
            },
            onOpponentCleared = { board, score ->
                val who = match?.opponent?.name ?: "Your opponent"
                fx.banner("$who cleared Board $board ($score)", Palette.ember, height = 0.4f, y = 0.9f, duration = 2.6f)
                audio.play(Sfx.FORGE_WARNING, 0.7f)
            },
            onStoked = { levels ->
                val e = engine
                val who = match?.opponent?.name ?: "Your rival"
                val added = e?.stoke(levels) ?: 0
                if (e != null) state = e.state
                forgeFlareAt = fx.now
                audio.play(Sfx.HAMMER_APPEAR, 0.8f)
                _haptics.tryEmit(Haptic.HEAVY)
                fx.banner(if (added > 0) "$who stoked your forge! +$added" else "$who stoked your forge, but it's already full", Palette.lava, height = 0.42f, y = ROWS - 1.2f, duration = 2.2f)
                match?.report(e?.state ?: return@MatchSession)
            },
        )
        overlays = listOf(Overlay.MatchLobby)
    }

    /** Leave (or close) the match and return to the title. Leaving a live match forfeits it. */
    fun leaveMatch() {
        val m = match ?: return
        click()
        m.leave()
        match = null
        engine = null
        state = null
        overlays = emptyList()
        screen = Screen.TITLE
        savedGame = store.loadGame()
        lastSync = -100f
    }

    fun rematch() {
        val m = match ?: return
        val opponent = m.opponent ?: return
        val difficulty = m.difficulty
        val goal = m.goal
        leaveMatch()
        challenge(Rival(opponent.playerId, opponent.name), difficulty, goal)
    }

    fun hallRows(tab: HallTab): List<HallRow> {
        val myId = online.account?.playerId
        val mine = highScores.map { HallRow(it, null, null, isMe = true) }
        val rows = when (tab) {
            HallTab.MINE -> mine
            HallTab.NEARBY -> mine + peers.filter { it.direct }.flatMap { p -> p.scores.map { HallRow(it, "nearby", p.playerId, false) } }
            HallTab.FRIENDS -> mine + online.friends.flatMap { f -> f.scores.map { HallRow(it.toHighScore(), "friend", f.playerId, false) } }
            HallTab.GLOBAL -> {
                fun notMe(id: String?) = id == null || id != myId
                // The ranked board: one line per player (their best).
                val server = online.leaderboard.filter { notMe(it.playerId) }.map { HallRow(it.toHighScore(), if (it.online) "online" else null, it.playerId, false) }
                val relayed = peers.filter { it.open && notMe(it.playerId) }.flatMap { p -> p.scores.map { HallRow(it, if (p.direct) "nearby" else null, p.playerId, false) } }
                val own = if (settings.sharePlus) mine else emptyList()
                own + server + relayed
            }
        }
        val unique = rows.distinctBy { Triple(it.score.name, it.score.score, it.score.epochMillis) }
        // Global ranks players, so each appears once with their best.
        val perPlayer = if (tab == HallTab.GLOBAL) unique.sortedByDescending { it.score.score }.distinctBy { it.playerId ?: (it.score.name + if (it.isMe) "#me" else "") } else unique
        return perPlayer
            .sortedByDescending { it.score.score }
            .take(if (tab == HallTab.GLOBAL) 100 else Store.MAX_SCORES)
    }

    fun isFriend(playerId: String?): Boolean = playerId != null && online.friends.any { it.playerId == playerId }

    fun canBefriend(row: HallRow): Boolean {
        val id = row.playerId ?: return false
        return !row.isMe && id != online.account?.playerId && online.friends.none { it.playerId == id } &&
            online.outgoing.none { it.playerId == id }
    }

    // ---- Settings & lifecycle -----------------------------------------------------------------------------------

    fun updateSettings(s: Settings) {
        if (s.lanShare != settings.lanShare) if (s.lanShare) startLan() else stopLan()
        if (s.theme != settings.theme) {
            Palette.theme = s.theme
            audio.setTheme(s.theme)
        }
        Palette.pieceSet = s.pieceSet
        settings = s
        audio.sfxVolume = s.sfxVolume
        audio.musicVolume = s.musicVolume
        store.saveSettings(s)
    }

    fun setTheme(theme: ThemeId) = updateSettings(settings.copy(theme = theme))

    // ---- Updates ------------------------------------------------------------------------------------------------------

    fun checkForUpdate(manual: Boolean) {
        if (!updater.supported) return
        updateChecked = true
        updater.latest { info ->
            post {
                val newer = info != null && info.build > updater.installedBuild
                if (newer && (manual || info!!.build != settings.skippedUpdateBuild)) {
                    update = info
                    updateOffered = false
                    if (manual) {
                        if (screen == Screen.TITLE) {
                            // Asked for from Options on the title screen: show it in place of Options right away.
                            updateOffered = true
                            overlays = overlays.filter { it != Overlay.Options } + Overlay.Update
                        } else {
                            // Never over an active game; it's offered when back on the title screen.
                            notice = "Version ${info!!.version} is available. It'll be offered on the title screen."
                        }
                    }
                } else if (manual) {
                    notice = if (info == null) "Couldn't reach GitHub to check for updates." else "You have the latest version."
                }
            }
        }
    }

    /** Offer the update at a quiet moment: on the title screen with nothing else open. */
    private fun offerUpdate() {
        if (update == null || updateOffered || needsName || screen != Screen.TITLE || overlay != null) return
        updateOffered = true
        overlays = listOf(Overlay.Update)
    }

    fun installUpdate() {
        val info = update ?: return
        click()
        updateProgress = 0f
        updater.install(info, progress = { p -> post { updateProgress = p } }) { error ->
            post {
                updateProgress = null
                if (error != null) notice = "Update failed: $error" else overlays = overlays.filter { it != Overlay.Update }
            }
        }
    }

    fun skipUpdate() {
        val info = update ?: return
        click()
        updateSettings(settings.copy(skippedUpdateBuild = info.build))
        update = null
        overlays = overlays.filter { it != Overlay.Update }
    }

    // ---- Player name --------------------------------------------------------------------------------------------------

    /** First launch: nothing else happens until the player has picked a name. */
    val needsName: Boolean get() = !settings.nameChosen

    /** Sets the one name used everywhere: Hall of Fame, friends, matches, chat and the global board. */
    fun choosePlayerName(raw: String): Boolean {
        val name = cleanName(raw) ?: return false
        click()
        updateSettings(settings.copy(playerName = name, nameChosen = true))
        online.setName(name)
        if (overlay == Overlay.ChangeName) overlays = overlays.dropLast(1)
        return true
    }

    fun click() = audio.play(Sfx.CLICK, 0.7f)

    fun onAppForeground() {
        audio.startMusic()
        foreground = true
        if (settings.checkUpdates && !updateChecked) checkForUpdate(manual = false)
        lastSync = -100f
        if (settings.lanShare) startLan()
    }

    fun onAppBackground() {
        audio.pauseMusic()
        foreground = false
        stopLan()
        persist()
        // Closing or leaving the game saves it and goes back to the title, where Continue picks it up.
        // A live match isn't left this way: that would forfeit it.
        if (screen == Screen.GAME && match == null) toTitle()
        lastFrameNanos = 0L
    }

    private fun persist() {
        val s = engine?.state ?: return
        if (s.challengeId != null) {
            store.saveChallengeGame(if (s.gameOver) null else s)
            savedChallenge = store.loadChallengeGame()
            return
        }
        if (s.matchSeed != null) return // Matches are never saved over the single-player game.
        store.saveGame(s)
        savedGame = if (s.gameOver) null else s
    }

    fun forgetNearbyScores() {
        click()
        store.clearPeers()
        peers = emptyList()
    }

    fun dispose() {
        match?.leave()
        stopLan()
        persist()
        audio.release()
    }

    companion object {
        private const val BROADCAST_SECONDS = 5f
        const val NAME_MAX = 18

        /** Trimmed, control characters removed, at most [NAME_MAX]; null when nothing is left. */
        fun cleanName(raw: String): String? =
            raw.filter { !it.isISOControl() }.trim().take(NAME_MAX).trim().ifEmpty { null }
    }
}
