package com.geoguy89.refinersfire.net

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.geoguy89.refinersfire.data.HighScore
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.Ranks
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where the game server lives. Overridable for tests and local development. */
object OnlineConfig {
    const val DEFAULT_URL = "https://refiners-fire.geoguy89.workers.dev"
    var baseUrl: String = DEFAULT_URL
}

// ---- Transport (implemented per platform) ---------------------------------------------------------------------------

interface HttpTransport {
    /** Asynchronous request. [done] gets the status code (-1 when the server can't be reached) and body, on any thread. */
    fun request(method: String, url: String, body: String?, headers: Map<String, String>, done: (Int, String?) -> Unit)
    fun openSocket(url: String, headers: Map<String, String>, listener: SocketListener): LiveSocket
}

interface SocketListener {
    fun onOpen()
    fun onMessage(text: String)
    /** The socket is gone; [refused] is true when the handshake itself was rejected (not a member, match over). */
    fun onClosed(refused: Boolean)
}

interface LiveSocket {
    fun send(text: String)
    fun close()
}

object NoHttp : HttpTransport {
    override fun request(method: String, url: String, body: String?, headers: Map<String, String>, done: (Int, String?) -> Unit) = done(-1, null)
    override fun openSocket(url: String, headers: Map<String, String>, listener: SocketListener): LiveSocket {
        listener.onClosed(refused = true)
        return object : LiveSocket {
            override fun send(text: String) = Unit
            override fun close() = Unit
        }
    }
}

// ---- Wire types ------------------------------------------------------------------------

@Serializable
data class Account(val playerId: String, val secret: String, val friendCode: String, val name: String)

@Serializable
data class ScoreDto(
    val name: String,
    val score: Long,
    val board: Int,
    val difficulty: Difficulty,
    val mode: GameMode,
    val t: Long,
    val playerId: String? = null,
) {
    fun toHighScore() = HighScore(name, score, Ranks.rankFor(score), board, difficulty, mode, t)

    companion object {
        fun of(h: HighScore) = ScoreDto(h.name, h.score, h.board, h.difficulty, h.mode, h.epochMillis)
    }
}

@Serializable
data class Friend(
    val playerId: String,
    val name: String,
    /** Null when they hide their activity. */
    val lastSeenAgoMs: Long? = null,
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0,
    val scores: List<ScoreDto> = emptyList(),
    /** Their chat public key, once their game has published one. */
    val publicKey: String? = null,
    val activityHidden: Boolean = false,
) {
    val online: Boolean get() = lastSeenAgoMs != null && lastSeenAgoMs < 30_000
    val best: Long get() = scores.maxOfOrNull { it.score } ?: 0
}

@Serializable
data class FriendRequest(val id: String, val playerId: String, val name: String)

/** 12345 -> "12,345". */
fun thousands(n: Long): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

/** The kinds of 1v1 match, with the choices each offers ([MatchGoal.value]). */
enum class MatchType(val id: String, val title: String, val blurb: String, val values: List<Int>, val default: Int) {
    TIMED("time", "Timed", "Highest score when the clock runs out. If a forge overflows, that player is out and the other plays on.", listOf(5, 8, 12), 8),
    RACE("boards", "Race", "First to reach the target board wins. Overflow your forge and you lose.", listOf(1, 2, 3, 5), 2),
    SCORE("points", "Score Rush", "First to the target score wins. Overflow your forge and you lose.", listOf(1_000, 2_500, 5_000, 10_000), 2_500),
    SURVIVAL("survival", "Survival", "No clock and no target: the first forge to overflow loses. Careful play wins.", listOf(0), 0),
    STOKE("stoke", "Stoke Duel", "Every line you clear stokes your rival's forge up a level (symbol lines stoke it twice). Highest score when time's up.", listOf(5, 8, 12), 8);

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: TIMED
    }
}

/** How a match is won: a [MatchType] id plus its setting (minutes, boards to clear, or points). */
@Serializable
data class MatchGoal(val type: String = "time", val value: Int = 8) {
    val kind: MatchType get() = MatchType.of(type)
    /** Races (boards, points) and Survival end when someone overflows; the others let the other player play on. */
    val race: Boolean get() = kind == MatchType.RACE || kind == MatchType.SCORE || kind == MatchType.SURVIVAL
    val timed: Boolean get() = kind == MatchType.TIMED || kind == MatchType.STOKE

    fun label(difficulty: Difficulty): String = when (kind) {
        MatchType.TIMED -> "Timed · $value minutes"
        MatchType.RACE -> "Race · first to Board ${difficulty.startBoard + value}"
        MatchType.SCORE -> "Score Rush · first to ${thousands(value.toLong() * difficulty.scoreMultiplier)}"
        MatchType.SURVIVAL -> "Survival · last forge standing"
        MatchType.STOKE -> "Stoke Duel · $value minutes"
    }

