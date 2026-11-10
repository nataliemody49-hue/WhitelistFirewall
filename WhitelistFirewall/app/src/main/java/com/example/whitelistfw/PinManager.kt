package com.example.whitelistfw

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PinManager {

    private fun prefs(c: Context) = EncryptedSharedPreferences.create(
        c, "secure",
        MasterKey.Builder(c).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun hash(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun hasPin(c: Context): Boolean = prefs(c).contains("hash")

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(pin, salt))
            .putInt("fails", 0)
            .putLong("lockUntil", 0L)
            .apply()
    }

    fun lockRemainingMs(c: Context): Long {
        val left = prefs(c).getLong("lockUntil", 0L) - System.currentTimeMillis()
        return if (left > 0) left else 0L
    }

    fun verify(c: Context, pin: String): Boolean {
        if (lockRemainingMs(c) > 0) return false
        val p = prefs(c)
        val salt = Base64.decode(p.getString("salt", "")!!, Base64.NO_WRAP)
        val ok = MessageDigest.isEqual(
            hash(pin, salt).toByteArray(),
            (p.getString("hash", "") ?: "").toByteArray()
        )
        val e = p.edit()
        if (ok) {
            e.putInt("fails", 0)
        } else {
            val f = p.getInt("fails", 0) + 1
            e.putInt("fails", f)
            if (f % 5 == 0) {
                // قفل مؤقت بعد كل 5 محاولات خاطئة، ويتضاعف حتى ساعة
                val secs = minOf(30L shl ((f / 5 - 1).coerceAtMost(7)), 3600L)
                e.putLong("lockUntil", System.currentTimeMillis() + secs * 1000)
            }
        }
        e.apply()
        return ok
    }
}
