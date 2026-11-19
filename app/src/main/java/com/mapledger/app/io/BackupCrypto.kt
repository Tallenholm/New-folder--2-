package com.mapledger.app.io

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Passphrase-encrypted backups: PBKDF2-HMAC-SHA256 (210k iterations) → AES-256-GCM.
 * File layout: "MLBK" | version(1) | salt(16) | iv(12) | ciphertext+tag.
 * The passphrase is never stored; a lost passphrase means the backup cannot be opened.
 */
object BackupCrypto {
    private val MAGIC = "MLBK".toByteArray()
    private const val VERSION: Byte = 1
    private const val ITER = 210_000

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also(rnd::nextBytes)
        val iv = ByteArray(12).also(rnd::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        return MAGIC + byteArrayOf(VERSION) + salt + iv + c.doFinal(plain)
    }

    /** @throws javax.crypto.AEADBadTagException on wrong passphrase or tampered file. */
    fun decrypt(blob: ByteArray, passphrase: CharArray): ByteArray {
        require(blob.size > 33 && blob.copyOfRange(0, 4).contentEquals(MAGIC)) { "Not a MapLedger backup file" }
        require(blob[4] == VERSION) { "Unsupported backup version ${blob[4]}" }
        val salt = blob.copyOfRange(5, 21)
        val iv = blob.copyOfRange(21, 33)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        c.updateAAD(MAGIC)
        return c.doFinal(blob, 33, blob.size - 33)
    }

    private fun key(pass: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, ITER, 256)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(k, "AES")
    }
}
