package com.geoguy89.refinersfire.data

import com.geoguy89.refinersfire.ThemeId
import com.geoguy89.refinersfire.net.Account
import com.geoguy89.refinersfire.net.AsyncSubmitBody
import com.geoguy89.refinersfire.net.ChatKeyPair
import com.geoguy89.refinersfire.net.ChatLine
import com.geoguy89.refinersfire.net.Friend
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.GameState
import com.geoguy89.refinersfire.game.LifetimeStats
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

@Serializable
data class Settings(
    val sfxVolume: Float = 0.8f,
    val musicVolume: Float = 0.5f,
    val haptics: Boolean = true,
    val theme: ThemeId = ThemeId.MODERN,
    /** Exchange own high scores with other copies of the game on the local network. */
    val lanShare: Boolean = true,
    /** Global: on the ranked leaderboard, open to challenges from other Global players, and scores passed on nearby. */
    val sharePlus: Boolean = false,
    /** Hide activity: the server keeps no record of when you play; friends see "Activity hidden". */
    val hideActivity: Boolean = false,
    /** Notifications for chats, challenges and friend requests when the game isn't on screen. */
    val notifications: Boolean = true,
    /** Piece shapes from another theme; null matches the theme. */
    val pieceSet: ThemeId? = null,
    val playerName: String = "Refiner",
    /** The player has chosen their name (asked once, before anything else). */
    val nameChosen: Boolean = false,
    /** Look for a newer release when the game starts (Android). */
    val checkUpdates: Boolean = true,
    /** "Skip this version": don't offer this build again. */
    val skippedUpdateBuild: Int = 0,
    val difficulty: Difficulty = Difficulty.EASY,
    val mode: GameMode = GameMode.STRATEGIC,
)

@Serializable
data class HighScore(
    val name: String,
    val score: Long,
    val rank: String,
    val board: Int,
    val difficulty: Difficulty,
    val mode: GameMode,
    val epochMillis: Long,
)

/**
 * High scores from another device: met directly on the local network ([direct]), or passed on by a Local+ player.
 * [open] means the owner is in Local+ and allows them to be passed on again.
 */
@Serializable
data class PeerScores(
    val deviceId: String,
    val scores: List<HighScore>,
    val lastSeen: Long,
    val playerId: String? = null,
    val direct: Boolean = true,
    val open: Boolean = false,
)

/** Minimal persistent string storage; SharedPreferences on Android, a properties file on desktop. */
interface KeyValueStore {
    fun get(key: String): String?
    /** Stores [value], or removes the key when it is null. */
    fun put(key: String, value: String?)
}

class MemoryKeyValueStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}

