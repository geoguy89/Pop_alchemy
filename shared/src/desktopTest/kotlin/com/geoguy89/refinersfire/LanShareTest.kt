package com.geoguy89.refinersfire

import com.geoguy89.refinersfire.audio.SilentAudio
import com.geoguy89.refinersfire.data.HighScore
import com.geoguy89.refinersfire.data.MemoryKeyValueStore
import com.geoguy89.refinersfire.data.Store
import com.geoguy89.refinersfire.game.Difficulty
import com.geoguy89.refinersfire.game.GameMode
import com.geoguy89.refinersfire.net.LanProtocol
import com.geoguy89.refinersfire.net.LanTransport
import com.geoguy89.refinersfire.net.UdpLanTransport
import com.geoguy89.refinersfire.ui.GameViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LanShareTest {
    private fun score(name: String, points: Long) =
        HighScore(name, points, "", 3, Difficulty.EASY, GameMode.STRATEGIC, 1_700_000_000_000L + points)

    @Test
    fun protocolRoundTripsAndValidates() {
        val a = "0123456789abcdef"
        val bytes = LanProtocol.encode(a, listOf(score("Ann", 1200), score("Ann", 450)))
        val update = LanProtocol.decode(bytes, ownDeviceId = "fedcba9876543210")
        assertNotNull(update)
        assertEquals(a, update!!.deviceId)
        assertEquals(listOf(1200L, 450L), update.scores.map { it.score })
        assertEquals("Apprentice Smelter", update.scores[0].rank) // Rank is recomputed, not trusted.
        assertNull("own broadcast ignored", LanProtocol.decode(bytes, ownDeviceId = a))
        assertNull(LanProtocol.decode("not json".encodeToByteArray(), "fedcba9876543210"))
        assertNull(LanProtocol.decode(ByteArray(LanProtocol.MAX_PACKET + 1), "fedcba9876543210"))
        val foreign = """{"app":"other","v":1,"id":"$a","scores":[]}""".encodeToByteArray()
        assertNull(LanProtocol.decode(foreign, "fedcba9876543210"))
        val junk = """{"app":"refinersfire","v":1,"id":"$a","scores":[{"n":"   ","s":5,"b":1,"d":"EASY","m":"STRATEGIC","t":0},{"n":"X","s":-4,"b":1,"d":"EASY","m":"STRATEGIC","t":0}]}"""
        assertTrue("blank names and negative scores dropped", LanProtocol.decode(junk.encodeToByteArray(), "fedcba9876543210")!!.scores.isEmpty())
    }

    /** An in-memory network where each device only hears the devices it is linked to. */
    private class FakeNet {
        val links = HashMap<FakeTransport, MutableSet<FakeTransport>>()
        fun link(a: FakeTransport, b: FakeTransport) {
            links.getOrPut(a) { HashSet() } += b
            links.getOrPut(b) { HashSet() } += a
        }
    }

    private class FakeTransport(val net: FakeNet) : LanTransport {
        var receiver: ((ByteArray) -> Unit)? = null
        override fun start(onReceive: (ByteArray) -> Unit) { receiver = onReceive }
        override fun send(bytes: ByteArray) { net.links[this]?.forEach { it.receiver?.invoke(bytes) } }
        override fun stop() { receiver = null }
    }

    @Test
    fun onlyOwnScoresAreShared() {
        val net = FakeNet()
        val tA = FakeTransport(net); val tB = FakeTransport(net); val tC = FakeTransport(net)
        // A and C never meet; B sits between them (home and work).
        net.link(tA, tB); net.link(tB, tC)
        fun device(t: FakeTransport, vararg own: HighScore): GameViewModel {
            val store = Store(MemoryKeyValueStore())
            store.saveSettings(com.geoguy89.refinersfire.data.Settings(sharePlus = false))
            own.forEach { store.addHighScore(it) }
            return GameViewModel(store, SilentAudio, t).also { it.onAppForeground() }
        }
        val a = device(tA, score("Ann", 3000))
        val b = device(tB, score("Bob", 2000))
        val c = device(tC, score("Cat", 1000))
        var t = 1L
        repeat(4) { for (vm in listOf(a, b, c)) vm.onFrame(t); t += 6_000_000_000L }

        fun names(vm: GameViewModel) = vm.peers.flatMap { p -> p.scores.map { it.name } }.toSet()
        assertEquals(setOf("Bob"), names(a))
        assertEquals(setOf("Ann", "Cat"), names(b))
        assertEquals("C hears B's own scores, never A's", setOf("Bob"), names(c))
        // Received scores never enter the device's own table (so they can't be re-broadcast).
        assertEquals(listOf("Bob"), b.highScores.map { it.name })

        b.updateSettings(b.settings.copy(lanShare = false))
        assertNull("turning sharing off stops the transport", tB.receiver)
    }

    /** A-B and B-C linked; A and C never meet. Returns the names C ends up knowing about. */
    private fun chain(aPlus: Boolean, bPlus: Boolean, cPlus: Boolean): Set<String> {
        val net = FakeNet()
        val tA = FakeTransport(net); val tB = FakeTransport(net); val tC = FakeTransport(net)
        net.link(tA, tB); net.link(tB, tC)
        fun device(t: FakeTransport, plus: Boolean, own: HighScore): GameViewModel {
            val store = Store(MemoryKeyValueStore())
            store.addHighScore(own)
            store.saveSettings(com.geoguy89.refinersfire.data.Settings(sharePlus = plus))
            return GameViewModel(store, SilentAudio, t).also { it.onAppForeground() }
        }
        val a = device(tA, aPlus, score("Ann", 3000))
        val b = device(tB, bPlus, score("Bob", 2000))
        val c = device(tC, cPlus, score("Cat", 1000))
        var t = 1L
        repeat(6) { for (vm in listOf(a, b, c)) vm.onFrame(t); t += 6_000_000_000L }
        return c.peers.flatMap { p -> p.scores.map { it.name } }.toSet()
    }

    @Test
    fun localPlusPassesOnOnlyOpenScores() {
        assertEquals("all Local+: Ann reaches Cat through Bob", setOf("Ann", "Bob"), chain(aPlus = true, bPlus = true, cPlus = true))
        assertEquals("Ann is Local only: never passed on", setOf("Bob"), chain(aPlus = false, bPlus = true, cPlus = true))
        assertEquals("Bob is Local only: he passes nothing on", setOf("Bob"), chain(aPlus = true, bPlus = false, cPlus = true))
        assertEquals("Cat is Local only: she ignores relayed scores", setOf("Bob"), chain(aPlus = true, bPlus = true, cPlus = false))
    }

    @Test
    fun relayedScoresAreMarkedAsNotMetDirectly() {
        val net = FakeNet()
        val tA = FakeTransport(net); val tB = FakeTransport(net); val tC = FakeTransport(net)
        net.link(tA, tB); net.link(tB, tC)
        val vms = listOf(tA to "Ann", tB to "Bob", tC to "Cat").map { (t, n) ->
            val store = Store(MemoryKeyValueStore())
            store.addHighScore(score(n, 500))
            store.saveSettings(com.geoguy89.refinersfire.data.Settings(sharePlus = true))
            GameViewModel(store, SilentAudio, t).also { it.onAppForeground() }
        }
        var t = 1L
        repeat(6) { vms.forEach { it.onFrame(t) }; t += 6_000_000_000L }
        val c = vms[2]
        assertEquals(setOf("Bob"), c.hallRows(com.geoguy89.refinersfire.ui.HallTab.NEARBY).filter { !it.isMe }.map { it.score.name }.toSet())
        assertEquals(setOf("Ann", "Bob", "Cat"), c.hallRows(com.geoguy89.refinersfire.ui.HallTab.GLOBAL).map { it.score.name }.toSet())
    }

    @Test
    fun udpLoopbackDelivers() {
        val loop = InetAddress.getLoopbackAddress()
        val latch = CountDownLatch(1)
        var got: ByteArray? = null
        val receiver = UdpLanTransport(port = 47731, targets = { emptyList() })
        val sender = UdpLanTransport(port = 47732, targets = { listOf(InetSocketAddress(loop, 47731)) })
        receiver.start { got = it; latch.countDown() }
        sender.start { }
        try {
            val payload = LanProtocol.encode("0123456789abcdef", listOf(score("Ann", 900)))
            // UDP may drop the first datagram while the socket settles; resend until it arrives.
            repeat(10) { if (latch.count > 0) { sender.send(payload); latch.await(200, TimeUnit.MILLISECONDS) } }
            assertTrue("datagram arrived", latch.count == 0L)
            assertEquals(900L, LanProtocol.decode(got!!, "fedcba9876543210")!!.scores.single().score)
        } finally {
            receiver.stop(); sender.stop()
        }
    }
}
