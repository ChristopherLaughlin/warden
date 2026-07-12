package com.warden.blocker.vpn

import java.nio.ByteBuffer

/**
 * Minimal IPv4 + UDP + DNS helpers, just enough to sinkhole or forward DNS queries.
 * Deliberately handles only the common case (IPv4 / UDP / single question). TCP-DNS,
 * IPv6 and EDNS edge cases are punted to the forwarder and left as follow-ups.
 */
object DnsPacket {

    private const val IPV4_MIN_HEADER = 20
    private const val UDP_HEADER = 8
    const val DNS_PORT = 53

    data class Parsed(
        val ipHeaderLen: Int,
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dnsPayloadOffset: Int,
        val dnsPayloadLen: Int,
        val qname: String?,
    )

    /** Returns null if the packet isn't IPv4/UDP or is malformed. */
    fun parse(packet: ByteArray, length: Int): Parsed? {
        if (length < IPV4_MIN_HEADER + UDP_HEADER) return null
        val version = (packet[0].toInt() ushr 4) and 0xF
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0xF) * 4
        // ihl comes from the packet itself; validate it fits before reading UDP/DNS at that
        // offset, so a malformed header can't make us parse stale bytes from the reused buffer.
        if (ihl < IPV4_MIN_HEADER || ihl + UDP_HEADER > length) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17 /* UDP */) return null

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)
        val udpOffset = ihl
        val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or (packet[udpOffset + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or (packet[udpOffset + 3].toInt() and 0xFF)
        val dnsOffset = udpOffset + UDP_HEADER
        val dnsLen = length - dnsOffset
        if (dnsLen <= 12) return null

        val qname = runCatching { readQName(packet, dnsOffset + 12) }.getOrNull()
        return Parsed(ihl, srcIp, dstIp, srcPort, dstPort, dnsOffset, dnsLen, qname)
    }

    /** Reads the QNAME labels of the first DNS question, lowercased, no trailing dot. */
    private fun readQName(buf: ByteArray, start: Int): String {
        val sb = StringBuilder()
        var i = start
        while (i < buf.size) {
            val len = buf[i].toInt() and 0xFF
            if (len == 0) break
            if (len and 0xC0 != 0) break // compression pointer; unexpected in a question
            i++
            if (i + len > buf.size) break
            if (sb.isNotEmpty()) sb.append('.')
            for (j in 0 until len) sb.append((buf[i + j].toInt() and 0xFF).toChar())
            i += len
        }
        return sb.toString().lowercase()
    }

    /**
     * Builds a response packet that answers the query in [request] with A-record 0.0.0.0,
     * effectively blackholing the domain. IP src/dst and UDP ports are swapped so it
     * routes straight back to the querying app.
     */
    fun buildSinkholeResponse(request: ByteArray, length: Int, parsed: Parsed): ByteArray {
        val dnsHeaderLen = 12
        val questionLen = parsed.dnsPayloadLen - dnsHeaderLen
        // Answer: pointer to name (0xC00C) + type A + class IN + TTL + rdlen 4 + 0.0.0.0
        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,
            0x00, 0x01, // type A
            0x00, 0x01, // class IN
            0x00, 0x00, 0x00, 0x1E, // TTL 30s
            0x00, 0x04, // rdlength
            0x00, 0x00, 0x00, 0x00, // 0.0.0.0
        )
        val dnsLen = parsed.dnsPayloadLen + answer.size
        val totalLen = parsed.ipHeaderLen + UDP_HEADER + dnsLen
        val out = ByteBuffer.allocate(totalLen)

        // --- IPv4 header (copy request, swap addresses, fix length) ---
        out.put(request, 0, parsed.ipHeaderLen)
        out.putShort(2, totalLen.toShort())
        // swap src/dst
        for (k in 0 until 4) out.put(12 + k, parsed.dstIp[k])
        for (k in 0 until 4) out.put(16 + k, parsed.srcIp[k])
        out.putShort(10, 0) // checksum zeroed, recomputed below
        fixIpChecksum(out.array(), parsed.ipHeaderLen)

        // --- UDP header (swap ports, fix length) ---
        val u = parsed.ipHeaderLen
        out.putShort(u, parsed.dstPort.toShort())
        out.putShort(u + 2, parsed.srcPort.toShort())
        out.putShort(u + 4, (UDP_HEADER + dnsLen).toShort())
        out.putShort(u + 6, 0) // UDP checksum optional for IPv4

        // --- DNS: copy header+question from request, flip QR/AA, set answer count ---
        val d = parsed.ipHeaderLen + UDP_HEADER
        out.position(d)
        out.put(request, parsed.dnsPayloadOffset, dnsHeaderLen + questionLen)
        val arr = out.array()
        arr[d + 2] = (arr[d + 2].toInt() or 0x80).toByte() // QR = response
        arr[d + 6] = 0x00; arr[d + 7] = 0x01 // ANCOUNT = 1
        out.position(d + dnsHeaderLen + questionLen)
        out.put(answer)
        return arr
    }

    private fun fixIpChecksum(buf: ByteArray, headerLen: Int) {
        var sum = 0L
        var i = 0
        while (i < headerLen) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv().toInt() and 0xFFFF
        buf[10] = (checksum ushr 8).toByte()
        buf[11] = (checksum and 0xFF).toByte()
    }
}
