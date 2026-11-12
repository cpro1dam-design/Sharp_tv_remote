package tvremote.core

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * The phone's identity towards the TV: an RSA key and a self-signed certificate.
 *
 * The TV remembers this certificate once pairing succeeds, so it must be kept
 * (see [toPkcs12] / [fromPkcs12]) — a new identity means pairing again.
 */
class ClientIdentity(val privateKey: PrivateKey, val certificate: X509Certificate) {

    fun toPkcs12(): ByteArray {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry(ALIAS, privateKey, PASSWORD, arrayOf(certificate))
        return ByteArrayOutputStream().also { ks.store(it, PASSWORD) }.toByteArray()
    }

    /** TLS context presenting this certificate and accepting the TV's self-signed one. */
    fun sslContext(): SSLContext {
        val keyManager = object : X509ExtendedKeyManager() {
            override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
            override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
            // Server side is only used by tests that impersonate a TV.
            override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
            override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
            override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = ALIAS
            override fun getCertificateChain(alias: String?) = arrayOf(certificate)
            override fun getPrivateKey(alias: String?) = privateKey
        }
        // The TV uses a self-signed certificate: there is no authority to check it
        // against. Its key is bound into the pairing secret instead, so a fake TV
        // cannot complete pairing without the code shown on the real screen.
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        return SSLContext.getInstance("TLS").apply {
            init(arrayOf(keyManager), arrayOf(trustAll), SecureRandom())
        }
    }

    fun connect(host: String, port: Int, connectTimeoutMs: Int = 5000): SSLSocket {
        val socket = sslContext().socketFactory.createSocket() as SSLSocket
        try {
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.soTimeout = connectTimeoutMs
            socket.tcpNoDelay = true
            socket.startHandshake()
            return socket
        } catch (e: Exception) {
            runCatching { socket.close() }
            throw e
        }
    }

    companion object {
        private const val ALIAS = "tvremote"
        private val PASSWORD = "tvremote".toCharArray()

        fun generate(commonName: String = "atvremote"): ClientIdentity {
            val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()
            val name = X500Name("CN=$commonName")
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                name,
                BigInteger(64, SecureRandom()).abs().add(BigInteger.ONE),
                Date(now - TimeUnit.DAYS.toMillis(1)),
                Date(now + TimeUnit.DAYS.toMillis(365L * 30)),
                name,
                keyPair.public,
            )
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))
            return ClientIdentity(keyPair.private, cert)
        }

        fun fromPkcs12(data: ByteArray): ClientIdentity {
            val ks = KeyStore.getInstance("PKCS12")
            ks.load(ByteArrayInputStream(data), PASSWORD)
            val key = ks.getKey(ALIAS, PASSWORD) as PrivateKey
            val cert = ks.getCertificate(ALIAS) as X509Certificate
            return ClientIdentity(key, cert)
        }
    }
}
