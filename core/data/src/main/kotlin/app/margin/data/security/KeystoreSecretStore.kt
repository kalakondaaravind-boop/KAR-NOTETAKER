package app.margin.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.margin.domain.settings.SecretStore
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores user-supplied secrets (AI API keys) encrypted with an AES-256-GCM key that lives in the
 * Android Keystore and never leaves it. Ciphertext is kept in a separate DataStore file that is
 * excluded from Android cloud backup (see data_extraction_rules.xml): a restored device must
 * re-enter its keys, because the Keystore key cannot be migrated.
 */
class KeystoreSecretStore(private val store: DataStore<Preferences>) : SecretStore {
    private val alias = "margin.secrets.v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override suspend fun put(name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(value.toByteArray(Charsets.UTF_8))
        val blob = Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
        store.edit { it[stringPreferencesKey(name)] = blob }
    }

    override suspend fun get(name: String): String? {
        val blob = store.data.first()[stringPreferencesKey(name)] ?: return null
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(c.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated (e.g. device restore). Treat as absent; the UI asks the user to re-enter it.
            null
        }
    }

    override suspend fun remove(name: String) {
        store.edit { it.remove(stringPreferencesKey(name)) }
    }
}
