package com.superapp.app.features.editor.workspace

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object Encoding {

    /**
     * Detect encoding from a byte array using BOM + strict UTF-8 check.
     * Falls back to platform default charset.
     */
    fun detect(bytes: ByteArray): Charset {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) return StandardCharsets.UTF_8

        if (bytes.size >= 2) {
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return Charset.forName("UTF-16BE")
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return Charset.forName("UTF-16LE")
        }

        // Strict UTF-8 validation
        return try {
            val decoded = String(bytes, StandardCharsets.UTF_8)
            val reencoded = decoded.toByteArray(StandardCharsets.UTF_8)
            if (reencoded.contentEquals(bytes)) StandardCharsets.UTF_8
            else Charset.defaultCharset()
        } catch (_: Exception) {
            Charset.defaultCharset()
        }
    }

    fun decode(bytes: ByteArray): String {
        val cs = detect(bytes)
        return String(bytes, cs)
    }
}
