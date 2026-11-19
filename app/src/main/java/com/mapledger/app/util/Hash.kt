package com.mapledger.app.util

import java.security.MessageDigest

object Hash {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun sha256(s: String): String = sha256(s.toByteArray(Charsets.UTF_8))
}