    /** The setting as a short button label. */
    fun valueLabel(difficulty: Difficulty): String = when (kind) {
        MatchType.TIMED, MatchType.STOKE -> "$value min"
        MatchType.RACE -> "Board ${difficulty.startBoard + value}"
        MatchType.SCORE -> thousands(value.toLong() * difficulty.scoreMultiplier)
        MatchType.SURVIVAL -> ""
    }
}

@Serializable
data class Invite(
    val id: String,
    val incoming: Boolean,
    val playerId: String,
    val name: String,
    val difficulty: Difficulty,
    val status: String,
    val matchId: String? = null,
    val ageMs: Long = 0,
    val goalType: String = "time",
    val goalValue: Int = 8,
) {
    val goal: MatchGoal get() = MatchGoal(goalType, goalValue)
}

/** Someone you can challenge: a friend, or a player on the Global leaderboard. */
@Serializable
data class Rival(val playerId: String, val name: String, val online: Boolean = false, val friend: Boolean = false)

/** One line of the ranked Global leaderboard: a player's best score. */
@Serializable
data class LeaderboardEntry(
    val playerId: String,
    val name: String,
    val best: Long,
    val board: Int = 1,
    val difficulty: Difficulty = Difficulty.EASY,
    val mode: GameMode = GameMode.STRATEGIC,
    val t: Long = 0,
    val online: Boolean = false,
) {
    fun toHighScore() = HighScore(name, best, Ranks.rankFor(best), board, difficulty, mode, t)
}

/** An async challenge: the same seeded run, played by each friend in their own time. */
@Serializable
data class AsyncChallenge(
    val id: String,
    /** True when a friend challenged us. */
    val incoming: Boolean,
    val playerId: String,
    val name: String,
    val difficulty: Difficulty,
    val boards: Int,
    val seed: Long,
    /** playing (challenger's run not in yet), waiting (friend's turn), done, declined, cancelled, expired. */
    val status: String,
    val ageMs: Long = 0,
    val myScore: Long? = null,
    val theirScore: Long? = null,
    val myBoards: Int? = null,
    val theirBoards: Int? = null,
    val winner: String? = null,
) {
    /** It's our move: a friend's challenge waiting for our run. */
    val ourMove: Boolean get() = incoming && status == "waiting"
    /** We started it but haven't finished our own run. */
    val ourRunUnfinished: Boolean get() = !incoming && status == "playing"
}

@Serializable
data class Me(val playerId: String, val friendCode: String, val name: String, val shareGlobal: Boolean = false, val publicKey: String? = null, val hideActivity: Boolean = false)

@Serializable
data class SyncResponse(
    val me: Me,
    val friends: List<Friend> = emptyList(),
    val incoming: List<FriendRequest> = emptyList(),
    val outgoing: List<FriendRequest> = emptyList(),
    val invites: List<Invite> = emptyList(),
    val messages: List<InboundMessage> = emptyList(),
    val asyncChallenges: List<AsyncChallenge> = emptyList(),
)

@Serializable private data class RegisterBody(val name: String)
@Serializable private data class ProfileBody(val name: String? = null, val publicKey: String? = null, val hideActivity: Boolean? = null, val pushToken: String? = null)
@Serializable private data class LeaderboardResponse(val players: List<LeaderboardEntry>)
@Serializable private data class ChatBody(val to: String, val nonce: String, val ct: String)
@Serializable private data class AsyncCreateBody(val playerId: String, val difficulty: Difficulty, val boards: Int)
@Serializable data class AsyncCreated(val id: String, val seed: Long, val difficulty: Difficulty, val boards: Int)
@Serializable data class AsyncSubmitBody(val id: String, val score: Long, val boards: Int, val timeMs: Long)
@Serializable private data class AsyncSubmitted(val status: String, val winner: String? = null)
@Serializable private data class ScoresBody(val scores: List<ScoreDto>, val shareGlobal: Boolean)
@Serializable private data class CodeBody(val code: String)
@Serializable private data class PlayerBody(val playerId: String)
@Serializable private data class RespondBody(val id: String, val accept: Boolean)
@Serializable private data class IdBody(val id: String)
@Serializable private data class InviteBody(val playerId: String, val difficulty: Difficulty, val goalType: String, val goalValue: Int)
@Serializable private data class FriendResult(val status: String, val name: String)
@Serializable private data class ErrorBody(val error: String)
@Serializable private data class InviteCreated(val id: String)
@Serializable private data class MatchAccepted(val matchId: String? = null)
@Serializable private data class GlobalResponse(val scores: List<ScoreDto>)

internal val wireJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

