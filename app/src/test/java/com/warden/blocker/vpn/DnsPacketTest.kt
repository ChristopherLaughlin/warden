package com.warden.blocker.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {

    private val srcIp = byteArrayOf(10, 0, 0, 2)
    private val dstIp = byteArrayOf(10, 111, 222.toByte(), 2)
    private val srcPort = 12345
    private val dnsId = 0xABCD

    /** Build a minimal IPv4/UDP/DNS 'A' query for [host] destined for the virtual resolver. */
    private fun buildQuery(host: String): ByteArray {
        val labels = host.split(".")
        val question = ArrayList<Byte>()
        labels.forEach { label ->
            question.add(label.length.toByte())
            label.forEach { question.add(it.code.toByte()) }
        }
        question.add(0) // root
        question.addAll(listOf(0x00, 0x01).map { it.toByte() }) // type A
        question.addAll(listOf(0x00, 0x01).map { it.toByte() }) // class IN

        val dns = ArrayList<Byte>()
        dns.addAll(listOf((dnsId ushr 8), (dnsId and 0xFF)).map { it.toByte() })
        dns.addAll(listOf(0x01, 0x00).map { it.toByte() }) // flags: RD
        dns.addAll(listOf(0x00, 0x01).map { it.toByte() }) // qdcount 1
        dns.addAll(listOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00).map { it.toByte() }) // an/ns/ar
        dns.addAll(question)

        val udpLen = 8 + dns.size
        val udp = ArrayList<Byte>()
        udp.addAll(listOf((srcPort ushr 8), (srcPort and 0xFF)).map { it.toByte() })
        udp.addAll(listOf(0x00, 0x35).map { it.toByte() }) // dst port 53
        udp.addAll(listOf((udpLen ushr 8), (udpLen and 0xFF)).map { it.toByte() })
        udp.addAll(listOf(0x00, 0x00).map { it.toByte() }) // checksum 0
        udp.addAll(dns)

        val totalLen = 20 + udp.size
        val ip = ArrayList<Byte>()
        ip.add(0x45); ip.add(0x00) // v4, IHL 5, DSCP 0
        ip.addAll(listOf((totalLen ushr 8), (totalLen and 0xFF)).map { it.toByte() })
        ip.addAll(listOf(0x00, 0x00, 0x40, 0x00).map { it.toByte() }) // id, flags
        ip.add(0x40); ip.add(17) // TTL 64, proto UDP
        ip.addAll(listOf(0x00, 0x00).map { it.toByte() }) // header checksum 0 (parse ignores)
        srcIp.forEach { ip.add(it) }
        dstIp.forEach { ip.add(it) }
        ip.addAll(udp)
        return ip.toByteArray()
    }

    @Test fun parsesQueryNameAndPorts() {
        val packet = buildQuery("example.com")
        val parsed = DnsPacket.parse(packet, packet.size)
        assertNotNull(parsed)
        assertEquals("example.com", parsed!!.qname)
        assertEquals(53, parsed.dstPort)
        assertEquals(srcPort, parsed.srcPort)
    }

    @Test fun ignoresNonUdpAndTooShort() {
        assertNull(DnsPacket.parse(ByteArray(10), 10))
        val tcp = buildQuery("example.com").copyOf()
        tcp[9] = 6 // protocol TCP
        assertNull(DnsPacket.parse(tcp, tcp.size))
    }

    @Test fun rejectsBogusHeaderLength() {
        // IHL nibble claims a 60-byte header on a ~57-byte packet → must be rejected, not
        // parsed against stale buffer bytes.
        val p = buildQuery("example.com")
        p[0] = 0x4F.toByte() // version 4, IHL 15 (=60 bytes)
        assertNull(DnsPacket.parse(p, p.size))
    }

    @Test fun sinkholeResponseIsWellFormed() {
        val packet = buildQuery("tracker.example.com")
        val parsed = DnsPacket.parse(packet, packet.size)!!
        val resp = DnsPacket.buildSinkholeResponse(packet, packet.size, parsed)

        // IPv4, correct declared length, protocol preserved.
        assertEquals(4, (resp[0].toInt() ushr 4) and 0xF)
        val declaredLen = ((resp[2].toInt() and 0xFF) shl 8) or (resp[3].toInt() and 0xFF)
        assertEquals(resp.size, declaredLen)
        assertEquals(17, resp[9].toInt() and 0xFF)

        // Addresses swapped so it routes back to the querying app.
        assertEquals(dstIp.toList(), resp.copyOfRange(12, 16).toList())
        assertEquals(srcIp.toList(), resp.copyOfRange(16, 20).toList())

        // Valid IPv4 header checksum (ones-complement sum folds to 0xFFFF).
        assertTrue("bad IP checksum", ipChecksumValid(resp, 20))

        // DNS: QR set, one answer, and it resolves to 0.0.0.0.
        val d = 20 + 8
        assertTrue("QR bit not set", resp[d + 2].toInt() and 0x80 != 0)
        val ancount = ((resp[d + 6].toInt() and 0xFF) shl 8) or (resp[d + 7].toInt() and 0xFF)
        assertEquals(1, ancount)
        val lastFour = resp.copyOfRange(resp.size - 4, resp.size).toList()
        assertEquals(listOf<Byte>(0, 0, 0, 0), lastFour)
    }

    private fun ipChecksumValid(buf: ByteArray, headerLen: Int): Boolean {
        var sum = 0L
        var i = 0
        while (i < headerLen) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        while (sum shr 16 != 0L) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum == 0xFFFFL
    }
}
