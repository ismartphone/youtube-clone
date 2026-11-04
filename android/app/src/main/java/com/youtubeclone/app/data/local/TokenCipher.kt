package com.youtubeclone.app.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chiffre les tokens en AES-GCM avec une clé de l'Android Keystore, une clé par compte (ADR-022) :
 * [alias] désigne la clé. La clé ne peut pas être extraite du téléphone : le fichier seul ne permet
 * pas de relire les tokens, et détruire la clé d'un compte rend ses tokens illisibles pour de bon.
 */
@Singleton
class TokenCipher @Inject constructor() {

    /** Renvoie l'IV suivi du texte chiffré, encodés en Base64. */
    fun encrypt(alias: String, plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(alias))
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /** Lève une exception si le texte a été modifié ou si la clé a disparu (réinstallation, restauration). */
    fun decrypt(alias: String, encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(alias),
            GCMParameterSpec(TAG_LENGTH_BITS, bytes, 0, IV_LENGTH),
        )
        return String(cipher.doFinal(bytes, IV_LENGTH, bytes.size - IV_LENGTH), Charsets.UTF_8)
    }

    /** Détruit la clé : tout ce qu'elle a chiffré devient illisible. Sans effet si elle n'existe pas. */
    @Synchronized
    fun deleteKey(alias: String) {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    @Synchronized
    private fun getOrCreateKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
