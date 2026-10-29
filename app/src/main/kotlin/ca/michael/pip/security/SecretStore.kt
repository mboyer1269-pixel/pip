package ca.michael.pip.security

import android.content.Context
import android.util.Base64
import ca.michael.pip.core.notebook.GcmBlob

/**
 * The brain key and the ACP pairing secret (SEC-3). Only AES-GCM ciphertext
 * is written to disk; the key is in Keystore. Values are never logged.
 */
class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("pip_sealed", Context.MODE_PRIVATE)

    fun brainKey(): String? = read(BRAIN_KEY_PREF)?.toString(Charsets.UTF_8)
    fun hasBrainKey(): Boolean = prefs.contains(BRAIN_KEY_PREF)
    fun setBrainKey(value: String) = write(BRAIN_KEY_PREF, value.toByteArray(Charsets.UTF_8))
    fun clearBrainKey() = prefs.edit().remove(BRAIN_KEY_PREF).apply()

    fun acpSecret(): ByteArray? = read(ACP_PAIRING_PREF)
    fun hasAcpSecret(): Boolean = prefs.contains(ACP_PAIRING_PREF)
    fun setAcpSecret(value: ByteArray) = write(ACP_PAIRING_PREF, value)
    fun clearAcpSecret() = prefs.edit().remove(ACP_PAIRING_PREF).apply()

    private fun write(name: String, plain: ByteArray) {
        val sealed = GcmBlob.seal(KeystoreKeys.getOrCreate(KeystoreKeys.SECRETS), plain)
        prefs.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    private fun read(name: String): ByteArray? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            GcmBlob.open(KeystoreKeys.getOrCreate(KeystoreKeys.SECRETS), Base64.decode(stored, Base64.NO_WRAP))
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val BRAIN_KEY_PREF = "brain_key"
        const val ACP_PAIRING_PREF = "acp_secret"
    }
}
