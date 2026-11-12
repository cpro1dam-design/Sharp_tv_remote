package tvremote.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProtocolTest {

    @Test
    fun `protobuf round trip`() {
        val bytes = ProtoWriter()
            .int(1, 2).int(2, 200).int(3, -1)
            .message(10) { string(1, "atvremote"); string(2, "Téléphone") }
            .bytes(40, byteArrayOf(1, 2, 3))
            .toByteArray()
        val msg = ProtoMessage.parse(bytes)
        assertEquals(2, msg.int(1))
        assertEquals(200, msg.int(2))
        assertEquals(-1, msg.int(3))
        assertEquals("Téléphone", msg.message(10)?.string(2))
        assertContentEquals(byteArrayOf(1, 2, 3), msg.bytes(40))
    }

    @Test
    fun `known encoding`() {
        // protocol_version=2, status=200 -> 08 02 10 c8 01
        assertContentEquals(
            byteArrayOf(0x08, 0x02, 0x10, 0xC8.toByte(), 0x01),
            ProtoWriter().int(1, 2).int(2, 200).toByteArray(),
        )
    }

    @Test
    fun `framing round trip with long message`() {
        val payload = ByteArray(300) { it.toByte() }
        val out = ByteArrayOutputStream()
        Framing.write(out, payload)
        assertEquals(302, out.size()) // 2-byte varint length
        assertContentEquals(payload, Framing.read(ByteArrayInputStream(out.toByteArray())))
    }

    @Test
    fun `identity survives pkcs12`() {
        val id = ClientIdentity.generate()
        val back = ClientIdentity.fromPkcs12(id.toPkcs12())
        assertEquals(id.certificate, back.certificate)
        assertContentEquals(id.privateKey.encoded, back.privateKey.encoded)
    }

    @Test
    fun `pairing then remote session against a fake TV`() {
        val tv = FakeTv()
        try {
            val phone = ClientIdentity.generate()

            // Wrong code is rejected locally, the connection stays usable.
            PairingClient("127.0.0.1", phone, "Test", tv.pairingPort).use { pairing ->
                pairing.start()
                val code = tv.code.poll(5, TimeUnit.SECONDS)!!
                val wrong = (if (code[0] == '0') "1" else "0") + code.substring(1)
                assertFailsWith<InvalidCodeException> { pairing.finish(wrong) }
                pairing.finish(code)
            }
            assertTrue(tv.isPaired(phone))

            val keys = CopyOnWriteArrayList<Int>()
            tv.onKey = { keys += it }
            val ready = CountDownLatch(1)
            val volume = CountDownLatch(1)
            var tvInfo: TvInfo? = null
            val remote = RemoteClient("127.0.0.1", phone, object : RemoteListener {
                override fun onReady(info: TvInfo) { tvInfo = info; ready.countDown() }
                override fun onVolumeChanged(volume: VolumeInfo) {
                    if (volume.level == 12 && volume.max == 100) volumeLatch.countDown()
                }
                private val volumeLatch = volume
            }, tv.remotePort)
            remote.connect()
            assertTrue(ready.await(5, TimeUnit.SECONDS))
            assertEquals(TvInfo("FakeTV", "Sharp", "5.0"), tvInfo)
            remote.sendKey(KeyCodes.VOLUME_UP)
            remote.sendKey(KeyCodes.HOME)
            assertTrue(volume.await(5, TimeUnit.SECONDS))
            tv.awaitKeys(2)
            assertEquals(listOf(KeyCodes.VOLUME_UP, KeyCodes.HOME), keys.toList())
            assertTrue(tv.pongs.get() >= 1)
            remote.close()

            // An unknown phone is refused.
            val stranger = ClientIdentity.generate()
            assertFailsWith<NotPairedException> {
                RemoteClient("127.0.0.1", stranger, object : RemoteListener {}, tv.remotePort).connect()
            }
        } finally {
            tv.close()
        }
    }
}

/**
 * Minimal stand-in for an Android TV, written independently of the client code
 * (secret computed from hex strings, the way the reference implementations do).
 */
private class FakeTv : AutoCloseable {
    private val identity = ClientIdentity.generate("FakeTV")
    private val ctx = identity.sslContext()
    private val pairing = (ctx.serverSocketFactory.createServerSocket(0, 5, InetAddress.getLoopbackAddress()) as SSLServerSocket)
        .apply { needClientAuth = true }
    private val remote = (ctx.serverSocketFactory.createServerSocket(0, 5, InetAddress.getLoopbackAddress()) as SSLServerSocket)
        .apply { needClientAuth = true }
    private val paired = CopyOnWriteArrayList<BigInteger>()
    val code = java.util.concurrent.LinkedBlockingQueue<String>()
    val pongs = java.util.concurrent.atomic.AtomicInteger()
    @Volatile var onKey: (Int) -> Unit = {}
    private val keyCount = java.util.concurrent.Semaphore(0)

