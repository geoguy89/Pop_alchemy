package com.geoguy89.refinersfire.net

import com.geoguy89.refinersfire.data.HighScore
import com.geoguy89.refinersfire.data.PeerScores
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.game.Ranks
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Sends and receives raw datagrams on the local network. The platform implementation broadcasts over UDP;
 * [NoLanTransport] is used in tests and previews.
 */
interface LanTransport {
    /** Start listening. [onReceive] may be called on a background thread. */
    fun start(onReceive: (ByteArray) -> Unit)
    /** Broadcast [bytes] to the local network. Must not block the caller. */
    fun send(bytes: ByteArray)
    fun stop()
}

object NoLanTransport : LanTransport {
    override fun start(onReceive: (ByteArray) -> Unit) = Unit
    override fun send(bytes: ByteArray) = Unit
    override fun stop() = Unit
}

@Serializable
data class LanScore(
    @SerialName("n") val name: String,
    @SerialName("s") val score: Long,
    @SerialName("b") val board: Int,
    @SerialName("d") val difficulty: Difficulty,
    @SerialName("m") val mode: GameMode,
    @SerialName("t") val epochMillis: Long,
    /** Device that set the score (absent: the sender). */
    @SerialName("o") val origin: String? = null,
    /** Online player id of that device, when it has one. */
    @SerialName("p") val originPlayer: String? = null,
    /** The owner chose Local+: this score may be passed on. */
    @SerialName("x") val open: Boolean = false,
)

@Serializable
data class LanPacket(
    val app: String,
    val v: Int,
    /** Random per-install id of the sender. */
    val id: String,
    val scores: List<LanScore>,
    /** Sender's online player id, so nearby players can add each other as friends. */
    @SerialName("pid") val playerId: String? = null,
    /** Sender is in Local+ mode. */
    val plus: Boolean = false,
)

/** Scores that came from a third device via a Local+ sender. Only ever scores their owner marked open. */
class ForwardedScores(val deviceId: String, val playerId: String?, val scores: List<HighScore>)

/** A packet after validation: who sent it, their own scores, and anything they passed on. */
class PeerUpdate(
    val deviceId: String,
    val playerId: String?,
    val scores: List<HighScore>,
    /** The sender's own scores may be passed on (they're in Local+). */
    val open: Boolean,
    val forwarded: List<ForwardedScores> = emptyList(),
)

/**
 * The score-sharing wire format.
 *
 * Local: a device sends only the scores set on that device, and ignores anything a neighbour passes on.
 * Local+: a device also passes on scores it has heard about, but only those whose owner chose Local+ ("open").
 * Consent travels with each score, so a Local-only player's scores never go beyond the people they meet.
 */
object LanProtocol {
    const val PORT = 47621
    const val APP = "refinersfire"
    const val VERSION = 1
    const val MAX_PACKET = 8192
    const val MAX_SCORES = 10
    /** Forwarded entries per packet, so it stays well under one datagram. */
    const val MAX_FORWARDED = 40
    private const val MAX_NAME = 18
    private const val MAX_SCORE = 100_000_000L
    private val idPattern = Regex("^[0-9a-f]{16,32}$")
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(
        deviceId: String,
        own: List<HighScore>,
        playerId: String? = null,
        plus: Boolean = false,
        known: List<PeerScores> = emptyList(),
    ): ByteArray {
        val mine = own.sortedByDescending { it.score }.take(MAX_SCORES)
            .map { LanScore(it.name, it.score, it.board, it.difficulty, it.mode, it.epochMillis, open = plus) }
        val passedOn = if (!plus) emptyList() else known.filter { it.open && it.deviceId != deviceId }
            .flatMap { p -> p.scores.map { h -> LanScore(h.name, h.score, h.board, h.difficulty, h.mode, h.epochMillis, p.deviceId, p.playerId, open = true) } }
            .sortedByDescending { it.score }
            .take(MAX_FORWARDED)
        val packet = LanPacket(APP, VERSION, deviceId, mine + passedOn, playerId, plus)
        return json.encodeToString(LanPacket.serializer(), packet).encodeToByteArray()
    }

    /** Parses and validates a datagram; returns null for anything that isn't a well-formed packet from another device. */
    fun decode(bytes: ByteArray, ownDeviceId: String): PeerUpdate? {
        if (bytes.size > MAX_PACKET) return null
        val p = try {
            json.decodeFromString(LanPacket.serializer(), bytes.decodeToString())
        } catch (e: Exception) {
            return null
        }
        if (p.app != APP || p.v != VERSION || !idPattern.matches(p.id) || p.id == ownDeviceId) return null
        fun clean(s: LanScore): HighScore? {
            val name = s.name.trim().take(MAX_NAME)
            return if (name.isEmpty() || s.score !in 1..MAX_SCORE || s.board !in 1..9999) null
            else HighScore(name, s.score, Ranks.rankFor(s.score), s.board, s.difficulty, s.mode, s.epochMillis)
        }
        val playerPattern = Regex("^[0-9a-f]{32}$")
        fun player(id: String?) = id?.takeIf { playerPattern.matches(it) }
        val (own, others) = p.scores.partition { it.origin == null || it.origin == p.id }
        val forwarded = others
            .filter { it.open && it.origin != ownDeviceId && idPattern.matches(it.origin!!) }
            .take(MAX_FORWARDED)
            .groupBy { it.origin!! }
            .map { (origin, list) -> ForwardedScores(origin, player(list.first().originPlayer), list.mapNotNull(::clean).take(MAX_SCORES)) }
        return PeerUpdate(p.id, player(p.playerId), own.take(MAX_SCORES).mapNotNull(::clean), open = p.plus || own.any { it.open }, forwarded = forwarded)
    }
}
