package tvremote.core

import java.io.Closeable
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/**
 * Pairing over port 6467 ("Polo" protocol, Android TV Remote v2).
 *
 * 1. [start] connects and negotiates; the TV then shows a 6-character code.
 * 2. [finish] sends the secret derived from that code and both certificates.
 */
class PairingClient(
    private val host: String,
    private val identity: ClientIdentity,
    private val clientName: String,
    private val port: Int = PAIRING_PORT,
) : Closeable {

    private var socket: SSLSocket? = null

    /** Connects and asks the TV to display a pairing code. Blocking. */
    fun start() {
        val s = identity.connect(host, port)
        s.soTimeout = 15_000
        socket = s

        send(pairingMessage { message(F_PAIRING_REQUEST) { string(1, SERVICE_NAME); string(2, clientName) } })
        expect(F_PAIRING_REQUEST_ACK)

        send(pairingMessage {
            message(F_PAIRING_OPTION) {
                message(1) { int(1, ENCODING_HEXADECIMAL); int(2, CODE_SYMBOLS) } // input_encodings
                int(3, ROLE_INPUT) // preferred_role
            }
        })
        expect(F_PAIRING_OPTION)

        send(pairingMessage {
            message(F_PAIRING_CONFIGURATION) {
                message(1) { int(1, ENCODING_HEXADECIMAL); int(2, CODE_SYMBOLS) }
                int(2, ROLE_INPUT) // client_role
            }
        })
        expect(F_PAIRING_CONFIGURATION_ACK)
        // The code is now on the TV screen; the user may take a while to type it.
        s.soTimeout = 0
    }

    /** Sends the code shown on the TV. Throws [InvalidCodeException] if it is wrong. */
    fun finish(code: String) {
        val s = socket ?: throw IllegalStateException("start() n'a pas été appelé")
        val secret = computeSecret(code, identity.certificate, s.session.peerCertificates.first())
        s.soTimeout = 15_000
        send(pairingMessage { message(F_PAIRING_SECRET) { bytes(1, secret) } })
        try {
            expect(F_PAIRING_SECRET_ACK)
        } catch (e: PairingException) {
            throw InvalidCodeException("La télé a refusé le code", e)
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun pairingMessage(body: ProtoWriter.() -> Unit): ByteArray =
        ProtoWriter().int(1, PROTOCOL_VERSION).int(2, STATUS_OK).apply(body).toByteArray()

    private fun send(bytes: ByteArray) = Framing.write(socket!!.outputStream, bytes)

    private fun expect(field: Int): ProtoMessage {
        val msg = try {
            ProtoMessage.parse(Framing.read(socket!!.inputStream))
        } catch (e: Exception) {
            throw PairingException("La télé a interrompu l'appairage (${e.message})", e)
        }
        val status = msg.int(2)
        if (status != STATUS_OK) {
            throw PairingException(
                when (status) {
                    STATUS_BAD_SECRET -> "Code incorrect"
                    STATUS_BAD_CONFIGURATION -> "Configuration refusée par la télé"
                    else -> "Erreur d'appairage (statut $status)"
                }
            )
        }
        if (!msg.has(field)) throw PairingException("Réponse inattendue de la télé")
        return msg
    }

    companion object {
        const val PAIRING_PORT = 6467
        private const val SERVICE_NAME = "atvremote"
        private const val PROTOCOL_VERSION = 2
        private const val STATUS_OK = 200L
        private const val STATUS_BAD_CONFIGURATION = 401L
        private const val STATUS_BAD_SECRET = 402L

        private const val F_PAIRING_REQUEST = 10
        private const val F_PAIRING_REQUEST_ACK = 11
        private const val F_PAIRING_OPTION = 20
        private const val F_PAIRING_CONFIGURATION = 30
        private const val F_PAIRING_CONFIGURATION_ACK = 31
        private const val F_PAIRING_SECRET = 40
        private const val F_PAIRING_SECRET_ACK = 41

        private const val ENCODING_HEXADECIMAL = 3
        private const val CODE_SYMBOLS = 6
        private const val ROLE_INPUT = 1

        /**
         * secret = SHA-256(client modulus ‖ client exponent ‖ server modulus ‖ server exponent ‖ code[2..6]).
         * The first byte of the hash must equal code[0..2]: the TV uses it as a checksum,
         * which lets us reject a mistyped code before sending it.
         */
        fun computeSecret(code: String, client: Certificate, server: Certificate): ByteArray {
            val clean = code.trim().uppercase()
            if (clean.length != CODE_SYMBOLS || clean.any { it !in "0123456789ABCDEF" }) {
                throw InvalidCodeException("Le code doit faire 6 caractères (chiffres et lettres A à F)")
            }
            val c = client.publicKey as RSAPublicKey
            val s = server.publicKey as RSAPublicKey
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(unsigned(c.modulus))
            digest.update(unsigned(c.publicExponent))
            digest.update(unsigned(s.modulus))
            digest.update(unsigned(s.publicExponent))
            digest.update(hex(clean.substring(2)))
            val hash = digest.digest()
            if ((hash[0].toInt() and 0xFF) != clean.substring(0, 2).toInt(16)) {
                throw InvalidCodeException("Code incorrect, vérifie les caractères affichés sur la télé")
            }
            return hash
        }

        private fun unsigned(n: BigInteger): ByteArray {
            val b = n.toByteArray()
            return if (b.size > 1 && b[0] == 0.toByte()) b.copyOfRange(1, b.size) else b
        }

        private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}

open class PairingException(message: String, cause: Throwable? = null) : ProtocolException(message, cause)

class InvalidCodeException(message: String, cause: Throwable? = null) : PairingException(message, cause)
