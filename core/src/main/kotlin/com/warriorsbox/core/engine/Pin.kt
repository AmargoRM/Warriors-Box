package com.warriorsbox.core.engine

import java.security.MessageDigest
import java.security.SecureRandom

/** PIN de 4 dígitos del modo entrenador. Se guarda solo el hash con sal, nunca el PIN. */
object Pin {

    fun isValidFormat(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        var data = (salt + ":" + pin).toByteArray()
        repeat(10_000) { data = digest.digest(data) }
        return data.toHex()
    }

    /** Formato guardado: "sal$hash". */
    fun encode(pin: String): String {
        val salt = newSalt()
        return salt + "$" + hash(pin, salt)
    }

    fun verify(pin: String, stored: String?): Boolean {
        if (stored.isNullOrBlank() || '$' !in stored) return false
        val (salt, expected) = stored.split('$', limit = 2)
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
