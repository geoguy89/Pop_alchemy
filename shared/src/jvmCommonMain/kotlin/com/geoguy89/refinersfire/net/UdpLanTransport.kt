package com.geoguy89.refinersfire.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * UDP broadcast on the local network. Datagrams go to the limited broadcast address and to every interface's subnet
 * broadcast address, so they reach other copies of the game on the same Wi-Fi or wired LAN.
 *
 * [onActive] lets Android hold a Wi-Fi multicast lock while listening (some devices drop broadcasts without it).
 * [targets] overrides the destinations; tests use it to talk over loopback.
 */
class UdpLanTransport(
    private val port: Int = LanProtocol.PORT,
    private val targets: (() -> List<InetSocketAddress>)? = null,
    private val onActive: (Boolean) -> Unit = {},
) : LanTransport {
    @Volatile private var socket: DatagramSocket? = null
    private var sender: ExecutorService? = null

    @Synchronized
    override fun start(onReceive: (ByteArray) -> Unit) {
        if (socket != null) return
        val bindTo = InetSocketAddress(port) // Outside apply{}: there, `port` would be DatagramSocket.getPort().
        val s = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(bindTo)
            }
        } catch (e: Exception) {
            // Port taken or no network stack: sharing is simply off.
            return
        }
        socket = s
        sender = Executors.newSingleThreadExecutor { r -> Thread(r, "refinersfire-lan-send").apply { isDaemon = true } }
        onActive(true)
        thread(isDaemon = true, name = "refinersfire-lan-recv") {
            val buf = ByteArray(LanProtocol.MAX_PACKET + 1)
            while (socket === s) {
                try {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    onReceive(p.data.copyOfRange(p.offset, p.offset + p.length))
                } catch (e: SocketException) {
                    break // Closed by stop().
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun send(bytes: ByteArray) {
        val s = socket ?: return
        sender?.execute {
            for (addr in targets?.invoke() ?: broadcastTargets()) {
                try {
                    s.send(DatagramPacket(bytes, bytes.size, addr))
                } catch (_: Exception) {
                }
            }
        }
    }

    @Synchronized
    override fun stop() {
        val s = socket ?: return
        socket = null
        sender?.shutdownNow()
        sender = null
        s.close()
        onActive(false)
    }

    private fun broadcastTargets(): List<InetSocketAddress> {
        val addrs = LinkedHashSet<InetAddress>()
        addrs += InetAddress.getByName("255.255.255.255")
        try {
            for (ni in NetworkInterface.getNetworkInterfaces()) {
                if (!ni.isUp || ni.isLoopback) continue
                for (ia in ni.interfaceAddresses) {
                    if (ia.address is Inet4Address) ia.broadcast?.let { addrs += it }
                }
            }
        } catch (_: Exception) {
        }
        return addrs.map { InetSocketAddress(it, port) }
    }
}