    val pairingPort get() = pairing.localPort
    val remotePort get() = remote.localPort

    fun isPaired(id: ClientIdentity) = (id.certificate.publicKey as RSAPublicKey).modulus in paired

    fun awaitKeys(n: Int) = check(keyCount.tryAcquire(n, 5, TimeUnit.SECONDS))

    init {
        thread(isDaemon = true) { while (!pairing.isClosed) runCatching { servePairing(pairing.accept() as SSLSocket) } }
        thread(isDaemon = true) {
            while (!remote.isClosed) {
                val s = runCatching { remote.accept() as SSLSocket }.getOrNull() ?: continue
                thread(isDaemon = true) { runCatching { serveRemote(s) } }
            }
        }
    }

    private fun reply(s: SSLSocket, body: ProtoWriter.() -> Unit) =
        Framing.write(s.outputStream, ProtoWriter().int(1, 2).int(2, 200).apply(body).toByteArray())

    private fun servePairing(s: SSLSocket) = s.use {
        val clientKey = s.session.peerCertificates.first().publicKey as RSAPublicKey
        val serverKey = identity.certificate.publicKey as RSAPublicKey
        var expected = ""
        while (true) {
            val m = ProtoMessage.parse(Framing.read(s.inputStream))
            check(m.int(1) == 2L && m.int(2) == 200L)
            when {
                m.has(10) -> reply(s) { message(11) { string(1, "FakeTV") } }
                m.has(20) -> {
                    val enc = m.message(20)!!.messages(1).single()
                    check(enc.int(1) == 3L && enc.int(2) == 6L)
                    reply(s) { message(20) { message(2) { int(1, 3); int(2, 6) }; int(3, 1) } }
                }
                m.has(30) -> {
                    check(m.message(30)!!.int(2) == 1L)
                    reply(s) { message(31) {} }
                    // Pick a random 4-hex suffix, derive the checksum byte, "display" the code.
                    val suffix = "%04X".format(java.util.Random().nextInt(0x10000))
                    val hash = hash(clientKey, serverKey, suffix)
                    expected = "%02X".format(hash[0].toInt() and 0xFF) + suffix
                    code.put(expected)
                }
                m.has(40) -> {
                    val secret = m.message(40)!!.bytes(1)!!
                    if (secret.contentEquals(hash(clientKey, serverKey, expected.substring(2)))) {
                        paired += clientKey.modulus
                        reply(s) { message(41) { bytes(1, secret) } }
                    } else {
                        Framing.write(s.outputStream, ProtoWriter().int(1, 2).int(2, 402).toByteArray())
                    }
                    return
                }
            }
        }
    }

    private fun hash(client: RSAPublicKey, server: RSAPublicKey, suffix: String): ByteArray {
        fun hex(s: String) = BigInteger(s, 16).toByteArray().let { if (it[0] == 0.toByte() && it.size * 2 > s.length) it.copyOfRange(1, it.size) else it }
        val d = MessageDigest.getInstance("SHA-256")
        d.update(hex(client.modulus.toString(16).uppercase()))
        d.update(hex("0" + client.publicExponent.toString(16).uppercase()))
        d.update(hex(server.modulus.toString(16).uppercase()))
        d.update(hex("0" + server.publicExponent.toString(16).uppercase()))
        d.update(hex(suffix).let { b -> ByteArray(suffix.length / 2 - b.size) + b })
        return d.digest()
    }

    private fun serveRemote(s: SSLSocket) = s.use {
        val clientKey = s.session.peerCertificates.first().publicKey as RSAPublicKey
        if (clientKey.modulus !in paired) return
        fun send(w: ProtoWriter) = Framing.write(s.outputStream, w.toByteArray())
        send(ProtoWriter().message(1) { int(1, 622); message(2) { string(1, "FakeTV"); string(2, "Sharp"); string(6, "5.0") } })
        check(ProtoMessage.parse(Framing.read(s.inputStream)).message(1)!!.int(1) == 622L)
        send(ProtoWriter().message(2) { int(1, 622) })
        check(ProtoMessage.parse(Framing.read(s.inputStream)).has(2))
        send(ProtoWriter().message(8) { int(1, 7); int(2, 1) })
        send(ProtoWriter().message(50) { int(6, 100); int(7, 12); bool(8, false) })
        while (true) {
            val m = ProtoMessage.parse(Framing.read(s.inputStream))
            when {
                m.has(9) -> { check(m.message(9)!!.int(1) == 7L); pongs.incrementAndGet() }
                m.has(10) -> {
                    val k = m.message(10)!!
                    check(k.int(2) == 3L)
                    onKey(k.int(1).toInt()); keyCount.release()
                }
            }
        }
    }

    override fun close() {
        pairing.close(); remote.close()
    }
}
