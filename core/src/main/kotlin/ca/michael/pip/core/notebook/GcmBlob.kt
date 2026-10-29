package ca.michael.pip.core.notebook

import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM sealing with the IV stored as a prefix (SPEC 6.1).
 *
 * The cipher generates a fresh random IV on every seal (FR-5.2). This works
 * unchanged with an Android Keystore key, which refuses caller-chosen IVs.
 */
object GcmBlob {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val IV_BYTES = 12
    private const val TAG_BITS = 128

    fun seal(key: Key, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        check(iv.size == IV_BYTES) { "unexpected IV size" }
        return iv + cipher.doFinal(plaintext)
    }

    fun open(key: Key, blob: ByteArray): ByteArray {
        require(blob.size > IV_BYTES) { "blob too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, blob, 0, IV_BYTES))
        return cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES)
    }
}
