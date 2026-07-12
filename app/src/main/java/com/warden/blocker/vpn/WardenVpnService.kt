package com.warden.blocker.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.warden.blocker.MainActivity
import com.warden.blocker.R
import com.warden.blocker.WardenApp
import com.warden.blocker.wardenContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel

/**
 * Local, no-root VPN that captures DNS traffic and blackholes queries for blocked
 * domains. Non-blocked queries are forwarded to an upstream resolver and NAT'd back.
 * Only DNS is routed into the tunnel, so all other traffic is untouched.
 */
class WardenVpnService : VpnService() {

    private var tunnel: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null

    @Volatile private var blockedDomains: Set<String> = emptySet()

    /** The device's real DNS resolvers, captured before the tunnel takes over the active
     *  network. We forward non-blocked queries here so Warden never redirects your DNS to a
     *  third party of its own choosing. */
    @Volatile private var upstreamDns: List<InetAddress> = emptyList()
    private val fallbackDns: InetAddress by lazy { InetAddress.getByName(FALLBACK_DNS) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTunnel(); stopSelf(); return START_NOT_STICKY }
            else -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        if (tunnel != null) { scope.launch { refreshBlocklist() }; return }
        startForeground(NOTIF_ID, buildNotification())

        // Capture the real resolvers BEFORE establish() — afterwards the active network is us.
        upstreamDns = systemDnsServers()

        val builder = Builder()
            .setSession("Warden")
            .addAddress(VIRTUAL_ADDRESS, 24)
            .addDnsServer(VIRTUAL_DNS)
            .addRoute(VIRTUAL_DNS, 32) // only DNS to our virtual resolver enters the tunnel
            .setBlocking(true)
        runCatching { builder.addDisallowedApplication(packageName) } // never filter ourselves

        tunnel = builder.establish() ?: run {
            Log.e(TAG, "VPN establish() returned null (consent not granted?)")
            stopSelf(); return
        }

        running = true
        loopJob = scope.launch {
            refreshBlocklist()
            runPacketLoop(tunnel!!)
        }
    }

    private suspend fun refreshBlocklist() {
        blockedDomains = wardenContainer.blockEngine.blockedDomains().toSet()
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        // Guard the whole loop: a malformed packet or transient socket error must never
        // crash the app — it just stops filtering until blocking is toggled again.
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val packet = ByteArray(MTU)
        val upstream = try {
            DatagramChannel.open().also { protect(it.socket()) }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open upstream DNS socket; stopping tunnel", e)
            return
        }

        try {
            while (scope.isActive) {
                val read = try { input.read(packet) } catch (e: Exception) { break }
                if (read <= 0) continue
                val parsed = try { DnsPacket.parse(packet, read) } catch (e: Exception) { null } ?: continue
                if (parsed.dstPort != DnsPacket.DNS_PORT) continue

                val name = parsed.qname ?: continue
                if (isBlocked(name)) {
                    // Never log the domain — that would leak browsing history into logcat.
                    runCatching { output.write(DnsPacket.buildSinkholeResponse(packet, read, parsed)) }
                } else {
                    forward(packet, read, parsed, upstream, output)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Packet loop terminated unexpectedly", e)
        } finally {
            runCatching { upstream.close() }
        }
    }

    /** A domain is blocked if it equals or is a subdomain of any blocked entry. */
    private fun isBlocked(host: String): Boolean =
        blockedDomains.any { host == it || host.endsWith(".$it") }

    /** Forward the raw DNS query to a real resolver and write the reply back into the tun. */
    private fun forward(
        packet: ByteArray,
        length: Int,
        parsed: DnsPacket.Parsed,
        upstream: DatagramChannel,
        output: FileOutputStream,
    ) {
        try {
            val query = ByteBuffer.wrap(
                packet, parsed.dnsPayloadOffset, parsed.dnsPayloadLen,
            )
            val target = upstreamDns.firstOrNull() ?: fallbackDns
            upstream.send(query, InetSocketAddress(target, DnsPacket.DNS_PORT))
            val replyBuf = ByteBuffer.allocate(MTU)
            upstream.socket().soTimeout = 3000
            upstream.receive(replyBuf)
            replyBuf.flip()
            val reply = ByteArray(replyBuf.remaining())
            replyBuf.get(reply)
            output.write(wrapReply(packet, parsed, reply))
        } catch (e: Exception) {
            // Don't log the domain (privacy); the message alone is enough to diagnose.
            Log.w(TAG, "DNS forward failed: ${e.javaClass.simpleName}")
        }
    }

    /** The device's DNS servers on the underlying network (empty if unavailable). */
    private fun systemDnsServers(): List<InetAddress> = runCatching {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        val net = cm.activeNetwork ?: return emptyList()
        cm.getLinkProperties(net)?.dnsServers?.filterNot { it.isLoopbackAddress } ?: emptyList()
    }.getOrDefault(emptyList())

    /** Wrap a raw upstream DNS reply in IP/UDP headers pointed back at the querying app. */
    private fun wrapReply(request: ByteArray, parsed: DnsPacket.Parsed, dnsReply: ByteArray): ByteArray {
        val totalLen = parsed.ipHeaderLen + 8 + dnsReply.size
        val out = ByteBuffer.allocate(totalLen)
        out.put(request, 0, parsed.ipHeaderLen)
        out.putShort(2, totalLen.toShort())
        for (k in 0 until 4) out.put(12 + k, parsed.dstIp[k])
        for (k in 0 until 4) out.put(16 + k, parsed.srcIp[k])
        out.putShort(10, 0)
        // reuse DnsPacket's IP checksum by round-tripping through a sinkhole-style fix
        val arr = out.array()
        ipChecksum(arr, parsed.ipHeaderLen)
        val u = parsed.ipHeaderLen
        out.putShort(u, parsed.dstPort.toShort())
        out.putShort(u + 2, parsed.srcPort.toShort())
        out.putShort(u + 4, (8 + dnsReply.size).toShort())
        out.putShort(u + 6, 0)
        out.position(parsed.ipHeaderLen + 8)
        out.put(dnsReply)
        return arr
    }

    private fun ipChecksum(buf: ByteArray, headerLen: Int) {
        var sum = 0L
        var i = 0
        while (i < headerLen) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val c = sum.inv().toInt() and 0xFFFF
        buf[10] = (c ushr 8).toByte()
        buf[11] = (c and 0xFF).toByte()
    }

    private fun stopTunnel() {
        running = false
        loopJob?.cancel()
        runCatching { tunnel?.close() }
        tunnel = null
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, WardenApp.VPN_CHANNEL_ID)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        runCatching { tunnel?.close() }
        super.onDestroy()
    }

    companion object {
        /** Whether the filtering tunnel is currently up — read by the Home health indicator so
         *  users are never *silently* unblocked when the OS kills the service. */
        @Volatile var running = false
            private set

        private const val TAG = "WardenVpn"
        const val ACTION_START = "com.warden.blocker.VPN_START"
        const val ACTION_STOP = "com.warden.blocker.VPN_STOP"
        private const val VIRTUAL_ADDRESS = "10.111.222.1"
        private const val VIRTUAL_DNS = "10.111.222.2"
        // Used only if the OS exposes no resolver (rare); prefer the device's own DNS.
        private const val FALLBACK_DNS = "1.1.1.1"
        private const val MTU = 32767
        private const val NOTIF_ID = 1001
    }
}
