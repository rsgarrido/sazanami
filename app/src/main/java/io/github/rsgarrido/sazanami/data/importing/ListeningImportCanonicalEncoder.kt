package io.github.rsgarrido.sazanami.data.importing

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Small deterministic typed-field encoder used before hashing import evidence.
 * Integers and lengths are signed big-endian JVM primitives; field IDs must be strictly ascending.
 */
class ListeningImportCanonicalEncoder(
    formatVersion: Int,
    private val expectedFieldCount: Int
) {
    private val bytes = ByteArrayOutputStream()
    private val output = DataOutputStream(bytes)
    private var lastFieldId = 0
    private var fieldCount = 0

    init {
        require(formatVersion in 1..0xffff)
        require(expectedFieldCount in 0..0xffff)
        output.writeInt(MAGIC)
        output.writeShort(formatVersion)
        output.writeShort(expectedFieldCount)
    }

    fun writeNull(fieldId: Int): ListeningImportCanonicalEncoder = field(fieldId, NULL) {}

    fun writeUtf8(fieldId: Int, value: String?): ListeningImportCanonicalEncoder =
        if (value == null) writeNull(fieldId) else field(fieldId, UTF8) {
            val encoded = value.toByteArray(Charsets.UTF_8)
            output.writeInt(encoded.size)
            output.write(encoded)
        }

    fun writeLong(fieldId: Int, value: Long): ListeningImportCanonicalEncoder =
        field(fieldId, INT64) { output.writeLong(value) }

    fun writeInt(fieldId: Int, value: Int): ListeningImportCanonicalEncoder =
        field(fieldId, INT32) { output.writeInt(value) }

    fun toByteArray(): ByteArray {
        check(fieldCount == expectedFieldCount) {
            "Expected $expectedFieldCount canonical fields but encoded $fieldCount."
        }
        output.flush()
        return bytes.toByteArray()
    }

    private inline fun field(
        fieldId: Int,
        type: Int,
        writeValue: () -> Unit
    ): ListeningImportCanonicalEncoder {
        check(fieldId in 1..0xffff && fieldId > lastFieldId) {
            "Canonical field IDs must be strictly ascending."
        }
        check(fieldCount < expectedFieldCount) { "Too many canonical fields." }
        output.writeShort(fieldId)
        output.writeByte(type)
        writeValue()
        lastFieldId = fieldId
        fieldCount++
        return this
    }

    private companion object {
        const val MAGIC = 0x4344504c // CDPL
        const val NULL = 0
        const val UTF8 = 1
        const val INT64 = 2
        const val INT32 = 3
    }
}