// ---- Client ---------------------------------------------------------------------------------------------------------

/**
 * Friends, the global Hall of Fame and match invites. All callbacks are delivered through [post], which the view model
 * drains on the UI thread, so the state here is only ever touched from that thread.
 */
class OnlineService(
    private val http: HttpTransport,
    private val post: (() -> Unit) -> Unit,
    initialAccount: Account?,
    private val saveAccount: (Account) -> Unit,
) {
    var account by mutableStateOf(initialAccount)
        private set
    var friends by mutableStateOf<List<Friend>>(emptyList())
    var incoming by mutableStateOf<List<FriendRequest>>(emptyList())
        private set
    var outgoing by mutableStateOf<List<FriendRequest>>(emptyList())
        private set
    var invites by mutableStateOf<List<Invite>>(emptyList())
        private set
    var global by mutableStateOf<List<ScoreDto>>(emptyList())
        private set
    var asyncChallenges by mutableStateOf<List<AsyncChallenge>>(emptyList())
        private set
    var leaderboard by mutableStateOf<List<LeaderboardEntry>>(emptyList())
        private set
    /** False after a request fails to reach the server; true again on the next success. */
    var reachable by mutableStateOf(true)
        private set
    private var registering = false
    private var syncing = false
    /** Called after every successful sync, e.g. to start a match whose invite was just accepted. */
    var onSynced: (SyncResponse) -> Unit = {}
    /** The server doesn't know our account (for example after a server move): forget it so a new one is made. */
    var onAccountRejected: () -> Unit = {}

    private val url get() = OnlineConfig.baseUrl.trimEnd('/')

    private fun auth(): Map<String, String> =
        account?.let { mapOf("Authorization" to "Bearer ${it.playerId}.${it.secret}") } ?: emptyMap()

    private fun <T> send(
        method: String,
        path: String,
        body: String?,
        ser: KSerializer<T>?,
        onError: (String) -> Unit = {},
        onOk: (T?) -> Unit = {},
    ) {
        http.request(method, url + path, body, auth() + ("Content-Type" to "application/json")) { code, text ->
            post {
                reachable = code != -1
                if (code == 401 && account != null) {
                    account = null
                    syncing = false
                    onAccountRejected()
                }
                when {
                    code in 200..299 -> onOk(ser?.let { s -> text?.let { runCatching { wireJson.decodeFromString(s, it) }.getOrNull() } })
                    code == -1 -> onError("Can't reach the game server. Check your connection.")
                    else -> onError(text?.let { runCatching { wireJson.decodeFromString(ErrorBody.serializer(), it).error }.getOrNull() } ?: "Server error ($code)")
                }
            }
        }
    }

    private inline fun <reified T> encode(ser: KSerializer<T>, v: T) = wireJson.encodeToString(ser, v)

    /** Creates this install's account (and friend code) on first use. */
    fun ensureAccount(name: String, then: () -> Unit = {}) {
        if (account != null) { then(); return }
        if (registering) return
        registering = true
        send("POST", "/v1/register", encode(RegisterBody.serializer(), RegisterBody(name)), Account.serializer(),
            onError = { registering = false },
        ) { acc ->
            registering = false
            if (acc != null) {
                account = acc
                saveAccount(acc)
                then()
            }
        }
    }

    fun sync(done: () -> Unit = {}) {
        if (account == null || syncing) return
        syncing = true
        send("GET", "/v1/sync", null, SyncResponse.serializer(), onError = { syncing = false; done() }) { r ->
            syncing = false
            if (r != null) {
                friends = r.friends
                incoming = r.incoming
                outgoing = r.outgoing
                invites = r.invites
                asyncChallenges = r.asyncChallenges
                account?.let { a ->
                    if (a.name != r.me.name || a.friendCode != r.me.friendCode) {
                        val updated = a.copy(name = r.me.name, friendCode = r.me.friendCode)
                        account = updated
                        saveAccount(updated)
                    }
                }
                onSynced(r)
            }
            done()
        }
    }

    fun setName(name: String) {
        val a = account ?: return
        if (a.name == name) return
        account = a.copy(name = name).also(saveAccount)
        send<Unit>("POST", "/v1/profile", encode(ProfileBody.serializer(), ProfileBody(name = name)), null)
    }

    fun setHideActivity(hide: Boolean) {
        send<Unit>("POST", "/v1/profile", encode(ProfileBody.serializer(), ProfileBody(hideActivity = hide)), null)
    }

    /** The device token for notifications, or "" to stop them. */
    fun setPushToken(token: String) {
        if (account == null) return
        send<Unit>("POST", "/v1/profile", encode(ProfileBody.serializer(), ProfileBody(pushToken = token)), null)
    }

    fun fetchLeaderboard() {
        send("GET", "/v1/leaderboard", null, LeaderboardResponse.serializer()) { r -> if (r != null) leaderboard = r.players }
    }

    fun publishKey(publicKey: String) {
        send<Unit>("POST", "/v1/profile", encode(ProfileBody.serializer(), ProfileBody(publicKey = publicKey)), null)
    }

    fun sendChat(to: String, sealed: Sealed, onError: (String) -> Unit) {
        send<Unit>("POST", "/v1/chat/send", encode(ChatBody.serializer(), ChatBody(to, sealed.nonce, sealed.ct)), null, onError = onError)
    }

    /** Uploads this device's own Hall of Fame. [shareGlobal] also lists it on the global board. */
    fun pushScores(own: List<HighScore>, shareGlobal: Boolean) {
        if (account == null) return
        val body = ScoresBody(own.sortedByDescending { it.score }.take(10).map(ScoreDto::of), shareGlobal)
        send<Unit>("POST", "/v1/scores", encode(ScoresBody.serializer(), body), null)
    }

    fun fetchGlobal() {
        send("GET", "/v1/global", null, GlobalResponse.serializer()) { r -> if (r != null) global = r.scores }
    }

    fun addFriendByCode(code: String, done: (String) -> Unit) =
        friendRequest(encode(CodeBody.serializer(), CodeBody(code)), done)

    fun addFriend(playerId: String, done: (String) -> Unit) =
        friendRequest(encode(PlayerBody.serializer(), PlayerBody(playerId)), done)

    private fun friendRequest(body: String, done: (String) -> Unit) {
        send("POST", "/v1/friends/request", body, FriendResult.serializer(), onError = done) { r ->
            done(if (r?.status == "friends") "You and ${r.name} are now friends!" else "Request sent to ${r?.name ?: "them"}. They'll see it next time they open the game.")
            sync()
        }
    }

    fun respondToRequest(id: String, accept: Boolean) {
        incoming = incoming.filter { it.id != id }
        send<Unit>("POST", "/v1/friends/respond", encode(RespondBody.serializer(), RespondBody(id, accept)), null) { sync() }
    }

    fun removeFriend(playerId: String) {
        friends = friends.filter { it.playerId != playerId }
        send<Unit>("POST", "/v1/friends/remove", encode(PlayerBody.serializer(), PlayerBody(playerId)), null) { sync() }
    }

    fun invite(playerId: String, difficulty: Difficulty, goal: MatchGoal, onError: (String) -> Unit) {
        send("POST", "/v1/match/invite", encode(InviteBody.serializer(), InviteBody(playerId, difficulty, goal.type, goal.value)), InviteCreated.serializer(), onError = onError) { sync() }
    }

    /** Accepting returns the match id straight away, so the accepter doesn't wait for the next sync. */
    fun respondToInvite(id: String, accept: Boolean, onMatch: (String) -> Unit, onError: (String) -> Unit) {
        invites = invites.filter { it.id != id }
        send("POST", "/v1/match/respond", encode(RespondBody.serializer(), RespondBody(id, accept)), MatchAccepted.serializer(), onError = onError) { r ->
            r?.matchId?.let(onMatch)
        }
    }

    fun cancelInvite(id: String) {
        invites = invites.filter { it.id != id }
        send<Unit>("POST", "/v1/match/cancel", encode(IdBody.serializer(), IdBody(id)), null)
    }

    fun createAsync(playerId: String, difficulty: Difficulty, boards: Int, onError: (String) -> Unit, onCreated: (AsyncCreated) -> Unit) {
        send("POST", "/v1/async/create", encode(AsyncCreateBody.serializer(), AsyncCreateBody(playerId, difficulty, boards)), AsyncCreated.serializer(), onError = onError) { r ->
            if (r != null) onCreated(r)
        }
    }

    /** [done] gets true when the server has the result (or already had it), false to retry later. */
    fun submitAsync(result: AsyncSubmitBody, done: (Boolean) -> Unit) {
        send("POST", "/v1/async/submit", encode(AsyncSubmitBody.serializer(), result), AsyncSubmitted.serializer(), onError = { err ->
            // A 409 means it was already recorded (or it's gone): don't keep retrying.
            done(!err.startsWith("Can't reach"))
        }) { done(true); sync() }
    }

    fun declineAsync(id: String) {
        asyncChallenges = asyncChallenges.filter { it.id != id }
        send<Unit>("POST", "/v1/async/decline", encode(IdBody.serializer(), IdBody(id)), null) { sync() }
    }

    fun openMatchSocket(matchId: String, listener: SocketListener): LiveSocket {
        val ws = url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://")
        return http.openSocket("$ws/v1/match/$matchId/ws", auth(), listener)
    }
}