/** Settings, the hall of fame and the game in progress, stored as JSON. */
class Store(private val prefs: KeyValueStore) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun loadSettings(): Settings = decode("settings", Settings.serializer()) ?: Settings()
    fun saveSettings(s: Settings) = prefs.put("settings", json.encodeToString(Settings.serializer(), s))

    fun loadHighScores(): List<HighScore> = decode("scores", ListSerializer(HighScore.serializer())) ?: emptyList()

    /** Adds the score if it makes the table; returns its position (0-based) or -1. */
    fun addHighScore(entry: HighScore): Int {
        val list = (loadHighScores() + entry).sortedByDescending { it.score }.take(MAX_SCORES)
        prefs.put("scores", json.encodeToString(ListSerializer(HighScore.serializer()), list))
        return list.indexOf(entry)
    }

    fun qualifies(score: Long): Boolean {
        val list = loadHighScores()
        return score > 0 && (list.size < MAX_SCORES || score > list.last().score)
    }

    /** Random id for this install, used to tell our own broadcasts from other devices'. */
    fun deviceId(): String = prefs.get("deviceId") ?: buildString {
        val r = kotlin.random.Random.Default
        repeat(16) { append("0123456789abcdef"[r.nextInt(16)]) }
    }.also { prefs.put("deviceId", it) }

    fun loadPeers(): List<PeerScores> = decode("peers", ListSerializer(PeerScores.serializer())) ?: emptyList()

    /**
     * Merge a neighbour's scores into what we already hold for that device (their top [MAX_SCORES]). Keeps the
     * [MAX_PEERS] most recently seen devices. Returns the new list.
     */
    fun mergePeer(
        deviceId: String,
        scores: List<HighScore>,
        now: Long,
        playerId: String? = null,
        direct: Boolean = true,
        open: Boolean = false,
    ): List<PeerScores> {
        val peers = loadPeers().toMutableList()
        val old = peers.firstOrNull { it.deviceId == deviceId }
        val merged = ((old?.scores ?: emptyList()) + scores)
            .distinctBy { Triple(it.name, it.score, it.epochMillis) }
            .sortedByDescending { it.score }
            .take(MAX_SCORES)
        peers.removeAll { it.deviceId == deviceId }
        peers += PeerScores(
            deviceId, merged, now,
            playerId = playerId ?: old?.playerId,
            direct = direct || old?.direct == true,
            // Consent is the owner's current choice when we hear it from them; a relayed copy can only confirm it.
            open = if (direct) open else (open || old?.open == true),
        )
        val kept = peers.sortedByDescending { it.lastSeen }.take(MAX_PEERS)
        prefs.put("peers", json.encodeToString(ListSerializer(PeerScores.serializer()), kept))
        return kept
    }

    fun clearPeers() = prefs.put("peers", null)

    /** This device's chat key pair. The private key is only ever stored here. */
    fun loadChatKey(): ChatKeyPair? = decode("chatKey", ChatKeyPair.serializer())
    fun saveChatKey(k: ChatKeyPair) = prefs.put("chatKey", json.encodeToString(ChatKeyPair.serializer(), k))

    fun loadChat(friendId: String): List<ChatLine> = decode("chat.$friendId", ListSerializer(ChatLine.serializer())) ?: emptyList()
    fun saveChat(friendId: String, lines: List<ChatLine>) =
        prefs.put("chat.$friendId", json.encodeToString(ListSerializer(ChatLine.serializer()), lines.takeLast(MAX_CHAT_LINES)))

    private val unreadSerializer = kotlinx.serialization.builtins.MapSerializer(String.serializer(), Int.serializer())

    fun loadUnread(): Map<String, Int> = decode("unread", unreadSerializer) ?: emptyMap()
    fun saveUnread(u: Map<String, Int>) = prefs.put("unread", json.encodeToString(unreadSerializer, u))

    fun loadStats(): LifetimeStats = decode("stats", LifetimeStats.serializer()) ?: LifetimeStats()
    fun saveStats(s: LifetimeStats) = prefs.put("stats", json.encodeToString(LifetimeStats.serializer(), s))

    /** Unlocked achievement ids and when. */
    fun loadAchievements(): Map<String, Long> = decode("achievements", achievementSerializer) ?: emptyMap()
    fun saveAchievements(a: Map<String, Long>) = prefs.put("achievements", json.encodeToString(achievementSerializer, a))
    private val achievementSerializer = kotlinx.serialization.builtins.MapSerializer(String.serializer(), Long.serializer())

    /** Unlocked ids the player hasn't looked at yet. */
    fun loadUnseenAchievements(): Set<String> = decode("achUnseen", kotlinx.serialization.builtins.SetSerializer(String.serializer())) ?: emptySet()
    fun saveUnseenAchievements(s: Set<String>) = prefs.put("achUnseen", json.encodeToString(kotlinx.serialization.builtins.SetSerializer(String.serializer()), s))

    /** An async challenge run in progress, kept apart from the single-player save. */
    fun loadChallengeGame(): GameState? = decode("challengeGame", GameState.serializer())
    fun saveChallengeGame(s: GameState?) = prefs.put("challengeGame", s?.let { json.encodeToString(GameState.serializer(), it) })

    /** Finished runs whose result hasn't reached the server yet. */
    fun loadPendingResults(): List<AsyncSubmitBody> = decode("pendingResults", ListSerializer(AsyncSubmitBody.serializer())) ?: emptyList()
    fun savePendingResults(l: List<AsyncSubmitBody>) = prefs.put("pendingResults", json.encodeToString(ListSerializer(AsyncSubmitBody.serializer()), l))

    /** Challenge ids already announced (new challenge, or its result), so each is mentioned once. */
    fun loadAnnounced(): Set<String> = decode("announced", kotlinx.serialization.builtins.SetSerializer(String.serializer())) ?: emptySet()
    fun saveAnnounced(s: Set<String>) = prefs.put("announced", json.encodeToString(kotlinx.serialization.builtins.SetSerializer(String.serializer()), s.toList().takeLast(200).toSet()))

    fun loadAccount(): Account? = decode("account", Account.serializer())
    fun saveAccount(a: Account) = prefs.put("account", json.encodeToString(Account.serializer(), a))
    fun clearAccount() = prefs.put("account", null)

    /** Last known friends list, so the Friends Hall of Fame works offline. */
    fun loadFriends(): List<Friend> = decode("friends", ListSerializer(Friend.serializer())) ?: emptyList()
    fun saveFriends(f: List<Friend>) = prefs.put("friends", json.encodeToString(ListSerializer(Friend.serializer()), f))

    fun loadGame(): GameState? = decode("game", GameState.serializer())
    fun saveGame(state: GameState?) {
        prefs.put("game", if (state == null || state.gameOver) null else json.encodeToString(GameState.serializer(), state))
    }

    private fun <T> decode(key: String, ser: kotlinx.serialization.KSerializer<T>): T? = try {
        prefs.get(key)?.let { json.decodeFromString(ser, it) }
    } catch (e: Exception) {
        null
    }

    companion object {
        const val MAX_SCORES = 10
        const val MAX_PEERS = 50
        const val MAX_CHAT_LINES = 200
    }
}
