package com.iptvplayerpro.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifrado de credenciales sensibles (contraseñas Xtream, configuraciones VPN)
 * usando Android Keystore (AES/GCM).
 *
 * - El material de la clave nunca sale del dispositivo.
 * - En disco solo se guardan `Base64(iv || ciphertext)`.
 * - Nunca se registran valores en claro en Logcat.
 */
class CryptoManager {

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY = "iptv_player_pro_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(MASTER_KEY, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    /** Cifra un texto y devuelve Base64(iv + ciphertext). Devuelve null si el texto es vacío. */
    fun encrypt(plain: String?): String? {
        if (plain.isNullOrEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Nunca registrar el valor en claro; solo la causa.
            android.util.Log.w("CryptoManager", "No se pudo cifrar el valor: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Descifra un valor producido por [encrypt]. Devuelve null si no es posible. */
    fun decrypt(encoded: String?): String? {
        if (encoded.isNullOrEmpty()) return null
        return try {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            require(data.size > IV_LENGTH) { "datos cifrados demasiado cortos" }
            val iv = data.copyOfRange(0, IV_LENGTH)
            val payload = data.copyOfRange(IV_LENGTH, data.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(payload), Charsets.UTF_8)
        } catch (e: Exception) {
            android.util.Log.w("CryptoManager", "No se pudo descifrar el valor: ${e.javaClass.simpleName}")
            null
        }
    }
}
