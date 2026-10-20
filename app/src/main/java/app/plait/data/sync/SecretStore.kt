package app.plait.data.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Keeps a small secret (the sync passphrase) encrypted with a key that never leaves the phone's secure hardware or keystore. */
object SecretStore {
    private const val ALIAS = "plait_sync_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build(),
        )
        return gen.generateKey()
    }

    fun put(context: Context, name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = c.doFinal(value.toByteArray())
        context.getSharedPreferences("plait_secrets", Context.MODE_PRIVATE).edit()
            .putString(name, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    fun get(context: Context, name: String): String? = runCatching {
        val s = context.getSharedPreferences("plait_secrets", Context.MODE_PRIVATE).getString(name, null) ?: return null
        val (iv, data) = s.split(':')
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))) }
        String(c.doFinal(Base64.decode(data, Base64.NO_WRAP)))
    }.getOrNull()

    fun remove(context: Context, name: String) { context.getSharedPreferences("plait_secrets", Context.MODE_PRIVATE).edit().remove(name).apply() }
}
