package tvremote.core

import java.io.Closeable
import java.io.EOFException
import java.net.SocketException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/** What the TV reports about itself. */
data class TvInfo(val model: String?, val vendor: String?, val appVersion: String?)

data class VolumeInfo(val level: Int, val max: Int, val muted: Boolean)

interface RemoteListener {
    /** The TV accepted us: keys can be sent. */
    fun onReady(info: TvInfo) {}
    fun onPowerChanged(isOn: Boolean) {}
    fun onVolumeChanged(volume: VolumeInfo) {}
    fun onCurrentApp(packageName: String) {}
    /** The connection is gone; [error] is null when [RemoteClient.close] was called. */
    fun onDisconnected(error: Throwable?) {}
}

/** Thrown when the TV does not know our certificate: pair again. */
class NotPairedException(cause: Throwable? = null) :
    ProtocolException("Ce téléphone n'est pas (ou plus) appairé avec la télé", cause)

/**
 * Remote-control session over port 6466 (Android TV Remote v2).
 *
 * [connect] blocks until the TV has accepted the session (or fails); afterwards a
 * background thread answers the TV's pings and reports state changes.
 */
class RemoteClient(
    private val host: String,
    private val identity: ClientIdentity,
    private val listener: RemoteListener,
    private val port: Int = REMOTE_PORT,
) : Closeable {

    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false
    private val writeLock = Any()

    val isConnected: Boolean get() = socket != null && !closed

    fun connect(timeoutMs: Int = 6000) {
        val s = try {
            identity.connect(host, port, timeoutMs)
        } catch (e: SSLException) {
            // A TV that does not know our certificate aborts the TLS handshake.
            throw NotPairedException(e)
        }
        socket = s
        var info = TvInfo(null, null, null)

        // Handshake: TV sends configure -> we answer; TV sends set_active -> we answer.
        try {
            s.soTimeout = timeoutMs
            while (true) {
                val msg = ProtoMessage.parse(Framing.read(s.inputStream))
                if (msg.has(F_CONFIGURE)) {
                    val device = msg.message(F_CONFIGURE)?.message(2)
                    info = TvInfo(device?.string(1), device?.string(2), device?.string(6))
                    send(ProtoWriter().message(F_CONFIGURE) {
                        int(1, FEATURES)
                        message(2) {
                            string(1, "Telephone")
                            string(2, "TelecommandeTV")
                            int(3, 1)
                            string(4, "1")
                            string(5, "tvremote.app")
                            string(6, "1.0.0")
                        }
                    })
                } else if (msg.has(F_SET_ACTIVE)) {
                    send(ProtoWriter().message(F_SET_ACTIVE) { int(1, FEATURES) })
                    break
                } else {
                    handle(msg)
                }
            }
        } catch (e: Exception) {
            runCatching { s.close() }
            socket = null
            throw when (e) {
                is EOFException, is SSLException, is SocketException -> NotPairedException(e)
                else -> e
            }
        }

        s.soTimeout = 0
        listener.onReady(info)
        thread(name = "tv-remote-reader", isDaemon = true) { readLoop(s) }
    }

    /** Short press of an Android key code (see [KeyCodes]). */
    fun sendKey(keyCode: Int) = sendKey(keyCode, DIRECTION_SHORT)

    fun sendKey(keyCode: Int, direction: Int) =
        send(ProtoWriter().message(F_KEY_INJECT) { int(1, keyCode); int(2, direction) })

    /** Opens an app on the TV from a deep link, e.g. `https://www.youtube.com`. */
    fun openLink(url: String) =
        send(ProtoWriter().message(F_APP_LINK_LAUNCH) { string(1, url) })

    override fun close() {
        closed = true
        runCatching { socket?.close() }
        socket = null
    }

    private fun readLoop(s: SSLSocket) {
        var error: Throwable? = null
        try {
            while (!closed) handle(ProtoMessage.parse(Framing.read(s.inputStream)))
        } catch (e: Throwable) {
            if (!closed) error = e
        } finally {
            runCatching { s.close() }
            if (socket === s) socket = null
            listener.onDisconnected(error)
        }
    }

    private fun handle(msg: ProtoMessage) {
        when {
            msg.has(F_PING_REQUEST) -> {
                val v = msg.message(F_PING_REQUEST)?.int(1) ?: 0
                send(ProtoWriter().message(F_PING_RESPONSE) { int(1, v) })
            }
            msg.has(F_START) -> listener.onPowerChanged(msg.message(F_START)?.bool(1) ?: false)
            msg.has(F_SET_VOLUME_LEVEL) -> msg.message(F_SET_VOLUME_LEVEL)?.let {
                listener.onVolumeChanged(VolumeInfo(it.int(7).toInt(), it.int(6).toInt(), it.bool(8)))
            }
            msg.has(F_IME_KEY_INJECT) -> msg.message(F_IME_KEY_INJECT)?.message(1)?.string(12)?.let {
                listener.onCurrentApp(it)
            }
        }
    }

    private fun send(message: ProtoWriter) {
        val s = socket ?: throw SocketException("Pas connecté à la télé")
        synchronized(writeLock) { Framing.write(s.outputStream, message.toByteArray()) }
    }

    companion object {
        const val REMOTE_PORT = 6466

        const val DIRECTION_START_LONG = 1
        const val DIRECTION_END_LONG = 2
        const val DIRECTION_SHORT = 3

        /** Feature bits we announce: ping, key, power, volume, app link... (same value as the official app). */
        private const val FEATURES = 622

        private const val F_CONFIGURE = 1
        private const val F_SET_ACTIVE = 2
        private const val F_PING_REQUEST = 8
        private const val F_PING_RESPONSE = 9
        private const val F_KEY_INJECT = 10
        private const val F_IME_KEY_INJECT = 20
        private const val F_START = 40
        private const val F_SET_VOLUME_LEVEL = 50
        private const val F_APP_LINK_LAUNCH = 90
    }
}
