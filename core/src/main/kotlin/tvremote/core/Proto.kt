package tvremote.core

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/**
 * Minimal Protocol Buffers encoder/decoder.
 *
 * The Android TV Remote protocol only uses a handful of small messages, so a
 * hand-written codec avoids pulling in protoc and generated code.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun int(field: Int, value: Long): ProtoWriter {
        tag(field, WIRE_VARINT)
        writeVarint(out, value)
        return this
    }

    fun int(field: Int, value: Int): ProtoWriter = int(field, value.toLong())

    fun bool(field: Int, value: Boolean): ProtoWriter = int(field, if (value) 1L else 0L)

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, WIRE_LENGTH_DELIMITED)
        writeVarint(out, value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, value: ProtoWriter): ProtoWriter = bytes(field, value.toByteArray())

    fun message(field: Int, build: ProtoWriter.() -> Unit): ProtoWriter =
        message(field, ProtoWriter().apply(build))

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wireType: Int) = writeVarint(out, ((field shl 3) or wireType).toLong())

    companion object {
        const val WIRE_VARINT = 0
        const val WIRE_FIXED64 = 1
        const val WIRE_LENGTH_DELIMITED = 2
        const val WIRE_FIXED32 = 5

        fun writeVarint(out: OutputStream, value: Long) {
            var v = value
            while (true) {
                if (v and 0x7FL.inv() == 0L) {
                    out.write(v.toInt())
                    return
                }
                out.write(((v and 0x7F) or 0x80).toInt())
                v = v ushr 7
            }
        }
    }
}

/** A decoded message: every field number maps to the list of values seen for it. */
class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    fun has(field: Int): Boolean = fields.containsKey(field)

    fun int(field: Int, default: Long = 0): Long = (fields[field]?.lastOrNull() as? Long) ?: default

    fun bool(field: Int): Boolean = int(field) != 0L

    fun bytes(field: Int): ByteArray? = fields[field]?.lastOrNull() as? ByteArray

    fun string(field: Int): String? = bytes(field)?.toString(Charsets.UTF_8)

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    fun messages(field: Int): List<ProtoMessage> =
        fields[field].orEmpty().filterIsInstance<ByteArray>().map { parse(it) }

    val fieldNumbers: Set<Int> get() = fields.keys

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = LinkedHashMap<Int, MutableList<Any>>()
            var pos = 0
            fun readVarint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (pos >= data.size) throw ProtocolException("Message protobuf tronqué")
                    val b = data[pos++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                    if (shift > 63) throw ProtocolException("Varint invalide")
                }
            }
            while (pos < data.size) {
                val key = readVarint()
                val field = (key ushr 3).toInt()
                val value: Any? = when ((key and 7).toInt()) {
                    ProtoWriter.WIRE_VARINT -> readVarint()
                    ProtoWriter.WIRE_LENGTH_DELIMITED -> {
                        val len = readVarint().toInt()
                        if (len < 0 || pos + len > data.size) throw ProtocolException("Longueur invalide")
                        data.copyOfRange(pos, pos + len).also { pos += len }
                    }
                    ProtoWriter.WIRE_FIXED64 -> { pos += 8; null }
                    ProtoWriter.WIRE_FIXED32 -> { pos += 4; null }
                    else -> throw ProtocolException("Type de champ inconnu")
                }
                if (value != null) fields.getOrPut(field) { mutableListOf() }.add(value)
            }
            return ProtoMessage(fields)
        }
    }
}

/** Messages on both ports are sent as `varint length` + `protobuf bytes`. */
object Framing {
    fun write(out: OutputStream, message: ByteArray) {
        val buf = ByteArrayOutputStream(message.size + 5)
        ProtoWriter.writeVarint(buf, message.size.toLong())
        buf.write(message)
        out.write(buf.toByteArray())
        out.flush()
    }

    fun read(input: InputStream, maxSize: Int = 1 shl 20): ByteArray {
        var len = 0L
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Connexion fermée par la télé")
            len = len or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) break
            shift += 7
            if (shift > 35) throw ProtocolException("Longueur de message invalide")
        }
        if (len > maxSize) throw ProtocolException("Message trop grand ($len octets)")
        val data = ByteArray(len.toInt())
        var read = 0
        while (read < data.size) {
            val n = input.read(data, read, data.size - read)
            if (n < 0) throw EOFException("Connexion fermée par la télé")
            read += n
        }
        return data
    }
}

open class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)
